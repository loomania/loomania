package io.github.loomania;

import static java.lang.invoke.MethodHandles.lookup;

import java.io.IOException;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.VarHandle;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A safe, non-pinning selector implementation which may be used from a single-carrier-thread VT executor.
 */
public final class NonPinningSelector extends Selector {
    private static final VarHandle wakeupHandle = ConstantBootstraps.fieldVarHandle(lookup(), "wakeup", VarHandle.class, NonPinningSelector.class, boolean.class);

    /**
     * The executor service to execute in our virtual thread environment.
     */
    private final ExecutorService executorService;
    /**
     * The underlying selector.
     */
    private final Selector selector;
    /**
     * Our ready-set.
     */
    private final ArrayDeque<Key> readySet = new ArrayDeque<>(256);
    /**
     * Our ready-lock.
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Our wait condition.
     */
    private final Condition cond = lock.newCondition();
    /**
     * Our open-state.
     */
    private volatile boolean open = true;
    /**
     * The wakeup permit.
     */
    @SuppressWarnings({ "unused", "FieldMayBeFinal" })
    private volatile boolean wakeup = false;
    /**
     * Our selector view which is used for channel registration.
     * We need an {@link AbstractSelector} to support registration, but that class also defines {@code final}
     * methods that we need to be able to override, so we cannot extend it directly.
     */
    private final AbstractSelector internalSelectorView;
    private final EventLoop eventLoop = new EventLoop() {
        public void unparkReadyThreadsOrWaitNanos(final long maxNanos) {
            try {
                selector.select(this::addAll, (maxNanos + 999_999) / 1_000_000);
            } catch (IOException e) {
                // todo: log
            }
            LockSupport.parkNanos(500_000L);
        }

        public void unparkReadyThreadsOrWait() {
            try {
                selector.select(this::addAll);
            } catch (IOException e) {
                // todo: log
            }
            LockSupport.parkNanos(500_000L);
        }

        public void unparkReadyThreads() {
            try {
                selector.selectNow(this::addAll);
            } catch (IOException e) {
                // todo: log
            }
            LockSupport.parkNanos(500_000L);
        }

        public void wakeup() {
            NonPinningSelector.this.wakeup();
        }

        public void requestTermination() {
            // no operation
        }

        public void terminationComplete() {
            try {
                selector.close();
            } catch (IOException ignored) {
            }
        }

        private void addAll(SelectionKey selectionKey) {
            readySet.add((Key) selectionKey.attachment());
        }
    };

    NonPinningSelector(final Selector selector, final ExecutorServiceListener listener) {
        this.selector = selector;
        internalSelectorView = new AbstractSelector(selector.provider()) {
            protected void implCloseSelector() {
                // no op
            }

            protected SelectionKey register(final AbstractSelectableChannel ch, final int ops, final Object att) {
                try {
                    SelectionKey nestedKey = ch.register(selector, ops);
                    Key key = new Key(ch, nestedKey);
                    nestedKey.attach(key);
                    return key;
                } catch (ClosedChannelException e) {
                    return new DeadKey(ch);
                }
            }

            public Set<SelectionKey> keys() {
                return NonPinningSelector.this.keys();
            }

            public Set<SelectionKey> selectedKeys() {
                return NonPinningSelector.this.selectedKeys();
            }

            public int selectNow() throws IOException {
                return NonPinningSelector.this.selectNow();
            }

            public int select(final long timeout) throws IOException {
                return NonPinningSelector.this.select(timeout);
            }

            public int select() throws IOException {
                return NonPinningSelector.this.select();
            }

            public int select(final Consumer<SelectionKey> action, final long timeout) throws IOException {
                return NonPinningSelector.this.select(action, timeout);
            }

            public int select(final Consumer<SelectionKey> action) throws IOException {
                return NonPinningSelector.this.select(action);
            }

            public int selectNow(final Consumer<SelectionKey> action) throws IOException {
                return NonPinningSelector.this.selectNow(action);
            }

            public Selector wakeup() {
                return NonPinningSelector.this.wakeup();
            }
        };
        executorService = Loomania.newEventLoopExecutorService(null, eventLoop, listener);
    }

    public static NonPinningSelector newNonPinningSelector(ExecutorServiceListener listener) throws IOException {
        return new NonPinningSelector(Selector.open(), listener);
    }

    public ExecutorService executorService() {
        return executorService;
    }

    public boolean isOpen() {
        return open;
    }

    public SelectorProvider provider() {
        return selector.provider();
    }

    public Set<SelectionKey> keys() {
        throw new UnsupportedOperationException("key sets");
    }

    public Set<SelectionKey> selectedKeys() {
        throw new UnsupportedOperationException("key sets");
    }

