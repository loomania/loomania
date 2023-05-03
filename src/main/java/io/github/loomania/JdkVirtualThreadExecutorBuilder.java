package io.github.loomania;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * A configurable builder for a JDK virtual thread executor.
 */
public final class JdkVirtualThreadExecutorBuilder {
    static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(30);
    static final int DEFAULT_MAX = 0x7ff;

    private int parallelism = Math.min(DEFAULT_MAX, Runtime.getRuntime().availableProcessors());
    private int corePoolSize = 0;
    private int maximumPoolSize = DEFAULT_MAX;
    private int minimumRunnable = 1;
    private Duration keepAliveTime = DEFAULT_KEEP_ALIVE;
    private String name = "virtual thread";

    JdkVirtualThreadExecutorBuilder() {}

    public int getParallelism() {
        return parallelism;
    }

    public JdkVirtualThreadExecutorBuilder setParallelism(final int parallelism) {
        this.parallelism = parallelism;
        return this;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public JdkVirtualThreadExecutorBuilder setCorePoolSize(final int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public JdkVirtualThreadExecutorBuilder setMaximumPoolSize(final int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
        return this;
    }

    public int getMinimumRunnable() {
        return minimumRunnable;
    }

    public JdkVirtualThreadExecutorBuilder setMinimumRunnable(final int minimumRunnable) {
        this.minimumRunnable = minimumRunnable;
        return this;
    }

    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    public JdkVirtualThreadExecutorBuilder setKeepAliveTime(final Duration keepAliveTime) {
        Objects.requireNonNull(keepAliveTime);
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    public String getName() {
        return name;
    }

    public JdkVirtualThreadExecutorBuilder setName(final String name) {
        Objects.requireNonNull(name);
        this.name = name;
        return this;
    }

    public ExecutorService build() {
        return Loomania.buildVirtualThreadFactory(this);
    }
}
