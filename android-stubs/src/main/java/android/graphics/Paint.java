package android.graphics;

@android.annotation.Stub
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 0x01;
    public static final int FILTER_BITMAP_FLAG = 0x02;
    public static final int DITHER_FLAG = 0x04;

    public Paint() {}
    public Paint(int flags) {}

    public void setAntiAlias(boolean aa) {}
    public void setColor(int color) {}
    public int getColor() { return 0; }
    public void setAlpha(int a) {}
    public int getAlpha() { return 255; }
}
