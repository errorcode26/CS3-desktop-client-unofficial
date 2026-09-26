package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowDialog;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Stub
public class Dialog implements DialogInterface {

    public CharSequence title;
    public CharSequence message;
    public View contentView;
    public boolean cancelable = true;

    public CharSequence positiveText;
    public DialogInterface.OnClickListener positiveListener;
    public CharSequence negativeText;
    public DialogInterface.OnClickListener negativeListener;
    public CharSequence neutralText;
    public DialogInterface.OnClickListener neutralListener;

    public CharSequence[] items;
    public DialogInterface.OnClickListener itemsListener;
    public int checkedItem = -1;
    public boolean isSingleChoice = false;
    public boolean isMultiChoice = false;
    public boolean[] checkedItems;
    public DialogInterface.OnMultiChoiceClickListener multiChoiceListener;

    public DialogInterface.OnShowListener showListener;
    public DialogInterface.OnDismissListener dismissListener;
    public DialogInterface.OnCancelListener cancelListener;

    private boolean isShowing = false;
    private final Window windowInstance = new Window();

    public Dialog(Context context) {}

    public Window getWindow() {
        return windowInstance;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        ShadowUi.INSTANCE.bump();
    }

    public void setTitle(int titleId) {
        this.title = null;
        ShadowUi.INSTANCE.bump();
    }

    public void setMessage(CharSequence message) {
        this.message = message;
        ShadowUi.INSTANCE.bump();
    }

    public void setContentView(View view) {
        this.contentView = view;
        ShadowUi.INSTANCE.bump();
    }

    public void setContentView(int layoutResId) {}

    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
    }

    public void setCanceledOnTouchOutside(boolean cancel) {}

    public void setOnShowListener(DialogInterface.OnShowListener listener) {
        this.showListener = listener;
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        this.cancelListener = listener;
    }

    public void show() {
        if (isShowing) return;
        isShowing = true;

        if (showListener != null) {
            showListener.onShow(this);
        }

        // Preserve Cloudflare interceptor hook
        try {
            Class<?> interceptor = Class.forName("com.lagradost.cloudstream3.desktop.network.DesktopCfDialogInterceptor");
            java.lang.reflect.Method method = interceptor.getMethod("onShowCalled", Object.class, String.class);
            method.invoke(null, this, null);
        } catch (Exception ignored) {}

        // Push to ShadowUi dialog stack
        ShadowUi.INSTANCE.push(new ShadowDialog(this, contentView, false, null, ShadowUi.INSTANCE.nextDialogId()));
    }

    public void dismiss() {
        if (!isShowing) return;
        isShowing = false;
        ShadowUi.INSTANCE.pop(this);
        if (dismissListener != null) {
            dismissListener.onDismiss(this);
        }
    }

    public void cancel() {
        if (!isShowing) return;
        isShowing = false;
        ShadowUi.INSTANCE.pop(this);
        if (cancelListener != null) {
            cancelListener.onCancel(this);
        }
        dismiss();
    }

    public void hide() {
        isShowing = false;
        ShadowUi.INSTANCE.pop(this);
    }
}
