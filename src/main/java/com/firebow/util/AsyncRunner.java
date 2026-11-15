package com.firebow.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chạy action trên thread riêng với delay millisecond thực.
 * Giống BOOST-FPS: tránh gửi packet đúng vào tick boundary.
 */
public final class AsyncRunner {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "firebow-worker");
        t.setDaemon(true);
        return t;
    });

    private AsyncRunner() {}

    public static void run(Runnable action) {
        EXECUTOR.execute(action);
    }

    public static void runDelayed(Runnable action, long delayMs) {
        EXECUTOR.execute(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            action.run();
        });
    }
}
