package android.net;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@android.annotation.Implemented
public class Uri {
    private final String value;
    private final URI uri;

    private Uri(String value) {
        this.value = value;
        URI parsed;
        try {
            parsed = URI.create(value);
        } catch (Exception e) {
            parsed = null;
        }
        this.uri = parsed;
    }

    public static Uri parse(String value) {
        return new Uri(value);
    }

    public static String encode(String s) {
        return encode(s, null);
    }

    public static String encode(String s, String allow) {
        if (s == null) {
            return null;
        }
        StringBuilder encoded = null;
        int oldLength = s.length();
        int current = 0;
        while (current < oldLength) {
            int nextToEncode = current;
            while (nextToEncode < oldLength && isAllowed(s.charAt(nextToEncode), allow)) {
                nextToEncode++;
            }
            if (nextToEncode == oldLength) {
                if (current == 0) {
                    return s;
                } else {
                    encoded.append(s, current, oldLength);
                    return encoded.toString();
                }
            }
            if (encoded == null) {
                encoded = new StringBuilder();
            }
            if (nextToEncode > current) {
                encoded.append(s, current, nextToEncode);
            }
            current = nextToEncode;
            int nextAllowed = current + 1;
            while (nextAllowed < oldLength && !isAllowed(s.charAt(nextAllowed), allow)) {
                nextAllowed++;
            }
            String toEncode = s.substring(current, nextAllowed);
            try {
                byte[] bytes = toEncode.getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    encoded.append('%');
                    char ch = Character.forDigit((b >> 4) & 0xf, 16);
                    encoded.append(Character.toUpperCase(ch));
                    ch = Character.forDigit(b & 0xf, 16);
                    encoded.append(Character.toUpperCase(ch));
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            current = nextAllowed;
        }
        return encoded == null ? s : encoded.toString();
    }

    private static boolean isAllowed(char c, String allow) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) != -1
                || (allow != null && allow.indexOf(c) != -1);
    }

    public static String decode(String s) {
        if (s == null) {
            return null;
        }
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    public String getPath() {
        return uri != null ? uri.getPath() : null;
    }

    public String getHost() {
        return uri != null ? uri.getHost() : null;
    }

    public String getScheme() {
        return uri != null ? uri.getScheme() : null;
    }

    public String getQuery() {
        return uri != null ? uri.getQuery() : null;
    }

    public Set<String> getQueryParameterNames() {
        return parseQueryParametersMulti().keySet();
    }

    public String getQueryParameter(String key) {
        if (key == null) {
            return null;
        }
        List<String> values = parseQueryParametersMulti().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public List<String> getQueryParameters(String key) {
        if (key == null) {
            return Collections.emptyList();
        }
        List<String> values = parseQueryParametersMulti().get(key);
        if (values == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private Map<String, List<String>> parseQueryParametersMulti() {
        String query = getQuery();
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> params = new LinkedHashMap<>();
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            String[] kv = part.split("=", 2);
            String rawKey = kv[0];
            if (rawKey.isEmpty()) {
                continue;
            }
            String key = decodeComponent(rawKey);
            String value = kv.length > 1 ? decodeComponent(kv[1]) : "";
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private String decodeComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    public String getLastPathSegment() {
        String path = getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    @Override
    public String toString() {
        return value;
    }
}
