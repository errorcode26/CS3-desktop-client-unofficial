package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public abstract class TwoStatePreference extends Preference {
    protected boolean checked = false;
    private CharSequence summaryOn;
    private CharSequence summaryOff;

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TwoStatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoStatePreference(Context context) {
        super(context);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public CharSequence getSummaryOn() {
        return summaryOn;
    }

    public void setSummaryOn(CharSequence summaryOn) {
        this.summaryOn = summaryOn;
    }

    public void setSummaryOn(int summaryResId) {
    }

    public CharSequence getSummaryOff() {
        return summaryOff;
    }

    public void setSummaryOff(CharSequence summaryOff) {
        this.summaryOff = summaryOff;
    }

    public void setSummaryOff(int summaryResId) {
    }
}
