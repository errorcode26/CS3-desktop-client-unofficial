package android.text.style;

@android.annotation.Stub
public class ForegroundColorSpan extends CharacterStyle {
    private final int color;

    public ForegroundColorSpan(int color) {
        this.color = color;
    }

    public int getForegroundColor() {
        return color;
    }
}
