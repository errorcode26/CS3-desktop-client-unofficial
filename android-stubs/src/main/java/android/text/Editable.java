package android.text;

@android.annotation.Stub
public interface Editable extends CharSequence, Appendable {
    Editable append(CharSequence text);
    Editable append(CharSequence text, int start, int end);
    Editable append(char text);
    Editable replace(int st, int en, CharSequence source, int start, int end);
    Editable replace(int st, int en, CharSequence source);
    Editable insert(int where, CharSequence text, int start, int end);
    Editable insert(int where, CharSequence text);
    Editable delete(int st, int en);
    void clear();
    void clearSpans();
}
