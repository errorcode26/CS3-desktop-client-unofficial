package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class ListPreference extends DialogPreference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private String value;

    public ListPreference(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public ListPreference(Context c, AttributeSet a, int d) { super(c, a, d); }
    public ListPreference(Context c, AttributeSet a) { super(c, a); }
    public ListPreference(Context c) { super(c); }

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

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public void setValueIndex(int index) {
        if (entryValues != null && index >= 0 && index < entryValues.length) {
            setValue(entryValues[index].toString());
        }
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = 0; i < entryValues.length; i++) {
                if (value.equals(entryValues[i].toString())) return i;
            }
        }
        return -1;
    }

    public CharSequence getEntry() {
        int index = findIndexOfValue(getValue());
        return (index >= 0 && entries != null && index < entries.length) ? entries[index] : null;
    }

    private void syncOptionsWithDesktop() {
        if (entries != null && entryValues != null && entries.length == entryValues.length && getKey() != null) {
            java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < entries.length; i++) {
                map.put(entries[i].toString(), entryValues[i].toString());
            }
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(
                getDesktopPrefName(),
                getKey(),
                "String",
                getDefaultValue(),
                false,
                map,
                null
            );
        }
    }
}
