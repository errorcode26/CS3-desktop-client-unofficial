#include <jni.h>
#include <windows.h>
#include <dwmapi.h>
#include <iostream>
#include <string>
#include <vector>
#include <functional>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <unordered_map>
#include <initguid.h>
#include "WebView2.h"

extern "C" {
typedef struct mpv_handle mpv_handle;
typedef enum mpv_format {
    MPV_FORMAT_NONE             = 0,
    MPV_FORMAT_STRING           = 1,
    MPV_FORMAT_OSD_STRING       = 2,
    MPV_FORMAT_FLAG             = 3,
    MPV_FORMAT_INT64            = 4,
    MPV_FORMAT_DOUBLE           = 5,
    MPV_FORMAT_NODE             = 6,
    MPV_FORMAT_NODE_ARRAY       = 7,
    MPV_FORMAT_NODE_MAP         = 8,
    MPV_FORMAT_BYTE_ARRAY       = 9
} mpv_format;
typedef int (*mpv_get_property_fn)(mpv_handle *ctx, const char *name, mpv_format format, void *data);
}

// Global state
HWND g_hostHwnd      = nullptr;  // The Java/AWT Canvas HWND
HWND g_containerHwnd = nullptr;  // Combined MPV render and WebView2 container child window
HWND g_messageHwnd   = nullptr;  // Message-only window for UI tasks
ICoreWebView2Controller* g_webviewController = nullptr;
ICoreWebView2*           g_webview           = nullptr;
bool                     g_webviewReady      = false;
std::wstring             g_pendingUrl        = L"";

mpv_handle* g_mpvHandle = nullptr;
std::mutex g_mpvMutex;
UINT_PTR g_syncTimer = 0;

std::mutex   g_pendingUrlMutex;

// JNI State for events
JavaVM*   g_jvm            = nullptr;
jobject   g_listener       = nullptr;
jmethodID g_listenerMethod = nullptr;

std::thread            g_uiThread;
DWORD                  g_uiThreadId = 0;
std::mutex             g_initMutex;
std::condition_variable g_initCv;
bool                   g_initComplete = false;

// ─── UI Task Queue ─────────────────────────────────────────
std::mutex g_uiTaskMutex;
std::vector<std::function<void()>> g_uiTasks;

void postUiTask(std::function<void()> task) {
    {
        std::lock_guard<std::mutex> lock(g_uiTaskMutex);
        g_uiTasks.push_back(std::move(task));
    }
    if (g_messageHwnd) {
        PostMessageW(g_messageHwnd, WM_APP + 0x4E50, 0, 0);
    }
}

