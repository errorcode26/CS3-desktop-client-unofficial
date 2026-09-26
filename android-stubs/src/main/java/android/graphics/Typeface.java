package android.graphics;

@android.annotation.Stub
public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    public static final Typeface DEFAULT = new Typeface();
    public static final Typeface DEFAULT_BOLD = new Typeface();
    public static final Typeface SANS_SERIF = new Typeface();
    public static final Typeface SERIF = new Typeface();
    public static final Typeface MONOSPACE = new Typeface();

    public boolean isBold() {
        return this == DEFAULT_BOLD;
    }

    public boolean isItalic() {
        return false;
    }

    public static Typeface create(String familyName, int style) {
        return (style == BOLD || style == BOLD_ITALIC) ? DEFAULT_BOLD : DEFAULT;
    }

    public static Typeface create(Typeface family, int style) {
        return (style == BOLD || style == BOLD_ITALIC) ? DEFAULT_BOLD : DEFAULT;
    }

    public static Typeface defaultFromStyle(int style) {
        return (style == BOLD || style == BOLD_ITALIC) ? DEFAULT_BOLD : DEFAULT;
    }
}
