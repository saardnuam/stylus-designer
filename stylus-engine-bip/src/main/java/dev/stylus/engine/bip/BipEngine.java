package dev.stylus.engine.bip;

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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Oracle BI Publisher engine adapter (docs/03): drives {@code oracle.xdo.template.FOProcessor}
 * from the user's installation through a reflection facade over an isolated classloader.
 * No Oracle type ever escapes this class; no Oracle jar is ever bundled (hard rule 1).
 *
 * The supported output formats are probed from the loaded FOProcessor's {@code FORMAT_*}
 * constants — the first slice of the runtime capability probe (F-2.28).
 */
public final class BipEngine implements ReportEngine {

    private static final String FO_PROCESSOR = "oracle.xdo.template.FOProcessor";

    /** Candidate FORMAT_* constant names per output format, in preference order. */
    private static final Map<OutputFormat, List<String>> FORMAT_CANDIDATES = Map.ofEntries(
            Map.entry(OutputFormat.PDF, List.of("FORMAT_PDF")),
            Map.entry(OutputFormat.RTF, List.of("FORMAT_RTF")),
            Map.entry(OutputFormat.HTML, List.of("FORMAT_HTML")),
            Map.entry(OutputFormat.MHTML, List.of("FORMAT_MHTML", "FORMAT_EXCEL_MHTML")),
            Map.entry(OutputFormat.XLSX, List.of("FORMAT_XLSX", "FORMAT_EXCEL_XLSX")),
            Map.entry(OutputFormat.PPTX, List.of("FORMAT_PPTX")),
            Map.entry(OutputFormat.ETEXT, List.of("FORMAT_ETEXT", "FORMAT_EFT")),
            Map.entry(OutputFormat.PDFZ, List.of("FORMAT_PDFZ")),
            Map.entry(OutputFormat.FO, List.of("FORMAT_XSLFO", "FORMAT_FO")));

    private final BipInstallation installation;
    private final ChildFirstClassLoader loader;
    private final Class<?> foProcessorClass;
    private final Map<OutputFormat, Byte> formatConstants;

    public BipEngine(BipInstallation installation) {
        this.installation = installation;
        this.loader = new ChildFirstClassLoader(installation.jars());
        try {
            this.foProcessorClass = Class.forName(FO_PROCESSOR, false, loader);
        } catch (ClassNotFoundException e) {
            throw new EngineException("Not a usable BI Publisher installation ("
                    + FO_PROCESSOR + " missing in " + installation.directory() + ")", e);
        }
        this.formatConstants = probeFormats(foProcessorClass);
        if (!formatConstants.containsKey(OutputFormat.PDF)) {
            throw new EngineException("FOProcessor in " + installation.directory()
                    + " exposes no FORMAT_PDF constant — unsupported BIP version?");
        }
    }

    private static Map<OutputFormat, Byte> probeFormats(Class<?> processor) {
        Map<OutputFormat, Byte> constants = new EnumMap<>(OutputFormat.class);
        for (Map.Entry<OutputFormat, List<String>> candidate : FORMAT_CANDIDATES.entrySet()) {
            for (String fieldName : candidate.getValue()) {
                try {
                    Field field = processor.getField(fieldName);
                    constants.put(candidate.getKey(), field.getByte(null));
                    break;
                } catch (ReflectiveOperationException ignored) {
                    // try next candidate name
                }
            }
        }
        return constants;
    }

    @Override
    public EngineId id() {
        return EngineId.BIP;
    }

    @Override
    public String displayName() {
        return "Oracle BI Publisher (" + installation.version() + ")";
    }

    @Override
    public boolean isAvailable() {
        return true; // construction already validated the installation
    }

    @Override
    public Set<OutputFormat> supportedFormats() {
        return formatConstants.keySet();
    }

    @Override
    public CapabilityMatrix capabilities() {
        return BipCapabilities.matrix(supportedFormats());
    }

    public BipInstallation installation() {
        return installation;
    }

    @Override
    public java.util.Optional<dev.stylus.engine.api.EngineConversions> conversions() {
        return java.util.Optional.of(new BipConversions(loader));
    }

