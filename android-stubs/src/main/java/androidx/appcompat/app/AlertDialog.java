package androidx.appcompat.app;

import android.content.Context;

@android.annotation.Stub
public class AlertDialog extends android.app.AlertDialog {

    protected AlertDialog(Context context) {
        super(context);
    }

    public static class Builder extends android.app.AlertDialog.Builder {
        public Builder(Context context) {
            super(context);
        }

        public Builder(Context context, int themeResId) {
            super(context);
        }
    }
}
