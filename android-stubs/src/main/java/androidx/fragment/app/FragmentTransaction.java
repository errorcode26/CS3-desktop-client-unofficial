package androidx.fragment.app;

@android.annotation.Stub
public abstract class FragmentTransaction {
    public FragmentTransaction add(int containerViewId, androidx.fragment.app.Fragment fragment, String tag) {
        return this;
    }

    public FragmentTransaction add(int containerViewId, androidx.fragment.app.Fragment fragment) {
        return this;
    }

    public FragmentTransaction add(androidx.fragment.app.Fragment fragment, String tag) {
        return this;
    }

    public FragmentTransaction replace(int containerViewId, androidx.fragment.app.Fragment fragment, String tag) {
        return this;
    }

    public FragmentTransaction replace(int containerViewId, androidx.fragment.app.Fragment fragment) {
        return this;
    }

    public FragmentTransaction remove(androidx.fragment.app.Fragment fragment) {
        return this;
    }

    public FragmentTransaction addToBackStack(String name) {
        return this;
    }

    public int commit() {
        return 0;
    }

    public int commitAllowingStateLoss() {
        return 0;
    }

    public void commitNow() {
    }

    public void commitNowAllowingStateLoss() {
    }
}