    @Override
    public RunResult run(RunRequest request, RunLogListener listener) {
        List<LogEntry> collected = new ArrayList<>();
        RunLogListener log = entry -> {
            collected.add(entry);
            listener.log(entry);
        };
        long start = System.nanoTime();
        ClassLoader previousContext = Thread.currentThread().getContextClassLoader();
        Runnable detachXdoLog = captureXdoLog(log);
        try {
            validate(request);
            log.log(LogLevel.PROCEDURE, "FOProcessor: " + request.template().getFileName()
                    + " + " + request.data().map(d -> d.getFileName().toString()).orElse("(no data)")
                    + " → " + request.format());

            Thread.currentThread().setContextClassLoader(loader);
            Object processor = foProcessorClass.getDeclaredConstructor().newInstance();

            call(processor, "setTemplate", request.template().toAbsolutePath().toString());
            call(processor, "setData", request.data().orElseThrow().toAbsolutePath().toString());
            call(processor, "setOutput", request.output().toAbsolutePath().toString());
            invoke(processor, "setOutputFormat", new Class<?>[] {byte.class},
                    formatConstants.get(request.format()));

            if (request.engineConfig().isPresent()) {
                call(processor, "setConfig", request.engineConfig().get().toAbsolutePath().toString());
            }
            request.outputLocale().ifPresent(locale ->
                    call(processor, "setLocale", locale.toLanguageTag()));
            if (request.xliff().isPresent()) {
                boolean applied = callIfPresent(processor, "setXLIFF",
                        request.xliff().get().toAbsolutePath().toString());
                if (!applied) {
                    throw new EngineException(
                            "This BI Publisher version has no setXLIFF — run without XLIFF");
                }
            }

            invoke(processor, "generate", new Class<?>[0]);

            Duration took = Duration.ofNanos(System.nanoTime() - start);
            log.log(LogLevel.EVENT, "Output written: " + request.output()
                    + " (" + Files.size(request.output()) + " bytes)");
            log.log(LogLevel.EVENT, "Total time: " + took.toMillis() + " ms");
            return RunResult.ok(request.output(), took, collected);
        } catch (Exception e) {
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : e;
            log.log(LogLevel.ERROR, String.valueOf(cause.getMessage() == null
                    ? cause : cause.getMessage()));
            return RunResult.failed(took, collected, cause);
        } finally {
            detachXdoLog.run();
            Thread.currentThread().setContextClassLoader(previousContext);
        }
    }

    /**
     * Routes Oracle's internal logger into the run log for this run (F-5.10, Template Viewer
     * parity: XDOLogImpl.setDestination + Logger.setLog). Captures at EVENT level — xdo.cfg's
     * {@code xdo-debug-level} can raise verbosity per run. Best-effort: a BIP build without
     * these classes simply logs nothing extra. Returns the detach action.
     */
    private Runnable captureXdoLog(RunLogListener log) {
        try {
            Class<?> loggerClass = Class.forName("oracle.xdo.common.log.Logger", false, loader);
            Class<?> logInterface = Class.forName("oracle.xdo.common.log.XDOLog", false, loader);
            Object previous = loggerClass.getMethod("getLog").invoke(null);

            XdoLogForwarder sink = new XdoLogForwarder(log);
            Object impl = Class.forName("oracle.xdo.common.log.XDOLogImpl", false, loader)
                    .getDeclaredConstructor().newInstance();
            impl.getClass().getMethod("setDestination", java.io.OutputStream.class)
                    .invoke(impl, sink);
            impl.getClass().getMethod("setLevel", int.class).invoke(impl, 3); // EVENT
            loggerClass.getMethod("setLog", logInterface).invoke(null, impl);

            return () -> {
                sink.close();
                try {
                    loggerClass.getMethod("setLog", logInterface).invoke(null, previous);
                } catch (ReflectiveOperationException ignored) {
                    // restoring the previous logger is best-effort
                }
            };
        } catch (ReflectiveOperationException e) {
            return () -> { };
        }
    }

    private void validate(RunRequest request) {
        if (!Files.isRegularFile(request.template())) {
            throw new EngineException("Template not found: " + request.template());
        }
        if (request.data().isEmpty()) {
            throw new EngineException("BI Publisher runs need an XML data file");
        }
        if (!Files.isRegularFile(request.data().get())) {
            throw new EngineException("Data file not found: " + request.data().get());
        }
        if (!supportedFormats().contains(request.format())) {
            throw new EngineException("Format " + request.format()
                    + " is not exposed by this BI Publisher version (probe: "
                    + supportedFormats() + ")");
        }
        if (request.template().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".fo")) {
            throw new EngineException(
                    "Direct .fo rendering is not wired for BIP yet — use the FOP engine");
        }
        request.styleTemplate().ifPresent(s -> {
            throw new EngineException("Style templates are not wired for local BIP runs yet (M6)");
        });
    }

    // ---------- tiny reflection facade ----------

    private void call(Object target, String method, String arg) {
        invoke(target, method, new Class<?>[] {String.class}, arg);
    }

    /** Invokes when the method exists on this BIP version; false when it does not. */
    private boolean callIfPresent(Object target, String method, String arg) {
        try {
            Method m = target.getClass().getMethod(method, String.class);
            m.invoke(target, arg);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw asEngineException(method, e);
        }
    }

    private void invoke(Object target, String method, Class<?>[] signature, Object... args) {
        try {
            Method m = target.getClass().getMethod(method, signature);
            m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw asEngineException(method, e);
        }
    }

    private EngineException asEngineException(String method, ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                ? ite.getCause() : e;
        return new EngineException("BI Publisher " + method + " failed: " + cause, cause);
    }
}
