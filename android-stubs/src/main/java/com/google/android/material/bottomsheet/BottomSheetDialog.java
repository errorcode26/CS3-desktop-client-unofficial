package com.google.android.material.bottomsheet;

import android.app.Dialog;
import android.content.Context;
import android.view.View;

@android.annotation.Stub
public class BottomSheetDialog extends Dialog {

    private BottomSheetBehavior<?> behavior = new BottomSheetBehavior<>();

    public BottomSheetDialog(Context context) {
        super(context);
    }

    public BottomSheetDialog(Context context, int theme) {
        super(context);
    }

    public BottomSheetBehavior<?> getBehavior() {
        return behavior;
    }

    public void setContentView(View view) {
    }

    public void setContentView(int layoutResID) {
    }

    @Override
    public void show() {
        try {
            Class<?> interceptor = Class.forName("com.lagradost.cloudstream3.desktop.network.DesktopCfDialogInterceptor");
            java.lang.reflect.Method method = interceptor.getMethod("onShowCalled", Object.class, String.class);
            method.invoke(null, this, null);
        } catch (Exception e) {
            // silently swallow
        }
    }
}
