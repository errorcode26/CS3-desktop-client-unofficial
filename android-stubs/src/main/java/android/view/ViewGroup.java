package android.view;

@android.annotation.Stub
public class ViewGroup extends View implements ViewParent, ViewManager {
    public static class LayoutParams {
        public int width;
        public int height;

        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

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
    }

    public ViewGroup() {}

    public ViewGroup(android.content.Context context) {
        super(context);
    }

    public void addView(android.view.View child) {
        if (child != null) {
            child.setParent(this);
        }
    }

    @Override
    public void addView(View view, ViewGroup.LayoutParams params) {
        if (view != null) {
            view.setLayoutParams(params);
            view.setParent(this);
        }
    }

    @Override
    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        if (view != null) {
            view.setLayoutParams(params);
        }
    }

    @Override
    public void removeView(View child) {
        if (child != null) {
            child.setParent(null);
        }
    }

    public void removeAllViews() {}

    @Override
    public void requestLayout() {}

    @Override
    public boolean isLayoutRequested() {
        return false;
    }
}
