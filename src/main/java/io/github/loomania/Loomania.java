package io.github.loomania;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Access to Loom internals for experimental munging (approach #2).
 * This approach strives for minimality, and to maximize safety.
 */
public final class Loomania {

    /**
     * Determine if Loomania is installed.
     *
     * @return {@code true} if installed, {@code false} otherwise
     */
    public static boolean isInstalled() {
        return false;
    }

    /**
     * Create a single-threaded virtual thread executor service which uses an event loop to schedule tasks.
     *
     * @param carrierThreadFactory the thread factory for carrier threads (must not be {@code null})
     * @param eventLoop the event loop implementation (must not be {@code null})
     * @param listener the executor service listener (must not be {@code null}, may be {@link ExecutorServiceListener#EMPTY})
     * @return the new executor service (not {@code null})
     */
    public static ExecutorService newEventLoopExecutorService(ThreadFactory carrierThreadFactory, EventLoop eventLoop, ExecutorServiceListener listener) {
        throw Nope.nope();
    }

    /**
     * Create a virtual thread executor which uses the given delegate executor service to schedule tasks.
     * Note that the executor service must be "well-behaved" in order to prevent situations such as deadlocks.
     *
     * @param delegate the delegate executor service (must not be {@code null})
     * @param name the name of the new executor (must not be {@code null})
     * @param listener the executor service listener (must not be {@code null}, may be {@link ExecutorServiceListener#EMPTY})
     * @return the new executor service (not {@code null})
     */
    public static ExecutorService newVirtualThreadExecutor(ExecutorService delegate, String name, ExecutorServiceListener listener) {
        throw Nope.nope();
    }

    /**
     * Create a builder for a fork-join-pool-based virtual thread executor.
     *
     * @return the new builder (not {@code null})
     */
    public static JdkVirtualThreadExecutorBuilder newJdkVirtualThreadExecutorBuilder() {
        throw Nope.nope();
    }

    static ExecutorService buildVirtualThreadFactory(JdkVirtualThreadExecutorBuilder builder) {
        throw Nope.nope();
    }

    private Loomania() {}
}
