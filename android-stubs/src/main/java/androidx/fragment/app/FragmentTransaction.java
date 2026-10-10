package androidx.fragment.app;

@android.annotation.Stub
public abstract class FragmentTransaction {
    private final java.util.List<Fragment> fragments = new java.util.ArrayList<>();
    private String lastTag = null;

    public FragmentTransaction add(int containerViewId, androidx.fragment.app.Fragment fragment, String tag) {
        if (fragment != null) {
            fragments.add(fragment);
            this.lastTag = tag;
        }
        return this;
    }

    public FragmentTransaction add(int containerViewId, androidx.fragment.app.Fragment fragment) {
        return add(containerViewId, fragment, null);
    }

    public FragmentTransaction add(androidx.fragment.app.Fragment fragment, String tag) {
        return add(0, fragment, tag);
    }

    public FragmentTransaction replace(int containerViewId, androidx.fragment.app.Fragment fragment, String tag) {
        if (fragment != null) {
            fragments.add(fragment);
            this.lastTag = tag;
        }
        return this;
    }

    public FragmentTransaction replace(int containerViewId, androidx.fragment.app.Fragment fragment) {
        return replace(containerViewId, fragment, null);
    }

    public FragmentTransaction remove(androidx.fragment.app.Fragment fragment) {
        if (fragment != null) {
            fragments.remove(fragment);
        }
        return this;
    }

    public FragmentTransaction addToBackStack(String name) {
        return this;
    }

    public int commit() {
        execute();
        return 0;
    }

    public int commitAllowingStateLoss() {
        execute();
        return 0;
    }

    public void commitNow() {
        execute();
    }

    public void commitNowAllowingStateLoss() {
        execute();
    }

    private void execute() {
        for (Fragment f : fragments) {
            f.runLifecycle(true, lastTag != null ? lastTag : "Settings");
        }
        fragments.clear();
    }
}
