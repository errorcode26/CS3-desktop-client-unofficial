package android.view.animation;

@android.annotation.Stub
public class DecelerateInterpolator implements Interpolator {
    public DecelerateInterpolator() {}
    public DecelerateInterpolator(float factor) {}

    @Override
    public float getInterpolation(float input) {
        return input;
    }
}
