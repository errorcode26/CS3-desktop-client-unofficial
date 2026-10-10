package android.app;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.Window;

@android.annotation.Stub
public class Activity extends Context {
    private final Window windowInstance = new Window();
    private View contentView = null;

    public Window getWindow() {
        return windowInstance;
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }

    public void runOnUiThread(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    public void setContentView(View view) {
        this.contentView = view;
    }

    public void setContentView(int layoutResId) {
        this.contentView = android.view.LayoutInflater.from(this).inflate(layoutResId, null);
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        if (contentView != null) {
            return (T) contentView.findViewById(id);
        }
        return null;
    }

    public boolean isFinishing() {
        return false;
    }

    public boolean isDestroyed() {
        return false;
    }
}
