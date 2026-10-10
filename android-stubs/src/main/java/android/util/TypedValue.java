package android.util;

@android.annotation.Stub
public class TypedValue {
    public static final int COMPLEX_UNIT_DIP = 1;

    public int data;
    public int resourceId;
    public int type;

    public static float applyDimension(int unit, float value, DisplayMetrics metrics) {
        float density = metrics != null ? metrics.density : 1.0f;
        return value * density;
    }
}
