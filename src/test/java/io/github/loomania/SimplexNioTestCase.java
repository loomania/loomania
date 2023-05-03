package io.github.loomania;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

/**
 * An NIO-based test which uses pipes to talk back and forth.
 */
public final class SimplexNioTestCase {
    // wake up every 100ms
    private static final long WAIT_NANOS = 10_000_000_000L;

    @Test
    public void testSimplex() throws IOException, InterruptedException {
        Pipe pipe = Pipe.open();
        final byte[] blob = Files.readAllBytes(Path.of(System.getProperty("user.dir")).resolve("src/test/java/" + SimplexNioTestCase.class.getName().replace('.', '/') + ".java"));
        try (Selector selector = Selector.open()) {
            try (Pipe.SourceChannel source = pipe.source()) {
                try (Pipe.SinkChannel sink = pipe.sink()) {
                    source.configureBlocking(false);
                    sink.configureBlocking(false);
                    source.register(selector, SelectionKey.OP_READ);
                    sink.register(selector, SelectionKey.OP_WRITE);
                    CountDownLatch steps = new CountDownLatch(2);
                    try (ExecutorService executorService = Loomania.newEventLoopExecutorService(Thread::new, new EventLoop() {
                        public long unparkReadyThreadsOrWaitNanos(final long maxNanos) {
                            System.out.println("Unpark ready threads, or wait nanos");
                            try {
                                final long millis = maxNanos / 1_000_000L;
                                if (millis == 0) {
                                    selector.selectNow(this::consumeKey);
                                } else {
                                    selector.select(this::consumeKey, millis);
                                }
                            } catch (IOException ignored) {}
                            return WAIT_NANOS;
                        }

                        public long unparkReadyThreadsOrWait() {
                            System.out.println("Unpark ready threads, or wait");
                            try {
                                selector.select(this::consumeKey);
                            } catch (IOException ignored) {}
                            return WAIT_NANOS;
                        }

                        public long unparkReadyThreads() {
                            System.out.println("Unpark ready threads now");
                            try {
                                selector.selectNow(this::consumeKey);
                            } catch (IOException ignored) {}
                            return WAIT_NANOS;
                        }

                        public void wakeup() {
                            selector.wakeup();
                        }

                        public void requestTermination() {
                            System.out.println("Request termination!");
                            // no op
                        }

                        public void terminationComplete() {
                            System.out.println("Termination complete!");
                            try {
                                selector.close();
                            } catch (IOException ignored) {}
                        }

                        private void consumeKey(final SelectionKey selectionKey) {
                            Thread thread = (Thread) selectionKey.attachment();
                            selectionKey.interestOpsAnd(~selectionKey.readyOps());
                            LockSupport.unpark(thread);
                        }
                    }, ExecutorServiceListener.EMPTY)) {
                        executorService.execute(() -> {
                            try {
                                // a terrible reader
                                final SelectionKey key = source.register(selector, SelectionKey.OP_READ, Thread.currentThread());
                                ByteBuffer worstBuf = ByteBuffer.allocateDirect(1);
                                int res;
                                for (int idx = 0; idx < blob.length; idx++) {
                                    while ((res = source.read(worstBuf)) == 0) {
                                        key.interestOps(SelectionKey.OP_READ);
                                        LockSupport.park();
                                    }
                                    key.interestOps(0);
                                    if (res == - 1) {
                                        System.out.println("Done early?!");
                                        executorService.shutdown();
                                        return;
                                    }
                                    if (blob[idx] != worstBuf.get(0)) {
                                        // oops!
                                        System.out.print("❌");
                                        System.out.flush();
                                    } else {
                                        // OK!
                                        System.out.print((char) (worstBuf.get(0) & 0xff));
                                        System.out.flush();
                                    }
                                    worstBuf.clear();
                                }
                                while ((res = source.read(worstBuf)) == 0) {
                                    key.interestOps(SelectionKey.OP_READ);
                                    LockSupport.park();
                                }
                                if (res == - 1) {
                                    System.out.println("Done!");
                                    source.close();
                                    executorService.shutdown();
                                    return;
                                }
                            } catch (IOException e) {
                                e.printStackTrace(System.err);
                            } finally {
                                steps.countDown();
                            }
                        });
                        executorService.execute(() -> {
                            try {
                                // a terrible writer
                                final SelectionKey key = sink.register(selector, SelectionKey.OP_WRITE, Thread.currentThread());
                                ByteBuffer worstBuf = ByteBuffer.allocateDirect(1);
                                for (int idx = 0; idx < blob.length; idx++) {
                                    key.interestOps(0);
                                    randomBlock();
                                    key.interestOps(SelectionKey.OP_WRITE);
                                    worstBuf.put(0, blob[idx]);
                                    while (sink.write(worstBuf) == 0) {
                                        key.interestOps(SelectionKey.OP_WRITE);
                                        LockSupport.park();
                                    }
                                    worstBuf.clear();
                                }
                                // we have to manually unpark the reader; a framework would normally do this for us
                                Thread readThread = (Thread) source.keyFor(selector).attachment();
                                sink.close();
                                LockSupport.unpark(readThread);
                            } catch (IOException e) {
                                e.printStackTrace(System.err);
                            } finally {
                                steps.countDown();
                            }
                        });
                        steps.await();
                    }
                }

                System.out.println("Exited!");
            }
        }
    }

    private static void randomBlock() {
        LockSupport.parkNanos(ThreadLocalRandom.current().nextInt(500_000, 1_000_000));
    }
}
