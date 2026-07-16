package androidx.preference;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.content.Context;
@android.annotation.Implemented
public abstract class PreferenceFragmentCompat extends Fragment {
    private PreferenceManager preferenceManager;
    private PreferenceScreen preferenceScreen;
    
    public abstract void onCreatePreferences(Bundle savedInstanceState, String rootKey);
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getContext();
        if (ctx == null) ctx = android.content.DesktopContextProvider.INSTANCE.getContext();
        preferenceManager = new PreferenceManager(ctx);
        // By invoking this, the plugin will populate the preferences
        try {
            onCreatePreferences(savedInstanceState, null);
        } catch(Exception e) {}
    }
    
    public PreferenceManager getPreferenceManager() { return preferenceManager; }
    public PreferenceScreen getPreferenceScreen() { return preferenceScreen; }
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        this.preferenceScreen = preferenceScreen;
        // The plugin usually sets its prefName as something from its package, 
        // but we can try to guess it based on its class name.
        String prefName = this.getClass().getSimpleName().replace("SettingsFragment", "").replace("Settings", "") + "_";
        preferenceScreen.setDesktopPrefName(prefName);
    }
}
