package android.os;

import java.util.HashMap;
import java.util.Map;

@android.annotation.Implemented
public class Bundle {
    private final Map<String, Object> map = new HashMap<>();

    public Bundle() {}

    public void putString(String key, String value) {
        map.put(key, value);
    }

    public void putInt(String key, int value) {
        map.put(key, value);
    }

    public void putBoolean(String key, boolean value) {
        map.put(key, value);
    }

    public String getString(String key) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : null;
    }

    public String getString(String key, String defaultValue) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : defaultValue;
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        Object val = map.get(key);
        return val instanceof Integer ? (Integer) val : defaultValue;
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : defaultValue;
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public void remove(String key) {
        map.remove(key);
    }
}
