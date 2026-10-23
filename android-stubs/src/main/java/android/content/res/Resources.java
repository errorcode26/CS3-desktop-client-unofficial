package android.content.res;

import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

@android.annotation.Implemented
public class Resources {
    private final DisplayMetrics displayMetrics = new DisplayMetrics();

    public static class Theme {
        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) {
            return false;
        }
    }

    public Drawable getDrawable(int id) {
        return null;
    }

    public Drawable getDrawable(int id, Theme theme) {
        return null;
    }

    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }

    public String getString(int resId) {
        String res = DesktopResourceRegistry.INSTANCE.getString(resId);
        return res != null ? res : "";
    }

    public String getString(int resId, Object... formatArgs) {
        String format = getString(resId);
        if (format != null && !format.isEmpty() && formatArgs != null && formatArgs.length > 0) {
            try {
                return String.format(format, formatArgs);
            } catch (Exception ignored) {}
        }
        return format != null ? format : "";
    }

    public String[] getStringArray(int id) {
        java.util.List<String> list = DesktopResourceRegistry.INSTANCE.getStringArray(id);
        if (list != null) {
            return list.toArray(new String[0]);
        }
        return new String[0];
    }

    public CharSequence[] getTextArray(int id) {
        return getStringArray(id);
    }

    public int getColor(int id) {
        return 0;
    }

    public int getDimensionPixelSize(int id) {
        return 0;
    }

    public XmlResourceParser getLayout(int id) {
        return DesktopResourceRegistry.INSTANCE.getLayoutParser(id);
    }

    public XmlResourceParser getXml(int id) {
        return DesktopResourceRegistry.INSTANCE.getXmlParser(id);
    }

    public XmlResourceParser getAnimation(int id) {
        return null;
    }

    public CharSequence getText(int id) {
        return getString(id);
    }

    public int getIdentifier(String name, String defType, String defPackage) {
        if ("id".equals(defType) || (name != null && name.startsWith("id/"))) {
            return DesktopResourceRegistry.INSTANCE.resolveId(name.substring(name.indexOf('/') + 1));
        }
        return DesktopResourceRegistry.INSTANCE.resolveId(name != null ? name : "");
    }

    public String getResourceEntryName(int resid) {
        return "";
    }

    public String getResourceName(int resid) {
        return "";
    }

    public String getResourcePackageName(int resid) {
        return "com.lagradost.cloudstream3";
    }

    public String getResourceTypeName(int resid) {
        return "layout";
    }
}
