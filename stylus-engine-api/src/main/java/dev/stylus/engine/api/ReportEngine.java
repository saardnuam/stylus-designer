package dev.stylus.engine.api;

import java.util.Optional;
import java.util.Set;

/**
 * The engine SPI — the load-bearing abstraction (docs/03). One implementation per target:
 * bundled Apache FOP (always available) and the Oracle BIP adapter (available once the user
 * points at a local BI Publisher installation, F-12.3).
 */
public interface ReportEngine {

    EngineId id();

    /** Human-readable name incl. detected version, e.g. "Apache FOP 2.10". */
    String displayName();

    /**
     * Whether the engine can run right now. FOP: always true. BIP: true only when a valid
     * local installation is configured (F-12.4 graceful FOP-only mode).
     */
    boolean isAvailable();

    Set<OutputFormat> supportedFormats();

    CapabilityMatrix capabilities();

    /**
     * Execute one run synchronously on the calling thread (callers put it on a background
     * thread — N10). Never throws for template/data errors: failures come back as an
     * unsuccessful {@link RunResult} with the error logged.
     */
    RunResult run(RunRequest request, RunLogListener listener);

    /** Conversion/debug tools, when the engine has them (BIP: RTF→XSL etc., F-5.15). */
    default Optional<EngineConversions> conversions() {
        return Optional.empty();
    }
}
