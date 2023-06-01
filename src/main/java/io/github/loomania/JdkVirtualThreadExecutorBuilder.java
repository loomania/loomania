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

    private ScopedValue_Temporary.Carrier carrier;
    private int parallelism = Math.min(DEFAULT_MAX, Runtime.getRuntime().availableProcessors());
    private int corePoolSize = 0;
    private int maximumPoolSize = DEFAULT_MAX;
    private int minimumRunnable = 1;
    private Duration keepAliveTime = DEFAULT_KEEP_ALIVE;
    private String name = "virtual thread";

    JdkVirtualThreadExecutorBuilder() {}

    /**
     * Get the context to propagate to each virtual thread.
     *
     * @return the context to propagate to each virtual thread, or {@code null} for none
     */
    public ScopedValue_Temporary.Carrier getCarrier() {
        return carrier;
    }

    /**
     * Set the context to propagate to each virtual thread.
     *
     * @param carrier the context to propagate to each virtual thread, or {@code null} for none
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setCarrier(final ScopedValue_Temporary.Carrier carrier) {
        this.carrier = carrier;
        return this;
    }

    /**
     * Get the parallelism value set on this builder.
     *
     * @return the parallelism value
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Set the parallelism value.
     *
     * @param parallelism the parallelism value
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setParallelism(final int parallelism) {
        this.parallelism = parallelism;
        return this;
    }

    /**
     * Get the core pool size.
     *
     * @return the core pool size
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Set the core pool size.
     *
     * @param corePoolSize the core pool size
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setCorePoolSize(final int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    /**
     * Get the maximum pool size.
     *
     * @return the maximum pool size
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Set the maximum pool size.
     *
     * @param maximumPoolSize the maximum pool size
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setMaximumPoolSize(final int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
        return this;
    }

    /**
     * Get the minimum runnable count.
     *
     * @return the minimum runnable count
     */
    public int getMinimumRunnable() {
        return minimumRunnable;
    }

    /**
     * Set the minimum runnable count.
     *
     * @param minimumRunnable the minimum runnable count
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setMinimumRunnable(final int minimumRunnable) {
        this.minimumRunnable = minimumRunnable;
        return this;
    }

    /**
     * Get the keep-alive time.
     *
     * @return the keep-alive time
     */
    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Set the keep-alive time.
     *
     * @param keepAliveTime the keep-alive time (must not be {@code null})
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setKeepAliveTime(final Duration keepAliveTime) {
        Objects.requireNonNull(keepAliveTime);
        this.keepAliveTime = keepAliveTime;
        return this;
    }

    /**
     * Get the thread pool name.
     *
     * @return the thread pool name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the thread pool name.
     *
     * @param name the thread pool name (must not be {@code null})
     * @return this builder (not {@code null})
     */
    public JdkVirtualThreadExecutorBuilder setName(final String name) {
        Objects.requireNonNull(name);
        this.name = name;
        return this;
    }

    /**
     * Build a new executor service with this builder's current configuration.
     *
     * @return the executor service (not {@code null})
     */
    public ExecutorService build() {
        return Loomania.buildVirtualThreadFactory(this);
    }
}
