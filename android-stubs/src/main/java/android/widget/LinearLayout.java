package android.widget;

import android.content.Context;
import android.view.ViewGroup;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Stub
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public int orientation = VERTICAL;
    public int gravity = 0;

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight = 0f;
        public int gravity = -1;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(Context c, Object attrs) {
            super(c, attrs);
        }
    }

    public LinearLayout() { super(); }
    public LinearLayout(Context context) { super(context); }
    public LinearLayout(Context context, Object attrs) { super(context, attrs); }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
        ShadowUi.INSTANCE.bump();
    }

    public int getOrientation() {
        return orientation;
    }

    public void setGravity(int gravity) {
        this.gravity = gravity;
    }

    public int getGravity() {
        return gravity;
    }
}
