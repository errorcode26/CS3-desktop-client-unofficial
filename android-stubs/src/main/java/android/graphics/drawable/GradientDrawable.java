package android.graphics.drawable;

@android.annotation.Stub
public class GradientDrawable extends Drawable {
    public enum Orientation {
        TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR
    }

    public GradientDrawable() {}

    public GradientDrawable(Orientation orientation, int[] colors) {}

    public void setOrientation(Orientation orientation) {}
    public void setColors(int[] colors) {}
    public void setStroke(int width, int color) {}

    public void setColor(int argb) {
        // No-op
    }

    public void setCornerRadius(float radius) {
        // No-op
    }
}

