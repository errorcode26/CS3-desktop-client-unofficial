package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public class SwitchPreference extends TwoStatePreference {
    private CharSequence switchTextOn;
    private CharSequence switchTextOff;

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchPreference(Context context) {
        super(context);
    }

    public CharSequence getSwitchTextOn() {
        return switchTextOn;
    }

    public void setSwitchTextOn(CharSequence switchTextOn) {
        this.switchTextOn = switchTextOn;
    }

    public void setSwitchTextOn(int resId) {
    }

    public CharSequence getSwitchTextOff() {
        return switchTextOff;
    }

    public void setSwitchTextOff(CharSequence switchTextOff) {
        this.switchTextOff = switchTextOff;
    }

    public void setSwitchTextOff(int resId) {
    }
}
