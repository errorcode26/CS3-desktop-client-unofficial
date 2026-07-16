package androidx.fragment.app;
import android.content.Context;
@android.annotation.Implemented
public class Fragment {
    public Context getContext() { return android.content.DesktopContextProvider.INSTANCE.getContext(); }
    public void onCreate(android.os.Bundle savedInstanceState) {}
}
