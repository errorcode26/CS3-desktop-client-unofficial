package android.view;

import android.graphics.Point;

@android.annotation.Stub
public class Display {
    public void getSize(Point outSize) {
        if (outSize != null) {
            outSize.x = 1920;
            outSize.y = 1080;
        }
    }

    public int getWidth() {
        return 1920;
    }

    public int getHeight() {
        return 1080;
    }
}
