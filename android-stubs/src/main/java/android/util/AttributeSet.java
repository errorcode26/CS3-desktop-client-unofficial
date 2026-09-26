package android.util;

@android.annotation.Implemented
public interface AttributeSet {
    int getAttributeCount();
    String getAttributeName(int index);
    String getAttributeValue(int index);
    String getAttributeValue(String namespace, String name);
    int getAttributeResourceValue(String namespace, String name, int defaultValue);
    int getAttributeResourceValue(int index, int defaultValue);
    boolean getAttributeBooleanValue(String namespace, String name, boolean defaultValue);
    boolean getAttributeBooleanValue(int index, boolean defaultValue);
    int getAttributeIntValue(String namespace, String name, int defaultValue);
    int getAttributeIntValue(int index, int defaultValue);
}
