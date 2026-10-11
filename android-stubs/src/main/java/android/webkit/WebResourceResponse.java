package android.webkit;

import java.io.InputStream;
import java.util.Map;

@android.annotation.Stub
public class WebResourceResponse {
    private String mimeType;
    private String encoding;
    private InputStream data;

    public WebResourceResponse(String mimeType, String encoding, InputStream data) {
        this.mimeType = mimeType;
        this.encoding = encoding;
        this.data = data;
    }

    public WebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> responseHeaders, InputStream data) {
        this.mimeType = mimeType;
        this.encoding = encoding;
        this.data = data;
    }

    public String getMimeType() { return mimeType; }
    public String getEncoding() { return encoding; }
    public InputStream getData() { return data; }
}
