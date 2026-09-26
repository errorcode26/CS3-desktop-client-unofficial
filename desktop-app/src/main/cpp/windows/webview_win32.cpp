#include "../include/player_bridge_common.h"

// WebView2 globals
ICoreWebView2Controller* g_webviewController = nullptr;
ICoreWebView2*           g_webview           = nullptr;
bool                     g_webviewReady      = false;
std::atomic<bool>        g_uiReady{false};
std::wstring             g_pendingUrl        = L"";
std::mutex               g_pendingUrlMutex;

std::thread             g_uiThread;
DWORD                   g_uiThreadId = 0;
std::mutex              g_initMutex;
std::condition_variable g_initCv;
bool                    g_initComplete = false;

extern HWND g_hostHwnd;
extern HWND g_containerHwnd;
extern HWND g_messageHwnd;

typedef HRESULT(STDAPICALLTYPE *CreateCoreWebView2EnvironmentWithOptionsFunc)(
    PCWSTR browserExecutableFolder, PCWSTR userDataFolder,
    ICoreWebView2EnvironmentOptions* environmentOptions,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* environmentCreatedHandler);

class WebMessageReceivedHandler : public ICoreWebView2WebMessageReceivedEventHandler {
    ULONG m_refCount = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppvObject) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2WebMessageReceivedEventHandler) {
            *ppvObject = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef()  override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG count = --m_refCount;
        if (count == 0) delete this;
        return count;
    }
    HRESULT STDMETHODCALLTYPE Invoke(ICoreWebView2* sender,
                                     ICoreWebView2WebMessageReceivedEventArgs* args) override {
        PWSTR messageJson = nullptr;
        if (SUCCEEDED(args->get_WebMessageAsJson(&messageJson)) && messageJson) {
            std::wstring wjson(messageJson);
            CoTaskMemFree(messageJson);

            auto extractStr = [&](const std::wstring& key) -> std::wstring {
                std::wstring needle = L"\"" + key + L"\":\"";
                auto pos = wjson.find(needle);
                if (pos == std::wstring::npos) return L"";
                pos += needle.size();
                auto end = wjson.find(L'"', pos);
                return (end != std::wstring::npos) ? wjson.substr(pos, end - pos) : L"";
            };
            auto extractNum = [&](const std::wstring& key) -> std::wstring {
                std::wstring r = extractStr(key);
                if (!r.empty()) return r;
                std::wstring needle = L"\"" + key + L"\":";
                auto pos = wjson.find(needle);
                if (pos == std::wstring::npos) return L"";
                pos += needle.size();
                if (pos < wjson.size() && wjson[pos] == L'"') {
                    pos++;
                    auto end = wjson.find(L'"', pos);
                    return (end != std::wstring::npos) ? wjson.substr(pos, end - pos) : L"";
                }
                auto end = pos;
                while (end < wjson.size() && (iswdigit(wjson[end]) || wjson[end] == L'.' || wjson[end] == L'-')) end++;
                return wjson.substr(pos, end - pos);
            };

            std::wstring evType = extractStr(L"type");
            std::wstring evValStr = extractStr(L"value");
            std::string valUtf8(evValStr.begin(), evValStr.end());
            bool handled = false;

            if (g_mpv_command_string && g_mpv_set_property_string) {
                if (evType == L"seekTo" || evType == L"seekBy") {
                    std::wstring wval = extractNum(L"value");
                    if (!wval.empty()) {
                        double ms = _wtof(wval.c_str());
                        double sec = ms / 1000.0;
                        char buf[64];
                        if (evType == L"seekTo") {
                            char startBuf[32];
                            snprintf(startBuf, sizeof(startBuf), "%.3f", sec);
                            snprintf(buf, sizeof(buf), "seek %.3f absolute", sec);
                            std::lock_guard<std::mutex> lk(g_mpvMutex);
                            if (g_mpvHandle) {
                                g_mpv_set_property_string(g_mpvHandle, "start", startBuf);
                                g_mpv_command_string(g_mpvHandle, buf);
                                handled = true;
                            }
                        } else {
                            snprintf(buf, sizeof(buf), "seek %.3f relative", sec);
                            std::lock_guard<std::mutex> lk(g_mpvMutex);
                            if (g_mpvHandle) {
                                g_mpv_command_string(g_mpvHandle, buf);
                                handled = true;
                            }
                        }
                    }
                } else if (evType == L"togglePlay") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_command_string(g_mpvHandle, "cycle pause");
                        handled = true;
                    }
                } else if (evType == L"play") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_set_property_string(g_mpvHandle, "pause", "no");
                        handled = true;
                    }
                } else if (evType == L"pause") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_set_property_string(g_mpvHandle, "pause", "yes");
                        handled = true;
                    }
                } else if (evType == L"toggleMute") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_command_string(g_mpvHandle, "cycle mute");
                        handled = true;
                    }
                } else if (evType == L"setVolume") {
                    std::wstring wval = extractNum(L"value");
                    if (!wval.empty()) {
                        char buf[64];
                        snprintf(buf, sizeof(buf), "set volume %.1f", _wtof(wval.c_str()));
                        std::lock_guard<std::mutex> lk(g_mpvMutex);
                        if (g_mpvHandle) {
                            g_mpv_command_string(g_mpvHandle, buf);
                            handled = true;
                        }
                    }
                } else if (evType == L"setSpeed") {
                    std::wstring wval = extractNum(L"value");
                    if (!wval.empty()) {
                        std::string sp(wval.begin(), wval.end());
                        std::lock_guard<std::mutex> lk(g_mpvMutex);
                        if (g_mpvHandle) {
                            g_mpv_set_property_string(g_mpvHandle, "speed", sp.c_str());
                            handled = true;
                        }
                    }
                } else if (evType == L"setSubDelay") {
                    std::wstring wval = extractNum(L"value");
                    if (!wval.empty()) {
                        char buf[64];
                        snprintf(buf, sizeof(buf), "add sub-delay %.3f", _wtof(wval.c_str()));
                        std::lock_guard<std::mutex> lk(g_mpvMutex);
                        if (g_mpvHandle) {
                            g_mpv_command_string(g_mpvHandle, buf);
                            handled = true;
                        }
                    }
                } else if (evType == L"cycleSubtitles") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_command_string(g_mpvHandle, "cycle sub");
                        handled = true;
                    }
                } else if (evType == L"toggleSubVisibility") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_command_string(g_mpvHandle, "cycle sub-visibility");
                        handled = true;
                    }
                } else if (evType == L"setSubtitleTrack") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_set_property_string(g_mpvHandle, "sid", valUtf8.c_str());
                        handled = true;
                    }
                } else if (evType == L"setAudioTrack") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_set_property_string(g_mpvHandle, "aid", valUtf8.c_str());
                        handled = true;
                    }
                } else if (evType == L"setVideoTrack") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_set_property_string(g_mpvHandle, "vid", valUtf8.c_str());
                        handled = true;
                    }
                } else if (evType == L"setAspect") {
                    std::lock_guard<std::mutex> lk(g_mpvMutex);
                    if (g_mpvHandle) {
                        g_mpv_set_property_string(g_mpvHandle, "video-aspect-override", valUtf8.c_str());
                        handled = true;
                    }
                } else if (evType == L"setMpvProperty") {
                    auto colonPos = valUtf8.find(':');
                    if (colonPos != std::string::npos) {
                        std::string pName = valUtf8.substr(0, colonPos);
                        std::string pVal = valUtf8.substr(colonPos + 1);
                        std::lock_guard<std::mutex> lk(g_mpvMutex);
                        if (g_mpvHandle) {
                            g_mpv_set_property_string(g_mpvHandle, pName.c_str(), pVal.c_str());
                            handled = true;
                        }
                    }
                } else if (evType == L"exitPlayer") {
                    dispatchPlayerEvent(wjson);
                    handled = true;
                }
            }

            if (evType == L"toggleStats") {
                g_statsVisible = !g_statsVisible;
                handled = true;
            }

            if (evType == L"ui_ready") {
                g_uiReady = true;
                if (g_messageHwnd) {
                    KillTimer(g_messageHwnd, 0x4E52);
                }
                if (g_webviewController) {
                    g_webviewController->put_IsVisible(TRUE);
                }
            }

            if (!handled) {
                dispatchPlayerEvent(wjson);
            }
        }
        return S_OK;
    }
};

