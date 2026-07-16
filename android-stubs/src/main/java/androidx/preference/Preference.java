package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import com.lagradost.common.storage.PluginSettingsSchemaRegistry;

@android.annotation.Implemented
public class Preference {
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private Object defaultValue;

    public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {}
    public Preference(Context context, AttributeSet attrs, int defStyleAttr) {}
    public Preference(Context context, AttributeSet attrs) {}
    public Preference(Context context) {}

    public void setKey(String key) { this.key = key; }
    public String getKey() { return key; }

    public void setTitle(CharSequence title) { this.title = title; }
    public void setTitle(int titleResId) {}
    public CharSequence getTitle() { return title; }

    public void setSummary(CharSequence summary) { this.summary = summary; }
    public void setSummary(int summaryResId) {}
    public CharSequence getSummary() { return summary; }

    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
    
    // Desktop integration: when a preference is added, we can register its type
    protected void registerWithDesktop(String prefName, String type) {
        if (key != null) {
            PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, type, defaultValue, false);
        }
    }
}
