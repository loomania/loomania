package io.github.dmlloyd.loomania;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

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
     * Exit when all virtual threads are complete.
     */
    void close();
}
