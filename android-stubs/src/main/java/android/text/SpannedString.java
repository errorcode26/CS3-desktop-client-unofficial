package android.text;

public class SpannedString implements Spanned, CharSequence {
    private final String text;

    public SpannedString(CharSequence source) {
        this.text = source != null ? source.toString() : "";
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public char charAt(int index) {
        return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }

    @Override
    public String toString() {
        return text;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        return (T[]) java.lang.reflect.Array.newInstance(type, 0);
    }

    @Override
    public int getSpanStart(Object tag) {
        return -1;
    }

    @Override
    public int getSpanEnd(Object tag) {
        return -1;
    }

    @Override
    public int getSpanFlags(Object tag) {
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class<?> type) {
        return limit;
    }

    public static SpannedString valueOf(CharSequence source) {
        if (source instanceof SpannedString) {
            return (SpannedString) source;
        }
        return new SpannedString(source);
    }
}
