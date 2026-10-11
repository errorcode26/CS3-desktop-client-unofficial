package android.animation;

@android.annotation.Stub
public interface TypeEvaluator<T> {
    T evaluate(float fraction, T startValue, T endValue);
}
