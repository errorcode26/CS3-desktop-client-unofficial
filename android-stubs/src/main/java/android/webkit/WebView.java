package android.webkit;

import android.content.Context;
import android.view.View;

@android.annotation.Stub
public class WebView extends View {
    public static java.util.function.Consumer<String> loadUrlHandler = null;

    public WebView(Context context) {
        // Silently accept creation
    }

    public WebSettings getSettings() {
        return null;
    }

    public void loadUrl(String url) {
        // Silently intercept direct WebView usage and pass to the global Playwright resolver!
        if (loadUrlHandler != null) {
            loadUrlHandler.accept(url);
        }
    }

    public void setWebViewClient(WebViewClient client) {
        // Silently accept
    }

    public void setWebChromeClient(WebChromeClient client) {
        // Silently accept
    }

    public void addJavascriptInterface(Object obj, String interfaceName) {
        // Silently accept
    }
}
