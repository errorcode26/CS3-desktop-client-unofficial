package android.app;

import android.content.Context;
import android.content.DialogInterface;

@android.annotation.Stub
public class Dialog implements DialogInterface {

    private boolean isShowing = false;
    private DialogInterface.OnDismissListener dismissListener;
    private DialogInterface.OnCancelListener cancelListener;
    private DialogInterface.OnShowListener showListener;

    public Dialog(Context context) {
    }

    public void show() {
        isShowing = true;
        if (showListener != null) {
            showListener.onShow(this);
        }
        try {
            Class<?> interceptor = Class.forName("com.lagradost.cloudstream3.desktop.network.DesktopCfDialogInterceptor");
            java.lang.reflect.Method method = interceptor.getMethod("onShowCalled", Object.class, String.class);
            method.invoke(null, this, null);
        } catch (Exception e) {
            // silently swallow — no desktop interceptor on classpath
        }
    }

    public void dismiss() {
        isShowing = false;
        if (dismissListener != null) {
            dismissListener.onDismiss(this);
        }
    }

    public void hide() {
        isShowing = false;
    }

    public void cancel() {
        isShowing = false;
        if (cancelListener != null) {
            cancelListener.onCancel(this);
        }
        dismiss();
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void setCancelable(boolean flag) {
    }

    public void setCanceledOnTouchOutside(boolean cancel) {
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        this.cancelListener = listener;
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void setOnShowListener(DialogInterface.OnShowListener listener) {
        this.showListener = listener;
    }

    public void setTitle(CharSequence title) {
    }

    public void setTitle(int titleId) {
    }

    public android.view.Window getWindow() {
        return new android.view.Window();
    }
}