class AcceleratorKeyPressedHandler : public ICoreWebView2AcceleratorKeyPressedEventHandler {
    ULONG m_refCount = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppvObject) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2AcceleratorKeyPressedEventHandler) {
            *ppvObject = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef()  override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG count = --m_refCount;
        if (count == 0) delete this;
        return count;
    }
    HRESULT STDMETHODCALLTYPE Invoke(ICoreWebView2Controller* sender,
                                     ICoreWebView2AcceleratorKeyPressedEventArgs* args) override {
        COREWEBVIEW2_KEY_EVENT_KIND keyEventKind;
        args->get_KeyEventKind(&keyEventKind);
        UINT virtualKey;
        args->get_VirtualKey(&virtualKey);

        BOOL isCtrl = (GetKeyState(VK_CONTROL) & 0x8000) != 0;
        if (isCtrl && (virtualKey == VK_OEM_PLUS || virtualKey == VK_OEM_MINUS || virtualKey == '0' ||
                       virtualKey == VK_NUMPAD0 || virtualKey == VK_ADD || virtualKey == VK_SUBTRACT)) {
            args->put_Handled(TRUE);
            return S_OK;
        }

        if (virtualKey == VK_ESCAPE &&
            (keyEventKind == COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN ||
             keyEventKind == COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN)) {
            BOOL isFullScreen = FALSE;
            if (g_webview && SUCCEEDED(g_webview->get_ContainsFullScreenElement(&isFullScreen)) && isFullScreen) {
                // Let WebView2 handle exiting fullscreen
            } else {
                args->put_Handled(TRUE);
                dispatchPlayerEvent(L"{\"type\":\"close\",\"value\":\"\"}");
            }
            return S_OK;
        }

        if (virtualKey == VK_F11 &&
            (keyEventKind == COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN ||
             keyEventKind == COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN)) {
            args->put_Handled(TRUE);
            dispatchPlayerEvent(L"{\"type\":\"toggleFullscreen\",\"value\":\"\"}");
        }
        return S_OK;
    }
};

