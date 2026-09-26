package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ListView;

@android.annotation.Stub
public class AlertDialog extends Dialog {

    private final ListView listViewField = new ListView();

    public AlertDialog(Context context) {
        super(context);
    }

    public ListView getListView() {
        return listViewField;
    }

    public static class Builder {
        private final AlertDialog dialog;

        public Builder(Context context) {
            this.dialog = new AlertDialog(context);
        }

        public Builder(Context context, int themeResId) {
            this.dialog = new AlertDialog(context);
        }

        public Builder setTitle(CharSequence title) {
            dialog.setTitle(title);
            return this;
        }

        public Builder setTitle(int titleId) {
            dialog.setTitle(titleId);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            dialog.setMessage(message);
            return this;
        }

        public Builder setView(View view) {
            dialog.setContentView(view);
            return this;
        }

        public Builder setView(int layoutResId) {
            dialog.setContentView(layoutResId);
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.positiveText = text;
            dialog.positiveListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.negativeText = text;
            dialog.negativeListener = listener;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.neutralText = text;
            dialog.neutralListener = listener;
            return this;
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            dialog.items = items;
            dialog.itemsListener = listener;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, DialogInterface.OnClickListener listener) {
            dialog.items = items;
            dialog.checkedItem = checkedItem;
            dialog.isSingleChoice = true;
            dialog.itemsListener = listener;
            return this;
        }

        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
            dialog.items = items;
            dialog.checkedItems = checkedItems;
            dialog.isMultiChoice = true;
            dialog.multiChoiceListener = listener;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            dialog.setCancelable(cancelable);
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
            dialog.setOnDismissListener(onDismissListener);
            return this;
        }

        public Builder setOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
            dialog.setOnCancelListener(onCancelListener);
            return this;
        }

        public Builder setOnShowListener(DialogInterface.OnShowListener onShowListener) {
            dialog.setOnShowListener(onShowListener);
            return this;
        }

        public AlertDialog create() {
            return dialog;
        }

        public AlertDialog show() {
            dialog.show();
            return dialog;
        }
    }
}
