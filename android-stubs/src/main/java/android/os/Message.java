package android.os;

@android.annotation.Implemented
public final class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;

    public Message() {}

    public static Message obtain() {
        return new Message();
    }
}