class FullScreenChangedHandler : public ICoreWebView2ContainsFullScreenElementChangedEventHandler {
    ULONG m_refCount = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppvObject) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2ContainsFullScreenElementChangedEventHandler) {
            *ppvObject = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG count = --m_refCount;
        if (count == 0) delete this;
        return count;
    }
    HRESULT STDMETHODCALLTYPE Invoke(ICoreWebView2* sender, IUnknown* args) override {
        BOOL isFullScreen = FALSE;
        if (sender && SUCCEEDED(sender->get_ContainsFullScreenElement(&isFullScreen))) {
            postUiTask([isFullScreen]() {
                if (isFullScreen) {
                    if (g_containerHwnd) {
                        HMONITOR hMon = MonitorFromWindow(g_containerHwnd, MONITOR_DEFAULTTONEAREST);
                        MONITORINFO mi = { sizeof(mi) };
                        if (GetMonitorInfoW(hMon, &mi)) {
                            int monW = mi.rcMonitor.right - mi.rcMonitor.left;
                            int monH = mi.rcMonitor.bottom - mi.rcMonitor.top;
                            SetParent(g_containerHwnd, nullptr);
                            SetWindowLongPtrW(g_containerHwnd, GWL_STYLE, WS_POPUP | WS_VISIBLE);
                            SetWindowPos(g_containerHwnd, HWND_TOPMOST, mi.rcMonitor.left, mi.rcMonitor.top, monW, monH, SWP_SHOWWINDOW);
                            if (g_webviewController) {
                                RECT r = {0, 0, (LONG)monW, (LONG)monH};
                                g_webviewController->put_Bounds(r);
                            }
                        }
                    }
                } else {
                    if (g_containerHwnd && g_hostHwnd) {
                        SetWindowLongPtrW(g_containerHwnd, GWL_STYLE, WS_CHILD | WS_VISIBLE);
                        SetParent(g_containerHwnd, g_hostHwnd);
                        RECT clientRect = {};
                        GetClientRect(g_hostHwnd, &clientRect);
                        int w = clientRect.right - clientRect.left;
                        int h = clientRect.bottom - clientRect.top;
                        SetWindowPos(g_containerHwnd, HWND_TOP, 0, 0, w, h, SWP_SHOWWINDOW);
                        if (g_webviewController) {
                            RECT r = {0, 0, (LONG)w, (LONG)h};
                            g_webviewController->put_Bounds(r);
                        }
                    }
                }
            });
        }
        return S_OK;
    }
};

class ControllerCompletedHandler : public ICoreWebView2CreateCoreWebView2ControllerCompletedHandler {
    ULONG m_refCount = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppvObject) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2CreateCoreWebView2ControllerCompletedHandler) {
            *ppvObject = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef()  override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG count = --m_refCount;
        if (count == 0) delete this;
        return count;
    }
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2Controller* controller) override {
        std::cout << "[NativeBridge] ControllerCompleted, HRESULT=0x"
                  << std::hex << result << std::dec << std::endl;

        if (FAILED(result) || controller == nullptr) {
            std::cerr << "[NativeBridge] WebView2 controller creation FAILED!" << std::endl;
            return S_OK;
        }

        g_webviewController = controller;
        g_webviewController->AddRef();
        g_webviewController->put_IsVisible(FALSE);
        g_webviewController->put_ZoomFactor(1.0);

        ICoreWebView2Controller2* controller2 = nullptr;
        if (SUCCEEDED(g_webviewController->QueryInterface(IID_ICoreWebView2Controller2, (void**)&controller2))) {
            COREWEBVIEW2_COLOR transparent = {0, 0, 0, 0};
            controller2->put_DefaultBackgroundColor(transparent);
            controller2->Release();
        }

        g_webviewController->get_CoreWebView2(&g_webview);

        RECT bounds;
        GetClientRect(g_containerHwnd, &bounds);
        g_webviewController->put_Bounds(bounds);

        ICoreWebView2Settings* settings = nullptr;
        if (g_webview && SUCCEEDED(g_webview->get_Settings(&settings)) && settings) {
            settings->put_AreDefaultContextMenusEnabled(FALSE);
            settings->put_IsStatusBarEnabled(FALSE);
            settings->put_IsZoomControlEnabled(FALSE);
            settings->Release();
        }

        EventRegistrationToken token;
        g_webview->add_WebMessageReceived(new WebMessageReceivedHandler(), &token);

        EventRegistrationToken accelToken;
        g_webviewController->add_AcceleratorKeyPressed(new AcceleratorKeyPressedHandler(), &accelToken);

        EventRegistrationToken fsToken;
        g_webview->add_ContainsFullScreenElementChanged(new FullScreenChangedHandler(), &fsToken);

        g_webviewReady = true;
        LOG_TO_FILE("[NativeBridge] WebView2 Initialized Successfully!");

        // Safe fallback in case ui_ready message is delayed; primary reveal is driven by ui_ready
        SetTimer(g_messageHwnd, 0x4E52, 2500, nullptr);

        SetWindowPos(g_containerHwnd, HWND_TOP, 0, 0, 0, 0,
                     SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);

        {
            std::lock_guard<std::mutex> lock(g_pendingUrlMutex);
            if (!g_pendingUrl.empty()) {
                g_webview->Navigate(g_pendingUrl.c_str());
                g_pendingUrl.clear();
            }
        }

        return S_OK;
    }
};