void processUiTasks() {
    std::vector<std::function<void()>> tasks;
    {
        std::lock_guard<std::mutex> lock(g_uiTaskMutex);
        tasks.swap(g_uiTasks);
    }
    for (auto& task : tasks) {
        if (task) task();
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// ─── JNI Event Dispatcher ──────────────────────────────────────────────────
void dispatchPlayerEvent(const std::wstring& message) {
    if (!g_jvm || !g_listener || !g_listenerMethod) return;

    JNIEnv* env = nullptr;
    bool didAttach = false;
    jint getEnvStat = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    
    if (getEnvStat == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
            didAttach = true;
        } else {
            return;
        }
        didAttach = true;
    } else if (getEnvStat == JNI_EVERSION) {
        return;
    }

    std::string str(message.begin(), message.end());
    jstring jType = env->NewStringUTF("message");
    jstring jVal  = env->NewStringUTF(str.c_str());
    env->CallVoidMethod(g_listener, g_listenerMethod, jType, jVal);
    env->DeleteLocalRef(jType);
    env->DeleteLocalRef(jVal);

    if (didAttach) {
        g_jvm->DetachCurrentThread();
    }
}

typedef HRESULT(STDAPICALLTYPE *CreateCoreWebView2EnvironmentWithOptionsFunc)(
    PCWSTR browserExecutableFolder, PCWSTR userDataFolder,
    ICoreWebView2EnvironmentOptions* environmentOptions,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* environmentCreatedHandler);


// ─── WebView2 Event Handlers ───────────────────────────────────────────────
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
            dispatchPlayerEvent(std::wstring(messageJson));
            CoTaskMemFree(messageJson);
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
        if (virtualKey == VK_F11 &&
            (keyEventKind == COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN ||
             keyEventKind == COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN)) {
            args->put_Handled(TRUE);
            dispatchPlayerEvent(L"{\"type\":\"toggleFullscreen\",\"value\":\"\"}");
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
        g_webviewController->put_IsVisible(FALSE); // hidden until ui_ready

        g_webviewController->get_CoreWebView2(&g_webview);

        // Fit bounds to container
        RECT bounds;
        GetClientRect(g_containerHwnd, &bounds);
        g_webviewController->put_Bounds(bounds);

        // ── Transparent background ──
        // Makes WebView2 background fully transparent so MPV video shows through.
        ICoreWebView2Controller2* controller2 = nullptr;
        if (SUCCEEDED(g_webviewController->QueryInterface(IID_ICoreWebView2Controller2, (void**)&controller2))) {
            COREWEBVIEW2_COLOR transparent = {0, 0, 0, 0};
            controller2->put_DefaultBackgroundColor(transparent);
            controller2->Release();
        }

        // ── Disable context menus and status bar ──
        ICoreWebView2Settings* settings = nullptr;
        if (g_webview && SUCCEEDED(g_webview->get_Settings(&settings)) && settings) {
            settings->put_AreDefaultContextMenusEnabled(FALSE);
            settings->put_IsStatusBarEnabled(FALSE);
            settings->Release();
        }

        EventRegistrationToken token;
        g_webview->add_WebMessageReceived(new WebMessageReceivedHandler(), &token);

        EventRegistrationToken accelToken;
        g_webviewController->add_AcceleratorKeyPressed(new AcceleratorKeyPressedHandler(), &accelToken);

        g_webviewReady = true;
        std::cout << "[NativeBridge] WebView2 Initialized Successfully!" << std::endl;

        // Bring the WebView2 overlay above the MPV render window immediately
        SetWindowPos(g_containerHwnd, HWND_TOP, 0, 0, 0, 0,
                     SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);

        // Navigate to any URL that arrived before we were ready
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

// ─── WndProcs ──────────────────────────────────────────────────────────────
LRESULT CALLBACK ContainerWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_ERASEBKGND: {
            RECT rect = {};
            GetClientRect(hwnd, &rect);
            FillRect((HDC)wParam, &rect, (HBRUSH)GetStockObject(BLACK_BRUSH));
            return 1;
        }
        // Suppress default WM_SIZE processing to avoid white flicker on resize.
        // Return 0 here, let layout timer drive sizing.
        case WM_SIZE:
            return 0;
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

LRESULT CALLBACK MessageWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_APP + 0x4E50: { // processUiTasks
            processUiTasks();
            return 0;
        }
        case WM_TIMER: {
            if (wParam == 0x4E51) {
                std::lock_guard<std::mutex> lock(g_mpvMutex);
                if (g_webviewReady && g_webview && g_mpvHandle) {
                    static HMODULE mpvDll = nullptr;
                    static mpv_get_property_fn get_prop = nullptr;
                    if (!mpvDll) {
                        mpvDll = LoadLibraryA("libmpv-2.dll");
                        if (!mpvDll) mpvDll = LoadLibraryA("mpv-2.dll");
                        if (!mpvDll) mpvDll = LoadLibraryA("mpv.dll");
                        if (mpvDll) get_prop = (mpv_get_property_fn)GetProcAddress(mpvDll, "mpv_get_property");
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
                        
                        // DIRECT C++ HOOK: Force dismiss overlay exactly when frames start rendering.
                        static bool hasFiredDismiss = false;
                        if (core_idle) {
                            hasFiredDismiss = false; // Reset whenever player goes idle (buffering/loading new stream)
                        } else if (position > 0.1 && !hasFiredDismiss) {
                            hasFiredDismiss = true;
                            g_webview->ExecuteScript(L"window.__dismissProbingOverlay && window.__dismissProbingOverlay()", nullptr);
                        }

                        double demuxer_cache = 0.0;
                        get_prop(g_mpvHandle, "demuxer-cache-duration", MPV_FORMAT_DOUBLE, &demuxer_cache);
                        double bufferPos = position + demuxer_cache;

                        std::string json = "{\"type\":\"state_update\",\"positionMs\":" + std::to_string((long long)(position * 1000)) +
                                           ",\"bufferMs\":" + std::to_string((long long)(bufferPos * 1000)) +
                                           ",\"durationMs\":" + std::to_string((long long)(duration * 1000)) +
                                           ",\"isLoading\":" + (core_idle ? "true" : "false") +
                                           ",\"isPlaying\":" + (pause ? "false" : "true") + "}";

                        int size = MultiByteToWideChar(CP_UTF8, 0, json.c_str(), -1, nullptr, 0);
                        if (size > 0) {
                            std::wstring wJson(size, 0);
                            MultiByteToWideChar(CP_UTF8, 0, json.c_str(), -1, &wJson[0], size);
                            g_webview->PostWebMessageAsJson(wJson.c_str());
                        }
                    }
                }
                return 0;
            }
            // Relayout timer (500ms).
            // Re-reads GetClientRect on the host every tick so the container always
            // covers the full physical-pixel host area — fixes white gaps on resize.
            if (g_hostHwnd && g_containerHwnd) {
                RECT clientRect = {};
                if (GetClientRect(g_hostHwnd, &clientRect)) {
                    int w = std::max(1, (int)(clientRect.right  - clientRect.left));
                    int h = std::max(1, (int)(clientRect.bottom - clientRect.top));
                    SetWindowPos(g_containerHwnd, HWND_TOP, 0, 0, w, h,
                                 SWP_SHOWWINDOW | SWP_NOACTIVATE);
                    if (g_webviewController) {
                        RECT bounds = {0, 0, (LONG)w, (LONG)h};
                        g_webviewController->put_Bounds(bounds);
                        if (g_webviewReady) g_webviewController->put_IsVisible(TRUE);
                    }
                }
            }
            return 0;
        }
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

WNDPROC g_originalHostWndProc = nullptr;
LRESULT CALLBACK HostSubclassProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == WM_ERASEBKGND) {
        RECT rect = {};
        GetClientRect(hwnd, &rect);
        FillRect((HDC)wParam, &rect, (HBRUSH)GetStockObject(BLACK_BRUSH));
        return 1;
    }
    return CallWindowProc(g_originalHostWndProc, hwnd, msg, wParam, lParam);
}

