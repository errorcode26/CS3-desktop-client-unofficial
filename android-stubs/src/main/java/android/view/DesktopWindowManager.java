package android.view;

@android.annotation.Stub
public class DesktopWindowManager implements WindowManager {
    private final Display display = new Display();

    @Override
    public Display getDefaultDisplay() {
        return display;
    }

    @Override
    public void addView(View view, ViewGroup.LayoutParams params) {
    }

    @Override
    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
    }

    @Override
    public void removeView(View view) {
    }
}
