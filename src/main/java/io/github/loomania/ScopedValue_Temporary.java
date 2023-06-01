package io.github.loomania;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A trivial, slow clone of {@link jdk.incubator.concurrent.ScopedValue}, for use until that API is no longer in preview.
 */
public final class ScopedValue_Temporary<T> {
    private static final Snapshot EMPTY = new Snapshot(null, Map.of());
    private static final ThreadLocal<Snapshot> current = ThreadLocal.withInitial(() -> EMPTY);

    private ScopedValue_Temporary() {}

    public static final class Carrier {
        final Carrier prev;
        final ScopedValue_Temporary<?> key;
        final Object value;

        Carrier(final Carrier prev, final ScopedValue_Temporary<?> key, final Object value) {
            this.prev = prev;
            this.key = key;
            this.value = value;
        }

        public <T> Carrier where(ScopedValue_Temporary<T> key, T value) {
            Objects.requireNonNull(key, "key");
            return new Carrier(this, key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(ScopedValue_Temporary<T> key) {
            if (key == this.key) {
                return (T) value;
            } else if (prev == null) {
                throw new NoSuchElementException();
            } else {
                return prev.get(key);
            }
        }

        public void run(Runnable op) {
            Snapshot snapshot = current.get();
            Map<ScopedValue_Temporary<?>, Object> map = new HashMap<>();
            fillMap(map);
            current.set(new Snapshot(snapshot, map));
            try {
                op.run();
            } finally {
                current.set(snapshot);
            }
        }

        private void fillMap(final Map<ScopedValue_Temporary<?>, Object> map) {
            map.put(key, value);
            if (prev != null) {
                prev.fillMap(map);
            }
        }
    }

    static final class Snapshot {
        final Snapshot prev;
        final Map<ScopedValue_Temporary<?>, Object> values;

        Snapshot(final Snapshot prev, final Map<ScopedValue_Temporary<?>, Object> values) {
            this.prev = prev;
            this.values = values;
        }

        @SuppressWarnings("unchecked")
        <T> T orElse(ScopedValue_Temporary<T> key, T defaultValue) {
            Objects.requireNonNull(key, "key");
            if (values.containsKey(key)) {
                return (T) values.get(key);
            } else if (prev != null) {
                return prev.orElse(key, defaultValue);
            } else {
                return defaultValue;
            }
        }

        @SuppressWarnings("unchecked")
        <T, X extends Throwable> T orElseThrow(ScopedValue_Temporary<T> key, Supplier<? extends X> exceptionSupplier) throws X {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(exceptionSupplier, "exceptionSupplier");
            if (values.containsKey(key)) {
                return (T) values.get(key);
            } else if (prev != null) {
                return prev.orElseThrow(key, exceptionSupplier);
            } else {
                throw exceptionSupplier.get();
            }
        }

        boolean has(ScopedValue_Temporary<?> key) {
            return values.containsKey(key) || prev != null && prev.has(key);
        }
    }

    public static <T> ScopedValue_Temporary<T> newInstance() {
        return new ScopedValue_Temporary<>();
    }

    public boolean isBound() {
        return current.get().has(this);
    }

    public T orElse(T defaultValue) {
        return current.get().orElse(this, defaultValue);
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return current.get().orElseThrow(this, exceptionSupplier);
    }

    public static <T> Carrier where(ScopedValue_Temporary<T> key, T value) {
        Objects.requireNonNull(key, "key");
        return new Carrier(null, key, value);
    }

    public static <T> void where(ScopedValue_Temporary<T> key, T value, Runnable op) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(op, "op");
        where(key, value).run(op);
    }
}
