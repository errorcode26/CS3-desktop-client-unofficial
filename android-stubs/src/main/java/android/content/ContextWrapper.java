package android.content;

@android.annotation.Implemented
public class ContextWrapper extends Context {
    protected Context baseContext;

    public ContextWrapper() {
        super();
        this.baseContext = this;
    }

    public ContextWrapper(Context base) {
        super();
        this.baseContext = base;
    }

    public Context getBaseContext() {
        return baseContext;
    }
}
