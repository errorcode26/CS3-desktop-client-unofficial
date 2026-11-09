package android.text;

@android.annotation.Implemented
public class Html {
    public static final int FROM_HTML_MODE_LEGACY = 0;
    public static final int FROM_HTML_MODE_COMPACT = 63;

    public interface ImageGetter {
        android.graphics.drawable.Drawable getDrawable(String source);
    }

    public interface TagHandler {
        void handleTag(boolean opening, String tag, Editable output, org.xml.sax.XMLReader xmlReader);
    }

    public static Spanned fromHtml(String source) {
        return new SpannedString(cleanHtml(source));
    }

    public static Spanned fromHtml(String source, int flags) {
        return new SpannedString(cleanHtml(source));
    }

    public static Spanned fromHtml(String source, int flags, ImageGetter imageGetter, TagHandler tagHandler) {
        return new SpannedString(cleanHtml(source));
    }

    public static String toHtml(Spanned text) {
        return text != null ? text.toString() : "";
    }

    public static String toHtml(Spanned text, int option) {
        return text != null ? text.toString() : "";
    }

    private static String cleanHtml(String source) {
        if (source == null) return "";
        return source
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n\n")
            .replaceAll("<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    }
}
