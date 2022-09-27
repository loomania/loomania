package io.github.dmlloyd.loomania;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

/**
 * Adapted from Gunnar Morling's Loom fairness tests. See <a href="https://morling.dev/blog/loom-and-thread-fairness">his blog</a>
 * for more info.
 */
public class GunnarFairnessTest {
    public static long blackHole;

    public static final boolean PRINT_CS = false;

    public static final boolean FJP = false;

    public static final boolean YIELD = true;
    public static final boolean SLEEP = false;
    public static final boolean PARK_NANOS = false;

    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(64);
        if (FJP) {
            try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
                runLoop(latch, exec);
                latch.await();
            }
        } else {
            Thread thread = new Thread(() -> Loomania.enterVirtuality(exec -> runLoop(latch, exec)));
            thread.setDaemon(true);
            thread.start();
            latch.await();
        }
    }

    private static void runLoop(final CountDownLatch latch, final Executor exec) {
        for (int i = 0; i < 64; i++) {
            final Instant start = Instant.now();
            final int id = i;
            exec.execute(() -> {
                runOne(start, id);
                latch.countDown();
            });
        }
    }

    private static void runOne(final Instant start, int id) {
        long res = 0;
        for (int j = 0; j < 100_000_000; j++) {
            if (j % 1_000_000 == 0) {
                if (PRINT_CS) {
                    System.out.println("yield" + id);
                }
                if (YIELD) {
                    Thread.yield();
                }
                if (SLEEP) {
                    try {
                        Thread.sleep(1L);
                    } catch (InterruptedException ignored) {
                    }
                }
                if (PARK_NANOS) {
                    LockSupport.parkNanos(1);
                }
                if (PRINT_CS) {
                    System.out.println("resume" + id);
                }
            }
            res = (res + 17) * 1337L;
        }

        blackHole = res;

        System.out.println(id + ";" + Duration.between(start, Instant.now()).toMillis());
    }
}