// ─── Native UI Thread ──────────────────────────────────────────────────────
void runNativeUiThread(HWND hostHwnd, int width, int height) {
    g_uiThreadId = GetCurrentThreadId();

    HRESULT oleResult = OleInitialize(nullptr);
    if (FAILED(oleResult)) {
        std::cerr << "[NativeBridge] FATAL: OleInitialize failed" << std::endl;
    }

    HINSTANCE hInstance = GetModuleHandle(nullptr);

    // ── Register WebView2 container window class ──
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

    // Ensure the host canvas clips children so they don't bleed outside
    LONG_PTR hostStyle = GetWindowLongPtrW(hostHwnd, GWL_STYLE);
    SetWindowLongPtrW(hostHwnd, GWL_STYLE, hostStyle | WS_CLIPCHILDREN | WS_CLIPSIBLINGS);

    // ── Create WebView2 container (also used as MPV render surface) ──
    g_containerHwnd = CreateWindowExW(
        0, // No WS_EX_LAYERED or WS_EX_TRANSPARENT, WebView2 controller background is transparent
        L"CloudStreamWebView2Container", L"",
        WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS,
        0, 0, width, height,
        hostHwnd, nullptr, hInstance, nullptr);

    if (!g_containerHwnd) {
        std::cerr << "[NativeBridge] FATAL: WebView2 container CreateWindowExW failed, error="
                  << GetLastError() << std::endl;
    } else {
        std::cout << "[NativeBridge] WebView2 container HWND created: " << g_containerHwnd << std::endl;
    }

    // ── Initialize WebView2 ──
    HMODULE hLoader = GetModuleHandleW(L"WebView2Loader.dll");
    if (!hLoader) hLoader = LoadLibraryW(L"WebView2Loader.dll");

    auto createEnvFunc = (CreateCoreWebView2EnvironmentWithOptionsFunc)
        GetProcAddress(hLoader, "CreateCoreWebView2EnvironmentWithOptions");

    wchar_t tempPath[MAX_PATH];
    GetTempPathW(MAX_PATH, tempPath);
    std::wstring userData = std::wstring(tempPath) + L"CloudStreamWebView2";

    SetEnvironmentVariableW(L"WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS", 
        L"--allow-file-access-from-files --disable-web-security --allow-running-insecure-content --disk-cache-size=1 --disable-application-cache --aggressive-cache-discard");

    HRESULT hr = createEnvFunc(nullptr, userData.c_str(), nullptr, new EnvironmentCompletedHandler());
    std::cout << "[NativeBridge] CreateEnvironment hr=0x" << std::hex << hr << std::dec << std::endl;

    // Signal Kotlin that the container HWND is ready (MPV wid can now be set)
    {
        std::lock_guard<std::mutex> lock(g_initMutex);
        g_initComplete = true;
    }
    g_initCv.notify_one();

    // ── Start 500ms relayout timer ──
    // Ensures container always matches physical host dimensions on resize/fullscreen.
    SetTimer(g_messageHwnd, 1, 500, nullptr);

    // Message loop for COM callbacks
    MSG msg = {};
    while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    // ── Cleanup ──
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

    if (SUCCEEDED(oleResult)) {
        OleUninitialize();
    }
}

