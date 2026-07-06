package dev.stylus.app;

import dev.stylus.app.data.XmlSampleTree;
import dev.stylus.model.Band;
import dev.stylus.model.InlineNode;
import dev.stylus.model.OutputMode;
import dev.stylus.model.PageSetup;
import dev.stylus.model.ReportDocument;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central designer state: the open document, its file, the sample data tree, the current
 * selection and the undo/redo history (F-1.39 — snapshot-based; bands are immutable records
 * so snapshots are shallow copies with structural sharing). All mutation on the FX thread.
 */
public final class DesignerState {

    /** Cheap document snapshot: top-level lists copied, immutable nodes shared. */
    private record Snapshot(
            String title,
            OutputMode outputMode,
            PageSetup pageSetup,
            List<InlineNode> pageHeader,
            List<InlineNode> pageFooter,
            List<Band> bands,
            Map<String, String> parameters,
            boolean modified) {

        static Snapshot of(ReportDocument doc) {
            return new Snapshot(doc.title(), doc.outputMode(), doc.pageSetup(),
                    List.copyOf(doc.pageHeader()), List.copyOf(doc.pageFooter()),
                    List.copyOf(doc.bands()), new LinkedHashMap<>(doc.parameters()),
                    doc.isModified());
        }

        void restoreTo(ReportDocument doc) {
            doc.setTitle(title);
            doc.setOutputMode(outputMode);
            doc.setPageSetup(pageSetup);
            doc.pageHeader().clear();
            doc.pageHeader().addAll(pageHeader);
            doc.pageFooter().clear();
            doc.pageFooter().addAll(pageFooter);
            doc.bands().clear();
            doc.bands().addAll(bands);
            doc.parameters().clear();
            doc.parameters().putAll(parameters);
            if (modified) {
                doc.touch();
            } else {
                doc.markSaved();
            }
        }
    }

    private static final int HISTORY_LIMIT = 100;

    private ReportDocument document = ReportDocument.empty();
    private Path templateFile;
    private Path sampleFile;
    private XmlSampleTree.Parsed sample;
    private Object selection; // Band | InlineNode | null
    private dev.stylus.engine.api.EngineId targetEngine = dev.stylus.engine.api.EngineId.FOP;

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private Snapshot lastStable = Snapshot.of(document);

    private final List<Runnable> documentListeners = new ArrayList<>();
    private final List<Runnable> selectionListeners = new ArrayList<>();
    private final List<Runnable> sampleListeners = new ArrayList<>();

    // ---------- document ----------

    public ReportDocument document() {
        return document;
    }

    public void setDocument(ReportDocument document, Path file) {
        this.document = document;
        this.templateFile = file;
        this.selection = null;
        undoStack.clear();
        redoStack.clear();
        lastStable = Snapshot.of(document);
        fire(documentListeners);
        fire(selectionListeners);
    }

    /** Model mutated in place → history push (state before the edit) + rerender + unsaved. */
    public void documentEdited() {
        undoStack.push(lastStable);
        if (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
        document.touch();
        lastStable = Snapshot.of(document);
        fire(documentListeners);
    }

    // ---------- undo / redo (F-1.39) ----------

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(Snapshot.of(document));
        Snapshot previous = undoStack.pop();
        previous.restoreTo(document);
        lastStable = previous;
        selection = null;
        fire(documentListeners);
        fire(selectionListeners);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(Snapshot.of(document));
        Snapshot next = redoStack.pop();
        next.restoreTo(document);
        lastStable = next;
        selection = null;
        fire(documentListeners);
        fire(selectionListeners);
    }

    public Path templateFile() {
        return templateFile;
    }

    public void setTemplateFile(Path file) {
        this.templateFile = file;
    }

    /** Saved: clear the modified flag and refresh chrome. */
    public void markSaved() {
        document.markSaved();
        lastStable = Snapshot.of(document);
        fire(documentListeners);
    }

    // ---------- sample data ----------

    public XmlSampleTree.Parsed sample() {
        return sample;
    }

    public Path sampleFile() {
        return sampleFile;
    }

    public void setSample(XmlSampleTree.Parsed sample, Path file) {
        this.sample = sample;
        this.sampleFile = file;
        fire(sampleListeners);
    }

