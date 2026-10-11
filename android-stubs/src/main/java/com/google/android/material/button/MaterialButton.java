package com.google.android.material.button;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

@android.annotation.Stub
public class MaterialButton extends Button {
    public MaterialButton(Context context) {
        super(context);
    }

    public MaterialButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaterialButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setIconResource(int iconResourceId) {}
    public void setCornerRadius(int cornerRadius) {}
    public void setStrokeWidth(int strokeWidth) {}
    public void setStrokeColorResource(int strokeColorResourceId) {}
}
