package dev.stylus.engine.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Outcome of one engine run: output file on success, collected log, total time (F-5.6). */
public record RunResult(
        boolean success,
        Path output,
        Duration duration,
        List<LogEntry> log,
        Throwable error) {

    public static RunResult ok(Path output, Duration duration, List<LogEntry> log) {
        return new RunResult(true, output, duration, List.copyOf(log), null);
    }

    public static RunResult failed(Duration duration, List<LogEntry> log, Throwable error) {
        return new RunResult(false, null, duration, List.copyOf(log), error);
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(error);
    }
}
