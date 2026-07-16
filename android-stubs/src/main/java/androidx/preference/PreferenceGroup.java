package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public class PreferenceGroup extends Preference {
    public PreferenceGroup(Context c, AttributeSet a, int d, int d2) { super(c,a,d,d2); }
    public PreferenceGroup(Context c, AttributeSet a, int d) { super(c,a,d); }
    public PreferenceGroup(Context c, AttributeSet a) { super(c,a); }
    public PreferenceGroup(Context c) { super(c); }
    
    public void addPreference(Preference preference) {
        // Desktop extension to trigger registration when added to a screen
        if (preference instanceof SwitchPreferenceCompat || preference instanceof CheckBoxPreference) {
            preference.registerWithDesktop(getDesktopPrefName(), "Boolean");
        } else if (preference instanceof EditTextPreference || preference instanceof ListPreference || preference instanceof DropDownPreference) {
            preference.registerWithDesktop(getDesktopPrefName(), "String");
        } else if (preference instanceof MultiSelectListPreference) {
            preference.registerWithDesktop(getDesktopPrefName(), "StringSet");
        } else if (preference instanceof SeekBarPreference) {
            preference.registerWithDesktop(getDesktopPrefName(), "Int");
        }
    }
    
    // Stub
    protected String getDesktopPrefName() { return "global_"; }
}
