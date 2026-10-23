package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public class DropDownPreference extends ListPreference {
    public DropDownPreference(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public DropDownPreference(Context c, AttributeSet a, int d) { super(c, a, d); }
    public DropDownPreference(Context c, AttributeSet a) { super(c, a); }
    public DropDownPreference(Context c) { super(c); }
}
