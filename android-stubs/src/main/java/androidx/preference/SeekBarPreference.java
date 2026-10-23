package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public class SeekBarPreference extends Preference {
    private int value = 0;
    private int min = 0;
    private int max = 100;
    private boolean updatesContinuously = false;
    private int seekBarIncrement = 1;

    public SeekBarPreference(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public SeekBarPreference(Context c, AttributeSet a, int d) { super(c, a, d); }
    public SeekBarPreference(Context c, AttributeSet a) { super(c, a); }
    public SeekBarPreference(Context c) { super(c); }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public boolean isUpdatesContinuously() {
        return updatesContinuously;
    }

    public void setUpdatesContinuously(boolean updatesContinuously) {
        this.updatesContinuously = updatesContinuously;
    }

    public int getSeekBarIncrement() {
        return seekBarIncrement;
    }

    public void setSeekBarIncrement(int seekBarIncrement) {
        this.seekBarIncrement = seekBarIncrement;
    }
}
