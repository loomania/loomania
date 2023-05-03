package io.github.loomania;

/**
 *
 */
final class Nope {
    private Nope() {}

    static UnsupportedOperationException nope() {
        return new UnsupportedOperationException(nopeMsg());
    }

    static String nopeMsg() {
        int major = Runtime.version().feature();
        if (major < 19) {
            return "Requires Java 19 or later";
        } else if (major < 21) {
            return "Requires `--enable-preview`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, and `--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED`";
        } else {
            return "Requires `--add-opens=java.base/java.lang=ALL-UNNAMED` and `--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED`";
        }
    }
}
