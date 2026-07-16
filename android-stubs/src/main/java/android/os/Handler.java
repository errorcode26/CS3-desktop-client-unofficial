package android.os;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@android.annotation.Implemented
public class Handler {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public Handler() {}
    public Handler(Looper looper) {}
    public Handler(Looper looper, Callback callback) {}

    public boolean post(Runnable r) {
        executor.execute(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        executor.schedule(r, delayMillis, TimeUnit.MILLISECONDS);
        return true;
    }

    public void removeCallbacks(Runnable r) {
        // Stub: Not easily supported with basic Executor
    }

    public void removeCallbacksAndMessages(Object token) {}

    public interface Callback {
        boolean handleMessage(Message msg);
    }
}
