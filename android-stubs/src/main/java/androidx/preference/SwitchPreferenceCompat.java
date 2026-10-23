package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class SwitchPreferenceCompat extends TwoStatePreference {
    private CharSequence switchTextOn;
    private CharSequence switchTextOff;

    public SwitchPreferenceCompat(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public SwitchPreferenceCompat(Context c, AttributeSet a, int d) { super(c, a, d); }
    public SwitchPreferenceCompat(Context c, AttributeSet a) { super(c, a); }
    public SwitchPreferenceCompat(Context c) { super(c); }

    public CharSequence getSwitchTextOn() { return switchTextOn; }
    public void setSwitchTextOn(CharSequence switchTextOn) { this.switchTextOn = switchTextOn; }
    public void setSwitchTextOn(int resId) {}

    public CharSequence getSwitchTextOff() { return switchTextOff; }
    public void setSwitchTextOff(CharSequence switchTextOff) { this.switchTextOff = switchTextOff; }
    public void setSwitchTextOff(int resId) {}
}
