package android.widget;

import android.content.Context;
import android.text.Editable;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Stub
public class EditText extends TextView {
    public int inputType = 0;

    public EditText() { super(); }
    public EditText(Context context) { super(context); }
    public EditText(Context context, Object attrs) { super(context, attrs); }

    public void setRawInputType(int type) { this.inputType = type; }
    public void setInputType(int type) { this.inputType = type; }
    public int getInputType() { return inputType; }

    @Override
    public Editable getText() {
        if (text instanceof Editable) {
            return (Editable) text;
        }
        Editable editable = new android.text.SpannableStringBuilder(text);
        this.text = editable;
        return editable;
    }

    public void programmaticText(CharSequence newText) {
        notifyTextChanged(newText != null ? newText : "");
        ShadowUi.INSTANCE.bump();
    }
}
