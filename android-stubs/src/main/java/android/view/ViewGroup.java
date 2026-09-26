package android.view;

import android.content.Context;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;
import java.util.ArrayList;
import java.util.List;

@android.annotation.Stub
public class ViewGroup extends View implements ViewParent, ViewManager {

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public int width = MATCH_PARENT;
        public int height = WRAP_CONTENT;

        public LayoutParams() {}

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public LayoutParams(LayoutParams source) {
            if (source != null) {
                this.width = source.width;
                this.height = source.height;
            }
        }

        public LayoutParams(Context c, Object attrs) {}
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int topMargin = 0;
        public int bottomMargin = 0;
        public int leftMargin = 0;
        public int rightMargin = 0;
        public int marginStart = 0;
        public int marginEnd = 0;

        public MarginLayoutParams() { super(); }
        public MarginLayoutParams(int width, int height) { super(width, height); }
        public MarginLayoutParams(LayoutParams source) { super(source); }
        public MarginLayoutParams(MarginLayoutParams source) {
            super(source);
            if (source != null) {
                this.topMargin = source.topMargin;
                this.bottomMargin = source.bottomMargin;
                this.leftMargin = source.leftMargin;
                this.rightMargin = source.rightMargin;
                this.marginStart = source.marginStart;
                this.marginEnd = source.marginEnd;
            }
        }
        public MarginLayoutParams(Context c, Object attrs) { super(c, attrs); }

        public void setMargins(int left, int top, int right, int bottom) {
            this.leftMargin = left;
            this.topMargin = top;
            this.rightMargin = right;
            this.bottomMargin = bottom;
        }

        public void setMarginStart(int start) { this.marginStart = start; }
        public void setMarginEnd(int end) { this.marginEnd = end; }
        public int getMarginStart() { return marginStart; }
        public int getMarginEnd() { return marginEnd; }
        public int getTopMargin() { return topMargin; }
        public int getBottomMargin() { return bottomMargin; }
        public int getLeftMargin() { return leftMargin; }
        public int getRightMargin() { return rightMargin; }
    }

    public final List<View> children = new ArrayList<>();

    public ViewGroup() {}
    public ViewGroup(Context context) { super(context); }
    public ViewGroup(Context context, Object attrs) { super(context, attrs); }

    public void addView(View child) {
        if (child != null) {
            children.add(child);
            child.setParent(this);
            ShadowUi.INSTANCE.bump();
        }
    }

    public void addView(View child, int index) {
        if (child != null) {
            int targetIndex = Math.max(0, Math.min(index, children.size()));
            children.add(targetIndex, child);
            child.setParent(this);
            ShadowUi.INSTANCE.bump();
        }
    }

    @Override
    public void addView(View child, LayoutParams params) {
        if (child != null) {
            child.setLayoutParams(params);
            children.add(child);
            child.setParent(this);
            ShadowUi.INSTANCE.bump();
        }
    }

    @Override
    public void updateViewLayout(View view, LayoutParams params) {
        if (view != null) {
            view.setLayoutParams(params);
            ShadowUi.INSTANCE.bump();
        }
    }

    @Override
    public void removeView(View child) {
        if (child != null) {
            children.remove(child);
            child.setParent(null);
            ShadowUi.INSTANCE.bump();
        }
    }

    public void removeAllViews() {
        for (View c : children) {
            c.setParent(null);
        }
        children.clear();
        ShadowUi.INSTANCE.bump();
    }

    public int getChildCount() {
        return children.size();
    }

    public View getChildAt(int index) {
        if (index >= 0 && index < children.size()) {
            return children.get(index);
        }
        return null;
    }

    @Override
    public void requestLayout() {}

    @Override
    public boolean isLayoutRequested() {
        return false;
    }
}
