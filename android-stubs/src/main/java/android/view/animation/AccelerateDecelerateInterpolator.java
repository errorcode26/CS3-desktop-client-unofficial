package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public class AccelerateDecelerateInterpolator implements Interpolator {
    public AccelerateDecelerateInterpolator() {}
    public AccelerateDecelerateInterpolator(Context context, AttributeSet attrs) {}

    @Override
    public float getInterpolation(float input) {
        return (float) (Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
    }
}
