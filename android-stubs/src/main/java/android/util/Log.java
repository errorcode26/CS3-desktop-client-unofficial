package android.util;

import com.lagradost.common.logging.AppLogger;

@android.annotation.Implemented
public final class Log {
    public static int d(String tag, String msg) {
        AppLogger.INSTANCE.d(tag, msg);
        return 0;
    }
    public static int d(String tag, String msg, Throwable tr) {
        AppLogger.INSTANCE.d(tag, msg);
        return 0;
    }
    public static int e(String tag, String msg) {
        AppLogger.INSTANCE.e(tag, msg, null);
        return 0;
    }
    public static int e(String tag, String msg, Throwable tr) {
        AppLogger.INSTANCE.e(tag, msg, tr);
        return 0;
    }
    public static int i(String tag, String msg) {
        AppLogger.INSTANCE.i(tag, msg);
        return 0;
    }
    public static int i(String tag, String msg, Throwable tr) {
        AppLogger.INSTANCE.i(tag, msg);
        return 0;
    }
    public static int v(String tag, String msg) {
        AppLogger.INSTANCE.d(tag, msg); // VERBOSE maps to DEBUG in SLF4J
        return 0;
    }
    public static int v(String tag, String msg, Throwable tr) {
        AppLogger.INSTANCE.d(tag, msg);
        return 0;
    }
    public static int w(String tag, String msg) {
        AppLogger.INSTANCE.w(tag, msg, null);
        return 0;
    }
    public static int w(String tag, String msg, Throwable tr) {
        AppLogger.INSTANCE.w(tag, msg, tr);
        return 0;
    }
    public static int w(String tag, Throwable tr) {
        AppLogger.INSTANCE.w(tag, "", tr);
        return 0;
    }
    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
