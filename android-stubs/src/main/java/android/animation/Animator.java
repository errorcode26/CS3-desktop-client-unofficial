package android.animation;

@android.annotation.Stub
public abstract class Animator {
    public interface AnimatorListener {
        void onAnimationStart(Animator animation);
        void onAnimationEnd(Animator animation);
        void onAnimationCancel(Animator animation);
        void onAnimationRepeat(Animator animation);
    }

    public void addListener(AnimatorListener listener) {}
    public void start() {}
    public void cancel() {}
    public boolean isRunning() { return false; }
    public abstract void setInterpolator(TimeInterpolator value);
}
