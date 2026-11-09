package android.content.res;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@android.annotation.Stub
public class AssetManager {
    public static final int ACCESS_BUFFER = 3;
    public static final int ACCESS_RANDOM = 1;
    public static final int ACCESS_STREAMING = 2;
    public static final int ACCESS_UNKNOWN = 0;

    public InputStream open(String filename) throws IOException {
        return open(filename, ACCESS_STREAMING);
    }

    public InputStream open(String filename, int accessMode) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            InputStream is = cl.getResourceAsStream("assets/" + filename);
            if (is == null) {
                is = cl.getResourceAsStream(filename);
            }
            if (is != null) {
                return is;
            }
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    public String[] list(String path) {
        return new String[0];
    }

    public void close() {}
}
