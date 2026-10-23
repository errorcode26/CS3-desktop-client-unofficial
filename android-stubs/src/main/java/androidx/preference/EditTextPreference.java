package androidx.preference;
import android.content.Context;
import android.util.AttributeSet;
@android.annotation.Implemented
public class EditTextPreference extends DialogPreference {
    private String text;

    public EditTextPreference(Context c, AttributeSet a, int d, int d2) { super(c, a, d, d2); }
    public EditTextPreference(Context c, AttributeSet a, int d) { super(c, a, d); }
    public EditTextPreference(Context c, AttributeSet a) { super(c, a); }
    public EditTextPreference(Context c) { super(c); }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public interface OnBindEditTextListener {
        void onBindEditText(android.widget.EditText editText);
    }

    public void setOnBindEditTextListener(OnBindEditTextListener onBindEditTextListener) {}
}
