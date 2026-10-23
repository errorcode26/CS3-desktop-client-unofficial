package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@android.annotation.Implemented
public class MultiSelectListPreference extends DialogPreference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private Set<String> values = Collections.emptySet();

    public MultiSelectListPreference(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public MultiSelectListPreference(Context c, AttributeSet a, int d) { super(c, a, d); }
    public MultiSelectListPreference(Context c, AttributeSet a) { super(c, a); }
    public MultiSelectListPreference(Context c) { super(c); }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
        syncOptionsWithDesktop();
    }
    public void setEntries(int entriesResId) {
        if (getContext() != null && getContext().getResources() != null) {
            setEntries(getContext().getResources().getTextArray(entriesResId));
        }
    }
    public CharSequence[] getEntries() { return entries; }

    public void setEntryValues(CharSequence[] entryValues) {
        this.entryValues = entryValues;
        syncOptionsWithDesktop();
    }
    public void setEntryValues(int entryValuesResId) {
        if (getContext() != null && getContext().getResources() != null) {
            setEntryValues(getContext().getResources().getTextArray(entryValuesResId));
        }
    }
    public CharSequence[] getEntryValues() { return entryValues; }

    public void setValues(Set<String> values) {
        this.values = values != null ? values : Collections.emptySet();
    }
    public Set<String> getValues() {
        return values;
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = 0; i < entryValues.length; i++) {
                if (value.equals(entryValues[i].toString())) return i;
            }
        }
        return -1;
    }

    private void syncOptionsWithDesktop() {
        if (entries != null && entryValues != null && entries.length == entryValues.length && getKey() != null) {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < entries.length; i++) {
                map.put(entries[i].toString(), entryValues[i].toString());
            }
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(
                getDesktopPrefName(),
                getKey(),
                "StringSet",
                getDefaultValue(),
                false,
                map,
                null
            );
        }
    }
}
