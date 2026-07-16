package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class SeekBarPreference extends Preference {
    public SeekBarPreference(Context c, AttributeSet a) { super(c,a); }
    public SeekBarPreference(Context c) { super(c); }
    public void setMin(int min) {}
    public void setMax(int max) {}
}
