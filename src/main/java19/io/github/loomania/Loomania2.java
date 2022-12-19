package io.github.loomania;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class Loomania2 {

    private static final boolean ok;
    private static final MethodHandle currentCarrierThread;
    private static final MethodHandle virtualThreadFactory;

    static {
        boolean isOk = false;
        MethodHandle ct = null;
        MethodHandle vtf = null;
        try {
            MethodHandles.Lookup thr = MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
            ct = thr.findStatic(Thread.class, "currentCarrierThread", MethodType.methodType(Thread.class));
            Class<?> vtbClass = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder", false, null);
            vtf = thr.findConstructor(vtbClass, MethodType.methodType(void.class, Executor.class));
            // create efficient transformer
            vtf = vtf.asType(MethodType.methodType(Thread.Builder.OfVirtual.class, Executor.class));
            isOk = true;
        } catch (Exception | Error e) {
            // no good
            System.err.println("Failed to initialize Loomania (" + Nope.nopeMsg() + "): " + e);
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

    public static void enterVirtuality(EventLoopScheduler scheduler) {
        if (! ok) throw Nope.nope();
        //noinspection resource
        new Runner(scheduler).run();
    }

    private Loomania2() {
    }

    static final class Runner extends AbstractExecutorService implements VirtualThreadExecutorService {
        private static final VarHandle stateHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "state", VarHandle.class, Runner.class, long.class);

        private static final long STATE_TERMINATE_REQUESTED = 1L << 62;
        private static final long STATE_TERMINATED = 1L << 63;
        private static final long STATE_NTHREADS_MASK = 0xffff_ffffL;

        private final ConcurrentLinkedQueue<Runnable> sharedQueue = new ConcurrentLinkedQueue<>();
        private final ArrayDeque<Runnable> localQueue = new ArrayDeque<>(1024);
        private final Thread thread;
        private final ThreadFactory internalThreadFactory;
        private final Executor internalExecutor = this::executeInternal;
        private final EventLoopScheduler scheduler;
        // ArrayDeque::add always returns {@code true}, which is very convenient...
        private final Predicate<Runnable> bulkRemover = localQueue::add;
        private final CountDownLatch terminationLatch = new CountDownLatch(1);
        @SuppressWarnings("unused") // stateHandle
        private volatile long state;

        Runner(final EventLoopScheduler scheduler) {
            this.scheduler = scheduler;
            thread = Thread.currentThread();
            if (isVirtual(thread)) {
                throw new IllegalArgumentException("Carrier thread cannot be virtual");
            }

            try {
                // todo: we want a constructor which accepts a scheduled executor of some sort to handle timed park, but this works for now
                Thread.Builder.OfVirtual ov = (Thread.Builder.OfVirtual) virtualThreadFactory.invokeExact(internalExecutor);
                // todo: give the user access to the thread builder, somehow
                ov.name("Loomania Virtual Thread");
                this.internalThreadFactory = ov.factory();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        public Thread newThread(final Runnable runnable) {
            // todo: the call to tryStartThread should be in Thread.start() (subclass Thread)
            if (! isTerminateRequestedState(state) && tryStartThread()) try {
                // todo: do this in a way that does not create yet another allocation (subclass Thread)
                return internalThreadFactory.newThread(() -> {
                    try {
                        runnable.run();
                    } finally {
                        exitThread();
                    }
                });
            } catch (Throwable t) {
                // todo: remove this catch block when the call to tryStartThread() is in Thread.start()
                exitThread();
                throw t;
            } else {
                return null;
            }
        }

        private boolean tryStartThread() {
            long oldState, newState, witness;
            oldState = state;
            for (;;) {
                if (isTerminateRequestedState(oldState)) {
                    return false;
                }
                if ((oldState & STATE_NTHREADS_MASK) == STATE_NTHREADS_MASK) {
                    // too many threads! not likely
                    return false;
                }
                newState = oldState + 1;
                witness = compareAndExchangeState(oldState, newState);
                if (witness == oldState) {
                    // success
                    return true;
                }
                // missed the CAX, try again
                oldState = witness;
            }
        }

        private void exitThread() {
            long oldState, newState, witness;
            oldState = state;
            for (;;) {
                newState = oldState - 1;
                if (oldState == STATE_TERMINATE_REQUESTED + 1) {
                    // the last counted thread is exiting
                    newState |= STATE_TERMINATED;
                }
                witness = compareAndExchangeState(oldState, newState);
                if (witness == oldState) {
                    // success
                    break;
                }
                // missed the CAX, try again
                oldState = witness;
            }
            if (isTerminatedState(newState)) {
                try {
                    scheduler.terminate();
                } catch (Throwable ignored) {
                }
                terminationLatch.countDown();
            }
        }

        public void shutdown() {
            if (isTerminateRequestedState(state)) {
                // short-circuit
                return;
            }
            // try to shut down
            try {
                execute(this::shutdownInternal);
            } catch (RejectedExecutionException ignored) {
            }
        }

        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        public boolean isShutdown() {
            return isTerminateRequestedState(state);
        }

        public boolean isTerminated() {
            return isTerminatedState(state);
        }

        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            // short-circuit
            if (isTerminatedState(state)) {
                return true;
            }
            terminationLatch.await(timeout, unit);
            return isTerminatedState(state);
        }

        private long getAndBitwiseOrState(long orValue) {
            return (long) stateHandle.getAndBitwiseOr(this, orValue);
        }

        private long compareAndExchangeState(final long oldState, final long newState) {
            return (long) stateHandle.compareAndExchange(this, oldState, newState);
        }

        private static boolean isTerminateRequestedState(final long state) {
            return (state & STATE_TERMINATE_REQUESTED) != 0;
        }

        private static boolean isTerminatedState(final long state) {
            return (state & STATE_TERMINATED) != 0;
        }

        private void shutdownInternal() {
            final long observedState = getAndBitwiseOrState(STATE_TERMINATE_REQUESTED);
            if ((observedState & STATE_TERMINATE_REQUESTED) == 0) {
                // we are the official shutdown thread
                try {
                    scheduler.shutdown();
                } catch (Throwable ignored) {}

            }
        }

        /**
         * One year's worth of nanoseconds is a decent amount of leeway
         */
        private static final long NANOS_PER_YEAR = 365L * 24 * 60 * 60 * 1_000_000_000;
        private static final long MAX_DEADLINE = Long.MAX_VALUE - NANOS_PER_YEAR;

        void run() {
            /*
               We continually run tasks from the queue.
               If the queue is empty and there are no scheduled tasks, tell the scheduler to park indefinitely.
               If the queue is empty and there are scheduled tasks, tell the scheduler to park until the deadline.
               If the queue is non-empty and the scheduler deadline has not elapsed, run tasks.
               If the queue is non-empty and the scheduler deadline has elapsed, tell the scheduler to unpark ready tasks immediately.

               If at any time we would call into the scheduler, drain the shared queue.
             */

            Runnable task;
            // this is the max nanos which may elapse without talking to the scheduler
            long remaining = Math.max(-1L, Math.min(scheduler.initialize(this), MAX_DEADLINE));
            outer: for (;;) {
                if (remaining == -1) for (;;) {
                    // we do not need to talk to the scheduler until we are out of tasks to execute

                    // run one task
                    task = localQueue.poll();
                    if (task != null) {
                        safeRun(task);
                        continue;
                    }

                    // todo: we could be more (or less) aggressive about draining the shared queue
                    sharedQueue.removeIf(bulkRemover);

                    if (localQueue.isEmpty()) {

                        // try the scheduler for more tasks
                        remaining = Math.max(-1L, Math.min(scheduler.unparkReadyThreads(), MAX_DEADLINE));
                        continue outer;
                    }
                } else for (;;) {
                    // we need to talk to the scheduler when the deadline elapses

                    // run one task
                    task = localQueue.poll();
                    if (task != null) {
                        safeRun(task);
                        continue;
                    }

                    sharedQueue.removeIf(bulkRemover);
                }
            }
        }

        private void safeRun(final Runnable task) {
            try {
                task.run();
            } catch (Throwable t) {
                // this is a tad absurd, but we can't safely print from this thread without deadlock risk
                execute(() -> {
                    System.err.print("Uncaught problem in virtual thread scheduler task: ");
                    t.printStackTrace(System.err);
                });
            }
        }

        /**
         * The executor that the JDK sees.
         *
         * @param task the task (continuation) to execute (must not be {@code null})
         */
        private void executeInternal(Runnable task) {
            /* normally this method won't be called after termination, however it is possible for our shutdown thread
               to need scheduling after termination completes, thus we do not want to assert here */
            if (currentCarrierThread() == thread) {
                localQueue.add(task);
            } else {
                sharedQueue.add(task);
                try {
                    scheduler.wakeup();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
