package android.util;

@android.annotation.Stub
public class TypedValue {
    public static final int COMPLEX_UNIT_DIP = 1;

    public static float applyDimension(int unit, float value, DisplayMetrics metrics) {
        float density = metrics != null ? metrics.density : 1.0f;
        return value * density;
    }
}
