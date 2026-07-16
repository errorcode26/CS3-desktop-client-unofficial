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
