package android.view;

@android.annotation.Stub
public interface ViewParent {
    void requestLayout();
    boolean isLayoutRequested();
}