class EnvironmentCompletedHandler : public ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
    ULONG m_refCount = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppvObject) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler) {
            *ppvObject = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef()  override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG count = --m_refCount;
        if (count == 0) delete this;
        return count;
    }
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2Environment* env) override {
        std::cout << "[NativeBridge] EnvironmentCompleted, HRESULT=0x"
                  << std::hex << result << std::dec << std::endl;
        if (env) {
            env->CreateCoreWebView2Controller(g_containerHwnd, new ControllerCompletedHandler());
        }
        return S_OK;
    }
};

LRESULT CALLBACK ContainerWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_ERASEBKGND: {
            RECT rect = {};
            GetClientRect(hwnd, &rect);
            FillRect((HDC)wParam, &rect, (HBRUSH)GetStockObject(BLACK_BRUSH));
            return 1;
        }
        case WM_SIZE:
            return 0;
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

LRESULT CALLBACK MessageWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_APP + 0x4E50: {
            processUiTasks();
            return 0;
        }
        case WM_TIMER: {
            if (wParam == 0x4E52) {
                KillTimer(hwnd, 0x4E52);
                if (g_webviewController && g_webviewReady) {
                    g_uiReady = true;
                    g_webviewController->put_IsVisible(TRUE);
                }
                return 0;
            }
            if (wParam == 0x4E51) {
                std::lock_guard<std::mutex> lock(g_mpvMutex);
                if (g_webviewReady && g_webview && g_mpvHandle) {
                    static HMODULE mpvDll = nullptr;
                    static mpv_get_property_fn get_prop = nullptr;
                    if (!mpvDll) {
                        mpvDll = LoadLibraryA("libmpv-2.dll");
                        if (!mpvDll) mpvDll = LoadLibraryA("mpv-2.dll");
                        if (!mpvDll) mpvDll = LoadLibraryA("mpv.dll");
                        if (mpvDll) {
                            get_prop = (mpv_get_property_fn)GetProcAddress(mpvDll, "mpv_get_property");
                            if (!g_mpv_get_property)
                                g_mpv_get_property = get_prop;
                            if (!g_mpv_get_property_string)
                                g_mpv_get_property_string = (mpv_get_property_string_fn)GetProcAddress(mpvDll, "mpv_get_property_string");
                            if (!g_mpv_free)
                                g_mpv_free = (mpv_free_fn)GetProcAddress(mpvDll, "mpv_free");
                            if (!g_mpv_command_string)
                                g_mpv_command_string = (mpv_command_string_fn)GetProcAddress(mpvDll, "mpv_command_string");
                            if (!g_mpv_set_property_string)
                                g_mpv_set_property_string = (mpv_set_property_string_fn)GetProcAddress(mpvDll, "mpv_set_property_string");
                        }
                    }
                    if (get_prop) {
                        double duration = 0.0;
                        get_prop(g_mpvHandle, "duration", MPV_FORMAT_DOUBLE, &duration);
                        double position = 0.0;
                        get_prop(g_mpvHandle, "time-pos", MPV_FORMAT_DOUBLE, &position);
                        int pause = 0;
                        get_prop(g_mpvHandle, "pause", MPV_FORMAT_FLAG, &pause);
                        int core_idle = 0;
                        get_prop(g_mpvHandle, "core-idle", MPV_FORMAT_FLAG, &core_idle);
                        int paused_for_cache = 0;
                        get_prop(g_mpvHandle, "paused-for-cache", MPV_FORMAT_FLAG, &paused_for_cache);
                        
                        bool is_buffering = (paused_for_cache != 0) || ((core_idle != 0) && (pause == 0));
                        
                        double demuxer_cache = 0.0;
                        get_prop(g_mpvHandle, "demuxer-cache-duration", MPV_FORMAT_DOUBLE, &demuxer_cache);
                        double bufferPos = position + demuxer_cache;

                        static bool hasFiredDismiss = false;
                        if (is_buffering || position < 0.05) {
                            hasFiredDismiss = false;
                        } else if (!hasFiredDismiss && !is_buffering && (demuxer_cache >= 3.0 || position > 0.5)) {
                            hasFiredDismiss = true;
                            g_webview->ExecuteScript(L"window.__dismissProbingOverlay && window.__dismissProbingOverlay()", nullptr);
                        }

                        std::string json = "{\"type\":\"state_update\",\"positionMs\":" + std::to_string((long long)(position * 1000)) +
                                           ",\"bufferMs\":" + std::to_string((long long)(bufferPos * 1000)) +
                                           ",\"durationMs\":" + std::to_string((long long)(duration * 1000)) +
                                           ",\"isLoading\":" + (is_buffering ? "true" : "false") +
                                           ",\"isPlaying\":" + (pause ? "false" : "true") + "}";

                        int size = MultiByteToWideChar(CP_UTF8, 0, json.c_str(), -1, nullptr, 0);
                        if (size > 0) {
                            std::wstring wJson(size, 0);
                            MultiByteToWideChar(CP_UTF8, 0, json.c_str(), -1, &wJson[0], size);
                            g_webview->PostWebMessageAsJson(wJson.c_str());
                        }

                        if (g_statsVisible && g_mpv_get_property_string && g_mpv_free) {
                            auto readStrFree = [&](const char* prop) -> std::string {
                                char* v = g_mpv_get_property_string(g_mpvHandle, prop);
                                if (!v) return "\"N/A\"";
                                std::string s(v);
                                g_mpv_free(v);
                                std::string r = "\"";
                                for (char c : s) {
                                    if (c == '"')  r += "\\\"";
                                    else if (c == '\\') r += "\\\\";
                                    else r += c;
                                }
                                r += "\"";
                                return r;
                            };

                            double vbitrate = 0.0, abitrate = 0.0, vfps = 0.0, dispfps = 0.0, avsync = 0.0;
                            int64_t vw = 0, vh = 0, drop = 0, vodrop = 0, asamprate = 0;
                            get_prop(g_mpvHandle, "width",              MPV_FORMAT_INT64,  &vw);
                            get_prop(g_mpvHandle, "height",             MPV_FORMAT_INT64,  &vh);
                            get_prop(g_mpvHandle, "video-bitrate",      MPV_FORMAT_DOUBLE, &vbitrate);
                            get_prop(g_mpvHandle, "audio-bitrate",      MPV_FORMAT_DOUBLE, &abitrate);
                            get_prop(g_mpvHandle, "estimated-vf-fps",   MPV_FORMAT_DOUBLE, &vfps);
                            get_prop(g_mpvHandle, "display-fps",        MPV_FORMAT_DOUBLE, &dispfps);
                            get_prop(g_mpvHandle, "avsync",             MPV_FORMAT_DOUBLE, &avsync);
                            get_prop(g_mpvHandle, "drop-frame-count",   MPV_FORMAT_INT64,  &drop);
                            get_prop(g_mpvHandle, "vo-drop-frame-count",MPV_FORMAT_INT64,  &vodrop);
                            get_prop(g_mpvHandle, "audio-params/samplerate", MPV_FORMAT_INT64, &asamprate);

                            std::string vcodec   = readStrFree("video-codec");
                            std::string acodec   = readStrFree("audio-codec");
                            std::string hwdec    = readStrFree("hwdec-current");
                            std::string achans   = readStrFree("audio-channels");
                            std::string fmt      = readStrFree("file-format");
                            std::string path     = readStrFree("path");
                            std::string vformat  = readStrFree("video-format");
                            std::string cmatrix  = readStrFree("video-params/colormatrix");
                            std::string cprim    = readStrFree("video-params/primaries");
                            std::string clevels  = readStrFree("video-params/colorlevels");

                            std::string statsStr = "{\"type\":\"stats_update\""
                                ",\"videoCodec\":" + vcodec + 
                                ",\"audioCodec\":" + acodec +
                                ",\"width\":" + std::to_string(vw) + 
                                ",\"height\":" + std::to_string(vh) +
                                ",\"videoBitrate\":" + std::to_string((long long)vbitrate) + 
                                ",\"audioBitrate\":" + std::to_string((long long)abitrate) +
                                ",\"fps\":" + std::to_string(vfps) +
                                ",\"displayFps\":" + std::to_string(dispfps) +
                                ",\"avsync\":" + std::to_string(avsync) +
                                ",\"droppedFrames\":" + std::to_string(drop) + 
                                ",\"voDroppedFrames\":" + std::to_string(vodrop) +
                                ",\"hwdec\":" + hwdec + 
                                ",\"audioChannels\":" + achans +
                                ",\"audioSampleRate\":" + std::to_string(asamprate) +
                                ",\"videoFormat\":" + vformat +
                                ",\"colorMatrix\":" + cmatrix +
                                ",\"colorPrimaries\":" + cprim +
                                ",\"colorLevels\":" + clevels +
                                ",\"format\":" + fmt + 
                                ",\"path\":" + path + "}";

                            int sz = MultiByteToWideChar(CP_UTF8, 0, statsStr.c_str(), -1, nullptr, 0);
                            if (sz > 0) {
                                std::wstring wStats(sz, 0);
                                MultiByteToWideChar(CP_UTF8, 0, statsStr.c_str(), -1, &wStats[0], sz);
                                g_webview->PostWebMessageAsJson(wStats.c_str());
                            }
                        }
                    }
                }
                return 0;
            }
            if (g_hostHwnd && g_containerHwnd) {
                RECT clientRect = {};
                if (GetClientRect(g_hostHwnd, &clientRect)) {
                    int w = std::max(1, (int)(clientRect.right  - clientRect.left));
                    int h = std::max(1, (int)(clientRect.bottom - clientRect.top));
                    
                    static int lastW = 0, lastH = 0;
                    if (w != lastW || h != lastH) {
                        lastW = w;
                        lastH = h;
                        SetWindowPos(g_containerHwnd, HWND_TOP, 0, 0, w, h,
                                     SWP_SHOWWINDOW | SWP_NOACTIVATE);
                        if (g_webviewController) {
                            RECT bounds = {0, 0, (LONG)w, (LONG)h};
                            g_webviewController->put_Bounds(bounds);
                        }
                    }
                }
            }
            return 0;
        }
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

