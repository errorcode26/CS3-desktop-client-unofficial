package android.text;

@android.annotation.Stub
public class SpannableStringBuilder implements Editable, CharSequence {
    private final StringBuilder sb;

    public SpannableStringBuilder() {
        this.sb = new StringBuilder();
    }

    public SpannableStringBuilder(CharSequence text) {
        this.sb = new StringBuilder(text != null ? text : "");
    }

    @Override
    public int length() {
        return sb.length();
    }

    @Override
    public char charAt(int index) {
        return sb.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return sb.subSequence(start, end);
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    @Override
    public SpannableStringBuilder append(CharSequence text) {
        sb.append(text);
        return this;
    }

    @Override
    public SpannableStringBuilder append(CharSequence text, int start, int end) {
        sb.append(text, start, end);
        return this;
    }

    @Override
    public SpannableStringBuilder append(char text) {
        sb.append(text);
        return this;
    }

    @Override
    public SpannableStringBuilder replace(int st, int en, CharSequence source, int start, int end) {
        sb.replace(st, en, source.subSequence(start, end).toString());
        return this;
    }

    @Override
    public SpannableStringBuilder replace(int st, int en, CharSequence source) {
        sb.replace(st, en, source != null ? source.toString() : "");
        return this;
    }

    @Override
    public SpannableStringBuilder insert(int where, CharSequence text, int start, int end) {
        sb.insert(where, text.subSequence(start, end).toString());
        return this;
    }

    @Override
    public SpannableStringBuilder insert(int where, CharSequence text) {
        sb.insert(where, text != null ? text.toString() : "");
        return this;
    }

    @Override
    public SpannableStringBuilder delete(int st, int en) {
        sb.delete(st, en);
        return this;
    }

    @Override
    public void clear() {
        sb.setLength(0);
    }

    @Override
    public void clearSpans() {}
}
