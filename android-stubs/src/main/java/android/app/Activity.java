package android.app;

import android.content.Context;
import android.content.Intent;

@android.annotation.Stub
public class Activity extends Context {
    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }

    public void runOnUiThread(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
