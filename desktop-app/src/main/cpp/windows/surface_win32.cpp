#include "../include/player_bridge_common.h"

// Win32 Global handles
HWND g_hostHwnd      = nullptr;
HWND g_containerHwnd = nullptr;
HWND g_messageHwnd   = nullptr;
UINT_PTR g_syncTimer = 0;

// UI Task Queue
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

// Forward declarations from webview_win32.cpp
extern ICoreWebView2Controller* g_webviewController;
extern ICoreWebView2*           g_webview;
extern bool                     g_webviewReady;
extern std::wstring             g_pendingUrl;
extern std::mutex               g_pendingUrlMutex;
void runNativeUiThread(HWND hostHwnd, int width, int height);

extern std::thread              g_uiThread;
extern DWORD                    g_uiThreadId;
extern std::mutex               g_initMutex;
extern std::condition_variable  g_initCv;
extern bool                     g_initComplete;

// WndProc Subclasses
WNDPROC g_originalTopLevelWndProc = nullptr;
LRESULT CALLBACK TopLevelSubclassProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == 0x02E0) { // WM_DPICHANGED
        RECT currentRect;
        GetWindowRect(hwnd, &currentRect);
        RECT* prcSuggested = (RECT*)lParam;
        SetWindowPos(hwnd, nullptr, prcSuggested->left, prcSuggested->top,
                     currentRect.right - currentRect.left,
                     currentRect.bottom - currentRect.top,
                     SWP_NOZORDER | SWP_NOACTIVATE);
        return 0;
    }
    if (msg == 0x0231) { // WM_ENTERSIZEMOVE
        if (g_webviewController && g_webviewReady) {
            g_webviewController->put_IsVisible(FALSE);
        }
    }
    if (msg == 0x0232) { // WM_EXITSIZEMOVE
        if (g_webviewController && g_webviewReady) {
            g_webviewController->put_IsVisible(TRUE);
        }
    }
    if (g_originalTopLevelWndProc) {
        return CallWindowProc(g_originalTopLevelWndProc, hwnd, msg, wParam, lParam);
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
    if (msg == WM_PAINT) {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);
        FillRect(hdc, &ps.rcPaint, (HBRUSH)GetStockObject(BLACK_BRUSH));
        EndPaint(hwnd, &ps);
        return 0;
    }
    if (msg == WM_SIZE) {
        if (g_containerHwnd) {
            int w = LOWORD(lParam);
            int h = HIWORD(lParam);
            SetWindowPos(g_containerHwnd, nullptr, 0, 0, w, h, SWP_NOZORDER | SWP_NOACTIVATE);
            if (g_webviewController) {
                RECT bounds = {0, 0, w, h};
                g_webviewController->put_Bounds(bounds);
            }
        }
    }
    if (msg == WM_SETFOCUS) {
        if (g_webviewController) {
            g_webviewController->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
        }
    }
    return CallWindowProc(g_originalHostWndProc, hwnd, msg, wParam, lParam);
}

// Fullscreen state management
struct WindowFullscreenState {
    LONG_PTR       style      = 0;
    LONG_PTR       exStyle    = 0;
    WINDOWPLACEMENT placement = { sizeof(WINDOWPLACEMENT) };
};

std::mutex g_fullscreenMutex;
std::unordered_map<HWND, WindowFullscreenState> g_fullscreenStates;

