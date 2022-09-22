package io.github.dmlloyd.loomania;

/**
 *
 */
final class Nope {
    private Nope() {}

    static UnsupportedOperationException nope() {
        return new UnsupportedOperationException("Requires Java 19 or later with `--enable-preview` and `--add-opens=java.base/java.lang=ALL-UNNAMED`");
    }
}
