package android.webkit;

import android.content.Context;
import android.view.View;

@android.annotation.Stub
public class WebView extends View {
    public static java.util.function.BiConsumer<WebView, String> loadUrlHandler = null;
    private WebViewClient webViewClient = null;

    public WebView(Context context) {
        // Silently accept creation
    }

    public WebSettings getSettings() {
        return new WebSettings();
    }

    public void loadUrl(String url) {
        if (loadUrlHandler != null) {
            loadUrlHandler.accept(this, url);
        }
    }

    public void setWebViewClient(WebViewClient client) {
        this.webViewClient = client;
    }

    public WebViewClient getWebViewClient() {
        return this.webViewClient;
    }

    public void setWebChromeClient(WebChromeClient client) {
        // Silently accept
    }

    public void addJavascriptInterface(Object obj, String interfaceName) {
        // Silently accept
    }

    public void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
        if (resultCallback != null) {
            resultCallback.onReceiveValue(null);
        }
    }

    public void stopLoading() {
        // No-op
    }

    public void reload() {
        // No-op
    }

    public void destroy() {
        // No-op
    }
}
