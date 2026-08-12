package androidx.fragment.app;

@android.annotation.Stub
public abstract class FragmentTransaction {
    public abstract FragmentTransaction add(int containerViewId, androidx.fragment.app.Fragment fragment, String tag);
    public abstract FragmentTransaction remove(androidx.fragment.app.Fragment fragment);
    public abstract int commit();
    public abstract int commitAllowingStateLoss();
}
