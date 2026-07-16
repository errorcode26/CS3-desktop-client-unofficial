package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

@android.annotation.Stub
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public float weight;
        public int gravity = -1;
        public int marginEnd = 0;
        public int marginStart = 0;
        public int topMargin = 0;
        public int bottomMargin = 0;
        public int leftMargin = 0;
        public int rightMargin = 0;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source.width, source.height);
        }

        public void setMarginEnd(int end) {
            this.marginEnd = end;
        }

        public void setMarginStart(int start) {
            this.marginStart = start;
        }

        public void setMargins(int left, int top, int right, int bottom) {
            this.leftMargin = left;
            this.topMargin = top;
            this.rightMargin = right;
            this.bottomMargin = bottom;
        }
    }

    public LinearLayout(Context context) {}
    
    public void setOrientation(int orientation) {}
    public int getOrientation() { return 0; }
    public void setGravity(int gravity) {}
}
