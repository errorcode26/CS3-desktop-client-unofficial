package android.app;

import android.content.Context;
import android.content.Intent;

@android.annotation.Stub
public class Activity extends Context {
    public void startActivity(Intent intent) {
        throw new UnsupportedOperationException("Android Activity/Intent called on Desktop.");
    }

    public void runOnUiThread(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
