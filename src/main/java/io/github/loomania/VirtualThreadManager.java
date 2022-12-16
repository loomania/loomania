package io.github.loomania;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.LongConsumer;

/**
 * A virtual thread manager which can be used to run many blocking tasks within the same carrier thread.
 */
public interface VirtualThreadManager extends Executor, ThreadFactory, AutoCloseable {
    /**
     * Execute a task in a unique virtual thread.
     *
     * @param runnable the runnable task
     */
    default void execute(Runnable runnable) {
        newThread(runnable).start();
    }

    /**
     * Create a new virtual thread.
     *
     * @param runnable a runnable to be executed in the thread
     * @return the unstarted virtual thread
     */
    Thread newThread(Runnable runnable);

    /**
     * Determine if the scheduler has pending tasks.
     * If {@code false}, parking or exiting this virtual thread will cause the carrier to be idle.
     *
     * @return {@code true} if there are pending tasks, or {@code false} otherwise
     * @throws IllegalStateException if called from outside the carrier thread
     */
    boolean hasPendingTasks() throws IllegalStateException;

    /**
     * Create a new latency monitor for this task queue.
     * The returned object is reusable but is not thread-safe.
     * Only one latency probe should be started at a time.
     *
     * @param resultListener a listener that is called with the actual latency, in nanoseconds
     * @return the latency monitor
     */
    LatencyMonitor createLatencyMonitor(LongConsumer resultListener);

    /**
     * Exit when all virtual threads are complete.
     */
    void close();

    /**
     * A reusable latency monitor.
     */
    final class LatencyMonitor {
        private final Executor executor;
        private final LongConsumer consumer;
        private final Runnable finish = this::finish;
        private boolean running;
        private long start;

        LatencyMonitor(final Executor executor, LongConsumer consumer) {
            this.executor = executor;
            this.consumer = consumer;
        }

        private void finish() {
            running = false;
            consumer.accept(Math.max(0, System.nanoTime() - start));
        }

        /**
         * Start a latency probe.
         * The registered consumer will be called with the number of nanoseconds of latency.
         * The listener should execute quickly (preferably without blocking).
         */
        public void start() {
            if (running) {
                throw new IllegalStateException("Latency monitor is already running");
            }
            running = true;
            start = System.nanoTime();
            executor.execute(finish);
        }
    }
}
