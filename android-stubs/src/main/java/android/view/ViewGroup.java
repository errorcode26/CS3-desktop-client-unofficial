package android.view;

@android.annotation.Stub
public class ViewGroup extends View {
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
    }

    public void addView(android.view.View child) {
        // No-op
    }

    public void addView(android.view.View child, LayoutParams params) {
        // No-op
    }
}
