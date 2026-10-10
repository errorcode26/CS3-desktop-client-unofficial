package androidx.appcompat.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

@android.annotation.Stub
public class AlertDialog extends android.app.AlertDialog {

    public AlertDialog(Context context) {
        super(context);
    }

    public static class Builder extends android.app.AlertDialog.Builder {
        public Builder(Context context) {
            super(context);
        }

        public Builder(Context context, int themeResId) {
            super(context, themeResId);
        }

        @Override
        protected android.app.AlertDialog createDialog(Context context) {
            return new AlertDialog(context);
        }

        @Override
        public Builder setTitle(CharSequence title) {
            super.setTitle(title);
            return this;
        }

        @Override
        public Builder setTitle(int titleId) {
            super.setTitle(titleId);
            return this;
        }

        @Override
        public Builder setMessage(CharSequence message) {
            super.setMessage(message);
            return this;
        }

        @Override
        public Builder setMessage(int messageId) {
            super.setMessage(messageId);
            return this;
        }

        @Override
        public Builder setView(View view) {
            super.setView(view);
            return this;
        }

        @Override
        public Builder setView(int layoutResId) {
            super.setView(layoutResId);
            return this;
        }

        @Override
        public Builder setCustomTitle(View customTitleView) {
            super.setCustomTitle(customTitleView);
            return this;
        }

        @Override
        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            super.setPositiveButton(text, listener);
            return this;
        }

        @Override
        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
            super.setPositiveButton(textId, listener);
            return this;
        }

        @Override
        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            super.setNegativeButton(text, listener);
            return this;
        }

        @Override
        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
            super.setNegativeButton(textId, listener);
            return this;
        }

        @Override
        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            super.setNeutralButton(text, listener);
            return this;
        }

        @Override
        public Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
            super.setNeutralButton(textId, listener);
            return this;
        }

        @Override
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            super.setItems(items, listener);
            return this;
        }

        @Override
        public Builder setItems(int itemsId, DialogInterface.OnClickListener listener) {
            super.setItems(itemsId, listener);
            return this;
        }

        @Override
        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, DialogInterface.OnClickListener listener) {
            super.setSingleChoiceItems(items, checkedItem, listener);
            return this;
        }

        @Override
        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
            super.setMultiChoiceItems(items, checkedItems, listener);
            return this;
        }

        @Override
        public Builder setCancelable(boolean cancelable) {
            super.setCancelable(cancelable);
            return this;
        }

        @Override
        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            super.setOnDismissListener(listener);
            return this;
        }

        @Override
        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) {
            super.setOnCancelListener(listener);
            return this;
        }

        @Override
        public Builder setOnShowListener(DialogInterface.OnShowListener listener) {
            super.setOnShowListener(listener);
            return this;
        }

        @Override
        public AlertDialog create() {
            return (AlertDialog) super.create();
        }

        @Override
        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            return d;
        }
    }
}
