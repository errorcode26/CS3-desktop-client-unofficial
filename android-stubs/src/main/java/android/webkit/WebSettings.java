package android.webkit;

@android.annotation.Stub
public class WebSettings {
    public void setJavaScriptEnabled(boolean flag) {}
    public void setDomStorageEnabled(boolean flag) {}
    public void setMediaPlaybackRequiresUserGesture(boolean flag) {}
    public void setBlockNetworkImage(boolean flag) {}
    public void setUserAgentString(String ua) {}
    public void setAllowFileAccess(boolean flag) {}
    public void setAllowContentAccess(boolean flag) {}
    public void setAppCacheEnabled(boolean flag) {}
    public void setDatabaseEnabled(boolean flag) {}
    public void setLoadsImagesAutomatically(boolean flag) {}
    public void setUseWideViewPort(boolean flag) {}
    public void setLoadWithOverviewMode(boolean flag) {}
    public void setSupportZoom(boolean flag) {}
    public void setBuiltInZoomControls(boolean flag) {}
    public void setDisplayZoomControls(boolean flag) {}
    public void setCacheMode(int mode) {}
    public void setMixedContentMode(int mode) {}
    public void setSupportMultipleWindows(boolean flag) {}
    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {}
    public void setDefaultTextEncodingName(String name) {}
    public void setStandardFontFamily(String font) {}
    public void setPluginState(PluginState state) {}
    
    public enum PluginState {
        ON, ON_DEMAND, OFF
    }
}
