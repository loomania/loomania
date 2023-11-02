package io.github.loomania;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class LoomaniaImpl {

    private static final boolean ok;
    private static final MethodHandle currentCarrierThread;
    private static final MethodHandle virtualThreadFactory;
    private static final MethodHandle threadStartWithContainer;

    static {
        boolean isOk = false;
        MethodHandle ct = null;
        MethodHandle vtf = null;
        MethodHandle tswc = null;
        try {
            MethodHandles.Lookup thr = MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
            ct = thr.findStatic(Thread.class, "currentCarrierThread", MethodType.methodType(Thread.class));
            Class<?> vtbClass = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder", false, null);
            vtf = thr.findConstructor(vtbClass, MethodType.methodType(void.class, Executor.class));
            // create efficient transformer
            vtf = vtf.asType(MethodType.methodType(Thread.Builder.OfVirtual.class, Executor.class));
            //void start(jdk.internal.vm.ThreadContainer container)
            tswc = thr.findVirtual(Thread.class, "start", MethodType.methodType(void.class, jdk.internal.vm.ThreadContainer.class));
            isOk = true;
        } catch (Exception | Error e) {
            // no good
            System.err.println("Failed to initialize Loomania (" + Nope.nopeMsg() + "): " + e);
        }
        ok = isOk;
        currentCarrierThread = ct;
        virtualThreadFactory = vtf;
        threadStartWithContainer = tswc;
    }

    static boolean isInstalled() {
        return ok;
    }

    static Thread currentCarrierThread() {
        if (! ok) throw Nope.nope();
        try {
            Thread currentThread = currentThread();
            return currentThread.isVirtual() ? (Thread) currentCarrierThread.invokeExact() : currentThread;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    static JdkVirtualThreadExecutorBuilder newJdkVirtualThreadExecutorBuilder() {
        if (! ok) throw Nope.nope();
        return new JdkVirtualThreadExecutorBuilder();
    }

    static ExecutorService buildVirtualThreadFactory(JdkVirtualThreadExecutorBuilder builder) {
        String name = builder.getName();
        int corePoolSize = max(0, builder.getCorePoolSize());
        int maximumPoolSize = min(JdkVirtualThreadExecutorBuilder.DEFAULT_MAX, min(corePoolSize, builder.getMaximumPoolSize()));
        int parallelism = min(maximumPoolSize, max(0, builder.getParallelism()));
        int minimumRunnable = min(JdkVirtualThreadExecutorBuilder.DEFAULT_MAX, max(0, builder.getMinimumRunnable()));
        Duration keepAliveTime = builder.getKeepAliveTime();
        long keepAliveMillis = keepAliveTime.toMillis();
        ForkJoinPool fjp = new ForkJoinPool(
            parallelism,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true,
            corePoolSize,
            maximumPoolSize,
            minimumRunnable,
            pool -> true,
            keepAliveMillis,
            TimeUnit.MILLISECONDS
        );
        return newVirtualThreadExecutor(builder.getCarrier(), fjp, name, ExecutorServiceListener.EMPTY);
    }

    static ExecutorService newEventLoopExecutorService(ScopedValue_Temporary.Carrier carrier, EventLoop eventLoop, ExecutorServiceListener listener) {
        Objects.requireNonNull(eventLoop, "eventLoop");
        Objects.requireNonNull(listener, "listener");
        EventLoopExecutorService eventLoopExecutor = new EventLoopExecutorService(eventLoop);
        VirtualThreadExecutorService virtualThreadExecutor = (VirtualThreadExecutorService) newVirtualThreadExecutor(carrier, eventLoopExecutor, "event loop", listener);
        eventLoopExecutor.setVirtualThreadExecutor(virtualThreadExecutor);
        return virtualThreadExecutor;
    }

    static ExecutorService newVirtualThreadExecutor(ScopedValue_Temporary.Carrier carrier, ExecutorService delegate, String name, ExecutorServiceListener listener) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(listener, "listener");
        final ThreadFactory factory;
        Thread.Builder.OfVirtual ov = newVirtualThreadFactory(delegate);
        ov.name(name);
        factory = ov.factory();
        return new VirtualThreadExecutorService(carrier, factory, delegate, listener);
    }

    private static Thread.Builder.OfVirtual newVirtualThreadFactory(Executor executor) {
        try {
            return (Thread.Builder.OfVirtual) virtualThreadFactory.invokeExact(executor);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    private LoomaniaImpl() {
    }

    private static void startThreadWithContainer(final Thread thread, final jdk.internal.vm.ThreadContainer threadContainer) {
        try {
            threadStartWithContainer.invokeExact(thread, threadContainer);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }

    /**
     * An "outer" executor service which can handle shutdown cleanly by waiting for all virtual threads to exit
     * and then shutting down a delegate service.
     * This class implements the thread container needed to track running virtual threads.
     */
    static class VirtualThreadExecutorService extends AbstractExecutorService {
        private static final VarHandle stateHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "state", VarHandle.class, VirtualThreadExecutorService.class, long.class);

        private static final long STATE_PRE_TERMINATE_REQUESTED = 1L << 62;
        private static final long STATE_TERMINATE_REQUESTED = 1L << 63;
        private static final long STATE_NTHREADS_MASK = 0xffff_ffffL;

        final ThreadFactory virtualThreadFactory;
        final ExecutorService delegateService;
        final ExecutorServiceListener listener;
        final ScopedValue_Temporary.Carrier carrier;

        @SuppressWarnings("unused") // stateHandle
        private volatile long state;

        private final jdk.internal.vm.ThreadContainer threadContainer = new jdk.internal.vm.ThreadContainer(false) {
            public long threadCount() {
                return state & STATE_NTHREADS_MASK;
            }

            public void onStart(final Thread thread) {
                if (! tryStartThread()) {
                    throw new RejectedExecutionException("Already shut down");
                }
            }

            public void onExit(final Thread thread) {
                exitThread();
            }

            public Stream<Thread> threads() {
                // we refuse to cooperate with this tyranny
                return Stream.of();
            }
        };

        VirtualThreadExecutorService(final ScopedValue_Temporary.Carrier carrier, final ThreadFactory virtualThreadFactory, final ExecutorService delegateService, final ExecutorServiceListener listener) {
            this.carrier = carrier;
            this.virtualThreadFactory = virtualThreadFactory;
            this.delegateService = delegateService;
            this.listener = listener;
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
                witness = compareAndExchangeState(oldState, newState);
                if (witness == oldState) {
                    // success
                    break;
                }
                // missed the CAX, try again
                oldState = witness;
            }
            if ((oldState & STATE_NTHREADS_MASK) == 1 && isTerminateRequestedState(oldState)) {
                // the last counted thread is exiting
                emptied();
            }
        }

        public void shutdown() {
            long oldState = state;
            if (isPreTerminateRequestedState(oldState)) {
                return;
            }
            long newState;
            long witness;
            for (;;) {
                newState = oldState | STATE_PRE_TERMINATE_REQUESTED;
                witness = compareAndExchangeState(oldState, newState);
                if (witness == oldState) {
                    // shutdown has been initiated
                    break;
                }
                oldState = witness;
            }
            // now call pre-terminate task
            safeRun(listener::shutdownRequested);
            // now, update state for termination request
            // assert witness == oldState
            for (;;) {
                newState = oldState | STATE_TERMINATE_REQUESTED;
                witness = compareAndExchangeState(oldState, newState);
                if (witness == oldState) {
                    // shutdown has been initiated
                    break;
                }
                oldState = witness;
            }
            safeRun(listener::shutdownInitiated);
            if (threadCountOf(oldState) == 0) {
                // no threads are live so proceed directly to termination
                emptied();
            }
        }

        void emptied() {
            delegateService.execute(delegateService::shutdown);
            safeRun(listener::terminated);
        }

        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        public boolean isShutdown() {
            return isPreTerminateRequestedState(state);
        }

        public boolean isTerminated() {
            return isShutdown() && delegateService.isTerminated();
        }

        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegateService.awaitTermination(timeout, unit);
        }

        public void execute(Runnable command) {
            startThreadWithContainer(virtualThreadFactory.newThread(carrier != null ? () -> carrier.run(command) : command), threadContainer);
        }

        private long compareAndExchangeState(final long oldState, final long newState) {
            return (long) stateHandle.compareAndExchange(this, oldState, newState);
        }

        private static boolean isTerminateRequestedState(final long state) {
            return (state & STATE_TERMINATE_REQUESTED) != 0;
        }

        private static boolean isPreTerminateRequestedState(final long state) {
            return (state & STATE_PRE_TERMINATE_REQUESTED) != 0;
        }

        private static long threadCountOf(final long state) {
            return state & STATE_NTHREADS_MASK;
        }

    }

    static class EventLoopExecutorService extends AbstractExecutorService implements ExecutorServiceListener {
        private final ConcurrentLinkedQueue<Runnable> sharedQueue = new ConcurrentLinkedQueue<>();
        private final ArrayDeque<Runnable> localQueue = new ArrayDeque<>(1024);
        private final CountDownLatch terminationLatch = new CountDownLatch(1);
        // ArrayDeque::add always returns {@code true}, which is very convenient...
        private final Predicate<Runnable> bulkRemover = localQueue::add;
        private final Thread carrierThread;
        private final EventLoop eventLoop;

        // internal carrier-thread-local state
        private VirtualThreadExecutorService virtualThreadExecutor;

        /**
         * The event loop should wait until something becomes ready, or until it is explicitly awoken.
         */
        private static final int CMD_WAIT = 1;
        /**
         * The event loop should wait until something becomes ready, the timeout elapses, or until it is explicitly awoken.
         * (XXX not used yet)
         */
        private static final int CMD_WAIT_TIMED = 2;
        /**
         * The event loop should poll for readiness and then return.
         */
        private static final int CMD_NO_WAIT = 3;

        private static final VarHandle eventLoopCommandHandle = ConstantBootstraps.fieldVarHandle(MethodHandles.lookup(), "eventLoopCommand", VarHandle.class, EventLoopExecutorService.class, int.class);

        @SuppressWarnings("unused") // eventLoopCommandHandle
        private int eventLoopCommand = CMD_WAIT;
        private Thread eventLoopThread;
        private volatile Runnable eventLoopThreadContinuation;
        private volatile boolean continueEventLoop;

        EventLoopExecutorService(EventLoop eventLoop) {
            carrierThread = new Thread(this::carrierThreadBody, "Event loop thread");
            carrierThread.setDaemon(true);
            carrierThread.start();
            this.eventLoop = eventLoop;
        }

        public void shutdown() {
            // we can safely assume that all virtual threads must be gone, and that this is called exactly once from the carrier
            terminationLatch.countDown();
        }

        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        public boolean isShutdown() {
            throw new UnsupportedOperationException();
        }

        public boolean isTerminated() {
            return terminationLatch.getCount() == 0;
        }

        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            return terminationLatch.await(timeout, unit);
        }

        public void execute(final Runnable command) {
            if (eventLoopThreadContinuation == null) {
                eventLoopThreadContinuation = command;
                continueEventLoop = true;
                // just in case the carrier thread already parked, wake it up
                LockSupport.unpark(carrierThread);
            } else if (currentCarrierThread() == carrierThread) {
                // the event loop cannot possibly be waiting for events at this exact moment
                if (eventLoopThreadContinuation == command) {
                    // takes priority
                    continueEventLoop = true;
                } else {
                    localQueue.add(command);
                }
            } else {
                sharedQueue.add(command);
                try {
                    // wake up the event loop in case it happens to be waiting for events
                    eventLoop.wakeup();
                } catch (Throwable ignored) {}
                // the carrier might be parked if all of its threads are asleep
                LockSupport.unpark(carrierThread);
                // the event loop itself may be parked in CMD_WAIT* but that's OK because the carrier thread will proceed
            }
        }

        void setVirtualThreadExecutor(final VirtualThreadExecutorService virtualThreadExecutor) {
            this.virtualThreadExecutor = virtualThreadExecutor;
            // capture the event loop thread before we do anything else
            eventLoopThread = virtualThreadExecutor.virtualThreadFactory.newThread(this::eventLoopBody);
            VarHandle.fullFence();
            // this indirectly calls {@code #execute} with the event loop's continuation, before any other call is possible
            // also, note that we are starting this thread outside of the container so that it does not stop the outer executor from terminating
            eventLoopThread.start();
        }

        /**
         * The actual body of the carrier thread task.
         */
        private void carrierThreadBody() {
            try {
                for (;;) {
                    // drain shared queue
                    sharedQueue.removeIf(bulkRemover);
                    // run next task
                    if (continueEventLoop) {
                        continueEventLoop = false;
                        eventLoopCommand = CMD_NO_WAIT;
                        safeRun(eventLoopThreadContinuation);
                    }
                    // try to run one more task before continuing the event loop again
                    if (safeRun(localQueue.poll()) == null) {
                        // we did nothing; have to park again though
                        eventLoopCommand = CMD_WAIT;
                        // don't explicitly unpark the event loop thread because it's either already waiting or it's doing something else
                        // wait for a new task to come in on the shared queue
                        LockSupport.park();
                    } else {
                        eventLoopCommand = CMD_NO_WAIT;
                    }
                }
            } finally {
                // this last bit is called from outside the virtual thread universe!
                eventLoop.terminationComplete();
            }
        }

        /**
         * The body of the virtual thread which talks to the event loop instance.
         */
        private void eventLoopBody() {
            eventLoopThread = currentThread();
            VarHandle.fullFence();
            // loop infinitely; note that when the outer carrier thread exits, this thread will eventually just evaporate
            for (;;) {
                // this has to be atomic to avoid missing commands
                // todo: a weaker mode?
                int cmd = (int) eventLoopCommandHandle.getAndSet(this, CMD_NO_WAIT);
                try {
                    switch (cmd) {
                        case CMD_WAIT -> eventLoop.unparkReadyThreadsOrWait();
                        case CMD_NO_WAIT -> eventLoop.unparkReadyThreads();
                        case CMD_WAIT_TIMED -> throw new IllegalStateException("Timed wait not used yet");
                        default -> throw new IllegalStateException();
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * The outer executor service is being requested to terminate.
         */
        public void shutdownRequested() {
            // submit the shutdown thread task to the outer executor before it starts rejecting tasks
            virtualThreadExecutor.execute(eventLoop::requestTermination);
        }

        public void shutdownInitiated() {
            System.nanoTime();
        }

        public void terminated() {
            System.nanoTime();
        }
    }

    private static Runnable safeRun(Runnable r) {
        if (r != null) try {
            r.run();
        } catch (Throwable ignored) {
            // no safe way to handle this
        }
        return r;
    }
}
