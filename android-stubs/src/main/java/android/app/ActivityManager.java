package android.app;

public class ActivityManager {
    public static class MemoryInfo {
        // Mocking an 8GB device
        public long totalMem = 8L * 1024L * 1024L * 1024L;
        public long availMem = 4L * 1024L * 1024L * 1024L;
        public boolean lowMemory = false;
    }

    public void getMemoryInfo(MemoryInfo outInfo) {
        outInfo.totalMem = 8L * 1024L * 1024L * 1024L;
        outInfo.availMem = 4L * 1024L * 1024L * 1024L;
        outInfo.lowMemory = false;
    }
}
