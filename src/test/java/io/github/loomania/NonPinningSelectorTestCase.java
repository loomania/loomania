package io.github.loomania;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class NonPinningSelectorTestCase {

    @Test
    @Disabled("Hanging on exit")
    public void testSimpleTimeout() throws Exception {
        try (NonPinningSelector nps = NonPinningSelector.newNonPinningSelector(ExecutorServiceListener.EMPTY)) {
            try (ExecutorService executorService = nps.executorService()) {
                Future<?> future = executorService.submit(() -> {
                    System.out.println("Starting select!");
                    nps.select(300L);
                    System.out.println("Finished select!");
                    return null;
                });
                future.get(500L, TimeUnit.MILLISECONDS);
            }
        }
    }
}
