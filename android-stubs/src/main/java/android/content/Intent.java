package android.content;

import android.net.Uri;

@android.annotation.Stub
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";

    private final String action;
    private final Uri data;
    private ComponentName component;

    public Intent() {
        this.action = null;
        this.data = null;
    }

    public Intent(String action) {
        this.action = action;
        this.data = null;
    }

    public Intent(String action, Uri data) {
        this.action = action;
        this.data = data;
    }

    public Intent putExtra(String name, String value) {
        return this;
    }

    public Intent putExtra(String name, boolean value) {
        return this;
    }

    public Intent putExtra(String name, int value) {
        return this;
    }

    public Intent addFlags(int flags) {
        return this;
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
