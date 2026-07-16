package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class PreferenceScreen extends PreferenceGroup {
    private String prefName = "global_";
    public PreferenceScreen(Context c, AttributeSet a) { super(c,a); }
    public void setDesktopPrefName(String name) { this.prefName = name; }
    @Override protected String getDesktopPrefName() { return prefName; }
}