extern "C" {

JNIEXPORT jlong JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_initWebView(
    JNIEnv* env, jobject thiz, jlong hostHwndPtr, jint width, jint height)
{
    g_hostHwnd = (HWND)hostHwndPtr;
    LOG_TO_FILE("[NativeBridge] initWebView called, thread=" << GetCurrentThreadId());

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

    std::unique_lock<std::mutex> lock(g_initMutex);
    g_initCv.wait(lock, []() { return g_initComplete; });

    LOG_TO_FILE("[NativeBridge] Returning combined HWND=" << g_containerHwnd);
    return reinterpret_cast<jlong>(g_containerHwnd);
}

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

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_setFullscreen(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean fullscreen,
    jint x, jint y, jint width, jint height)
{
    HWND hwnd = (HWND)(intptr_t)hwndPtr;
    if (!hwnd || !IsWindow(hwnd)) return;

    static HBRUSH s_blackBrush = CreateSolidBrush(RGB(0, 0, 0));
    SetClassLongPtrW(hwnd, GCLP_HBRBACKGROUND, (LONG_PTR)s_blackBrush);

    if (fullscreen == JNI_TRUE) {
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

        if (IsIconic(hwnd) || IsZoomed(hwnd)) {
            ShowWindow(hwnd, SW_RESTORE);
        }

        LONG_PTR style   = GetWindowLongPtrW(hwnd, GWL_STYLE);
        LONG_PTR exStyle = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
        style   &= ~(LONG_PTR)(WS_CAPTION | WS_THICKFRAME);
        exStyle &= ~(LONG_PTR)(WS_EX_DLGMODALFRAME | WS_EX_WINDOWEDGE | WS_EX_CLIENTEDGE | WS_EX_STATICEDGE);
        SetWindowLongPtrW(hwnd, GWL_STYLE,   style);
        SetWindowLongPtrW(hwnd, GWL_EXSTYLE, exStyle);

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

        SetWindowLongPtrW(hwnd, GWL_STYLE,   state.style);
        SetWindowLongPtrW(hwnd, GWL_EXSTYLE, state.exStyle);
        SetWindowPos(
            hwnd, nullptr,
            0, 0, 0, 0,
            SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_NOACTIVATE
        );

        if (state.placement.showCmd == SW_SHOWMAXIMIZED) {
            WINDOWPLACEMENT normalPlacement = state.placement;
            normalPlacement.showCmd = SW_SHOWNORMAL;
            SetWindowPlacement(hwnd, &normalPlacement);
            ShowWindow(hwnd, SW_MAXIMIZE);
        } else {
            SetWindowPlacement(hwnd, &state.placement);
        }
    }
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_applyWindowChrome(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean darkMode,
    jint captionColorRgb, jint borderColorRgb, jint textColorRgb)
{
    HWND hwnd = (HWND)(intptr_t)hwndPtr;
    if (!hwnd || !IsWindow(hwnd)) return;

    BOOL enabled = (darkMode == JNI_TRUE) ? TRUE : FALSE;
    HRESULT hr = DwmSetWindowAttribute(hwnd, 20 /*DWMWA_USE_IMMERSIVE_DARK_MODE*/, &enabled, sizeof(enabled));
    if (FAILED(hr)) {
        DwmSetWindowAttribute(hwnd, 19 /*DWMWA_USE_IMMERSIVE_DARK_MODE legacy*/, &enabled, sizeof(enabled));
    }

    auto toColorRef = [](jint rgb) -> COLORREF {
        return RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    };
    COLORREF captionColor = toColorRef(captionColorRgb);
    COLORREF borderColor  = toColorRef(borderColorRgb);
    COLORREF textColor    = toColorRef(textColorRgb);
    DwmSetWindowAttribute(hwnd, 35 /*DWMWA_CAPTION_COLOR*/, &captionColor, sizeof(captionColor));
    DwmSetWindowAttribute(hwnd, 34 /*DWMWA_BORDER_COLOR*/,  &borderColor,  sizeof(borderColor));
    DwmSetWindowAttribute(hwnd, 36 /*DWMWA_TEXT_COLOR*/,    &textColor,    sizeof(textColor));

    static HBRUSH s_blackBrush = CreateSolidBrush(RGB(13, 13, 13));
    SetClassLongPtrW(hwnd, GCLP_HBRBACKGROUND, (LONG_PTR)s_blackBrush);
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_setPipSubclass(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean enable)
{
    HWND hwnd = (HWND)(intptr_t)hwndPtr;
    if (!hwnd || !IsWindow(hwnd)) return;

    if (enable == JNI_TRUE) {
        if (!g_originalTopLevelWndProc) {
            g_originalTopLevelWndProc = (WNDPROC)SetWindowLongPtrW(hwnd, GWLP_WNDPROC, (LONG_PTR)TopLevelSubclassProc);
        }
    } else {
        if (g_originalTopLevelWndProc) {
            SetWindowLongPtrW(hwnd, GWLP_WNDPROC, (LONG_PTR)g_originalTopLevelWndProc);
            g_originalTopLevelWndProc = nullptr;
        }
    }
}

} // extern "C"
