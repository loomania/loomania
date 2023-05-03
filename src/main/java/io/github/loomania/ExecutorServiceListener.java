package io.github.loomania;

/**
 * Listeners for events on an executor service.
 */
public interface ExecutorServiceListener {
    /**
     * The executor was requested to shut down.
     * After this method returns, new tasks will be rejected.
     */
    default void shutdownRequested() {}

    /**
     * The executor has initiated shutdown.
     * New tasks are being rejected.
     */
    default void shutdownInitiated() {}

    /**
     * The executor has completed shutdown.
     * No tasks remain.
     */
    default void terminated() {}

    default ExecutorServiceListener andThen(ExecutorServiceListener next) {
        ExecutorServiceListener outer = this;
        return new ExecutorServiceListener() {
            public void shutdownRequested() {
                try {
                    outer.shutdownRequested();
                } catch (Throwable ignored) {}
                next.shutdownRequested();
            }

            public void shutdownInitiated() {
                try {
                    outer.shutdownInitiated();
                } catch (Throwable ignored) {}
                next.shutdownInitiated();
            }

            public void terminated() {
                try {
                    outer.terminated();
                } catch (Throwable ignored) {}
                next.terminated();
            }
        };
    }

    /**
     * An executor service listener which takes no action.
     */
    ExecutorServiceListener EMPTY = new ExecutorServiceListener() {};
}
