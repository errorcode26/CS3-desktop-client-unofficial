package android.app;

import android.content.Context;
import android.content.DialogInterface;

@android.annotation.Stub
public class AlertDialog extends Dialog {

    protected AlertDialog(Context context) {
        super(context);
    }

    public static class Builder {
        private Context context;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) {
            return this;
        }

        public Builder setMessage(CharSequence message) {
            return this;
        }

        public Builder setView(android.view.View view) {
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
            return this;
        }

        public AlertDialog create() {
            return new AlertDialog(context);
        }

        public AlertDialog show() {
            AlertDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }
}
