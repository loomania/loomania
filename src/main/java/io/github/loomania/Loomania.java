package io.github.loomania;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Access to Loom internals for experimental munging (approach #2).
 * This approach strives for minimality, and to maximize safety.
 */
public final class Loomania {

    private static final boolean installed;

    static {
        boolean ok = false;
        try {
            ok = LoomaniaImpl.isInstalled();
        } catch (Throwable ignored) {}
        installed = ok;
    }

    /**
     * Determine if Loomania is installed.
     *
     * @return {@code true} if installed, {@code false} otherwise
     */
    public static boolean isInstalled() {
        return installed;
    }

    public static Executor getScheduler(Thread virtualThread) {
        if (! installed) throw Nope.nope();
        return LoomaniaImpl.getScheduler(virtualThread);
    }

    /**
     * Get a new, unstarted virtual thread which uses the given scheduler.
     *
     * @param scheduler the scheduler, or {@code null} for the default
     * @param name the thread name (must not be {@code null})
     * @param inheritThreadLocals {@code true} to inherit thread locals, or {@code false} otherwise
     * @param task the start task (must not be {@code null})
     * @param handler the uncaught exception handler, or {@code null} to use the default
     * @return a new, unstarted virtual thread (not {@code null})
     */
    public static Thread newVirtualThread(Executor scheduler, String name, boolean inheritThreadLocals, Runnable task, Thread.UncaughtExceptionHandler handler) {
        if (! installed) throw Nope.nope();
        return LoomaniaImpl.newVirtualThread(scheduler, name, inheritThreadLocals, task, handler);
    }

    /**
     * Create a single-threaded virtual thread executor service which uses an event loop to schedule tasks.
     *
     * @param carrier the context to propagate to virtual threads, or {@code null} for none
     * @param eventLoop the event loop implementation (must not be {@code null})
     * @param listener the executor service listener (must not be {@code null}, may be {@link ExecutorServiceListener#EMPTY})
     * @return the new executor service (not {@code null})
     */
    public static ExecutorService newEventLoopExecutorService(ScopedValue_Temporary.Carrier carrier, EventLoop eventLoop, ExecutorServiceListener listener) {
        if (! installed) throw Nope.nope();
        return LoomaniaImpl.newEventLoopExecutorService(carrier, eventLoop, listener);
    }

    /**
     * Create a virtual thread executor which uses the given delegate executor service to schedule tasks.
     * Note that the executor service must be "well-behaved" in order to prevent situations such as deadlocks.
     *
     * @param carrier the context to propagate to virtual threads, or {@code null} for none
     * @param delegate the delegate executor service (must not be {@code null})
     * @param name the name of the new executor (must not be {@code null})
     * @param listener the executor service listener (must not be {@code null}, may be {@link ExecutorServiceListener#EMPTY})
     * @return the new executor service (not {@code null})
     */
    public static ExecutorService newVirtualThreadExecutor(ScopedValue_Temporary.Carrier carrier, ExecutorService delegate, String name, ExecutorServiceListener listener) {
        if (! installed) throw Nope.nope();
        return LoomaniaImpl.newVirtualThreadExecutor(carrier, delegate, name, listener);
    }

    /**
     * Create a builder for a fork-join-pool-based virtual thread executor.
     *
     * @return the new builder (not {@code null})
     */
    public static JdkVirtualThreadExecutorBuilder newJdkVirtualThreadExecutorBuilder() {
        if (! installed) throw Nope.nope();
        return LoomaniaImpl.newJdkVirtualThreadExecutorBuilder();
    }

    private Loomania() {}
}
