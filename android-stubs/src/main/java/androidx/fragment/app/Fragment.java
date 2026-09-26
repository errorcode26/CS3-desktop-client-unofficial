package androidx.fragment.app;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowDialog;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Implemented
public class Fragment {
    private View viewField = null;

    public Context getContext() {
        return android.content.DesktopContextProvider.INSTANCE.getContext();
    }

    public View getView() {
        return viewField;
    }

    public View requireView() {
        return (viewField != null) ? viewField : new View();
    }

    public void onCreate(Bundle savedInstanceState) {}

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return null;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {}
    public void onStart() {}
    public void onResume() {}
    public void onPause() {}
    public void onStop() {}
    public void onDestroyView() {}
    public void onDestroy() {}

    public void runLifecycle(boolean record, String tag) {
        try {
            onCreate(null);
            View view = onCreateView(LayoutInflater.from(null), null, null);
            this.viewField = view;
            onViewCreated(view, null);
            if (record) {
                ShadowUi.INSTANCE.push(new ShadowDialog(this, view, true, tag, ShadowUi.INSTANCE.nextDialogId()));
            }
            onStart();
            onResume();
        } catch (Throwable t) {
            System.err.println("Fragment lifecycle error: " + t.getMessage());
        }
    }

    public FragmentManager getChildFragmentManager() {
        return new FragmentManager() {};
    }

    public FragmentManager getParentFragmentManager() {
        return new FragmentManager() {};
    }
}
