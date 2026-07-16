package android.app

class ActivityManager {
    class MemoryInfo {
        // Defaulting to 8GB for Desktop clients since getSystemService will likely return null
        @JvmField var totalMem: Long = 8L * 1024L * 1024L * 1024L
    }

    fun getMemoryInfo(outInfo: MemoryInfo) {
        // Mocking an 8GB device
        outInfo.totalMem = 8L * 1024L * 1024L * 1024L
    }
}
