package android.app;

import android.content.Context;
import android.content.DialogInterface;

@android.annotation.Stub
public class Dialog implements DialogInterface {

    public Dialog(Context context) {
    }

    public void show() {
    }

    public void dismiss() {
    }

    public void hide() {
    }

    public void cancel() {
    }

    public boolean isShowing() {
        return false;
    }

    public void setCancelable(boolean flag) {
    }

    public void setCanceledOnTouchOutside(boolean cancel) {
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
    }

    public void setOnShowListener(DialogInterface.OnShowListener listener) {
    }

    public void setTitle(CharSequence title) {
    }

    public void setTitle(int titleId) {
    }

    public android.view.Window getWindow() {
        return new android.view.Window();
    }
}
