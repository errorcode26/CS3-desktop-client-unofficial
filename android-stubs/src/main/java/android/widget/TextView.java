package android.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi;

@android.annotation.Stub
public class TextView extends View {
    public CharSequence text = "";
    public CharSequence hint = "";
    public int textColor = 0xFFFFFFFF;
    public int hintTextColor = 0xFF9E9E9E;
    public float textSizePx = 28f;
    public boolean bold = false;
    public boolean italic = false;
    public boolean allCaps = false;
    public int gravity = 0;
    public int maxLines = Integer.MAX_VALUE;
    public boolean singleLine = false;
    public float letterSpacing = 0f;

    public TextView() { super(); }
    public TextView(Context context) { super(context); }
    public TextView(Context context, Object attrs) { super(context, attrs); }
    public TextView(Context context, Object attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    protected final java.util.List<android.text.TextWatcher> textWatchers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addTextChangedListener(android.text.TextWatcher watcher) {
        if (watcher != null && !textWatchers.contains(watcher)) {
            textWatchers.add(watcher);
        }
    }

    public void removeTextChangedListener(android.text.TextWatcher watcher) {
        if (watcher != null) {
            textWatchers.remove(watcher);
        }
    }

    public void notifyTextChanged(CharSequence newText) {
        CharSequence oldText = this.text != null ? this.text : "";
        android.text.SpannableStringBuilder editable = new android.text.SpannableStringBuilder(newText);
        this.text = editable;
        for (android.text.TextWatcher watcher : textWatchers) {
            try {
                watcher.beforeTextChanged(oldText, 0, oldText.length(), newText != null ? newText.length() : 0);
                watcher.onTextChanged(newText, 0, oldText.length(), newText != null ? newText.length() : 0);
                watcher.afterTextChanged(editable);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public CharSequence getText() { return text; }
    public void setText(CharSequence text) {
        notifyTextChanged(text != null ? text : "");
        ShadowUi.INSTANCE.bump();
    }

    public CharSequence getHint() { return hint; }
    public void setHint(CharSequence hint) {
        this.hint = (hint != null) ? hint : "";
        ShadowUi.INSTANCE.bump();
    }

    public int getTextColor() { return textColor; }
    public void setTextColor(int color) {
        this.textColor = color;
        ShadowUi.INSTANCE.bump();
    }

    public int getHintTextColor() { return hintTextColor; }
    public void setHintTextColor(int color) { this.hintTextColor = color; }

    public float getTextSize() { return textSizePx / 2.0f; }
    public void setTextSize(float size) {
        this.textSizePx = size * 2.0f;
        ShadowUi.INSTANCE.bump();
    }

    public int getGravity() { return gravity; }
    public void setGravity(int gravity) { this.gravity = gravity; }

    public void setTypeface(Typeface typeface) {
        if (typeface != null && typeface.isBold()) {
            this.bold = true;
        }
    }

    public void setTypeface(Typeface typeface, int style) {
        this.bold = (style & 1) != 0;
        this.italic = (style & 2) != 0;
    }

    public Typeface getTypeface() {
        return Typeface.DEFAULT;
    }

    public void setLineSpacing(float add, float mult) {}

    public void setAllCaps(boolean allCaps) {
        this.allCaps = allCaps;
        ShadowUi.INSTANCE.bump();
    }

    public void setSingleLine() {
        this.singleLine = true;
        this.maxLines = 1;
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
        if (singleLine) this.maxLines = 1;
    }

    public void setMaxLines(int maxLines) { this.maxLines = maxLines; }
    public int getMaxLines() { return maxLines; }

    public void setLetterSpacing(float letterSpacing) { this.letterSpacing = letterSpacing; }
    public float getLetterSpacing() { return letterSpacing; }

    public void setEllipsize(Object where) { this.maxLines = 1; }

    public interface OnEditorActionListener {
        boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event);
    }

    public void setOnEditorActionListener(OnEditorActionListener l) {}
}
