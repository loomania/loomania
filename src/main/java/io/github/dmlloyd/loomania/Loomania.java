package io.github.dmlloyd.loomania;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Access to Loom internals for experimental munging.
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

    /**
     * Run the given task with the virtual thread pinned to the carrier.
     * This prevents any form of preemption during the execution of {@code task}.
     *
     * @param arg1 the first argument to pass to the task
     * @param arg2 the second argument to pass to the task
     * @param task the task to run while pinned (must not be {@code null})
     * @return the result of the task
     * @param <T> the type of the first argument
     * @param <U> the type of the second argument
     * @param <R> the return type
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static <T, U, R> R doPinned(T arg1, U arg2, BiFunction<T, U, R> task) {
        throw Nope.nope();
    }

    /**
     * Run the given task with the virtual thread pinned to the carrier.
     * This prevents any form of preemption during the execution of {@code task}.
     *
     * @param arg the argument to pass to the task
     * @param task the task to run while pinned (must not be {@code null})
     * @return the result of the task
     * @param <T> the type of the argument
     * @param <R> the return type
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static <T, R> R doPinned(T arg, Function<T, R> task) {
        throw Nope.nope();
    }

    /**
     * Run the given task with the virtual thread pinned to the carrier.
     * This prevents any form of preemption during the execution of {@code task}.
     *
     * @param task the task to run while pinned (must not be {@code null})
     * @return the result of the task
     * @param <R> the return type
     * @throws UnsupportedOperationException if Loomania is not installed
     */
    public static <R> R doPinned(Supplier<R> task) {
        throw Nope.nope();
    }

    private Loomania() {}
}
