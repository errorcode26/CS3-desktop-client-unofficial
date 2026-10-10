package android.webkit;

@android.annotation.Stub
public class WebSettings {
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";
    private boolean javaScriptEnabled = true;
    private boolean domStorageEnabled = true;
    private boolean mediaPlaybackRequiresUserGesture = false;
    private boolean blockNetworkImage = false;
    private boolean allowFileAccess = true;
    private boolean allowContentAccess = true;
    private boolean appCacheEnabled = true;
    private boolean databaseEnabled = true;
    private boolean loadsImagesAutomatically = true;
    private boolean useWideViewPort = true;
    private boolean loadWithOverviewMode = true;
    private boolean supportZoom = true;
    private boolean builtInZoomControls = false;
    private boolean displayZoomControls = false;
    private int cacheMode = 0;
    private int mixedContentMode = 0;
    private boolean supportMultipleWindows = false;
    private boolean javaScriptCanOpenWindowsAutomatically = false;
    private String defaultTextEncodingName = "UTF-8";
    private String standardFontFamily = "sans-serif";
    private PluginState pluginState = PluginState.OFF;

    public void setJavaScriptEnabled(boolean flag) { this.javaScriptEnabled = flag; }
    public boolean getJavaScriptEnabled() { return this.javaScriptEnabled; }

    public void setDomStorageEnabled(boolean flag) { this.domStorageEnabled = flag; }
    public boolean getDomStorageEnabled() { return this.domStorageEnabled; }

    public void setMediaPlaybackRequiresUserGesture(boolean flag) { this.mediaPlaybackRequiresUserGesture = flag; }
    public boolean getMediaPlaybackRequiresUserGesture() { return this.mediaPlaybackRequiresUserGesture; }

    public void setBlockNetworkImage(boolean flag) { this.blockNetworkImage = flag; }
    public boolean getBlockNetworkImage() { return this.blockNetworkImage; }

    public void setUserAgentString(String ua) { this.userAgent = ua; }
    public String getUserAgentString() { return this.userAgent; }

    public void setAllowFileAccess(boolean flag) { this.allowFileAccess = flag; }
    public boolean getAllowFileAccess() { return this.allowFileAccess; }

    public void setAllowContentAccess(boolean flag) { this.allowContentAccess = flag; }
    public boolean getAllowContentAccess() { return this.allowContentAccess; }

    public void setAppCacheEnabled(boolean flag) { this.appCacheEnabled = flag; }
    public boolean getAppCacheEnabled() { return this.appCacheEnabled; }

    public void setDatabaseEnabled(boolean flag) { this.databaseEnabled = flag; }
    public boolean getDatabaseEnabled() { return this.databaseEnabled; }

    public void setLoadsImagesAutomatically(boolean flag) { this.loadsImagesAutomatically = flag; }
    public boolean getLoadsImagesAutomatically() { return this.loadsImagesAutomatically; }

    public void setUseWideViewPort(boolean flag) { this.useWideViewPort = flag; }
    public boolean getUseWideViewPort() { return this.useWideViewPort; }

    public void setLoadWithOverviewMode(boolean flag) { this.loadWithOverviewMode = flag; }
    public boolean getLoadWithOverviewMode() { return this.loadWithOverviewMode; }

    public void setSupportZoom(boolean flag) { this.supportZoom = flag; }
    public boolean getSupportZoom() { return this.supportZoom; }

    public void setBuiltInZoomControls(boolean flag) { this.builtInZoomControls = flag; }
    public boolean getBuiltInZoomControls() { return this.builtInZoomControls; }

    public void setDisplayZoomControls(boolean flag) { this.displayZoomControls = flag; }
    public boolean getDisplayZoomControls() { return this.displayZoomControls; }

    public void setCacheMode(int mode) { this.cacheMode = mode; }
    public int getCacheMode() { return this.cacheMode; }

    public void setMixedContentMode(int mode) { this.mixedContentMode = mode; }
    public int getMixedContentMode() { return this.mixedContentMode; }

    public void setSupportMultipleWindows(boolean flag) { this.supportMultipleWindows = flag; }
    public boolean getSupportMultipleWindows() { return this.supportMultipleWindows; }

    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) { this.javaScriptCanOpenWindowsAutomatically = flag; }
    public boolean getJavaScriptCanOpenWindowsAutomatically() { return this.javaScriptCanOpenWindowsAutomatically; }

    public void setDefaultTextEncodingName(String name) { this.defaultTextEncodingName = name; }
    public String getDefaultTextEncodingName() { return this.defaultTextEncodingName; }

    public void setStandardFontFamily(String font) { this.standardFontFamily = font; }
    public String getStandardFontFamily() { return this.standardFontFamily; }

    public void setPluginState(PluginState state) { this.pluginState = state; }
    public PluginState getPluginState() { return this.pluginState; }

    public enum PluginState {
        ON, ON_DEMAND, OFF
    }
}