extern "C" {

// ─── initWebView ─────────────────────────────────────────────────────────────
// Returns the single container HWND which WebView2 is hosted inside and MPV renders onto.
JNIEXPORT jlong JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_initWebView(
    JNIEnv* env, jobject thiz, jlong hostHwndPtr, jint width, jint height)
{
    g_hostHwnd = (HWND)hostHwndPtr;
    std::cout << "[NativeBridge] initWebView called, thread=" << GetCurrentThreadId() << std::endl;

    // Subclass the AWT Canvas to prevent white flashes on resize
    if (!g_originalHostWndProc) {
        g_originalHostWndProc = (WNDPROC)SetWindowLongPtr(g_hostHwnd, GWLP_WNDPROC, (LONG_PTR)HostSubclassProc);
    }

    {
        std::lock_guard<std::mutex> lock(g_initMutex);
        g_initComplete = false;
    }
    {
        std::lock_guard<std::mutex> lock(g_pendingUrlMutex);
        g_pendingUrl.clear();
    }

    g_uiThread = std::thread(runNativeUiThread, g_hostHwnd, width, height);

    // Wait until child HWND is created before returning
    std::unique_lock<std::mutex> lock(g_initMutex);
    g_initCv.wait(lock, []() { return g_initComplete; });

    std::cout << "[NativeBridge] Returning combined HWND=" << g_containerHwnd << std::endl;
    return reinterpret_cast<jlong>(g_containerHwnd);
}

// ─── setFullscreen (per-window state) ─────────────────────────
struct WindowFullscreenState {
    LONG_PTR       style      = 0;
    LONG_PTR       exStyle    = 0;
    WINDOWPLACEMENT placement = { sizeof(WINDOWPLACEMENT) };
};

std::mutex g_fullscreenMutex;
std::unordered_map<HWND, WindowFullscreenState> g_fullscreenStates;

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_setFullscreen(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean fullscreen,
    jint x, jint y, jint width, jint height)
{
    HWND hwnd = (HWND)(intptr_t)hwndPtr;
    if (!hwnd || !IsWindow(hwnd)) return;

    // Ensure the OS window class background is black to prevent white flashbangs during resizing
    static HBRUSH s_blackBrush = CreateSolidBrush(RGB(0, 0, 0));
    SetClassLongPtrW(hwnd, GCLP_HBRBACKGROUND, (LONG_PTR)s_blackBrush);

    if (fullscreen == JNI_TRUE) {
        // Only save state if not already stored for this HWND
        {
            std::lock_guard<std::mutex> lock(g_fullscreenMutex);
            if (g_fullscreenStates.find(hwnd) == g_fullscreenStates.end()) {
                WindowFullscreenState state;
                state.style   = GetWindowLongPtrW(hwnd, GWL_STYLE);
                state.exStyle = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
                state.placement.length = sizeof(WINDOWPLACEMENT);
                GetWindowPlacement(hwnd, &state.placement);
                g_fullscreenStates.emplace(hwnd, state);
            }
        }

        // Restore from minimized/maximized so SetWindowPos gives correct results
        if (IsIconic(hwnd) || IsZoomed(hwnd)) {
            ShowWindow(hwnd, SW_RESTORE);
        }

        // Strip caption, thick border, and all extended-style decorations
        LONG_PTR style   = GetWindowLongPtrW(hwnd, GWL_STYLE);
        LONG_PTR exStyle = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
        style   &= ~(LONG_PTR)(WS_CAPTION | WS_THICKFRAME);
        exStyle &= ~(LONG_PTR)(WS_EX_DLGMODALFRAME | WS_EX_WINDOWEDGE | WS_EX_CLIENTEDGE | WS_EX_STATICEDGE);
        SetWindowLongPtrW(hwnd, GWL_STYLE,   style);
        SetWindowLongPtrW(hwnd, GWL_EXSTYLE, exStyle);

        // Query the actual monitor rect this window is on — works correctly on any monitor / DPI
        HMONITOR monitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
        MONITORINFO monitorInfo = {};
        monitorInfo.cbSize = sizeof(MONITORINFO);

        int targetX = x, targetY = y, targetW = width, targetH = height;
        if (monitor && GetMonitorInfoW(monitor, &monitorInfo)) {
            targetX = monitorInfo.rcMonitor.left;
            targetY = monitorInfo.rcMonitor.top;
            targetW = monitorInfo.rcMonitor.right  - monitorInfo.rcMonitor.left;
            targetH = monitorInfo.rcMonitor.bottom - monitorInfo.rcMonitor.top;
        }

        SetWindowPos(
            hwnd, HWND_TOP,
            targetX, targetY, targetW, targetH,
            SWP_FRAMECHANGED | SWP_NOOWNERZORDER | SWP_NOACTIVATE
        );
    } else {
        // Retrieve saved state
        WindowFullscreenState state;
        bool hasState = false;
        {
            std::lock_guard<std::mutex> lock(g_fullscreenMutex);
            auto it = g_fullscreenStates.find(hwnd);
            if (it != g_fullscreenStates.end()) {
                state    = it->second;
                g_fullscreenStates.erase(it);
                hasState = true;
            }
        }
        if (!hasState) return;

        // Restore both style and extended style
        SetWindowLongPtrW(hwnd, GWL_STYLE,   state.style);
        SetWindowLongPtrW(hwnd, GWL_EXSTYLE, state.exStyle);
        // Notify Windows the frame has changed (recalculates NC area)
        SetWindowPos(
            hwnd, nullptr,
            0, 0, 0, 0,
            SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_NOACTIVATE
        );

        // Restore window placement — handles maximized, normal, any position
        if (state.placement.showCmd == SW_SHOWMAXIMIZED) {
            // Restore to normal first so Windows can calculate the maximized rect cleanly
            WINDOWPLACEMENT normalPlacement = state.placement;
            normalPlacement.showCmd = SW_SHOWNORMAL;
            SetWindowPlacement(hwnd, &normalPlacement);
            ShowWindow(hwnd, SW_MAXIMIZE);
        } else {
            SetWindowPlacement(hwnd, &state.placement);
        }
    }
}

// ─── applyWindowChrome (DWM dark mode + caption colour) ──────────────────────
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_applyWindowChrome(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean darkMode,
    jint captionColorRgb, jint borderColorRgb, jint textColorRgb)
{
    HWND hwnd = (HWND)(intptr_t)hwndPtr;
    if (!hwnd || !IsWindow(hwnd)) return;

    BOOL enabled = (darkMode == JNI_TRUE) ? TRUE : FALSE;
    // Try Win11 attribute (20), fall back to legacy (19)
    HRESULT hr = DwmSetWindowAttribute(hwnd, 20 /*DWMWA_USE_IMMERSIVE_DARK_MODE*/, &enabled, sizeof(enabled));
    if (FAILED(hr)) {
        DwmSetWindowAttribute(hwnd, 19 /*DWMWA_USE_IMMERSIVE_DARK_MODE legacy*/, &enabled, sizeof(enabled));
    }

    // Caption / border / text colours (Windows 11 22000+ only — no-op on older)
    auto toColorRef = [](jint rgb) -> COLORREF {
        return RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    };
    COLORREF captionColor = toColorRef(captionColorRgb);
    COLORREF borderColor  = toColorRef(borderColorRgb);
    COLORREF textColor    = toColorRef(textColorRgb);
    DwmSetWindowAttribute(hwnd, 35 /*DWMWA_CAPTION_COLOR*/, &captionColor, sizeof(captionColor));
    DwmSetWindowAttribute(hwnd, 34 /*DWMWA_BORDER_COLOR*/,  &borderColor,  sizeof(borderColor));
    DwmSetWindowAttribute(hwnd, 36 /*DWMWA_TEXT_COLOR*/,    &textColor,    sizeof(textColor));
}

// ─── destroyWebView ───────────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_destroyWebView(
    JNIEnv* env, jobject thiz)
{
    if (g_hostHwnd && g_originalHostWndProc) {
        SetWindowLongPtr(g_hostHwnd, GWLP_WNDPROC, (LONG_PTR)g_originalHostWndProc);
    }
    g_originalHostWndProc = nullptr;
    g_hostHwnd            = nullptr;

    if (g_listener) {
        env->DeleteGlobalRef(g_listener);
        g_listener       = nullptr;
        g_listenerMethod = nullptr;
    }

    if (g_uiThreadId != 0) {
        PostThreadMessageW(g_uiThreadId, WM_QUIT, 0, 0);
    }
    if (g_uiThread.joinable()) {
        g_uiThread.join();
    }
    g_uiThreadId = 0;
}

