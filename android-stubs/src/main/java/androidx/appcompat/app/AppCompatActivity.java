package androidx.appcompat.app;

import android.app.Activity;

import androidx.fragment.app.FragmentManager;

@android.annotation.Stub
public class AppCompatActivity extends Activity {
    private static final FragmentManager FRAGMENT_MANAGER = new FragmentManager() {};

    public FragmentManager getSupportFragmentManager() {
        return FRAGMENT_MANAGER;
    }
}
