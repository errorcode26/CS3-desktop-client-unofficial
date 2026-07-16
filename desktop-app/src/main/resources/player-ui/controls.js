// controls.js — Standalone bridge utilities (currently controls.html uses its own inline script)
// This file is kept for reference and potential future extraction.
// The canonical sendToKotlin implementation is in controls.html.

// IMPORTANT: Always send a RAW OBJECT (not JSON.stringify) to postMessage().
// WebView2's get_WebMessageAsJson() returns the serialized JSON directly.
// If you JSON.stringify first and then postMessage(), the C++ side will receive
// a quoted string like "\"...\"" which breaks the JSON field extraction.
const sendToKotlin = (type, value) => {
    const payload = { type: type, value: (value === undefined ? '' : String(value)) };
    const webViewBridge = window.chrome && window.chrome.webview;
    if (webViewBridge) {
        webViewBridge.postMessage(payload);
        console.log('[JS→Kotlin] Dispatched:', payload);
    } else {
        console.warn('[JS→Kotlin] WebView2 bridge not found! Payload:', payload);
    }
};

// Listen for Kotlin calling us via executeScript: window.playerUpdate(stateJson)
// stateJson is a JSON string with fields: type, positionMs, durationMs, isPlaying, volume, isMuted
window.playerUpdate = (stateJson) => {
    try {
        const state = typeof stateJson === 'string' ? JSON.parse(stateJson) : stateJson;
        const statusEl = document.getElementById('status');
        if (statusEl && state.type === 'state_update') {
            statusEl.innerText = `Position: ${state.positionMs}ms | Playing: ${state.isPlaying}`;
        }
        console.log('[Kotlin→JS] Received:', state);
    } catch (e) {
        console.error('[Kotlin→JS] Error parsing state update:', e);
    }
};
