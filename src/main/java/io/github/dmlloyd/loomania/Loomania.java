package io.github.dmlloyd.loomania;

import java.util.function.Consumer;

/**
 * Access to Loom internals for experimental munging. Non-Java 19 version.
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
     * Determine whether the given thread is virtual.
     *
     * @param thread the thread (must not be {@code null})
     * @return {@code true} if the thread is virtual, {@code false} otherwise
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static boolean isVirtual(Thread thread) {
        throw Nope.nope();
    }

    /**
     * Get the carrier thread for the current thread.
     *
     * @return the carrier thread, or the current thread if it is not virtual
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static Thread currentCarrierThread() {
        throw Nope.nope();
    }

    /**
     * Cause the current thread to become a carrier for virtual threads until the virtual thread manager is terminated.
     * The given runner is run within a virtual thread on this carrier.
     *
     * @param mainThreadRunner the main thread runner (must not be {@code null})
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static void enterVirtuality(Consumer<VirtualThreadManager> mainThreadRunner) {
        throw Nope.nope();
    }

    /**
     * Cause the current thread to become a carrier for virtual threads until the virtual thread manager is terminated.
     * The given runner is run within a virtual thread on this carrier.
     *
     * @param mainThreadRunner the main thread runner (must not be {@code null})
     * @param unparker a {@code Runnable} to call when the carrier thread accepts a new task, or {@code null} for none
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static void enterVirtuality(Consumer<VirtualThreadManager> mainThreadRunner, Runnable unparker) {
        throw Nope.nope();
    }

    private Loomania() {}
}
