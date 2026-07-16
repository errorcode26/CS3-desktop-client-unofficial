package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class MultiSelectListPreference extends Preference {
    public MultiSelectListPreference(Context c, AttributeSet a) { super(c,a); }
    public MultiSelectListPreference(Context c) { super(c); }
    public void setEntries(CharSequence[] entries) {}
    public void setEntryValues(CharSequence[] entryValues) {}
}