// ─── resizeWebView ────────────────────────────────────────────────────────────
// Java passes AWT *logical* pixel dimensions. On a DPI-scaled secondary monitor
// (e.g. 125%) AWT's canvas.width is smaller than the physical pixel count.
// Win32 child-window coordinates inside g_hostHwnd are physical pixels, so we
// must always derive the target size from GetClientRect, not the Java argument.
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_resizeWebView(
    JNIEnv* env, jobject thiz, jint width, jint height)
{
    postUiTask([width, height]() {
        // Prefer the physical client rect of the host AWT canvas — this is
        // always in physical pixels regardless of monitor DPI scale.
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
            g_webviewController->put_IsVisible(TRUE);
        }
    });
}


// ─── executeScript ────────────────────────────────────────────────────────────
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

// ─── loadUrl ─────────────────────────────────────────────────────────────────
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

// ─── openDevTools ─────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_openDevTools(
    JNIEnv* env, jobject thiz)
{
    postUiTask([]() {
        if (g_webviewReady && g_webview) {
            g_webview->OpenDevToolsWindow();
        }
    });
}

// ─── setEventListener ────────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_setEventListener(
    JNIEnv* env, jobject thiz, jobject listener)
{
    if (g_listener) { env->DeleteGlobalRef(g_listener); g_listener = nullptr; }
    if (listener) {
        g_listener = env->NewGlobalRef(listener);
        jclass clazz       = env->GetObjectClass(listener);
        g_listenerMethod   = env->GetMethodID(clazz, "onPlayerEvent",
                                              "(Ljava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(clazz);
    }
}

// ─── postMessage ──────────────────────────────────────────────────────────────
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

// ─── startMpvSync ─────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_startMpvSync(
    JNIEnv* env, jobject thiz, jlong mpvPtr)
{
    {
        std::lock_guard<std::mutex> lock(g_mpvMutex);
        g_mpvHandle = (mpv_handle*)mpvPtr;
    }
    postUiTask([]() {
        if (!g_syncTimer && g_messageHwnd) {
            g_syncTimer = SetTimer(g_messageHwnd, 0x4E51, 100, nullptr);
            std::cout << "[NativeBridge] MPV native UI sync timer started" << std::endl;
        }
    });
}

// ─── stopMpvSync ─────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_stopMpvSync(
    JNIEnv* env, jobject thiz)
{
    {
        std::lock_guard<std::mutex> lock(g_mpvMutex);
        g_mpvHandle = nullptr; // Null out immediately to prevent timer from using a destroyed handle
    }
    postUiTask([]() {
        if (g_syncTimer && g_messageHwnd) {
            KillTimer(g_messageHwnd, g_syncTimer);
            g_syncTimer = 0;
            std::cout << "[NativeBridge] MPV native UI sync timer stopped" << std::endl;
        }
    });
}

} // extern "C"
