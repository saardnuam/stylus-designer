package dev.stylus.engine.fop;

import dev.stylus.engine.api.CapabilityMatrix;
import dev.stylus.engine.api.EngineException;
import dev.stylus.engine.api.EngineId;
import dev.stylus.engine.api.LogEntry;
import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.ReportEngine;
import dev.stylus.engine.api.RunLogListener;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import org.apache.fop.Version;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The bundled engine: Saxon-HE (XSLT 1.0/2.0/3.0) + Apache FOP (docs/03).
 *
 * Pipelines per requested format:
 *   HTML          → single Saxon transform to file (web mode, F-4.2)
 *   FO            → Saxon transform, save intermediate (F-5.11)
 *   PDF/PS/…/IF   → Saxon transform → in-memory FO → FOP render (F-4.4)
 * A template that is already XSL-FO (*.fo) skips the transform (Template Viewer parity, F-5.2).
 */
public final class FopEngine implements ReportEngine {

    private final SaxonTransformer transformer = new SaxonTransformer();
    private final FopRenderer renderer = new FopRenderer();

    @Override
    public EngineId id() {
        return EngineId.FOP;
    }

    @Override
    public String displayName() {
        return "Apache FOP " + Version.getVersion();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<OutputFormat> supportedFormats() {
        return FopCapabilities.FORMATS;
    }

    @Override
    public CapabilityMatrix capabilities() {
        return FopCapabilities.matrix();
    }

    @Override
    public RunResult run(RunRequest request, RunLogListener listener) {
        List<LogEntry> collected = new ArrayList<>();
        RunLogListener log = entry -> {
            collected.add(entry);
            listener.log(entry);
        };
        long start = System.nanoTime();
        try {
            validate(request);
            execute(request, log);
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            log.log(LogLevel.EVENT, "Output written: " + request.output()
                    + " (" + Files.size(request.output()) + " bytes)");
            log.log(LogLevel.EVENT, "Total time: " + took.toMillis() + " ms");
            return RunResult.ok(request.output(), took, collected);
        } catch (Exception e) {
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            log.log(LogLevel.ERROR, e.getMessage() == null ? e.toString() : e.getMessage());
            return RunResult.failed(took, collected, e);
        }
    }

    private void validate(RunRequest request) {
        if (!Files.isRegularFile(request.template())) {
            throw new EngineException("Template not found: " + request.template());
        }
        if (request.data().isPresent() && !Files.isRegularFile(request.data().get())) {
            throw new EngineException("Data file not found: " + request.data().get());
        }
        if (!supportedFormats().contains(request.format())) {
            throw new EngineException("Format " + request.format() + " is not supported by "
                    + displayName());
        }
        request.xliff().ifPresent(x ->
                { throw new EngineException("XLIFF application is not implemented yet (M6)"); });
        request.styleTemplate().ifPresent(x ->
                { throw new EngineException("Style templates are a BI Publisher feature"); });
    }

    private void execute(RunRequest request, RunLogListener log) throws Exception {
        OutputFormat format = request.format();
        Path template = request.template();
        boolean templateIsFo = template.getFileName().toString()
                .toLowerCase(Locale.ROOT).endsWith(".fo");

        if (format == OutputFormat.HTML) {
            requireData(request);
            log.log(LogLevel.PROCEDURE, "Transforming (XSLT → HTML): " + template.getFileName());
            transformer.transformToFile(template, request.data().get(), request.parameters(),
                    request.output(), log);
            return;
        }

        String fo;
        if (templateIsFo) {
            log.log(LogLevel.PROCEDURE, "Template is XSL-FO — skipping transform");
            fo = Files.readString(template);
        } else {
            requireData(request);
            log.log(LogLevel.PROCEDURE, "Transforming (XSLT → XSL-FO): " + template.getFileName());
            fo = transformer.transformToString(template, request.data().get(),
                    request.parameters(), log);
        }

        if (format == OutputFormat.FO) {
            Files.writeString(request.output(), fo);
            return;
        }

        log.log(LogLevel.PROCEDURE, "Rendering (FOP → " + format + ")");
        Path baseDir = template.toAbsolutePath().getParent();
        renderer.render(fo, baseDir, request.engineConfig().orElse(null), format,
                request.output(), log);
    }

    private void requireData(RunRequest request) {
        if (request.data().isEmpty()) {
            throw new EngineException("An XML data file is required to run an XSLT template");
        }
    }
}
