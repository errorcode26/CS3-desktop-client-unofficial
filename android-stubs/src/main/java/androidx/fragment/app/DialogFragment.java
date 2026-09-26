package androidx.fragment.app;

import android.content.Context;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Stub
public class DialogFragment extends Fragment {

    private android.app.Dialog dialogField;

    public void show(FragmentManager manager, String tag) {
        dialogField = new android.app.Dialog(getContext());

        // Preserve Cloudflare interceptor hook
        try {
            Class<?> interceptor = Class.forName("com.lagradost.cloudstream3.desktop.network.DesktopCfDialogInterceptor");
            java.lang.reflect.Method method = interceptor.getMethod("onShowCalled", Object.class, String.class);
            method.invoke(null, this, tag);
        } catch (Exception ignored) {}

        // Run full fragment lifecycle and register to ShadowUi
        runLifecycle(true, tag);
    }

    public void show(FragmentTransaction transaction, String tag) {
        show((FragmentManager) null, tag);
    }

    public void dismiss() {
        if (dialogField != null) {
            dialogField.dismiss();
        }
        ShadowUi.INSTANCE.pop(this);
    }

    public void dismissAllowingStateLoss() {
        if (dialogField != null) {
            dialogField.dismiss();
        }
        ShadowUi.INSTANCE.pop(this);
    }

    public void setCancelable(boolean cancelable) {}

    public boolean isCancelable() {
        return true;
    }

    public android.app.Dialog getDialog() {
        if (dialogField == null) {
            dialogField = new android.app.Dialog(getContext());
        }
        return dialogField;
    }

    @Override
    public Context getContext() {
        return android.content.DesktopContextProvider.INSTANCE.getContext();
    }

    public android.app.Activity getActivity() {
        return null;
    }
}
