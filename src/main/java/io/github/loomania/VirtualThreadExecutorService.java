package io.github.loomania;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

/**
 * A virtual thread executor service.
 */
public interface VirtualThreadExecutorService extends ThreadFactory, ExecutorService {
    /**
     * Execute a task on a dedicated virtual thread.
     *
     * @param command the runnable task (must not be {@code null})
     * @throws RejectedExecutionException if the executor has been shut down or if {@link Integer#MAX_VALUE} virtual
     *      threads are already running
     */
    default void execute(Runnable command) throws RejectedExecutionException {
        final Thread thread = newThread(command);
        if (thread == null) {
            throw new RejectedExecutionException("The executor cannot accept tasks");
        }
        thread.start();
    }
}
