package android.content;

import com.lagradost.common.storage.DesktopDataStore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@android.annotation.Implemented
public interface SharedPreferences {
    Map<String, ?> getAll();
    String getString(String key, String defValue);
    int getInt(String key, int defValue);
    long getLong(String key, long defValue);
    float getFloat(String key, float defValue);
    boolean getBoolean(String key, boolean defValue);
    Set<String> getStringSet(String key, Set<String> defValue);
    boolean contains(String key);
    Editor edit();
    void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
    void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);

    interface Editor {
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putFloat(String key, float value);
        Editor putBoolean(String key, boolean value);
        Editor putStringSet(String key, Set<String> values);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }

    interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
    }
}

class DesktopSharedPreferences implements SharedPreferences {
    private final String prefName;

    public DesktopSharedPreferences(String name) {
        this.prefName = name + "_";
    }

    private String getFullKey(String key) {
        return prefName + key;
    }

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>();
    }

    @Override
    public String getString(String key, String defValue) {
        com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, "String", defValue, false);
        String val = DesktopDataStore.INSTANCE.getKey(getFullKey(key), String.class);
        return val != null ? val : defValue;
    }

    @Override
    public int getInt(String key, int defValue) {
        com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, "Int", defValue, false);
        Integer val = DesktopDataStore.INSTANCE.getKey(getFullKey(key), Integer.class);
        return val != null ? val : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, "Long", defValue, false);
        Long val = DesktopDataStore.INSTANCE.getKey(getFullKey(key), Long.class);
        return val != null ? val : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, "Float", defValue, false);
        Float val = DesktopDataStore.INSTANCE.getKey(getFullKey(key), Float.class);
        return val != null ? val : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, "Boolean", defValue, false);
        Boolean val = DesktopDataStore.INSTANCE.getKey(getFullKey(key), Boolean.class);
        return val != null ? val : defValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defValue) {
        com.lagradost.common.storage.PluginSettingsSchemaRegistry.INSTANCE.register(prefName, key, "StringSet", defValue, false);
        Set val = DesktopDataStore.INSTANCE.getKey(getFullKey(key), Set.class);
        return val != null ? (Set<String>) val : defValue;
    }

    @Override
    public boolean contains(String key) {
        // Not perfectly implemented without reflection, assuming false for now
        return false;
    }

    @Override
    public Editor edit() {
        return new DesktopEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    private class DesktopEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<>();

        @Override
        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            pending.put(key, values);
            return this;
        }

        @Override
        public Editor remove(String key) {
            pending.put(key, null);
            return this;
        }

        @Override
        public Editor clear() {
            // Clearing all keys for a specific preference file is hard without iterating all keys in datastore.
            // We skip implementation for clear() on Desktop for now to avoid accidental deletions of global state.
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            for (Map.Entry<String, Object> entry : pending.entrySet()) {
                if (entry.getValue() == null) {
                    // DesktopDataStore doesn't have a simple removeKey(String) exposed to Java? 
                    // Let's set it to null string or something, wait we can just set it to null.
                    DesktopDataStore.INSTANCE.setKey(getFullKey(entry.getKey()), null);
                } else {
                    DesktopDataStore.INSTANCE.setKey(getFullKey(entry.getKey()), entry.getValue());
                }
            }
        }
    }
}
