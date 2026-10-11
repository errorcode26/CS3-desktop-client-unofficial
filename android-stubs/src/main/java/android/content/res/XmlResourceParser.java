package android.content.res;

import android.util.AttributeSet;
import org.xmlpull.v1.XmlPullParser;

@android.annotation.Stub
public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
    void close();
    String getAttributeNamespace(int index);
}
