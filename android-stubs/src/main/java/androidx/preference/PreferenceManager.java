package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

@android.annotation.Implemented
public class PreferenceManager {
    public static final String KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values";

    private static final java.util.Map<String, Preference> globalPreferences = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerGlobalPreference(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            globalPreferences.put(preference.getKey(), preference);
        }
    }

    public static boolean dispatchPreferenceChange(String key, Object newValue) {
        if (key == null) return true;
        Preference pref = globalPreferences.get(key);
        if (pref != null) {
            return pref.callChangeListener(newValue);
        }
        return true;
    }

    public static Preference getGlobalPreference(String key) {
        return key != null ? globalPreferences.get(key) : null;
    }

    private Context context;
    private PreferenceScreen preferenceScreen;

    public PreferenceManager(Context context) {
        this.context = context;
    }

    public PreferenceScreen createPreferenceScreen(Context context) {
        PreferenceScreen screen = new PreferenceScreen(context, null);
        screen.setPreferenceManager(this);
        return screen;
    }

    public PreferenceScreen getPreferenceScreen() {
        return preferenceScreen;
    }

    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        this.preferenceScreen = preferenceScreen;
    }

    public Preference findPreference(CharSequence key) {
        return preferenceScreen != null ? preferenceScreen.findPreference(key) : null;
    }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context != null ? context.getSharedPreferences(getDefaultSharedPreferencesName(context), getDefaultSharedPreferencesMode()) : null;
    }

    public static String getDefaultSharedPreferencesName(Context context) {
        return (context != null ? context.getPackageName() : "com.lagradost.cloudstream3") + "_preferences";
    }

    public static int getDefaultSharedPreferencesMode() {
        return 0; // Context.MODE_PRIVATE
    }
}
