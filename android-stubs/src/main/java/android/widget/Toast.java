package android.widget;

import android.content.Context;

@android.annotation.Implemented
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static Toast makeText(Context context, CharSequence text, int duration) {
        System.out.println("TOAST: " + text);
        return new Toast();
    }

    public void show() {
        // Already printed in makeText for desktop simplicity
    }
}
