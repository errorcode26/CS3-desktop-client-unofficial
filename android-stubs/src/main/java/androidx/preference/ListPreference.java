package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class ListPreference extends Preference {
    public ListPreference(Context c, AttributeSet a) { super(c,a); }
    public ListPreference(Context c) { super(c); }
    public void setEntries(CharSequence[] entries) {}
    public void setEntryValues(CharSequence[] entryValues) {}
}
