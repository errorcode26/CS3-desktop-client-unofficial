package android.content.res;

import android.util.DisplayMetrics;

@android.annotation.Implemented
public class Resources {
    private final DisplayMetrics displayMetrics = new DisplayMetrics();

    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }
}
