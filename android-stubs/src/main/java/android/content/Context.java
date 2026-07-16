package android.content;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@android.annotation.Implemented
public class Context {
    public static final int MODE_PRIVATE = 0;

    private static final String DEFAULT_PACKAGE_NAME = "com.lagradost.cloudstream3.desktop";

    private final File baseDir;
    private final File filesDir;
    private final File cacheDir;
    private final Resources resources = new Resources();
    private final PackageManager packageManager = new PackageManager();
    private final Map<String, SharedPreferences> preferences = new ConcurrentHashMap<>();

    public Context() {
        this(getDefaultBaseDir());
    }

    public Context(File baseDir) {
        this.baseDir = baseDir;
        if (!this.baseDir.exists()) {
            this.baseDir.mkdirs();
        }
        this.filesDir = new File(this.baseDir, "files");
        if (!this.filesDir.exists()) {
            this.filesDir.mkdirs();
        }
        this.cacheDir = new File(this.baseDir, "cache");
        if (!this.cacheDir.exists()) {
            this.cacheDir.mkdirs();
        }
    }

    public Context getApplicationContext() {
        return this;
    }

    public File getFilesDir() {
        return filesDir;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public File getDir(String name, int mode) {
        File dir = new File(baseDir, name);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences.computeIfAbsent(name, key -> new DesktopSharedPreferences(name));
    }

    public Resources getResources() {
        return resources;
    }

    public PackageManager getPackageManager() {
        return packageManager;
    }

    public String getPackageName() {
        return DEFAULT_PACKAGE_NAME;
    }

    public Object getSystemService(String name) {
        return null;
    }

    private static File getDefaultBaseDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String basePath;
        if (os.contains("win")) {
            basePath = System.getenv("APPDATA");
            if (basePath == null || basePath.isEmpty()) {
                basePath = System.getProperty("user.home");
            }
            basePath = basePath + File.separator + "CloudStreamDesktop";
        } else if (os.contains("mac")) {
            basePath = System.getProperty("user.home") + "/Library/Application Support/CloudStreamDesktop";
        } else {
            basePath = System.getProperty("user.home") + "/.local/share/CloudStreamDesktop";
        }
        return new File(basePath);
    }
}
