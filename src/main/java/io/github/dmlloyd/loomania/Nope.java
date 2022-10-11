package io.github.dmlloyd.loomania;

/**
 *
 */
final class Nope {
    private Nope() {}

    static UnsupportedOperationException nope() {
        return new UnsupportedOperationException(nopeMsg());
    }

    static String nopeMsg() {
        return "Requires Java 19 or later with `--enable-preview`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, and `--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED`";
    }
}
