package androidx.fragment.app;

import android.content.Context;

@android.annotation.Stub
public class DialogFragment {

    /**
     * Intercepts Android-style CF dialog show calls on desktop.
     * Uses reflection to avoid a circular compile-time dependency on desktop-app.
     */
    public void show(FragmentManager manager, String tag) {
        try {
            Class<?> interceptor = Class.forName("com.lagradost.cloudstream3.desktop.network.DesktopCfDialogInterceptor");
            java.lang.reflect.Method method = interceptor.getMethod("onShowCalled", Object.class, String.class);
            method.invoke(null, this, tag);
        } catch (Exception e) {
            // silently swallow — no desktop interceptor on classpath
        }
    }

    public void show(FragmentTransaction transaction, String tag) {
        // no-op — desktop has no fragment back stack
    }

    public void dismiss() {
        // no-op
    }

    public void dismissAllowingStateLoss() {
        // no-op
    }

    public Context getContext() {
        return null;
    }

    public android.app.Activity getActivity() {
        return null;
    }
}
