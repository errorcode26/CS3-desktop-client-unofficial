package android.animation;

import java.util.ArrayList;
import java.util.List;

@android.annotation.Stub
public class ValueAnimator extends Animator {
    public interface AnimatorUpdateListener {
        void onAnimationUpdate(ValueAnimator animation);
    }

    private Object animatedValue = 0.0f;
    private long duration = 300L;
    private final List<AnimatorUpdateListener> updateListeners = new ArrayList<>();
    private final List<AnimatorListener> listeners = new ArrayList<>();

    public static ValueAnimator ofFloat(float... values) {
        ValueAnimator anim = new ValueAnimator();
        if (values != null && values.length > 0) {
            anim.animatedValue = values[values.length - 1];
        }
        return anim;
    }

    public static ValueAnimator ofInt(int... values) {
        ValueAnimator anim = new ValueAnimator();
        if (values != null && values.length > 0) {
            anim.animatedValue = values[values.length - 1];
        }
        return anim;
    }

    public static ValueAnimator ofObject(TypeEvaluator<?> evaluator, Object... values) {
        ValueAnimator anim = new ValueAnimator();
        if (values != null && values.length > 0) {
            anim.animatedValue = values[values.length - 1];
        }
        return anim;
    }

    public Object getAnimatedValue() {
        return animatedValue;
    }

    public ValueAnimator setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public long getDuration() {
        return duration;
    }

    public void setStartDelay(long startDelay) {}

    @Override
    public void setInterpolator(TimeInterpolator value) {}

    public void addUpdateListener(AnimatorUpdateListener listener) {
        if (listener != null) updateListeners.add(listener);
    }

    @Override
    public void addListener(AnimatorListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void start() {
        for (AnimatorUpdateListener l : updateListeners) {
            try { l.onAnimationUpdate(this); } catch (Throwable ignored) {}
        }
        for (AnimatorListener l : listeners) {
            try {
                l.onAnimationStart(this);
                l.onAnimationEnd(this);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void cancel() {}

    @Override
    public boolean isRunning() {
        return false;
    }

    public void setRepeatCount(int value) {}
    public void setRepeatMode(int value) {}
}
