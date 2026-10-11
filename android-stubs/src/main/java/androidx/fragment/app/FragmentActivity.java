package androidx.fragment.app;

import android.app.Activity;

@android.annotation.Stub
public class FragmentActivity extends Activity {
    private static final FragmentManager FRAGMENT_MANAGER = new FragmentManager() {};

    public FragmentManager getSupportFragmentManager() {
        return FRAGMENT_MANAGER;
    }
}