void runNativeUiThread(HWND hostHwnd, int width, int height) {
    g_uiThreadId = GetCurrentThreadId();

    HRESULT oleResult = OleInitialize(nullptr);
    if (FAILED(oleResult)) {
        std::cerr << "[NativeBridge] FATAL: OleInitialize failed" << std::endl;
    }

    HINSTANCE hInstance = GetModuleHandle(nullptr);

    WNDCLASSW wcWV = {0};
    wcWV.lpfnWndProc   = ContainerWndProc;
    wcWV.hInstance     = hInstance;
    wcWV.lpszClassName = L"CloudStreamWebView2Container";
    wcWV.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    RegisterClassW(&wcWV);

    WNDCLASSW wcMsg = {0};
    wcMsg.lpfnWndProc   = MessageWndProc;
    wcMsg.hInstance     = hInstance;
    wcMsg.lpszClassName = L"CloudStreamMessageWindow";
    RegisterClassW(&wcMsg);

    g_messageHwnd = CreateWindowExW(
        0, L"CloudStreamMessageWindow", L"",
        0, 0, 0, 0, 0,
        HWND_MESSAGE, nullptr, hInstance, nullptr);

    LONG_PTR hostStyle = GetWindowLongPtrW(hostHwnd, GWL_STYLE);
    SetWindowLongPtrW(hostHwnd, GWL_STYLE, hostStyle | WS_CLIPCHILDREN | WS_CLIPSIBLINGS);

    g_containerHwnd = CreateWindowExW(
        0,
        L"CloudStreamWebView2Container", L"",
        WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS,
        0, 0, width, height,
        hostHwnd, nullptr, hInstance, nullptr);

    if (!g_containerHwnd) {
        std::cerr << "[NativeBridge] FATAL: WebView2 container CreateWindowExW failed, error="
                  << GetLastError() << std::endl;
    } else {
        LOG_TO_FILE("[NativeBridge] WebView2 container HWND created: " << g_containerHwnd);
    }

    HMODULE hLoader = GetModuleHandleW(L"WebView2Loader.dll");
    if (!hLoader) {
        static int s_selfMarker = 0;
        HMODULE hSelf = nullptr;
        GetModuleHandleExW(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            (LPCWSTR)&s_selfMarker,
            &hSelf);
        wchar_t selfPath[MAX_PATH] = {};
        GetModuleFileNameW(hSelf, selfPath, MAX_PATH);
        std::wstring jniDir(selfPath);
        auto lastSlash = jniDir.find_last_of(L"\\/");
        if (lastSlash != std::wstring::npos) jniDir = jniDir.substr(0, lastSlash + 1);
        std::wstring loaderPath = jniDir + L"WebView2Loader.dll";
        LOG_TO_FILE("[NativeBridge] Loading WebView2Loader from: " << std::string(loaderPath.begin(), loaderPath.end()));
        hLoader = LoadLibraryW(loaderPath.c_str());
        if (!hLoader) {
            LOG_TO_FILE("[NativeBridge] Absolute load failed (error=" << GetLastError() << "), falling back to bare name");
            hLoader = LoadLibraryW(L"WebView2Loader.dll");
        }
    }

    auto createEnvFunc = (CreateCoreWebView2EnvironmentWithOptionsFunc)
        GetProcAddress(hLoader, "CreateCoreWebView2EnvironmentWithOptions");

    wchar_t tempPath[MAX_PATH];
    GetTempPathW(MAX_PATH, tempPath);
    std::wstring userData = std::wstring(tempPath) + L"CloudStreamWebView2";

    SetEnvironmentVariableW(L"WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
        L"--allow-file-access-from-files --disable-web-security "
        L"--allow-running-insecure-content --default-background-color=00000000");

    HRESULT hr = createEnvFunc(nullptr, userData.c_str(), nullptr, new EnvironmentCompletedHandler());
    LOG_TO_FILE("[NativeBridge] CreateEnvironment hr=0x" << std::hex << hr << std::dec);

    {
        std::lock_guard<std::mutex> lock(g_initMutex);
        g_initComplete = true;
    }
    g_initCv.notify_one();

    SetTimer(g_messageHwnd, 1, 500, nullptr);

    MSG msg = {};
    while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    if (g_webviewController) {
        g_webviewController->Close();
        g_webviewController->Release();
        g_webviewController = nullptr;
    }
    if (g_webview) {
        g_webview->Release();
        g_webview = nullptr;
    }
    if (g_containerHwnd) {
        DestroyWindow(g_containerHwnd);
        g_containerHwnd = nullptr;
    }
    if (g_messageHwnd) {
        DestroyWindow(g_messageHwnd);
        g_messageHwnd = nullptr;
    }
    g_webviewReady = false;
    g_uiReady = false;

    if (SUCCEEDED(oleResult)) {
        OleUninitialize();
    }
}

