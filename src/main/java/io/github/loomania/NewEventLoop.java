package io.github.loomania;

/**
 * The event loop used with an event loop based virtual thread executor.
 */
public interface NewEventLoop {
    /**
     * Run the event loop.
     * The event loop should alternate between {@linkplain Context#runTasks(long) run tasks} and blocking for up to
     * the amount of time returned by that method while polling for ready threads or handling event loop level tasks.
     * If this method returns early, the executor will continue to run tasks for ready threads but it will no longer be
     * possible to poll for ready threads or handle event loop level tasks.
     * If this method throws an exception, it will be ignored.
     *
     * @param context the event loop context (not {@code null})
     */
    void run(Context context);

    /**
     * Wake up the event loop. This might entail waking up a selector, writing to a file descriptor, or
     * some other action which causes the event loop to be awoken if it is blocked.
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

    /**
     * The event loop context.
     * Must not be used from outside of the event loop thread or an exception will be thrown.
     */
    final class Context {
        Context() {}

        /**
         * Run ready tasks for up to {@code nanos} nanoseconds.
         * Note that ready tasks are not interrupted if the time is exceeded, so this method may return late.
         * If there are no ready tasks, or the event loop thread is explicitly unparked, this method may return early.
         * If the given value is less than or equal to zero, no tasks will be run.
         * This method must only be called from the event loop.
         *
         * @param nanos the number of nanoseconds to run tasks for
         * @return the number of nanoseconds (possibly zero) that the event loop may block for
         * @throws IllegalStateException if called from the wrong thread
         */
        public native long runTasks(long nanos);
    }
}
