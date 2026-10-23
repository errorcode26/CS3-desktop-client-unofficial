package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import com.lagradost.common.storage.PluginSettingsSchemaRegistry;

@android.annotation.Implemented
public class Preference {
    protected Context context;
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private Object defaultValue;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean selectable = true;
    private int order = 0;
    private int layoutResource = 0;
    private int widgetLayoutResource = 0;
    private String desktopPrefName = "global_";
    private PreferenceManager preferenceManager;

    private OnPreferenceChangeListener onPreferenceChangeListener;
    private OnPreferenceClickListener onPreferenceClickListener;

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this.context = context;
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Preference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Preference(Context context) {
        this(context, null);
    }

    public Context getContext() {
        return context;
    }

    public void setKey(String key) {
        this.key = key;
        PreferenceManager.registerGlobalPreference(this);
    }

    public String getKey() {
        return key;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
    }

    public void setTitle(int titleResId) {
        if (context != null && context.getResources() != null) {
            setTitle(context.getResources().getString(titleResId));
        }
    }

    public CharSequence getTitle() {
        return title;
    }

    public void setSummary(CharSequence summary) {
        this.summary = summary;
    }

    public void setSummary(int summaryResId) {
        if (context != null && context.getResources() != null) {
            setSummary(context.getResources().getString(summaryResId));
        }
    }

    public CharSequence getSummary() {
        return summary;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getLayoutResource() {
        return layoutResource;
    }

    public void setLayoutResource(int layoutResource) {
        this.layoutResource = layoutResource;
    }

    public int getWidgetLayoutResource() {
        return widgetLayoutResource;
    }

    public void setWidgetLayoutResource(int widgetLayoutResource) {
        this.widgetLayoutResource = widgetLayoutResource;
    }

    public PreferenceManager getPreferenceManager() {
        return preferenceManager;
    }

    public void setPreferenceManager(PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
    }

    public OnPreferenceChangeListener getOnPreferenceChangeListener() {
        return onPreferenceChangeListener;
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
        this.onPreferenceChangeListener = listener;
        PreferenceManager.registerGlobalPreference(this);
    }

    public OnPreferenceClickListener getOnPreferenceClickListener() {
        return onPreferenceClickListener;
    }

    public void setOnPreferenceClickListener(OnPreferenceClickListener listener) {
        this.onPreferenceClickListener = listener;
        PreferenceManager.registerGlobalPreference(this);
    }

    public boolean callChangeListener(Object newValue) {
        return onPreferenceChangeListener == null || onPreferenceChangeListener.onPreferenceChange(this, newValue);
    }

    public boolean callClickListener() {
        return onPreferenceClickListener == null || onPreferenceClickListener.onPreferenceClick(this);
    }

    public android.content.SharedPreferences getSharedPreferences() {
        return context != null ? PreferenceManager.getDefaultSharedPreferences(context) : null;
    }

    public void setDesktopPrefName(String name) {
        if (name != null && !name.isEmpty()) {
            this.desktopPrefName = name;
        }
    }

    public String getDesktopPrefName() {
        return desktopPrefName;
    }

    protected void registerWithDesktop(String prefName, String type) {
        setDesktopPrefName(prefName);
        if (key != null) {
            PluginSettingsSchemaRegistry.INSTANCE.register(getDesktopPrefName(), key, type, defaultValue, false, null, null);
        }
    }
}
