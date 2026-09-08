package com.google.android.material.bottomsheet;

@android.annotation.Stub
public class BottomSheetBehavior<V> {
    public static final int STATE_DRAGGING = 1;
    public static final int STATE_SETTLING = 2;
    public static final int STATE_EXPANDED = 3;
    public static final int STATE_COLLAPSED = 4;
    public static final int STATE_HIDDEN = 5;
    public static final int STATE_HALF_EXPANDED = 6;

    private int state = STATE_EXPANDED;

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
}
