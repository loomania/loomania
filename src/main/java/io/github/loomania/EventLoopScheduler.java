package io.github.loomania;

/**
 * A scheduler which is designed to interact with event loops.
 * The methods of the scheduler are not specified to be called from any particular thread, so care must be taken to conform
 * exactly to the method contract. [todo: we can tighten this up after some experimentation]
 */
public interface EventLoopScheduler {
    /**
     * Initialize the event loop scheduler. The given thread factory can be used to create virtual threads within this event loop
     * until the event loop terminates.
     *
     * @param executorService the executor service which can run tasks on this scheduler (not {@code null})
     * @return the maximum number of nanoseconds that may elapse before [the scheduler] should try to unpark ready threads,
     *      or {@code -1} for no maximum
     */
    long initialize(VirtualThreadExecutorService executorService);

    /**
     * Unpark any ready threads, waiting for up to the given maximum number of nanoseconds for threads to become ready.
     * The scheduler may unpark early if tasks become ready before the deadline (for example, if a selector's selection operation returns)
     * or if it was awakened by way of {@code #unpark()}.
     * If tasks are immediately ready, then this method need not actually park at all, but should in this case unpark all the ready threads.
     * If this method throws an exception, it will be ignored.
     *
     * @param maxNanos the maximum number of nanoseconds to park
     * @return the maximum number of nanoseconds that may elapse before [the scheduler] should try to unpark ready threads again,
     *      or {@code -1} for no maximum
     */
    long unparkReadyThreadsOrWaitNanos(long maxNanos);

    /**
     * Unpark any ready threads, waiting indefinitely for threads to become ready.
     * The scheduler may unpark if tasks become ready before the deadline (for example, if a selector's selection operation returns)
     * or if it was awakened by way of {@code #unpark()}.
     * If tasks are immediately ready, then this method need not actually park at all, but should in this case unpark all the ready threads.
     * If this method throws an exception, it will be ignored.
     *
     * @return the maximum number of nanoseconds that may elapse before [the scheduler] should try to unpark ready threads again,
     *      or {@code -1} for no maximum
     */
    long unparkReadyThreadsOrWait();

    /**
     * Unpark any ready threads, returning immediately.
     * This method should unpark all the ready threads and return.
     * This method is called when the deadline for unparking ready threads has elapsed, but other virtual threads are also ready.
     * If this method throws an exception, it will be ignored.
     *
     * @return the maximum number of nanoseconds that may elapse before [the scheduler] should try to unpark ready threads again,
     *      or {@code -1} for no maximum
     */
    long unparkReadyThreads();

    /**
     * Wake up the event loop. This might entail waking up a selector, writing to a file descriptor, or
     * some other action which causes the event loop to be woken up if it is blocked in one of the {@code unpark*()} methods.
     * This method is called when a virtual thread which is internal to this scheduler is unparked by a thread which is external to the
     * scheduler.
     * If this method throws an exception, it will be ignored.
     * <p>
     * This method may be called from <em>any</em> thread.
     */
    void wakeup();

    /**
     * Called when the scheduler is requested to be shut down.
     * The scheduler <em>may</em> initiate an orderly shutdown
     */
    void shutdown();

    /**
     * Called to terminate the scheduler.
     * The implementation should clean up all resources which were allocated in {@link #initialize(VirtualThreadExecutorService)}.
     * When this method is called, the executor service is terminated and cannot be used to create additional tasks.
     * All other virtual threads will have exited.
     */
    void terminate();
}
