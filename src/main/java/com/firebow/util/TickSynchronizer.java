package com.firebow.util;

/**
 * Đồng bộ action với client tick.
 * Giống BOOST-FPS ClientTickSynchronizer:
 * đảm bảo action chạy đúng vào đầu tick tiếp theo,
 * không lệch timing gây flag anti-cheat.
 */
public final class TickSynchronizer {

    private volatile long currentTick = 0;
    private final Object monitor = new Object();

    public void onClientTick() {
        synchronized (monitor) {
            currentTick++;
            monitor.notifyAll();
        }
    }

    /**
     * Block thread hiện tại cho đến khi tick tiếp theo xảy ra.
     * @param timeoutMs thời gian chờ tối đa
     * @return true nếu tick xảy ra, false nếu timeout
     */
    public boolean awaitNextTick(long timeoutMs) {
        long target = currentTick + 1;
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        synchronized (monitor) {
            while (currentTick < target) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    monitor.wait(remaining / 1_000_000L, (int)(remaining % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    public long getCurrentTick() {
        return currentTick;
    }
}
