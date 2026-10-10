package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ListView;

@android.annotation.Stub
public class AlertDialog extends Dialog {

    public static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE;
    public static final int BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE;
    public static final int BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL;

    private final ListView listViewField = new ListView();

    public AlertDialog(Context context) {
        super(context);
    }

    public ListView getListView() {
        return listViewField;
    }

    public android.widget.Button getButton(int whichButton) {
        return new android.widget.Button(getContext());
    }

    public static class Builder {
        protected final AlertDialog dialog;

        public Builder(Context context) {
            this(context, 0);
        }

        public Builder(Context context, int themeResId) {
            this.dialog = createDialog(context);
        }

        protected Builder(AlertDialog dialog) {
            this.dialog = dialog;
        }

        protected AlertDialog createDialog(Context context) {
            return new AlertDialog(context);
        }

        public Context getContext() {
            return dialog.getContext();
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

        public Builder setMessage(int messageId) {
            CharSequence text = null;
            try {
                text = dialog.getContext().getResources().getString(messageId);
            } catch (Exception ignored) {}
            if (text != null && !text.toString().isEmpty()) {
                dialog.setMessage(text);
            }
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

        public Builder setCustomTitle(View customTitleView) {
            dialog.setContentView(customTitleView);
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.positiveText = text;
            dialog.positiveListener = listener;
            return this;
        }

        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
            CharSequence text = null;
            try {
                text = dialog.getContext().getResources().getString(textId);
            } catch (Exception ignored) {}
            if (text == null || text.toString().isEmpty()) {
                text = "OK";
            }
            return setPositiveButton(text, listener);
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.negativeText = text;
            dialog.negativeListener = listener;
            return this;
        }

        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
            CharSequence text = null;
            try {
                text = dialog.getContext().getResources().getString(textId);
            } catch (Exception ignored) {}
            if (text == null || text.toString().isEmpty()) {
                text = "Cancel";
            }
            return setNegativeButton(text, listener);
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.neutralText = text;
            dialog.neutralListener = listener;
            return this;
        }

        public Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
            CharSequence text = null;
            try {
                text = dialog.getContext().getResources().getString(textId);
            } catch (Exception ignored) {}
            return setNeutralButton(text, listener);
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            dialog.items = items;
            dialog.itemsListener = listener;
            return this;
        }

        public Builder setItems(int itemsId, DialogInterface.OnClickListener listener) {
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

        public Builder setSingleChoiceItems(int itemsId, int checkedItem, DialogInterface.OnClickListener listener) {
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

        public Builder setMultiChoiceItems(int itemsId, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
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
