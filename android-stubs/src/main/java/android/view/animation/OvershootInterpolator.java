package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public class OvershootInterpolator implements Interpolator {
    private final float tension;

    public OvershootInterpolator() {
        this.tension = 2.0f;
    }

    public OvershootInterpolator(float tension) {
        this.tension = tension;
    }

    public OvershootInterpolator(Context context, AttributeSet attrs) {
        this.tension = 2.0f;
    }

    @Override
    public float getInterpolation(float t) {
        t -= 1.0f;
        return t * t * ((tension + 1) * t + tension) + 1.0f;
    }
}
