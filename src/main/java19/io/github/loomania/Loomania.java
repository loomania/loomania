package io.github.loomania;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class Loomania {

    private static final boolean ok;
    private static final MethodHandle currentCarrierThread;
    private static final MethodHandle virtualThreadFactory;
    private static final MethodHandle pin;
    private static final MethodHandle unpin;

    static {
        boolean isOk = false;
        MethodHandle ct = null;
        MethodHandle vtf = null;
        MethodHandle p = null;
        MethodHandle u = null;
        try {
            MethodHandles.Lookup thr = MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
            ct = thr.findStatic(Thread.class, "currentCarrierThread", MethodType.methodType(Thread.class));
            Class<?> vtbClass = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder", false, null);
            vtf = thr.findConstructor(vtbClass, MethodType.methodType(void.class, Executor.class));
            // create efficient transformer
            vtf = vtf.asType(MethodType.methodType(Thread.Builder.OfVirtual.class, Executor.class));
            Class<?> continuationClass = Class.forName("jdk.internal.vm.Continuation", false, null);
            MethodHandles.Lookup jimLookup = MethodHandles.privateLookupIn(continuationClass, MethodHandles.lookup());
            p = jimLookup.findStatic(continuationClass, "pin", MethodType.methodType(void.class));
            u = jimLookup.findStatic(continuationClass, "unpin", MethodType.methodType(void.class));
            isOk = true;
        } catch (Exception | Error e) {
            // no good
            System.err.println("Failed to initialize Loomania (" + Nope.nopeMsg() + "): " + e);
        }
        ok = isOk;
        currentCarrierThread = ct;
        virtualThreadFactory = vtf;
        pin = p;
        unpin = u;
    }

    public static boolean isInstalled() {
        return ok;
    }

    public static boolean isVirtual(Thread thread) {
        if (! ok) throw Nope.nope();
        return thread != null && thread.isVirtual();
    }

    public static Thread currentCarrierThread() {
        if (! ok) throw Nope.nope();
        try {
            Thread currentThread = Thread.currentThread();
            return currentThread.isVirtual() ? (Thread) currentCarrierThread.invokeExact() : currentThread;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    public static void enterVirtuality(Consumer<VirtualThreadManager> mainThreadRunner) {
        enterVirtuality(mainThreadRunner, null);
    }

    public static void enterVirtuality(Consumer<VirtualThreadManager> mainThreadRunner, Runnable unparker) {
        if (! ok) throw Nope.nope();
        @SuppressWarnings("resource")
        Runner runner = new Runner(unparker);
        runner.threadFactory.newThread(() -> mainThreadRunner.accept(runner)).start();
        runner.run();
    }

    public static <T, U, R> R doPinned(T arg1, U arg2, BiFunction<T, U, R> task) {
        if (! ok) throw Nope.nope();
        pin();
        try {
            return task.apply(arg1, arg2);
        } finally {
            unpin();
        }
    }

    public static <T, R> R doPinned(T arg, Function<T, R> task) {
        return doPinned(task, arg, Function::apply);
    }

    public static <R> R doPinned(Supplier<R> task) {
        return doPinned(task, Supplier::get);
    }

    private static void pin() {
        try {
            pin.invokeExact();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    private static void unpin() {
        try {
            unpin.invokeExact();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    private Loomania() {}

    static final class Runner implements VirtualThreadManager {

        // this basic impl prioritizes local tasks; non-local tasks will face higher latency.
        private final ArrayDeque<Runnable> sharedQueue = new ArrayDeque<>(256);
        private final ArrayDeque<Runnable> localQueue = new ArrayDeque<>(1024);
        private final Thread thread;
        private final ThreadFactory threadFactory;
        private final Executor internalExecutor = this::executeInternal;
        private final Runnable unparker;
        private volatile boolean stop;

        Runner(final Runnable unparker) {
            this.unparker = unparker;
            thread = Thread.currentThread();
            if (isVirtual(thread)) {
                throw new IllegalArgumentException("Carrier thread cannot be virtual");
            }

            try {
                Thread.Builder.OfVirtual ov = (Thread.Builder.OfVirtual) virtualThreadFactory.invokeExact(internalExecutor);
                ov.name("Loomania Virtual Thread");
                this.threadFactory = ov.factory();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        public Thread newThread(final Runnable runnable) {
            return threadFactory.newThread(runnable);
        }

        public boolean hasPendingTasks() throws IllegalStateException {
            if (currentCarrierThread() != thread) {
                throw new IllegalStateException("Called from wrong thread");
            }
            if (!localQueue.isEmpty()) {
                return true;
            }
            synchronized (sharedQueue) {
                return ! sharedQueue.isEmpty();
            }
        }

        public LatencyMonitor createLatencyMonitor(final LongConsumer resultListener) {
            // todo: using internal executor might not work so well
            return new LatencyMonitor(internalExecutor, resultListener);
        }

        public void close() {
            // TODO: this is not correct; we need to track the count of virtual threads and exit when it is zero...
            // But for now, we can just burn down the world
            stop();
        }

        void stop() {
            stop = true;
            if (currentCarrierThread() != thread) {
                // todo - no idea if this is right
                LockSupport.unpark(thread);
            }
        }

        void run() {
            ArrayDeque<Runnable> localQueue = this.localQueue;
            ArrayDeque<Runnable> sharedQueue = this.sharedQueue;
            Runnable task;
            for (;;) {
                task = localQueue.poll();
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        // this is a tad absurd, but we can't safely print from this thread without deadlock risk
                        execute(() -> {
                            System.err.print("Uncaught problem in virtual thread scheduler task: ");
                            t.printStackTrace(System.err);
                        });
                    }
                } else {
                    // danger! only certain things can be done in this block
                    synchronized (sharedQueue) {
                        if (localQueue.addAll(sharedQueue)) {
                            sharedQueue.clear();
                            continue;
                        } else {
                            if (stop) {
                                return;
                            }
                        }
                    }
                    LockSupport.park();
                }
            }
        }

        private void executeInternal(Runnable task) {
            if (stop) {
                throw new RejectedExecutionException();
            }
            if (currentCarrierThread() == thread) {
                // task should execute when current thread yields
                localQueue.add(task);
            } else {
                ArrayDeque<Runnable> sharedQueue = this.sharedQueue;
                synchronized (sharedQueue) {
                    sharedQueue.add(task);
                }
                if (unparker != null) {
                    // e.g. wake up selector...
                    unparker.run();
                }
                LockSupport.unpark(thread);
            }
        }
    }
}
