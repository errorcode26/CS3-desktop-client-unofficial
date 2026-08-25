#include "../include/player_bridge_common.h"

#ifndef _WIN32
// Linux Implementation Stub for CloudStream Desktop
// This file serves as the clean extension point for X11 / Wayland surface embedding on Linux.

void postUiTask(std::function<void()> task) {
    if (task) task();
}

void processUiTasks() {}

extern "C" {

JNIEXPORT jlong JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_initWebView(
    JNIEnv* env, jobject thiz, jlong hostHwndPtr, jint width, jint height)
{
    LOG_TO_FILE("[NativeBridge:Linux] initWebView called. Linux X11/Wayland embedding extension point.");
    // For Linux: hostHwndPtr is the X11 Window ID (XID) from AWT Canvas or JOGL / Skiko surface.
    // Return the XID directly to attach MPV --wid.
    return hostHwndPtr;
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_destroyWebView(
    JNIEnv* env, jobject thiz)
{
    LOG_TO_FILE("[NativeBridge:Linux] destroyWebView called.");
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_setFullscreen(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean fullscreen,
    jint x, jint y, jint width, jint height)
{
    // On Linux, full-screen is handled via EWMH window manager hints (_NET_WM_STATE_FULLSCREEN).
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_applyWindowChrome(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean darkMode,
    jint captionColorRgb, jint borderColorRgb, jint textColorRgb)
{
    // Linux window managers (GNOME, KDE) handle CSD / window decorations via GTK/Qt/KWin.
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_setPipSubclass(
    JNIEnv* env, jobject thiz,
    jlong hwndPtr, jboolean enable)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_resizeWebView(
    JNIEnv* env, jobject thiz, jint width, jint height)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_focusWebView(
    JNIEnv* env, jobject thiz)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_executeScript(
    JNIEnv* env, jobject thiz, jstring script)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_loadUrl(
    JNIEnv* env, jobject thiz, jstring url)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_openDevTools(
    JNIEnv* env, jobject thiz)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_postMessage(
    JNIEnv* env, jobject thiz, jstring message)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_warmupWebView2(
    JNIEnv* env, jobject thiz)
{
}

JNIEXPORT void JNICALL Java_com_lagradost_cloudstream3_desktop_player_webview_NativePlayerBridge_shutdownWebView2Warmup(
    JNIEnv* env, jobject thiz)
{
}

} // extern "C"
#endif
