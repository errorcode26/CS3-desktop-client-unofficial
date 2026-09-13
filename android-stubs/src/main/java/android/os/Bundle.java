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

    public void putDouble(String key, double value) {
        map.put(key, value);
    }

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public double getDouble(String key, double defaultValue) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).doubleValue() : defaultValue;
    }

    public void putLong(String key, long value) {
        map.put(key, value);
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).longValue() : defaultValue;
    }

    public void putFloat(String key, float value) {
        map.put(key, value);
    }

    public float getFloat(String key) {
        return getFloat(key, 0.0f);
    }

    public float getFloat(String key, float defaultValue) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).floatValue() : defaultValue;
    }

    public void putCharSequence(String key, CharSequence value) {
        map.put(key, value != null ? value.toString() : null);
    }

    public CharSequence getCharSequence(String key) {
        Object val = map.get(key);
        return val instanceof CharSequence ? (CharSequence) val : (val != null ? val.toString() : null);
    }

    public void putSerializable(String key, java.io.Serializable value) {
        map.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T extends java.io.Serializable> T getSerializable(String key) {
        Object val = map.get(key);
        return (val instanceof java.io.Serializable) ? (T) val : null;
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public void remove(String key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }
}
