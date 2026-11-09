package android.util;

import java.util.regex.Pattern;

@android.annotation.Implemented
public class Patterns {
    public static final Pattern TOP_LEVEL_DOMAIN = Pattern.compile("(?i)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    public static final Pattern DOMAIN_NAME = Pattern.compile("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}");
    public static final Pattern IP_ADDRESS = Pattern.compile("((25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})");
    public static final Pattern WEB_URL = Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*");
    public static final Pattern EMAIL_ADDRESS = Pattern.compile("[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}\\@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+");
    public static final Pattern PHONE = Pattern.compile("(\\+[0-9]+[\\- \\.]*)?(\\([0-9]+\\)[\\- \\.]*)?([0-9][0-9\\- \\.]+[0-9])");
}
