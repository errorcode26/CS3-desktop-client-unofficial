package android.graphics;

@android.annotation.Stub
public class Point {
    public int x;
    public int y;

    public Point() {
    }

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point src) {
        if (src != null) {
            this.x = src.x;
            this.y = src.y;
        }
    }

    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
