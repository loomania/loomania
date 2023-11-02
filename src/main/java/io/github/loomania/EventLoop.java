package io.github.loomania;

/**
 * An event loop used with a single-threaded virtual thread executor.
 */
public interface EventLoop {
    /**
     * Unpark any ready threads, waiting for up to the given maximum number of nanoseconds for threads to become ready.
     * The event loop may awaken at any time and return if tasks become ready (for example, if a selector's selection
     * operation returns) or if it was awakened by way of {@code #unpark()}.
     * If tasks are immediately ready, then this method need not actually block at all, but should in this case unpark all the ready threads.
     * If this method throws an exception, it will be ignored.
     * When this method returns, another event loop method will immediately be called.
     * Therefore, this method should park for some amount of time after unparking ready threads.
     *
     * @param maxNanos the maximum number of nanoseconds to park
     */
    void unparkReadyThreadsOrWaitNanos(long maxNanos);

    /**
     * Unpark any ready threads, waiting indefinitely for threads to become ready.
     * The event loop may awaken at any time and return if tasks become ready (for example, if a selector's selection
     * operation returns) or if it was awakened by way of {@code #unpark()}.
     * If tasks are immediately ready, then this method need not actually block at all, but should in this case unpark all the ready threads.
     * If this method throws an exception, it will be ignored.
     * When this method returns, another event loop method will immediately be called.
     * Therefore, this method should park for some amount of time after unparking ready threads.
     */
    void unparkReadyThreadsOrWait();

    /**
     * Unpark any ready threads without blocking.
     * This method is called when the deadline for unparking ready threads has elapsed, but other virtual threads are also ready.
     * If this method throws an exception, it will be ignored.
     * When this method returns, another event loop method will immediately be called.
     * Therefore, this method should park for some amount of time after unparking ready threads.
     */
    void unparkReadyThreads();

    /**
     * Wake up the event loop. This might entail waking up a selector, writing to a file descriptor, or
     * some other action which causes the event loop to be awoken if it is blocked in one of the {@code unpark*()} methods.
     * This method is called when a virtual thread which is internal to this scheduler is unparked by a thread which is external to the
     * scheduler.
     * If this method throws an exception, it will be ignored.
     * <p>
     * This method may be called from <em>any</em> thread.
     */
    void wakeup();

    /**
     * Request that the event loop be terminated.
     * This method is called from a dedicated shutdown virtual thread when the corresponding executor is shut down.
     * If this method throws an exception, it will be ignored and shutdown will proceed.
     */
    void requestTermination();

    /**
     * Indicate that the termination of the executor has completed.
     * When this method is called, no virtual threads exist in the executor any longer.
     * If this method throws an exception, it will be ignored and shutdown will complete.
     */
    void terminationComplete();
}