// WebView2 Warmup Logic
std::mutex gWebView2WarmupMutex;
std::condition_variable gWebView2WarmupCv;
std::thread gWebView2WarmupThread;
DWORD gWebView2WarmupThreadId = 0;
std::wstring g_warmupControlsUrl;
bool gWebView2WarmupStarted = false;

ICoreWebView2Environment* g_warmupEnv = nullptr;
ICoreWebView2Controller* g_warmupCtrl = nullptr;
ICoreWebView2* g_warmupWebView = nullptr;

class WarmupCtrlHandler : public ICoreWebView2CreateCoreWebView2ControllerCompletedHandler {
    ULONG m_cRef = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2CreateCoreWebView2ControllerCompletedHandler) {
            *ppv = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_cRef; }
    ULONG STDMETHODCALLTYPE Release() override {
        if (--m_cRef == 0) { delete this; return 0; } return m_cRef;
    }
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT res, ICoreWebView2Controller* ctrl) override {
        if (SUCCEEDED(res) && ctrl) {
            g_warmupCtrl = ctrl;
            g_warmupCtrl->AddRef();
            g_warmupCtrl->put_IsVisible(FALSE);
            g_warmupCtrl->get_CoreWebView2(&g_warmupWebView);
            if (g_warmupWebView && !g_warmupControlsUrl.empty()) {
                LOG_TO_FILE("[NativeBridge] Prewarming WebView2 with URL: " << std::string(g_warmupControlsUrl.begin(), g_warmupControlsUrl.end()));
                g_warmupWebView->Navigate(g_warmupControlsUrl.c_str());
            }
        } else {
            PostQuitMessage(0);
        }
        return S_OK;
    }
};

