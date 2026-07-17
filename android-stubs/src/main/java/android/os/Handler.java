package android.os;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@android.annotation.Implemented
public class Handler {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    // Tracks pending futures so removeCallbacks can actually cancel them
    private final ConcurrentHashMap<Runnable, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public Handler() {}
    public Handler(Looper looper) {}
    public Handler(Looper looper, Callback callback) {}

    public boolean post(Runnable r) {
        futures.put(r, executor.schedule(r, 0, TimeUnit.MILLISECONDS));
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        futures.put(r, executor.schedule(r, delayMillis, TimeUnit.MILLISECONDS));
        return true;
    }

    public void removeCallbacks(Runnable r) {
        ScheduledFuture<?> f = futures.remove(r);
        if (f != null) f.cancel(false);
    }

    public void removeCallbacksAndMessages(Object token) {
        // Cancel and clear all tracked futures for this handler
        for (ScheduledFuture<?> f : futures.values()) {
            f.cancel(false);
        }
        futures.clear();
    }

    public interface Callback {
        boolean handleMessage(Message msg);
    }
}

