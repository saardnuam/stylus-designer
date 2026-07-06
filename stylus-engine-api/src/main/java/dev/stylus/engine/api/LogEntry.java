package dev.stylus.engine.api;

import java.time.Instant;

/** One engine log line (run panel log pane, F-5.9). */
public record LogEntry(Instant timestamp, LogLevel level, String message) {

    public static LogEntry of(LogLevel level, String message) {
        return new LogEntry(Instant.now(), level, message);
    }

    @Override
    public String toString() {
        return "[" + level + "] " + message;
    }
}
