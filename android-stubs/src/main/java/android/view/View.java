package android.view;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;
import java.util.concurrent.atomic.AtomicInteger;

@android.annotation.Stub
public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    private static final AtomicInteger NEXT_GENERATED_ID = new AtomicInteger(0x100000);

    public int id = 0;
    public boolean isEnabled = true;
    private int visibility = VISIBLE;

    public ViewGroup.LayoutParams layoutParams;

    public Drawable background;
    public int paddingLeft = 0;
    public int paddingTop = 0;
    public int paddingRight = 0;
    public int paddingBottom = 0;

    public float alpha = 1.0f;
    public float translationX = 0f;
    public float translationY = 0f;
    public int minHeight = 0;
    public int minWidth = 0;

    public OnClickListener clickListener;
    public OnLongClickListener longClickListener;
    public OnFocusChangeListener focusChangeListener;
    public OnKeyListener keyListener;

    private Object tag;
    private ViewParent parent;
    private final ViewTreeObserver viewTreeObserver = new ViewTreeObserver();

    public View() {}
    public View(Context context) {}
    public View(Context context, Object attrs) {}

    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnLongClickListener {
        boolean onLongClick(View v);
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent event);
    }

    public interface OnTouchListener {
        boolean onTouch(View v, Object event);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getVisibility() { return visibility; }
    public void setVisibility(int visibility) {
        this.visibility = visibility;
        ShadowUi.INSTANCE.bump();
    }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { this.isEnabled = enabled; }

    public ViewGroup.LayoutParams getLayoutParams() { return layoutParams; }
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        this.layoutParams = params;
        ShadowUi.INSTANCE.bump();
    }

    public void setPadding(int left, int top, int right, int bottom) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
    }
    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingBottom() { return paddingBottom; }

    public Drawable getBackground() { return background; }
    public void setBackground(Drawable background) { this.background = background; }
    public void setBackgroundDrawable(Drawable background) { this.background = background; }
    public void setBackgroundColor(int color) { this.background = new ColorDrawable(color); }

    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) { this.alpha = alpha; }

    public float getTranslationX() { return translationX; }
    public void setTranslationX(float translationX) { this.translationX = translationX; }

    public float getTranslationY() { return translationY; }
    public void setTranslationY(float translationY) { this.translationY = translationY; }

    public void setMinHeight(int minHeight) { this.minHeight = minHeight; }
    public void setMinWidth(int minWidth) { this.minWidth = minWidth; }

    public void setOnClickListener(OnClickListener listener) {
        this.clickListener = listener;
    }
    public OnClickListener getOnClickListener() { return clickListener; }

    public void setOnLongClickListener(OnLongClickListener listener) {
        this.longClickListener = listener;
    }
    public OnLongClickListener getOnLongClickListener() { return longClickListener; }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        this.focusChangeListener = l;
    }
    public OnFocusChangeListener getOnFocusChangeListener() { return focusChangeListener; }

    public void setOnKeyListener(OnKeyListener l) {
        this.keyListener = l;
    }

    public void setOnTouchListener(OnTouchListener l) {}

    public View findViewById(int targetId) {
        if (this.id == targetId) return this;
        if (this instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) this;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child != null) {
                    View found = child.findViewById(targetId);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    public boolean performClick() {
        if (clickListener != null) {
            clickListener.onClick(this);
            return true;
        }
        return false;
    }

    public boolean performLongClick() {
        if (longClickListener != null) {
            return longClickListener.onLongClick(this);
        }
        return false;
    }

    public ViewPropertyAnimator animate() {
        return new ViewPropertyAnimator(this);
    }

    public boolean post(Runnable action) {
        if (action == null) return false;
        new android.os.Handler().post(action);
        return true;
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        if (action == null) return false;
        new android.os.Handler().postDelayed(action, delayMillis);
        return true;
    }

    public void removeCallbacks(Runnable action) {
        new android.os.Handler().removeCallbacks(action);
    }

    public void invalidate() {}
    public void requestLayout() {}
    public void bringToFront() {}
    public boolean requestFocus() { return true; }
    public void clearFocus() {}
    public boolean isFocused() { return false; }
    public void setClickable(boolean clickable) {}
    public void setFocusable(boolean focusable) {}
    public void setFocusableInTouchMode(boolean focusable) {}
    public void setSelected(boolean selected) {}
    public boolean isSelected() { return false; }

    public int getWidth() { return 0; }
    public int getHeight() { return 0; }

    public Object getTag() { return tag; }
    public void setTag(Object tag) { this.tag = tag; }

    public ViewParent getParent() { return parent; }
    public void setParent(ViewParent parent) { this.parent = parent; }

    public ViewTreeObserver getViewTreeObserver() { return viewTreeObserver; }

    public static int generateViewId() {
        return NEXT_GENERATED_ID.incrementAndGet();
    }
}
