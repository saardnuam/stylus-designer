package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.app.data.XmlSampleTree;
import dev.stylus.app.run.EngineRegistry;
import dev.stylus.codegen.XslReader;
import dev.stylus.codegen.XslWriter;
import javafx.application.HostServices;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The 3-pane IDE frame (F-1.1) + test bench drawer: coordinates the designer state, the
 * reader/writer round-trip on open/save (N7), sample-data loading and the code-view sync
 * (F-1.36: model → code on entering Code, code → model when leaving it).
 */
public final class Shell extends BorderPane {

    public static final double TREE_WIDTH = 262;
    public static final double PROPS_WIDTH = 308;

    private final DesignerState state;
    private final XslWriter writer = new XslWriter();
    private final XslReader reader = new XslReader();
    private final Runnable openPreferences;

    private final EngineRegistry engines = new EngineRegistry();
    private final PreviewPane previewPane;
    private final CanvasPane canvasPane;
    private final TestBenchPane benchPane;
    private final DataSourcePane dataSourcePane;
    private final PropertiesPane propertiesPane;
    private final StatusBar statusBar = new StatusBar();
    private final AppBar appBar;
    private final SplitPane canvasSplit;

    private View currentView = View.DESIGN;
    private String lastGeneratedCode = "";
    private double lastBenchDivider = 0.60;

    public Shell(DesignerState state, Runnable themeToggle, Runnable openPreferences,
                 HostServices hostServices) {
        this.state = state;
        this.openPreferences = openPreferences;
        getStyleClass().add("shell");

        // Engine capability matrices → element-support badges + palette gating (F-2.25/F-1.50).
        state.setCapabilities(engines.all().stream().collect(
                java.util.stream.Collectors.toMap(
                        dev.stylus.engine.api.ReportEngine::id,
                        dev.stylus.engine.api.ReportEngine::capabilities)));

        ExpressionEditorOverlay exprEditor = new ExpressionEditorOverlay(state);
        previewPane = new PreviewPane(hostServices);
        canvasPane = new CanvasPane(state, previewPane);
        benchPane = new TestBenchPane(engines, this::onOutputReady, this::openTemplate);
        dataSourcePane = new DataSourcePane(state);
        propertiesPane = new PropertiesPane(state, exprEditor);
        appBar = new AppBar(themeToggle, this::switchView, benchPane::startRun, this::toggleBench);

        VBox top = new VBox(buildMenuBar(), appBar, new FormatToolbar(state),
                new InsertToolbar(state, exprEditor));
        setTop(top);

        canvasSplit = new SplitPane(canvasPane, benchPane);
        canvasSplit.setOrientation(Orientation.VERTICAL);
        canvasSplit.setDividerPositions(lastBenchDivider);
        canvasSplit.getStyleClass().add("canvas-split");

        dataSourcePane.setPrefWidth(TREE_WIDTH);
        dataSourcePane.setMinWidth(TREE_WIDTH);
        dataSourcePane.setMaxWidth(TREE_WIDTH);
        propertiesPane.setPrefWidth(PROPS_WIDTH);
        propertiesPane.setMinWidth(PROPS_WIDTH);
        propertiesPane.setMaxWidth(PROPS_WIDTH);
        HBox.setHgrow(canvasSplit, Priority.ALWAYS);

        HBox body = new HBox(
                dataSourcePane, vSeparator(),
                canvasSplit, vSeparator(),
                propertiesPane);
        body.getStyleClass().add("shell-body");

        // Overlay layer: the expression editor floats bottom-right over the workspace (F-1.30).
        javafx.scene.layout.StackPane center = new javafx.scene.layout.StackPane(body, exprEditor);
        javafx.scene.layout.StackPane.setAlignment(exprEditor, javafx.geometry.Pos.BOTTOM_RIGHT);
        javafx.scene.layout.StackPane.setMargin(exprEditor, new javafx.geometry.Insets(0, 14, 14, 0));
        exprEditor.setPickOnBounds(false);
        setCenter(center);

        setBottom(statusBar);

        state.onDocumentChanged(this::refreshChrome);
        benchPane.setOnEngineChanged(state::setTargetEngine);
        refreshChrome();
    }

