package dev.stylus.app.run;

import dev.stylus.engine.api.LogEntry;
import dev.stylus.engine.api.ReportEngine;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.function.Consumer;

/**
 * Executes engine runs on a background thread (N10, Template Viewer parity F-5.6):
 * live log entries are forwarded to the FX thread; cancel interrupts the worker.
 */
public final class RunService {

    private Thread worker;
    private Task<RunResult> current;

    /** Starts a run; any previous run is cancelled first. All callbacks arrive on the FX thread. */
    public void run(ReportEngine engine, RunRequest request,
                    Consumer<LogEntry> onLog,
                    Consumer<RunResult> onDone) {
        cancel();

        Task<RunResult> task = new Task<>() {
            @Override
            protected RunResult call() {
                return engine.run(request, entry -> {
                    if (!isCancelled()) {
                        Platform.runLater(() -> onLog.accept(entry));
                    }
                });
            }
        };
        task.setOnSucceeded(e -> onDone.accept(task.getValue()));
        task.setOnFailed(e -> onDone.accept(RunResult.failed(
                java.time.Duration.ZERO, java.util.List.of(),
                task.getException())));
        // Cancelled: no callback — the UI already reset itself when cancel() was pressed.

        current = task;
        worker = new Thread(task, "stylus-run");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * FOP has no runtime XLIFF support (that is a BIP feature): pre-translate the template
     * through the model instead (F-9.5). Fails for fully-opaque templates — there is nothing
     * addressable to translate.
     */
    public static java.nio.file.Path applyXliffToTemplate(java.nio.file.Path template,
            java.nio.file.Path xliff) throws Exception {
        String source = java.nio.file.Files.readString(template);
        String baseName = template.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        var doc = new dev.stylus.codegen.XslReader().read(source, baseName);
        if (doc.isFullyOpaque()) {
            throw new IllegalStateException(
                    "template is not designer-readable — run it on the BIP engine instead");
        }
        dev.stylus.xliff.XliffFile.apply(doc, dev.stylus.xliff.XliffFile.readTargets(xliff));
        java.nio.file.Path translated =
                java.nio.file.Files.createTempFile("stylus-xliff-" + baseName + "-", ".xsl");
        translated.toFile().deleteOnExit();
        java.nio.file.Files.writeString(translated, new dev.stylus.codegen.XslWriter().write(doc));
        return translated;
    }

    public boolean isRunning() {
        return current != null && current.isRunning();
    }

    public void cancel() {
        if (current != null && current.isRunning()) {
            current.cancel(true);
            if (worker != null) {
                worker.interrupt();
            }
        }
        current = null;
        worker = null;
    }
}