    public int selectNow() throws IOException {
        lock.lock();
        try {
            return readySet.size();
        } finally {
            lock.unlock();
        }
    }

    private static final long MAX_NANOS_IN_LONG_MILLIS = Long.MAX_VALUE / 1_000_000L - 1;

    public int select(long timeout) throws IOException {
        if (timeout == 0) {
            return select();
        } else if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout");
        } else if (timeout > MAX_NANOS_IN_LONG_MILLIS) {
            timeout = MAX_NANOS_IN_LONG_MILLIS;
        }
        timeout *= 1_000_000L;
        lock.lock();
        try {
            if (wakeupHandle.compareAndSet(this, true, false)) {
                return readySet.size();
            }
            long start = System.nanoTime();
            while (readySet.isEmpty()) {
                try {
                    if (! cond.await(timeout, TimeUnit.NANOSECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (wakeupHandle.compareAndSet(this, true, false)) {
                    break;
                }
                long elapsed = -start + (start = System.nanoTime());
                if (elapsed > 0) {
                    timeout -= elapsed;
                }
            }
            return readySet.size();
        } finally {
            lock.unlock();
        }
    }

    public int select() throws IOException {
        lock.lock();
        try {
            checkOpen();
            if (wakeupHandle.compareAndSet(this, true, false)) {
                return readySet.size();
            }
            while (readySet.isEmpty()) {
                try {
                    cond.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                checkOpen();
                if (wakeupHandle.compareAndSet(this, true, false)) {
                    return readySet.size();
                }
            }
            return readySet.size();
        } finally {
            lock.unlock();
        }
    }

    public int select(final Consumer<SelectionKey> action, final long timeout) throws IOException {
        lock.lock();
        try {
            select(timeout);
            int count = 0;
            while (! readySet.isEmpty()) {
                action.accept(readySet.removeFirst());
                count ++;
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int select(final Consumer<SelectionKey> action) throws IOException {
        lock.lock();
        try {
            select();
            int count = 0;
            while (! readySet.isEmpty()) {
                action.accept(readySet.removeFirst());
                count ++;
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int selectNow(final Consumer<SelectionKey> action) throws IOException {
        lock.lock();
        try {
            int count = 0;
            while (! readySet.isEmpty()) {
                action.accept(readySet.removeFirst());
                count ++;
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    void acceptKey(SelectionKey key) {
        assert lock.isHeldByCurrentThread();
        Key ourKey = (Key) key.attachment();
        ourKey.copyReadyOps();
        key.interestOpsAnd(~ ourKey.readyOps);
        readySet.add(ourKey);
    }

    public Selector wakeup() {
        if (wakeupHandle.compareAndSet(this, false, true)) {
            lock.lock();
            try {
                cond.signalAll();
            } finally {
                lock.unlock();
            }
        }
        return this;
    }

    public SelectionKey register(SelectableChannel channel, int ops, Object att) throws ClosedChannelException {
        return channel.register(internalSelectorView, ops, att);
    }

    private void checkOpen() {
        if (! open) {
            throw new ClosedSelectorException();
        }
    }

    public void close() throws IOException {
        if (open) {
            lock.lock();
            try {
                if (open) {
                    open = false;
                    // todo - cancel upstream keys...?
                    readySet.clear();
                    cond.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private final class Key extends SelectionKey {
        private final SelectableChannel channel;
        private final SelectionKey internal;
        private int readyOps;

        private Key(final SelectableChannel channel, final SelectionKey internal) {
            this.channel = channel;
            this.internal = internal;
        }

        public SelectableChannel channel() {
            return channel;
        }

        public Selector selector() {
            return internalSelectorView;
        }

        public boolean isValid() {
            return internal.isValid();
        }

        public void cancel() {
            internal.cancel();
        }

        public int interestOps() {
            return internal.interestOps();
        }

        public Key interestOps(final int ops) {
            internal.interestOps(ops);
            return this;
        }

        void copyReadyOps() {
            this.readyOps = internal.readyOps();
        }

        public int readyOps() {
            return readyOps;
        }

        public int interestOpsOr(final int ops) {
            return internal.interestOpsOr(ops);
        }

        public int interestOpsAnd(final int ops) {
            return internal.interestOpsAnd(ops);
        }
    }

    private final class DeadKey extends SelectionKey {
        private final SelectableChannel channel;

        private DeadKey(final SelectableChannel channel) {
            this.channel = channel;
        }

        public SelectableChannel channel() {
            return channel;
        }

        public Selector selector() {
            return selector;
        }

        public boolean isValid() {
            return false;
        }

        public void cancel() {
        }

        public int interestOps() {
            return 0;
        }

        public SelectionKey interestOps(final int ops) {
            return this;
        }

        public int readyOps() {
            return 0;
        }
    }
}
