package androidx.fragment.app;

@android.annotation.Stub
public abstract class FragmentManager {
    public FragmentTransaction beginTransaction() {
        return new FragmentTransaction() {};
    }

    public Fragment findFragmentByTag(String tag) {
        return null;
    }

    public Fragment findFragmentById(int id) {
        return null;
    }

    public boolean executePendingTransactions() {
        return true;
    }

    public void popBackStack() {
    }

    public boolean isDestroyed() {
        return false;
    }

    public boolean isStateSaved() {
        return false;
    }
}