class WarmupEnvHandler : public ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
    ULONG m_refCount = 1;
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppvObject) override {
        if (riid == IID_IUnknown || riid == IID_ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler) {
            *ppvObject = this; AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        if (--m_refCount == 0) { delete this; return 0; } return m_refCount;
    }
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2Environment* env) override {
        if (SUCCEEDED(result) && env) {
            g_warmupEnv = env;
            g_warmupEnv->AddRef();
            g_warmupEnv->CreateCoreWebView2Controller(HWND_MESSAGE, new WarmupCtrlHandler());
        } else {
            PostQuitMessage(0);
        }
        return S_OK;
    }
};

void runWebView2WarmupThread(std::wstring controlsUrl) {
    g_warmupControlsUrl = controlsUrl;
    {
        std::lock_guard<std::mutex> lock(gWebView2WarmupMutex);
        gWebView2WarmupThreadId = GetCurrentThreadId();
    }
    gWebView2WarmupCv.notify_all();

    bool didOleInitialize = false;
    HRESULT oleResult = OleInitialize(nullptr);
    didOleInitialize = SUCCEEDED(oleResult);
    if (FAILED(oleResult)) return;

    HMODULE hLoader = GetModuleHandleW(L"WebView2Loader.dll");
    if (!hLoader) {
        static int s_selfMarker = 0;
        HMODULE hSelf = nullptr;
        GetModuleHandleExW(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            (LPCWSTR)&s_selfMarker,
            &hSelf);
        wchar_t selfPath[MAX_PATH] = {};
        GetModuleFileNameW(hSelf, selfPath, MAX_PATH);
        std::wstring jniDir(selfPath);
        auto lastSlash = jniDir.find_last_of(L"\\/");
        if (lastSlash != std::wstring::npos) jniDir = jniDir.substr(0, lastSlash + 1);
        std::wstring loaderPath = jniDir + L"WebView2Loader.dll";
        hLoader = LoadLibraryW(loaderPath.c_str());
        if (!hLoader) hLoader = LoadLibraryW(L"WebView2Loader.dll");
    }
    if (!hLoader) return;

    auto createEnvFunc = (CreateCoreWebView2EnvironmentWithOptionsFunc)
        GetProcAddress(hLoader, "CreateCoreWebView2EnvironmentWithOptions");
    if (!createEnvFunc) return;

    wchar_t tempPath[MAX_PATH];
    GetTempPathW(MAX_PATH, tempPath);
    std::wstring userData = std::wstring(tempPath) + L"CloudStreamWebView2";

    SetEnvironmentVariableW(L"WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
        L"--allow-file-access-from-files --disable-web-security "
        L"--allow-running-insecure-content --default-background-color=00000000");

    createEnvFunc(nullptr, userData.c_str(), nullptr, new WarmupEnvHandler());

    MSG msg = {};
    while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    if (g_warmupWebView) { g_warmupWebView->Release(); g_warmupWebView = nullptr; }
    if (g_warmupCtrl) { g_warmupCtrl->Close(); g_warmupCtrl->Release(); g_warmupCtrl = nullptr; }
    if (g_warmupEnv) { g_warmupEnv->Release(); g_warmupEnv = nullptr; }

    if (didOleInitialize) OleUninitialize();
}

