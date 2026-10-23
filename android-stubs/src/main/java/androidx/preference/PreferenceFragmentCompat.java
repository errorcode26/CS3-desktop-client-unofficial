package androidx.preference;

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

@android.annotation.Implemented
public abstract class PreferenceFragmentCompat extends Fragment {
    private PreferenceManager preferenceManager;
    private PreferenceScreen preferenceScreen;

    public abstract void onCreatePreferences(Bundle savedInstanceState, String rootKey);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getContext();
        if (ctx == null) {
            ctx = android.content.DesktopContextProvider.INSTANCE.getContext();
        }
        preferenceManager = new PreferenceManager(ctx);
        try {
            onCreatePreferences(savedInstanceState, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PreferenceManager getPreferenceManager() {
        return preferenceManager;
    }

    public PreferenceScreen getPreferenceScreen() {
        return preferenceScreen;
    }

    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        this.preferenceScreen = preferenceScreen;
        if (preferenceScreen == null) return;

        String prefName = null;
        try {
            Class<?> extLoader = Class.forName("com.lagradost.runtime.loader.ExtensionLoader");
            java.lang.reflect.Method method = extLoader.getMethod("getCallingPluginName");
            prefName = (String) method.invoke(null);
        } catch (Throwable ignored) {}

        if (prefName == null || prefName.isEmpty()) {
            String simple = this.getClass().getSimpleName();
            prefName = simple.replace("SettingsFragment", "")
                             .replace("Settings", "")
                             .replace("PrefsFragment", "")
                             .replace("Fragment", "");
            if (prefName.isEmpty() || "Prefs".equalsIgnoreCase(prefName)) {
                String pkg = this.getClass().getPackage() != null ? this.getClass().getPackage().getName() : "";
                prefName = pkg.substring(pkg.lastIndexOf('.') + 1);
            }
        }
        if (!prefName.endsWith("_")) {
            prefName = prefName + "_";
        }
        preferenceScreen.setDesktopPrefName(prefName);
    }

    @SuppressWarnings("unchecked")
    public <T extends Preference> T findPreference(CharSequence key) {
        return preferenceScreen != null ? preferenceScreen.findPreference(key) : null;
    }

    public androidx.recyclerview.widget.RecyclerView getListView() {
        return null;
    }

    public void addPreferencesFromResource(int preferencesResId) {
        setPreferencesFromResource(preferencesResId, null);
    }

    public void setPreferencesFromResource(int preferencesResId, String key) {
        try {
            Class<?> extLoader = Class.forName("com.lagradost.runtime.loader.ExtensionLoader");
            java.lang.reflect.Method method = extLoader.getMethod("parsePluginPreferences", Object.class, int.class);
            method.invoke(null, this, preferencesResId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
