package io.github.loomania;

import java.util.concurrent.Executor;
import java.util.function.LongConsumer;

/**
 * A reusable latency monitor.
 * Latency monitors are reusable but is not thread-safe.
 * This class serves as a simple example and need not live in this repository.
 */
public final class LatencyMonitor {
    private final Executor executor;
    private final LongConsumer consumer;
    private final Runnable finish = this::finish;
    private boolean running;
    private long start;

    /**
     * Construct a new instance.
     *
     * @param executor the executor to test (must not be {@code null})
     * @param consumer the consumer to call with the latency result (must not be {@code null})
     */
    public LatencyMonitor(final Executor executor, LongConsumer consumer) {
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
