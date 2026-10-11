package android.content.res;

@android.annotation.Stub
public class ColorStateList {
    
    public ColorStateList() {}

    public ColorStateList(int[][] states, int[] colors) {}

    public static ColorStateList valueOf(int color) {
        return new ColorStateList();
    }
}