    // ---------- menu ----------

    private MenuBar buildMenuBar() {
        Menu file = new Menu(I18n.t("menu.file"));

        MenuItem newTemplate = new MenuItem(I18n.t("menu.file.new"));
        newTemplate.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
        newTemplate.setOnAction(e -> {
            state.setDocument(dev.stylus.model.ReportDocument.empty(), null);
            switchView(View.DESIGN);
            appBar.selectView(View.DESIGN);
        });

        MenuItem newSubtemplate = new MenuItem(I18n.t("menu.file.newSubtemplate"));
        newSubtemplate.setOnAction(e -> createSubtemplate());

        MenuItem openTemplate = new MenuItem(I18n.t("menu.file.openTemplate"));
        openTemplate.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
        openTemplate.setOnAction(e -> chooseAndOpenTemplate());

        MenuItem openSample = new MenuItem(I18n.t("menu.file.openSample"));
        openSample.setAccelerator(KeyCombination.keyCombination("Shortcut+D"));
        openSample.setOnAction(e -> chooseAndOpenSample());

        MenuItem openWorkingDir = new MenuItem(I18n.t("menu.file.openWorkingDir"));
        openWorkingDir.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+O"));
        openWorkingDir.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle(I18n.t("menu.file.openWorkingDir"));
            java.io.File dir = chooser.showDialog(getScene().getWindow());
            if (dir != null) {
                benchPane.setWorkingDir(dir.toPath());
            }
        });

        MenuItem save = new MenuItem(I18n.t("menu.file.save"));
        save.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));
        save.setOnAction(e -> saveTemplate(false));

        MenuItem saveAs = new MenuItem(I18n.t("menu.file.saveAs"));
        saveAs.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+S"));
        saveAs.setOnAction(e -> saveTemplate(true));

        // macOS convention: ⌘, opens the settings dialog (language, theme, BIP home).
        MenuItem preferences = new MenuItem(I18n.t("menu.file.preferences"));
        preferences.setAccelerator(KeyCombination.keyCombination("Shortcut+Comma"));
        preferences.setOnAction(e -> openPreferences.run());

        file.getItems().addAll(newTemplate, newSubtemplate, openTemplate, openSample,
                openWorkingDir, new SeparatorMenuItem(), save, saveAs,
                new SeparatorMenuItem(), preferences);

        Menu edit = new Menu(I18n.t("menu.edit"));
        MenuItem undo = new MenuItem(I18n.t("menu.edit.undo"));
        undo.setAccelerator(KeyCombination.keyCombination("Shortcut+Z"));
        undo.setOnAction(e -> {
            // In the code view the shortcut belongs to the text editor's own history.
            if (currentView == View.CODE) {
                canvasPane.codeArea().undo();
            } else {
                state.undo();
            }
        });
        MenuItem redo = new MenuItem(I18n.t("menu.edit.redo"));
        redo.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+Z"));
        redo.setOnAction(e -> {
            if (currentView == View.CODE) {
                canvasPane.codeArea().redo();
            } else {
                state.redo();
            }
        });

        MenuItem cut = new MenuItem(I18n.t("menu.edit.cut"));
        cut.setAccelerator(KeyCombination.keyCombination("Shortcut+X"));
        cut.setOnAction(e -> clipboardAction(ClipboardOp.CUT));
        MenuItem copy = new MenuItem(I18n.t("menu.edit.copy"));
        copy.setAccelerator(KeyCombination.keyCombination("Shortcut+C"));
        copy.setOnAction(e -> clipboardAction(ClipboardOp.COPY));
        MenuItem paste = new MenuItem(I18n.t("menu.edit.paste"));
        paste.setAccelerator(KeyCombination.keyCombination("Shortcut+V"));
        paste.setOnAction(e -> clipboardAction(ClipboardOp.PASTE));

        edit.getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste);

        MenuBar bar = new MenuBar(file, edit, buildToolsMenu());
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private enum ClipboardOp { CUT, COPY, PASTE }

    /**
     * Menu accelerators shadow the native shortcuts of focused text controls (macOS system
     * menu takes them first) — forward to the focused editor; otherwise act on the canvas
     * selection (F-1.40).
     */
    private void clipboardAction(ClipboardOp op) {
        var owner = getScene().getFocusOwner();
        if (owner instanceof javafx.scene.control.TextInputControl text) {
            switch (op) {
                case CUT -> text.cut();
                case COPY -> text.copy();
                case PASTE -> text.paste();
            }
            return;
        }
        if (owner instanceof org.fxmisc.richtext.CodeArea code) {
            switch (op) {
                case CUT -> code.cut();
                case COPY -> code.copy();
                case PASTE -> code.paste();
            }
            return;
        }
        switch (op) {
            case CUT -> state.cutSelection();
            case COPY -> state.copySelection();
            case PASTE -> state.paste();
        }
    }

    // ---------- Tools menu: Template Viewer conversion parity (F-5.12/13/15) ----------

    private Menu buildToolsMenu() {
        Menu tools = new Menu(I18n.t("menu.tools"));

        // Engine-independent tools: XLIFF generation (F-9.5) + subtemplate imports (F-6.2).
        MenuItem xliffGen = new MenuItem(I18n.t("menu.tools.xliff"));
        xliffGen.setOnAction(e -> generateXliff());
        MenuItem addImport = new MenuItem(I18n.t("menu.tools.import"));
        addImport.setOnAction(e -> addSubtemplateImport());
        MenuItem browseSubs = new MenuItem(I18n.t("menu.tools.subtemplates"));
        browseSubs.setOnAction(e -> browseSubtemplates());
        tools.getItems().addAll(xliffGen, addImport, browseSubs, new SeparatorMenuItem());

        var conversions = engines.byId(dev.stylus.engine.api.EngineId.BIP)
                .flatMap(dev.stylus.engine.api.ReportEngine::conversions);
        if (conversions.isEmpty()) {
            MenuItem needsBip = new MenuItem(I18n.t("menu.tools.needsBip"));
            needsBip.setDisable(true);
            tools.getItems().add(needsBip);
            return tools;
        }
        var c = conversions.get();

        MenuItem rtf = new MenuItem(I18n.t("menu.tools.rtf2xsl"));
        rtf.setOnAction(e -> {
            Path in = chooseOpen("menu.tools.rtf2xsl", "dialog.filter.rtf", "*.rtf");
            if (in == null) {
                return;
            }
            Path out = chooseSave("menu.tools.rtf2xsl", xslNameFor(in));
            if (out != null) {
                runTool("rtfToXsl", () -> c.rtfToXsl(in, out), true);
            }
        });

        MenuItem rtfXliff = new MenuItem(I18n.t("menu.tools.rtf2xslXliff"));
        rtfXliff.setOnAction(e -> {
            Path in = chooseOpen("menu.tools.rtf2xslXliff", "dialog.filter.rtf", "*.rtf");
            if (in == null) {
                return;
            }
            Path out = chooseSave("menu.tools.rtf2xslXliff", xslNameFor(in));
            if (out != null) {
                Path xliff = out.resolveSibling(
                        out.getFileName().toString().replaceFirst("\\.[^.]+$", "") + ".xlf");
                runTool("rtfToXslAndXliff", () -> c.rtfToXslAndXliff(in, out, xliff), true);
            }
        });

        MenuItem excel = new MenuItem(I18n.t("menu.tools.excel2xsl"));
        excel.setOnAction(e -> {
            Path in = chooseOpen("menu.tools.excel2xsl", "dialog.filter.excel", "*.xls");
            if (in == null) {
                return;
            }
            Path out = chooseSave("menu.tools.excel2xsl", xslNameFor(in));
            if (out != null) {
                runTool("excelToXsl", () -> c.excelToXsl(in, out), true);
            }
        });

        MenuItem etext = new MenuItem(I18n.t("menu.tools.etext2xsl"));
        etext.setOnAction(e -> {
            Path in = chooseOpen("menu.tools.etext2xsl", "dialog.filter.rtf", "*.rtf");
            if (in == null) {
                return;
            }
            Path data = chooseOpen("tools.chooseData", "tree.badge.xml", "*.xml");
            if (data == null) {
                return;
            }
            Path out = chooseSave("menu.tools.etext2xsl", xslNameFor(in));
            if (out != null) {
                runTool("etextToXsl", () -> c.etextToXsl(in, data, out), true);
            }
        });

        MenuItem xpt = new MenuItem(I18n.t("menu.tools.xpt2xsl"));
        xpt.setOnAction(e -> {
            Path in = chooseOpen("menu.tools.xpt2xsl", "dialog.filter.xpt", "*.xpt");
            if (in == null) {
                return;
            }
            Path out = chooseSave("menu.tools.xpt2xsl", xslNameFor(in));
            if (out != null) {
                runTool("xptToXsl", () -> c.xptToXsl(in, out), true);
            }
        });

        MenuItem mergeFo = new MenuItem(I18n.t("menu.tools.mergeFo"));
        mergeFo.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("menu.tools.mergeFo"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(I18n.t("dialog.filter.fo"), "*.fo"));
            var files = chooser.showOpenMultipleDialog(getScene().getWindow());
            if (files == null || files.size() < 2) {
                return;
            }
            Path out = chooseSave("menu.tools.mergeFo", "merged.fo");
            if (out != null) {
                var paths = files.stream().map(java.io.File::toPath).toList();
                runTool("mergeFo", () -> c.mergeFo(paths, out), false);
            }
        });

        MenuItem profile = new MenuItem(I18n.t("menu.tools.profile"));
        profile.setOnAction(e -> {
            Path in = chooseOpen("menu.tools.profile", "dialog.filter.templates", "*.xsl", "*.xslt");
            if (in == null) {
                return;
            }
            String base = in.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            Path out = chooseSave("menu.tools.profile", base + "-profiled.xsl");
            if (out != null) {
                runTool("injectProfiling", () -> c.injectProfiling(in, out), true);
            }
        });

        tools.getItems().addAll(rtf, rtfXliff, excel, etext, xpt,
                new SeparatorMenuItem(), mergeFo, profile);
        return tools;
    }

    /** Creates a subtemplate library skeleton (named template + params) and opens it (F-6.1). */
    private void createSubtemplate() {
        Path out = chooseSave("menu.file.newSubtemplate", "subtemplate.xsl");
        if (out == null) {
            return;
        }
        String skeleton = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- Stylus subtemplate library: named templates callable via xsl:call-template. -->
                <xsl:stylesheet version="1.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:fo="http://www.w3.org/1999/XSL/Format">

                  <xsl:template name="example">
                    <xsl:param name="text" select="''"/>
                    <fo:block><xsl:value-of select="$text"/></fo:block>
                  </xsl:template>

                </xsl:stylesheet>
                """;
        try {
            Files.writeString(out, skeleton);
            openTemplate(out);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR,
                    I18n.t("menu.tools.failed", out.getFileName(), ex.getMessage())).showAndWait();
        }
    }

    /** Generate an XLIFF skeleton from the current document's translatable text (F-9.5). */
    private void generateXliff() {
        syncCodeToModelIfNeeded();
        var doc = state.document();
        if (doc.isFullyOpaque()) {
            new Alert(Alert.AlertType.WARNING, I18n.t("tools.xliff.opaque")).showAndWait();
            return;
        }
        javafx.scene.control.TextInputDialog langDialog =
                new javafx.scene.control.TextInputDialog("nl");
        langDialog.setTitle(I18n.t("menu.tools.xliff"));
        langDialog.setHeaderText(null);
        langDialog.setContentText(I18n.t("tools.xliff.target"));
        String target = langDialog.showAndWait().orElse(null);
        if (target == null || target.isBlank()) {
            return;
        }
        Path out = chooseSave("menu.tools.xliff", doc.title() + "-" + target.strip() + ".xlf");
        if (out == null) {
            return;
        }
        try {
            Files.writeString(out, dev.stylus.xliff.XliffFile.generate(
                    doc, I18n.currentLocale().getLanguage(), target.strip()));
            new Alert(Alert.AlertType.INFORMATION,
                    I18n.t("menu.tools.done", out)).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR,
                    I18n.t("menu.tools.failed", "XLIFF", ex.getMessage())).showAndWait();
        }
    }

    /** Adds an xsl:import of a subtemplate to the current document (F-6.2). */
    private void addSubtemplateImport() {
        Path chosen = chooseOpen("menu.tools.import", "dialog.filter.templates", "*.xsl", "*.xslt");
        if (chosen == null) {
            return;
        }
        Path base = state.templateFile() != null ? state.templateFile().getParent() : null;
        String href = base != null && chosen.startsWith(base)
                ? base.relativize(chosen).toString().replace('\\', '/')
                : chosen.toUri().toString();
        state.document().imports().add(href);
        state.documentEdited();
    }

    /** Subtemplate browser (F-6.5): callable templates across imports; double-click inserts. */
    private void browseSubtemplates() {
        var templates = Subtemplates.discover(state);
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog =
                new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("menu.tools.subtemplates"));
        javafx.scene.control.ListView<Subtemplates.NamedTemplate> list =
                new javafx.scene.control.ListView<>();
        list.getItems().setAll(templates);
        list.setPrefSize(420, 240);
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Subtemplates.NamedTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.signature() + "  —  " + item.source());
            }
        });
        final boolean[] inserted = {false};
        Runnable insert = () -> {
            Subtemplates.NamedTemplate chosen = list.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                inserted[0] = true;
                StringBuilder xml = new StringBuilder("<xsl:call-template name=\"")
                        .append(chosen.name()).append("\">");
                for (String param : chosen.params()) {
                    xml.append("<xsl:with-param name=\"").append(param).append("\" select=\"''\"/>");
                }
                String call = chosen.params().isEmpty()
                        ? "<xsl:call-template name=\"" + chosen.name() + "\"/>"
                        : xml + "</xsl:call-template>";
                state.document().bands().add(new dev.stylus.model.OpaqueBand(call));
                state.documentEdited();
                dialog.setResult(javafx.scene.control.ButtonType.OK);
                dialog.close();
            }
        };
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                insert.run();
            }
        });
        javafx.scene.control.Label hint = new javafx.scene.control.Label(
                I18n.t(templates.isEmpty() ? "tools.subtemplates.none" : "tools.subtemplates.hint"));
        hint.getStyleClass().add("props-note");
        hint.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, list, hint));
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK
                && !inserted[0]) {
            insert.run();
        }
    }

    /** Runs a conversion off the FX thread; XSL results open straight in the designer. */
    private void runTool(String name, java.util.concurrent.Callable<Path> job, boolean openResult) {
        Thread worker = new Thread(() -> {
            try {
                Path result = job.call();
                javafx.application.Platform.runLater(() -> {
                    if (openResult) {
                        openTemplate(result);
                    } else {
                        new Alert(Alert.AlertType.INFORMATION,
                                I18n.t("menu.tools.done", result)).showAndWait();
                    }
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                        I18n.t("menu.tools.failed", name, message)).showAndWait());
            }
        }, "stylus-tool-" + name);
        worker.setDaemon(true);
        worker.start();
    }

    private Path chooseOpen(String titleKey, String filterKey, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t(titleKey));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.t(filterKey), extensions),
                new FileChooser.ExtensionFilter(I18n.t("dialog.filter.all"), "*.*"));
        java.io.File f = chooser.showOpenDialog(getScene().getWindow());
        return f == null ? null : f.toPath();
    }

    private Path chooseSave(String titleKey, String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t(titleKey));
        chooser.setInitialFileName(initialName);
        java.io.File f = chooser.showSaveDialog(getScene().getWindow());
        return f == null ? null : f.toPath();
    }

    private static String xslNameFor(Path template) {
        return template.getFileName().toString().replaceFirst("\\.[^.]+$", "") + ".xsl";
    }

    private void chooseAndOpenTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("menu.file.openTemplate"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.t("dialog.filter.templates"),
                        "*.xsl", "*.xslt", "*.fo"),
                new FileChooser.ExtensionFilter(I18n.t("dialog.filter.all"), "*.*"));
        java.io.File f = chooser.showOpenDialog(getScene().getWindow());
        if (f != null) {
            openTemplate(f.toPath());
            benchPane.setWorkingDir(f.toPath().getParent());
        }
    }

    /** Opens a template through the reader — recognized bands render, the rest is opaque (N7). */
    private void openTemplate(Path template) {
        try {
            String source = Files.readString(template);
            String baseName = template.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            var doc = reader.read(source, baseName);
            state.setDocument(doc, template);
            lastGeneratedCode = source;
            canvasPane.codeArea().setText(source);
            switchView(View.DESIGN);
            appBar.selectView(View.DESIGN);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    I18n.t("code.openError", template, e.getMessage())).showAndWait();
        }
    }

    private void chooseAndOpenSample() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("menu.file.openSample"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML", "*.xml"));
        java.io.File f = chooser.showOpenDialog(getScene().getWindow());
        if (f != null) {
            loadSample(f.toPath());
        }
    }

    private void loadSample(Path xml) {
        try {
            XmlSampleTree.Parsed parsed = XmlSampleTree.parse(xml);
            state.setSample(parsed, xml);
            appBar.setDataSourceName("— " + xml.getFileName());
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    I18n.t("tree.openError", xml.getFileName(), e.getMessage())).showAndWait();
        }
    }

    private void saveTemplate(boolean forceDialog) {
        syncCodeToModelIfNeeded();
        Path target = state.templateFile();
        if (forceDialog || target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("menu.file.saveAs"));
            chooser.setInitialFileName(target != null
                    ? target.getFileName().toString() : "Untitled.xsl");
            java.io.File f = chooser.showSaveDialog(getScene().getWindow());
            if (f == null) {
                return;
            }
            target = f.toPath();
        }
        try {
            String source = writer.write(state.document());
            Files.writeString(target, source);
            state.setTemplateFile(target);
            state.document().setOriginalSource(source);
            state.markSaved();
            lastGeneratedCode = source;
            appBar.setFileName(target.getFileName().toString());
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    // ---------- view switch + code sync (F-1.36) ----------

    private void switchView(View view) {
        if (view == currentView) {
            return;
        }
        if (currentView == View.CODE) {
            syncCodeToModelIfNeeded();
        }
        if (view == View.CODE) {
            lastGeneratedCode = writer.write(state.document());
            canvasPane.codeArea().setText(lastGeneratedCode);
        }
        currentView = view;
        canvasPane.showView(view);
    }

    /** Re-parse edited code into the model; unrecognized content lands as opaque bands. */
    private void syncCodeToModelIfNeeded() {
        String code = canvasPane.codeArea().getText();
        if (code == null || code.equals(lastGeneratedCode)) {
            return;
        }
        String title = state.document().title();
        var doc = reader.read(code, title);
        doc.touch(); // diverged from the file on disk until saved
        state.setDocument(doc, state.templateFile());
        lastGeneratedCode = code;
    }

    // ---------- bench + chrome ----------

    private void onOutputReady(Path output, dev.stylus.engine.api.OutputFormat format) {
        previewPane.showOutput(output, format);
        appBar.selectView(View.PREVIEW);
        switchView(View.PREVIEW);
    }

    private void toggleBench() {
        if (canvasSplit.getItems().contains(benchPane)) {
            lastBenchDivider = canvasSplit.getDividerPositions()[0];
            canvasSplit.getItems().remove(benchPane);
        } else {
            canvasSplit.getItems().add(benchPane);
            canvasSplit.setDividerPositions(lastBenchDivider);
        }
    }

    private void refreshChrome() {
        appBar.setUnsaved(state.document().isModified());
        Path file = state.templateFile();
        appBar.setFileName(file != null
                ? file.getFileName().toString() : I18n.t("app.untitledFile"));
    }

    private static Separator vSeparator() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.getStyleClass().add("pane-divider");
        return s;
    }
}
