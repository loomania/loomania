package io.github.loomania;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

/**
 * An NIO-based test which uses pipes to talk back and forth.
 */
public final class SimplexNioTest {
    // wake up every 100ms
    private static final long WAIT_NANOS = 100_000_000L;

    @Test
    public void testSimplex() throws IOException {
        Pipe pipe = Pipe.open();
        final byte[] blob = Files.readAllBytes(Path.of(System.getProperty("user.dir")).resolve("src/test/java/io/github/loomania/SimplexNioTest.java"));
        try (Pipe.SourceChannel source = pipe.source()) {
            try (Pipe.SinkChannel sink = pipe.sink()) {
                source.configureBlocking(false);
                sink.configureBlocking(false);

                Loomania2.enterVirtuality(new EventLoopScheduler() {
                    final Selector selector = Selector.open();

                    public long initialize(final VirtualThreadExecutorService executorService) {
                        executorService.execute(() -> {
                            final Pipe.SourceChannel pipeSource = source;
                            try {
                                // a terrible reader
                                final SelectionKey key = pipeSource.register(selector, SelectionKey.OP_READ, Thread.currentThread());
                                ByteBuffer worstBuf = ByteBuffer.allocateDirect(1);
                                int res;
                                for (int idx = 0; idx < blob.length; idx ++) {
                                    key.interestOps(0);
                                    randomBlock();
                                    key.interestOps(SelectionKey.OP_READ);
                                    while ((res = pipeSource.read(worstBuf)) == 0) {
                                        LockSupport.park();
                                    }
                                    if (res == -1) {
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
                                while ((res = pipeSource.read(worstBuf)) == 0) {
                                    LockSupport.park();
                                }
                                if (res == -1) {
                                    System.out.println("Done!");
                                    executorService.shutdown();
                                    return;
                                }
                            } catch (IOException e) {
                                e.printStackTrace(System.err);
                            }
                        });
                        executorService.execute(() -> {
                            final Pipe.SinkChannel pipeSink = sink;
                            try {
                                // a terrible writer
                                final SelectionKey key = pipeSink.register(selector, SelectionKey.OP_WRITE, Thread.currentThread());
                                ByteBuffer worstBuf = ByteBuffer.allocateDirect(1);
                                for (int idx = 0; idx < blob.length; idx ++) {
                                    key.interestOps(0);
                                    randomBlock();
                                    key.interestOps(SelectionKey.OP_WRITE);
                                    worstBuf.put(0, blob[idx]);
                                    while (pipeSink.write(worstBuf) == 0) {
                                        LockSupport.park();
                                    }
                                    worstBuf.clear();
                                }
                                // we have to manually unpark the reader; a framework would normally do this for us
                                Thread readThread = (Thread) source.keyFor(selector).attachment();
                                pipeSink.close();
                                LockSupport.unpark(readThread);
                            } catch (IOException e) {
                                e.printStackTrace(System.err);
                            }
                        });
                        return WAIT_NANOS;
                    }

                    public long unparkReadyThreadsOrWaitNanos(final long maxNanos) {
                        try {
                            final long millis = maxNanos / 1_000_000L;
                            if (millis == 0) {
                                selector.selectNow(this::consumeKey);
                            } else {
                                selector.select(this::consumeKey, millis);
                            }
                        } catch (IOException ignored) {
                            // ???
                        }
                        return WAIT_NANOS;
                    }

                    public long unparkReadyThreadsOrWait() {
                        try {
                            selector.select(this::consumeKey);
                        } catch (IOException ignored) {
                            // ???
                        }
                        return WAIT_NANOS;
                    }

                    public long unparkReadyThreads() {
                        try {
                            selector.selectNow(this::consumeKey);
                        } catch (IOException ignored) {
                            // ???
                        }
                        return WAIT_NANOS;
                    }

                    private void consumeKey(final SelectionKey selectionKey) {
                        Thread thread = (Thread) selectionKey.attachment();
                        LockSupport.unpark(thread);
                    }

                    public void wakeup() {
                        selector.wakeup();
                    }

                    public void shutdown() {
                        // no operation
                    }

                    public void terminate() {
                        try {
                            selector.close();
                        } catch (IOException ignored) {
                            // ???
                        }
                    }
                });

                System.out.println("Exited!");
            }
        }
    }

    private static void randomBlock() {
        LockSupport.parkNanos(ThreadLocalRandom.current().nextInt(500_000, 1_000_000));
    }
}
