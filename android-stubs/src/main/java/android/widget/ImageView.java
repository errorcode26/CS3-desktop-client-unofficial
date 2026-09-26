package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Stub
public class ImageView extends View {
    public Drawable imageDrawable;
    public ScaleType scaleType = ScaleType.FIT_CENTER;

    public enum ScaleType {
        MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE
    }

    public ImageView() { super(); }
    public ImageView(Context context) { super(context); }
    public ImageView(Context context, Object attrs) { super(context, attrs); }

    public void setImageDrawable(Drawable drawable) {
        this.imageDrawable = drawable;
        ShadowUi.INSTANCE.bump();
    }

    public Drawable getDrawable() {
        return imageDrawable;
    }

    public void setImageResource(int resId) {
        ShadowUi.INSTANCE.bump();
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }
}
