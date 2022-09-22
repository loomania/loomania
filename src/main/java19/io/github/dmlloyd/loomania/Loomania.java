package io.github.dmlloyd.loomania;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class Loomania {

    private static final boolean ok;
    private static final MethodHandle currentCarrierThread;
    private static final MethodHandle virtualThreadFactory;

    static {
        boolean isOk = false;
        MethodHandle ct = null;
        MethodHandle vtf = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
            ct = lookup.findStatic(Thread.class, "currentCarrierThread", MethodType.methodType(Thread.class));
            Class<?> vtbClass = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder", false, null);
            vtf = lookup.findConstructor(vtbClass, MethodType.methodType(void.class, Executor.class));
            // create efficient transformer
            vtf = vtf.asType(MethodType.methodType(Thread.Builder.OfVirtual.class, Executor.class));
            isOk = true;
        } catch (Exception | Error e) {
            // no good
            System.err.println("Failed to initialize Loomania (make sure you have `--enable-preview` and `--add-opens=java.base/java.lang=ALL-UNNAMED` on Java 19): " + e);
        }
        ok = isOk;
        currentCarrierThread = ct;
        virtualThreadFactory = vtf;
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
        if (! ok) throw Nope.nope();
        @SuppressWarnings("resource")
        Runner runner = new Runner();
        runner.threadFactory.newThread(() -> mainThreadRunner.accept(runner)).start();
        runner.run();
    }

    private Loomania() {}

    static final class Runner implements VirtualThreadManager {

        // this basic impl prioritizes local tasks; non-local tasks will face higher latency.
        private final ArrayDeque<Runnable> sharedQueue = new ArrayDeque<>(256);
        private final ArrayDeque<Runnable> localQueue = new ArrayDeque<>(1024);
        private final Thread thread;
        private final ThreadFactory threadFactory;
        private final Executor internalExecutor = this::executeInternal;
        private volatile boolean stop;

        Runner() {
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
                LockSupport.unpark(thread);
            }
        }
    }
}
