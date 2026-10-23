package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@android.annotation.Implemented
public class PreferenceGroup extends Preference {
    private final List<Preference> preferences = new CopyOnWriteArrayList<>();

    public PreferenceGroup(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public PreferenceGroup(Context c, AttributeSet a, int d) { super(c, a, d); }
    public PreferenceGroup(Context c, AttributeSet a) { super(c, a); }
    public PreferenceGroup(Context c) { super(c); }

    public int getPreferenceCount() {
        return preferences.size();
    }

    public Preference getPreference(int index) {
        return preferences.get(index);
    }

    public boolean addPreference(Preference preference) {
        if (preference == null) return false;
        if (!preferences.contains(preference)) {
            preference.setDesktopPrefName(getDesktopPrefName());
            if (preference.getPreferenceManager() == null) {
                preference.setPreferenceManager(getPreferenceManager());
            }
            preferences.add(preference);

            // Register with desktop schema registry
            if (preference instanceof SwitchPreferenceCompat || preference instanceof CheckBoxPreference || preference instanceof SwitchPreference) {
                preference.registerWithDesktop(getDesktopPrefName(), "Boolean");
            } else if (preference instanceof EditTextPreference || preference instanceof ListPreference || preference instanceof DropDownPreference) {
                preference.registerWithDesktop(getDesktopPrefName(), "String");
            } else if (preference instanceof MultiSelectListPreference) {
                preference.registerWithDesktop(getDesktopPrefName(), "StringSet");
            } else if (preference instanceof SeekBarPreference) {
                preference.registerWithDesktop(getDesktopPrefName(), "Int");
            }
        }
        return true;
    }

    public boolean removePreference(Preference preference) {
        return preferences.remove(preference);
    }

    public void removeAll() {
        preferences.clear();
    }

    @SuppressWarnings("unchecked")
    public <T extends Preference> T findPreference(CharSequence key) {
        if (key == null) return null;
        String keyStr = key.toString();
        if (keyStr.equals(getKey())) {
            return (T) this;
        }
        for (Preference pref : preferences) {
            if (keyStr.equals(pref.getKey())) {
                return (T) pref;
            }
            if (pref instanceof PreferenceGroup) {
                T found = ((PreferenceGroup) pref).findPreference(key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public void setDesktopPrefName(String name) {
        super.setDesktopPrefName(name);
        for (Preference pref : preferences) {
            pref.setDesktopPrefName(name);
        }
    }
}