void startWebView2Warmup(std::wstring controlsUrl) {
    std::lock_guard<std::mutex> lock(gWebView2WarmupMutex);
    if (!gWebView2WarmupStarted) {
        gWebView2WarmupStarted = true;
        gWebView2WarmupThread = std::thread(runWebView2WarmupThread, controlsUrl);
    }
}

void stopWebView2Warmup() {
    std::thread threadToJoin;
    DWORD threadId = 0;
    {
        std::unique_lock<std::mutex> lock(gWebView2WarmupMutex);
        if (!gWebView2WarmupStarted) return;
        gWebView2WarmupCv.wait_for(lock, std::chrono::seconds(1), []() { return gWebView2WarmupThreadId != 0; });
        threadId = gWebView2WarmupThreadId;
    }
    if (threadId != 0) {
        PostThreadMessageW(threadId, WM_QUIT, 0, 0);
    }
    {
        std::lock_guard<std::mutex> lock(gWebView2WarmupMutex);
        if (gWebView2WarmupThread.joinable()) {
            threadToJoin = std::move(gWebView2WarmupThread);
        }
        gWebView2WarmupStarted = false;
        gWebView2WarmupThreadId = 0;
    }
    if (threadToJoin.joinable()) {
        threadToJoin.join();
    }
}

extern "C" {

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_resizeWebView(
    JNIEnv* env, jobject thiz, jint width, jint height)
{
    postUiTask([width, height]() {
        int physW = width, physH = height;
        if (g_hostHwnd) {
            RECT clientRect = {};
            if (GetClientRect(g_hostHwnd, &clientRect)) {
                int hwndW = clientRect.right  - clientRect.left;
                int hwndH = clientRect.bottom - clientRect.top;
                if (hwndW > 0 && hwndH > 0) {
                    physW = hwndW;
                    physH = hwndH;
                }
            }
        }

        if (g_containerHwnd) {
            SetWindowPos(g_containerHwnd, nullptr, 0, 0, physW, physH,
                         SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
        }
        if (g_webviewController) {
            RECT bounds = {0, 0, (LONG)physW, (LONG)physH};
            g_webviewController->put_Bounds(bounds);
            if (g_uiReady.load()) {
                g_webviewController->put_IsVisible(TRUE);
            }
        }
    });
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_focusWebView(
    JNIEnv* env, jobject thiz)
{
    postUiTask([]() {
        if (g_containerHwnd) {
            SetFocus(g_containerHwnd);
        }
        if (g_webviewController) {
            g_webviewController->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
        }
    });
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_executeScript(
    JNIEnv* env, jobject thiz, jstring script)
{
    const jchar* chars = env->GetStringChars(script, NULL);
    std::wstring wscript = std::wstring((wchar_t*)chars, env->GetStringLength(script));
    env->ReleaseStringChars(script, chars);

    postUiTask([wscript]() {
        if (g_webviewReady && g_webview) {
            g_webview->ExecuteScript(wscript.c_str(), nullptr);
        }
    });
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_loadUrl(
    JNIEnv* env, jobject thiz, jstring url)
{
    const jchar* chars = env->GetStringChars(url, NULL);
    std::wstring wurl = std::wstring((wchar_t*)chars, env->GetStringLength(url));
    env->ReleaseStringChars(url, chars);

    postUiTask([wurl]() {
        if (g_webview) {
            g_webview->Navigate(wurl.c_str());
        } else {
            std::lock_guard<std::mutex> lock(g_pendingUrlMutex);
            g_pendingUrl = wurl;
        }
    });
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_openDevTools(
    JNIEnv* env, jobject thiz)
{
    postUiTask([]() {
        if (g_webviewReady && g_webview) {
            g_webview->OpenDevToolsWindow();
        }
    });
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_postMessage(
    JNIEnv* env, jobject thiz, jstring message)
{
    const jchar* chars = env->GetStringChars(message, NULL);
    std::wstring wmessage = std::wstring((wchar_t*)chars, env->GetStringLength(message));
    env->ReleaseStringChars(message, chars);

    postUiTask([wmessage]() {
        if (g_webviewReady && g_webview) {
            g_webview->PostWebMessageAsJson(wmessage.c_str());
        }
    });
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_warmupWebView2(
    JNIEnv* env, jobject thiz, jstring controlsUrl)
{
    std::wstring urlW = L"";
    if (controlsUrl) {
        const jchar* chars = env->GetStringChars(controlsUrl, NULL);
        urlW = std::wstring((wchar_t*)chars, env->GetStringLength(controlsUrl));
        env->ReleaseStringChars(controlsUrl, chars);
    }
    startWebView2Warmup(urlW);
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_shutdownWebView2Warmup(JNIEnv* env, jobject thiz) {
    stopWebView2Warmup();
}

} // extern "C"
