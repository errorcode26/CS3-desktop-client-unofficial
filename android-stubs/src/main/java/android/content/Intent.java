package android.content;

import android.net.Uri;

@android.annotation.Stub
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";

    private final String action;
    private final Uri data;
    private ComponentName component;

    public Intent() {
        throw new UnsupportedOperationException("Android Intent called on Desktop.");
    }

    public Intent(String action, Uri data) {
        throw new UnsupportedOperationException("Android Intent called on Desktop.");
    }

    public String getAction() {
        return action;
    }

    public Uri getData() {
        return data;
    }

    public ComponentName getComponent() {
        return component;
    }

    public Intent setComponent(ComponentName component) {
        this.component = component;
        return this;
    }
}
