package android.webkit;

import java.util.HashMap;
import java.util.Map;

@android.annotation.Stub
public class MimeTypeMap {
    private static final MimeTypeMap INSTANCE = new MimeTypeMap();
    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();
    private static final Map<String, String> MIME_TO_EXT = new HashMap<>();

    static {
        add("mp4", "video/mp4");
        add("mkv", "video/x-matroska");
        add("m3u8", "application/vnd.apple.mpegurl");
        add("mpd", "application/dash+xml");
        add("webm", "video/webm");
        add("avi", "video/x-msvideo");
        add("mov", "video/quicktime");
        add("flv", "video/x-flv");
        add("ts", "video/mp2t");
        add("mp3", "audio/mpeg");
        add("aac", "audio/aac");
        add("flac", "audio/flac");
        add("wav", "audio/wav");
        add("ogg", "audio/ogg");
        add("jpg", "image/jpeg");
        add("jpeg", "image/jpeg");
        add("png", "image/png");
        add("webp", "image/webp");
        add("gif", "image/gif");
        add("json", "application/json");
        add("txt", "text/plain");
        add("html", "text/html");
        add("xml", "text/xml");
        add("pdf", "application/pdf");
        add("srt", "application/x-subrip");
        add("vtt", "text/vtt");
    }

    private static void add(String ext, String mime) {
        EXT_TO_MIME.put(ext.toLowerCase(), mime.toLowerCase());
        if (!MIME_TO_EXT.containsKey(mime.toLowerCase())) {
            MIME_TO_EXT.put(mime.toLowerCase(), ext.toLowerCase());
        }
    }

    public static MimeTypeMap getSingleton() {
        return INSTANCE;
    }

    public static String getFileExtensionFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        int fragment = url.indexOf('#');
        if (fragment >= 0) url = url.substring(0, fragment);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        int filenameIndex = url.lastIndexOf('/');
        String filename = filenameIndex >= 0 ? url.substring(filenameIndex + 1) : url;
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) return filename.substring(dot + 1).toLowerCase();
        return "";
    }

    public boolean hasMimeType(String mimeType) {
        return mimeType != null && MIME_TO_EXT.containsKey(mimeType.toLowerCase());
    }

    public String getMimeTypeFromExtension(String extension) {
        return extension != null ? EXT_TO_MIME.get(extension.toLowerCase()) : null;
    }

    public boolean hasExtension(String extension) {
        return extension != null && EXT_TO_MIME.containsKey(extension.toLowerCase());
    }

    public String getExtensionFromMimeType(String mimeType) {
        return mimeType != null ? MIME_TO_EXT.get(mimeType.toLowerCase()) : null;
    }
}
