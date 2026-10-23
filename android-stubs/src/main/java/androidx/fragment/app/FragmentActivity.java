package androidx.fragment.app;

import android.app.Activity;

@android.annotation.Stub
public class FragmentActivity extends Activity {
    private static final FragmentManager FRAGMENT_MANAGER = new FragmentManager() {};

    public FragmentActivity() {
        super();
    }

    public FragmentActivity(java.io.File baseDir) {
        super(baseDir);
    }

    public FragmentManager getSupportFragmentManager() {
        return FRAGMENT_MANAGER;
    }
}
