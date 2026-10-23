package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

@android.annotation.Implemented
public abstract class DialogPreference extends Preference {
    private CharSequence dialogTitle;
    private CharSequence dialogMessage;
    private int dialogLayoutResource;
    private CharSequence positiveButtonText;
    private CharSequence negativeButtonText;

    public DialogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DialogPreference(Context context) {
        super(context);
    }

    public CharSequence getDialogTitle() {
        return dialogTitle != null ? dialogTitle : getTitle();
    }

    public void setDialogTitle(CharSequence dialogTitle) {
        this.dialogTitle = dialogTitle;
    }

    public void setDialogTitle(int dialogTitleResId) {
    }

    public CharSequence getDialogMessage() {
        return dialogMessage;
    }

    public void setDialogMessage(CharSequence dialogMessage) {
        this.dialogMessage = dialogMessage;
    }

    public void setDialogMessage(int dialogMessageResId) {
    }

    public int getDialogLayoutResource() {
        return dialogLayoutResource;
    }

    public void setDialogLayoutResource(int dialogLayoutResource) {
        this.dialogLayoutResource = dialogLayoutResource;
    }

    public CharSequence getPositiveButtonText() {
        return positiveButtonText;
    }

    public void setPositiveButtonText(CharSequence positiveButtonText) {
        this.positiveButtonText = positiveButtonText;
    }

    public void setPositiveButtonText(int positiveButtonTextResId) {
    }

    public CharSequence getNegativeButtonText() {
        return negativeButtonText;
    }

    public void setNegativeButtonText(CharSequence negativeButtonText) {
        this.negativeButtonText = negativeButtonText;
    }

    public void setNegativeButtonText(int negativeButtonTextResId) {
    }
}
