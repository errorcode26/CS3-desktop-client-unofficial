package android.view;

@android.annotation.Stub
public interface WindowManager extends ViewManager {
    Display getDefaultDisplay();

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int x;
        public int y;
        public float alpha = 1.0f;
        public int flags;
        public int gravity;
        public int type;
        public int format;

        public LayoutParams() {
            super(WRAP_CONTENT, WRAP_CONTENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int type, int flags, int format) {
            super(width, height);
            this.type = type;
            this.flags = flags;
            this.format = format;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source != null ? source.width : WRAP_CONTENT, source != null ? source.height : WRAP_CONTENT);
        }
    }
}
