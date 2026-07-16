package android.view;

@android.annotation.Stub
public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    public View() {}
    public View(android.content.Context context) {}

    public interface OnClickListener {
        void onClick(View v);
    }

    public void setOnClickListener(OnClickListener listener) {
        // No-op for desktop stub
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        // No-op for desktop stub
    }

    public void setVisibility(int visibility) {
        // No-op for desktop stub
    }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        // No-op for desktop stub
    }

    public void setPadding(int left, int top, int right, int bottom) {
        // No-op for desktop stub
    }

    public void setBackgroundColor(int color) {
        // No-op for desktop stub
    }

    public void setBackground(android.graphics.drawable.Drawable background) {
        // No-op for desktop stub
    }

    public void setFocusable(boolean focusable) {
    }

    public void setClickable(boolean clickable) {
    }

    public boolean requestFocus() {
        return true;
    }

    public void setEnabled(boolean enabled) {
    }

    public void setId(int id) {
    }
}