    // ---------- selection ----------

    public Object selection() {
        return selection;
    }

    public void select(Object node) {
        this.selection = node;
        fire(selectionListeners);
    }

    // ---------- clipboard (F-1.40: copy/cut/paste of bands and tokens) ----------

    private Object clipboard; // Band | InlineNode | null

    public boolean hasClipboard() {
        return clipboard != null;
    }

    public void copySelection() {
        if (selection != null) {
            clipboard = selection;
        }
    }

    public void cutSelection() {
        if (selection == null) {
            return;
        }
        clipboard = selection;
        boolean removed = switch (selection) {
            case dev.stylus.model.Band band -> dev.stylus.model.ModelEdits.removeBand(document, band);
            case dev.stylus.model.InlineNode node ->
                    dev.stylus.model.ModelEdits.replaceInline(document, node, null);
            default -> false;
        };
        if (removed) {
            documentEdited();
            select(null);
        }
    }

    /**
     * Pastes a fresh copy: a band into the selected group (or top-level), an inline token
     * into the selected static band. The copy gets new identities so it is independently
     * editable next to its source.
     */
    public void paste() {
        switch (clipboard) {
            case dev.stylus.model.Band band -> {
                dev.stylus.model.Band copy = dev.stylus.model.ModelEdits.copyOf(band);
                if (selection instanceof dev.stylus.model.GroupBand group) {
                    var children = new ArrayList<dev.stylus.model.Band>(group.children());
                    children.add(copy);
                    dev.stylus.model.ModelEdits.replaceBand(document, group,
                            new dev.stylus.model.GroupBand(group.selectXPath(), group.sortKeys(), children));
                } else {
                    document.bands().add(copy);
                }
                documentEdited();
                select(copy);
            }
            case dev.stylus.model.InlineNode node -> {
                if (selection instanceof dev.stylus.model.StaticBand target) {
                    dev.stylus.model.InlineNode copy = dev.stylus.model.ModelEdits.copyOf(node);
                    var content = new ArrayList<dev.stylus.model.InlineNode>(target.content());
                    content.add(copy);
                    dev.stylus.model.ModelEdits.replaceBand(document, target,
                            new dev.stylus.model.StaticBand(content, target.style(), target.rules()));
                    documentEdited();
                    select(copy);
                }
            }
            case null, default -> { }
        }
    }

    // ---------- target engine (drives the engine-aware palette + warnings, F-1.31/F-2.25) ----------

    private final List<Runnable> engineListeners = new ArrayList<>();
    private java.util.Map<dev.stylus.engine.api.EngineId, dev.stylus.engine.api.CapabilityMatrix>
            capabilities = java.util.Map.of();

    /** Engine capability matrices, set once by the shell from the registry (F-2.25/F-1.50). */
    public void setCapabilities(
            java.util.Map<dev.stylus.engine.api.EngineId, dev.stylus.engine.api.CapabilityMatrix> caps) {
        this.capabilities = caps;
    }

    /** The active target engine's matrix; null when unknown. */
    public dev.stylus.engine.api.CapabilityMatrix targetCapabilities() {
        return capabilities.get(targetEngine);
    }

    public dev.stylus.engine.api.EngineId targetEngine() {
        return targetEngine;
    }

    public void setTargetEngine(dev.stylus.engine.api.EngineId engine) {
        if (engine != null && engine != targetEngine) {
            targetEngine = engine;
            fire(engineListeners);
        }
    }

    public void onTargetEngineChanged(Runnable listener) {
        engineListeners.add(listener);
    }

    // ---------- listeners ----------

    /**
     * Drops every UI listener — called before the shell is rebuilt (language/theme change,
     * F-9.2) so the fresh UI is the only subscriber while document/sample/undo state survives.
     */
    public void detachUi() {
        documentListeners.clear();
        selectionListeners.clear();
        sampleListeners.clear();
        engineListeners.clear();
    }

    public void onDocumentChanged(Runnable listener) {
        documentListeners.add(listener);
    }

    public void onSelectionChanged(Runnable listener) {
        selectionListeners.add(listener);
    }

    public void onSampleChanged(Runnable listener) {
        sampleListeners.add(listener);
    }

    private static void fire(List<Runnable> listeners) {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
