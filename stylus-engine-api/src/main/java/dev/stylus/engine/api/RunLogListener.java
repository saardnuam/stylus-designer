package dev.stylus.engine.api;

/** Receives engine log lines as they happen (live log pane; may be called from any thread). */
@FunctionalInterface
public interface RunLogListener {

    void log(LogEntry entry);

    default void log(LogLevel level, String message) {
        log(LogEntry.of(level, message));
    }

    /** Discards everything. */
    RunLogListener NULL = entry -> { };
}
