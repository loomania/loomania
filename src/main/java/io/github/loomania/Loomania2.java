package io.github.loomania;

/**
 * Access to Loom internals for experimental munging (approach #2).
 * This approach strives for minimality, and to maximize safety.
 */
public final class Loomania2 {

    /**
     * Determine if Loomania is installed.
     *
     * @return {@code true} if installed, {@code false} otherwise
     */
    public static boolean isInstalled() {
        return false;
    }

    /**
     * Cause the current thread to become a carrier for virtual threads.
     * This method will not return until the created executor service is terminated.
     * The given scheduler is used to schedule ready tasks.
     * The calling thread must not be virtual.
     *
     * @param scheduler the event loop scheduler (must not be {@code null})
     * @throws UnsupportedOperationException if Loomania is not installed
     * @throws IllegalArgumentException if the current thread is virtual
     * @throws NullPointerException if the {@code scheduler} argument is {@code null}
     */
    public static void enterVirtuality(EventLoopScheduler scheduler) throws UnsupportedOperationException,
        IllegalArgumentException,
        NullPointerException {
        throw Nope.nope();
    }

    private Loomania2() {}
}
