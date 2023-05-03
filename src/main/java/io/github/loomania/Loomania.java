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

    public static ExecutorService newEventLoopExecutorService(ThreadFactory carrierThreadFactory, EventLoop eventLoop, ExecutorServiceListener listener) {
        throw Nope.nope();
    }

    public static ExecutorService newVirtualThreadExecutor(ExecutorService delegate, String name, ExecutorServiceListener listener) {
        throw Nope.nope();
    }

    public static JdkVirtualThreadExecutorBuilder newJdkVirtualThreadExecutorBuilder() {
        throw Nope.nope();
    }

    static ExecutorService buildVirtualThreadFactory(JdkVirtualThreadExecutorBuilder builder) {
        throw Nope.nope();
    }

    private Loomania() {}
}
