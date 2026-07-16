package android.util;

@android.annotation.Implemented
public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    public static byte[] decode(String str, int flags) {
        if ((flags & URL_SAFE) != 0) {
            return java.util.Base64.getUrlDecoder().decode(str);
        } else {
            return java.util.Base64.getDecoder().decode(str);
        }
    }

    public static byte[] decode(byte[] input, int flags) {
        if ((flags & URL_SAFE) != 0) {
            return java.util.Base64.getUrlDecoder().decode(input);
        } else {
            return java.util.Base64.getDecoder().decode(input);
        }
    }

    public static String encodeToString(byte[] input, int flags) {
        if ((flags & URL_SAFE) != 0) {
            if ((flags & NO_PADDING) != 0) {
                 return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input);
            }
            return java.util.Base64.getUrlEncoder().encodeToString(input);
        } else {
            if ((flags & NO_PADDING) != 0) {
                 return java.util.Base64.getEncoder().withoutPadding().encodeToString(input);
            }
            return java.util.Base64.getEncoder().encodeToString(input);
        }
    }

    public static byte[] encode(byte[] input, int flags) {
        boolean urlSafe = (flags & URL_SAFE) != 0;
        boolean noPadding = (flags & NO_PADDING) != 0;
        java.util.Base64.Encoder encoder = urlSafe ? java.util.Base64.getUrlEncoder() : java.util.Base64.getEncoder();
        if (noPadding) {
            encoder = encoder.withoutPadding();
        }
        return encoder.encode(input);
    }
}
