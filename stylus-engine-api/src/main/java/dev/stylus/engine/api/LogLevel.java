package dev.stylus.engine.api;

/**
 * Log levels matching Template Viewer's {@code xdo-debug-level} semantics (F-5.9),
 * ordered from most to least severe. FOP/Saxon messages are mapped onto the same scale (F-5.10).
 */
public enum LogLevel {
    ERROR,
    EXCEPTION,
    EVENT,
    PROCEDURE,
    STATEMENT;

    /** True when a message at this level should be shown for a chosen verbosity threshold. */
    public boolean isAtLeast(LogLevel threshold) {
        return this.ordinal() <= threshold.ordinal();
    }
}
