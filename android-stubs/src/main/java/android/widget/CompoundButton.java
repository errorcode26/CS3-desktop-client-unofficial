package android.widget;

import android.content.Context;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

public class CompoundButton extends TextView implements Checkable {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }

    private boolean isChecked = false;
    public OnCheckedChangeListener checkedChangeListener;

    public CompoundButton() { super(); }
    public CompoundButton(Context context) { super(context); }
    public CompoundButton(Context context, Object attrs) { super(context, attrs); }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        if (this.isChecked != checked) {
            this.isChecked = checked;
            if (checkedChangeListener != null) {
                checkedChangeListener.onCheckedChanged(this, checked);
            }
            ShadowUi.INSTANCE.bump();
        }
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.checkedChangeListener = listener;
    }

    public OnCheckedChangeListener getOnCheckedChangeListener() {
        return checkedChangeListener;
    }
}
