package android.view;

import java.util.ArrayList;
import java.util.List;

@android.annotation.Stub
public class ViewPropertyAnimator {
    public final View view;
    private final List<Runnable> pending = new ArrayList<>();

    public ViewPropertyAnimator(View view) {
        this.view = view;
    }

    public ViewPropertyAnimator scaleX(float value) { return this; }
    public ViewPropertyAnimator scaleY(float value) { return this; }
    public ViewPropertyAnimator translationY(float value) { return this; }
    public ViewPropertyAnimator translationX(float value) { return this; }
    public ViewPropertyAnimator alpha(float value) { return this; }
    public ViewPropertyAnimator setDuration(long duration) { return this; }
    public ViewPropertyAnimator setInterpolator(Object interpolator) { return this; }

    public ViewPropertyAnimator withEndAction(Runnable action) {
        if (action != null) pending.add(action);
        return this;
    }

    public ViewPropertyAnimator withStartAction(Runnable action) {
        if (action != null) pending.add(action);
        return this;
    }

    public void start() {
        List<Runnable> actions = new ArrayList<>(pending);
        pending.clear();
        for (Runnable r : actions) {
            r.run();
        }
    }

    public void cancel() {
        pending.clear();
    }
}
