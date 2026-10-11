package android.text.style;

@android.annotation.Stub
public class StyleSpan extends CharacterStyle {
    private final int style;

    public StyleSpan(int style) {
        this.style = style;
    }

    public int getStyle() {
        return style;
    }
}
