package android.webkit;

@android.annotation.Stub
public class WebViewClient {
    public WebViewClient() {}

    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
    }

    public void onPageFinished(WebView view, String url) {
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return false;
    }

    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return null;
    }

    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return null;
    }
}
