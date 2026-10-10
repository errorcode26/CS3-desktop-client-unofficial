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

    public void loadData(String data, String mimeType, String encoding) {
        if (loadUrlHandler != null && data != null) {
            loadUrlHandler.accept(this, "data:" + (mimeType != null ? mimeType : "text/html") + ";charset=" + (encoding != null ? encoding : "utf-8") + "," + data);
        }
    }

    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        if (loadUrlHandler != null) {
            String target = baseUrl != null ? baseUrl : (data != null ? "data:" + (mimeType != null ? mimeType : "text/html") + "," + data : "about:blank");
            loadUrlHandler.accept(this, target);
        }
    }

    public void postUrl(String url, byte[] postData) {
        if (loadUrlHandler != null) {
            loadUrlHandler.accept(this, url);
        }
    }

    public void clearCache(boolean includeDiskFiles) {
        // No-op
    }

    public void clearHistory() {
        // No-op
    }

    public boolean canGoBack() {
        return false;
    }

    public void goBack() {
        // No-op
    }

    public boolean canGoForward() {
        return false;
    }

    public void goForward() {
        // No-op
    }

    public String getUrl() {
        return null;
    }

    public String getTitle() {
        return null;
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

    @Override
    public void setLayerType(int layerType, android.graphics.Paint paint) {
        super.setLayerType(layerType, paint);
    }

    @Override
    public int getLayerType() {
        return super.getLayerType();
    }
}
