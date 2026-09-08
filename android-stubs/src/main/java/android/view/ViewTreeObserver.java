package android.view;

@android.annotation.Stub
public class ViewTreeObserver {
    public interface OnGlobalLayoutListener {
        void onGlobalLayout();
    }

    public void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) {
        if (listener != null) {
            try {
                listener.onGlobalLayout();
            } catch (Throwable ignored) {
            }
        }
    }

    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener victim) {
    }

    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener victim) {
    }
}
