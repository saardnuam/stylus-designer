package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import dev.stylus.app.run.EngineRegistry;
import dev.stylus.app.run.RunService;
import dev.stylus.engine.api.LogEntry;
import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.ReportEngine;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * Test bench — the Template Viewer replacement (catalog §5.1/§5.2): working-directory browser
 * with data + template panes side by side (F-5.1), template filter (F-5.2), output format +
 * locale (F-5.3), XLIFF (F-5.4) and style template (F-5.5) selectors, Start/Cancel with progress
 * and total time (F-5.6), double-click viewers (F-5.7), F5 refresh (F-5.8) and the level-filtered
 * log pane (F-5.9/F-5.10).
 */
public final class TestBenchPane extends VBox {

    /** Template list filters, Template Viewer parity (F-5.2). */
    private enum TemplateFilter {
        ALL("bench.filter.all", "*"),
        XSL_FO("bench.filter.xslfo", "xsl", "xslt", "fo"),
        RTF_ETEXT("bench.filter.rtf", "rtf"),
        PDF_FORMS("bench.filter.pdf", "pdf"),
        EXCEL("bench.filter.excel", "xls", "xlsx"),
        XPT("bench.filter.xpt", "xpt");

        final String key;
        final List<String> extensions;

        TemplateFilter(String key, String... extensions) {
            this.key = key;
            this.extensions = List.of(extensions);
        }

        boolean matches(Path p) {
            if (extensions.contains("*")) {
                return true;
            }
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return extensions.stream().anyMatch(ext -> name.endsWith("." + ext));
        }
    }

    private final EngineRegistry engines;
    private final RunService runService = new RunService();
    private final Preferences prefs = Preferences.userNodeForPackage(TestBenchPane.class);

    private final Label workingDirLabel = new Label();
    private final ObservableList<Path> dataFiles = FXCollections.observableArrayList();
    private final ObservableList<Path> templateFiles = FXCollections.observableArrayList();
    private final FilteredList<Path> filteredTemplates = new FilteredList<>(templateFiles);
    private final ListView<Path> dataList = new ListView<>(dataFiles);
    private final ListView<Path> templateList = new ListView<>(filteredTemplates);
    private final ComboBox<TemplateFilter> filterCombo = new ComboBox<>();
    private final ComboBox<ReportEngine> engineCombo = new ComboBox<>();
    private final ComboBox<OutputFormat> formatCombo = new ComboBox<>();
    private final TextField localeField = new TextField();
    private final TextField xliffField = new TextField();
    private final java.util.Map<String, String> runParameters = new java.util.LinkedHashMap<>();
    private final Button parametersButton = new Button(I18n.t("bench.parameters"));
    private final TextField styleTemplateField = new TextField();
    private final Button startButton = new Button(I18n.t("bench.start"));
    private final Button cancelButton = new Button(I18n.t("bench.cancel"));
    private final ProgressBar progress = new ProgressBar();
    private final Label timeLabel = new Label();

    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final FilteredList<LogEntry> filteredLog = new FilteredList<>(logEntries);
    private final ListView<LogEntry> logList = new ListView<>(filteredLog);
    private final ComboBox<LogLevel> logLevelCombo = new ComboBox<>();

    private final BiConsumer<Path, OutputFormat> onOutputReady;
    private final Consumer<Path> onOpenTemplate;
    private Consumer<dev.stylus.engine.api.EngineId> onEngineChanged = id -> { };

    private Path workingDir;

    /** Notifies when the user picks another engine (drives the engine-aware palette, F-1.31). */
    public void setOnEngineChanged(Consumer<dev.stylus.engine.api.EngineId> listener) {
        this.onEngineChanged = listener;
        ReportEngine current = engineCombo.getValue();
        if (current != null) {
            listener.accept(current.id());
        }
    }

    private final ConfigPane configPane = new ConfigPane();

    public TestBenchPane(EngineRegistry engines,
                         BiConsumer<Path, OutputFormat> onOutputReady,
                         Consumer<Path> onOpenTemplate) {
        this.engines = engines;
        this.onOutputReady = onOutputReady;
        this.onOpenTemplate = onOpenTemplate;
        getStyleClass().add("bench-pane");

        buildControls();
        SplitPane center = buildFilePanes();
        VBox logPane = buildLogPane();

        SplitPane benchSplit = new SplitPane(center, logPane);
        benchSplit.setOrientation(Orientation.VERTICAL);
        benchSplit.setDividerPositions(0.62);

        // Run | Configuration tabs (Template Viewer parity: Files/Settings, F-5.16)
        VBox runContent = new VBox();
        runContent.getChildren().add(getChildren().remove(0)); // controls built above
        runContent.getChildren().add(benchSplit);
        VBox.setVgrow(benchSplit, Priority.ALWAYS);

        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane();
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        javafx.scene.control.Tab runTab =
                new javafx.scene.control.Tab(I18n.t("bench.tab.run"), runContent);
        javafx.scene.control.Tab configTab =
                new javafx.scene.control.Tab(I18n.t("bench.tab.config"), configPane);
        tabs.getTabs().addAll(runTab, configTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().add(tabs);

        // -Dstylus.workingDir=… (tests/dev) beats the remembered dir, beats the home dir.
        String remembered = System.getProperty("stylus.workingDir",
                prefs.get("workingDir", System.getProperty("user.home")));
        setWorkingDir(Path.of(remembered));
    }

    // ---------- UI assembly ----------

    private void buildControls() {
        Button browse = new Button(I18n.t("bench.browse"));
        browse.getStyleClass().add("bench-button");
        browse.setOnAction(e -> chooseWorkingDir());
        workingDirLabel.getStyleClass().add("mono-readout");

        Button refresh = new Button("⟳");
        refresh.getStyleClass().add("bench-button");
        refresh.setTooltip(new Tooltip(I18n.t("bench.refresh")));
        refresh.setOnAction(e -> refreshFiles());

        engineCombo.getItems().setAll(engines.all());
        engineCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ReportEngine engine) {
                return engine == null ? "" : engine.displayName();
            }
            @Override public ReportEngine fromString(String s) { return null; }
        });
        engineCombo.getSelectionModel().select(engines.defaultEngine());
        engineCombo.valueProperty().addListener((obs, old, engine) -> {
            refreshFormats();
            if (engine != null) {
                onEngineChanged.accept(engine.id());
            }
        });

        refreshFormats();

        localeField.setPromptText(I18n.t("bench.locale.prompt"));
        localeField.setPrefWidth(72);
        localeField.setText(Locale.getDefault().toLanguageTag());

        parametersButton.getStyleClass().add("bench-button");
        parametersButton.setOnAction(e -> editParameters());
        xliffField.setPromptText(I18n.t("bench.xliff.prompt"));
        xliffField.setPrefWidth(130);
        Button xliffBrowse = fileBrowseButton(xliffField, "*.xlf", "*.xliff");
        styleTemplateField.setPromptText(I18n.t("bench.styleTemplate.prompt"));
        styleTemplateField.setPrefWidth(130);
        Button styleBrowse = fileBrowseButton(styleTemplateField, "*.xsl", "*.xslt");

        startButton.getStyleClass().add("primary-button");
        startButton.setOnAction(e -> startRun());
        cancelButton.getStyleClass().add("bench-button");
        cancelButton.setDisable(true);
        cancelButton.setOnAction(e -> cancelRun());
        progress.setVisible(false);
        progress.setPrefWidth(90);
        timeLabel.getStyleClass().add("mono-readout");

        HBox row1 = new HBox(8, label("bench.workingDir"), workingDirLabel, browse, refresh);
        row1.setAlignment(Pos.CENTER_LEFT);
        HBox row2 = new HBox(8,
                label("bench.engine"), engineCombo,
                label("bench.format"), formatCombo,
                label("bench.locale"), localeField,
                label("bench.xliff"), xliffField, xliffBrowse,
                parametersButton,
                label("bench.styleTemplate"), styleTemplateField, styleBrowse,
                spacer(), progress, startButton, cancelButton, timeLabel);
        row2.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(6, row1, row2);
        controls.getStyleClass().add("bench-controls");
        getChildren().add(controls);
    }

    private SplitPane buildFilePanes() {
        dataList.setCellFactory(v -> pathCell());
        templateList.setCellFactory(v -> pathCell());
        dataList.getStyleClass().add("file-list");
        templateList.getStyleClass().add("file-list");

        dataList.setOnMouseClicked(e -> {
            Path selected = dataList.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && selected != null) {
                XmlViewerWindow.open(selected, getScene());
            }
        });
        templateList.setOnMouseClicked(e -> {
            Path selected = templateList.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && selected != null) {
                onOpenTemplate.accept(selected);
            }
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F5) {
                refreshFiles();
            }
        });

        filterCombo.getItems().setAll(TemplateFilter.values());
        filterCombo.getSelectionModel().select(TemplateFilter.ALL);
        filterCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TemplateFilter f) {
                return f == null ? "" : I18n.t(f.key);
            }
            @Override public TemplateFilter fromString(String s) { return null; }
        });
        filterCombo.valueProperty().addListener((obs, old, filter) ->
                filteredTemplates.setPredicate(p -> filter == null || filter.matches(p)));

        Label dataHeader = new Label(I18n.t("bench.dataFiles"));
        dataHeader.getStyleClass().add("eyebrow");
        VBox dataPane = new VBox(6, dataHeader, dataList);
        VBox.setVgrow(dataList, Priority.ALWAYS);
        dataPane.getStyleClass().add("bench-file-pane");

        Label templateHeader = new Label(I18n.t("bench.templateFiles"));
        templateHeader.getStyleClass().add("eyebrow");
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        HBox templateHead = new HBox(6, templateHeader, headSpacer, filterCombo);
        templateHead.setAlignment(Pos.CENTER_LEFT);
        VBox templatePane = new VBox(6, templateHead, templateList);
        VBox.setVgrow(templateList, Priority.ALWAYS);
        templatePane.getStyleClass().add("bench-file-pane");

        SplitPane files = new SplitPane(dataPane, templatePane);
        files.setDividerPositions(0.5);
        return files;
    }

    private VBox buildLogPane() {
        logLevelCombo.getItems().setAll(LogLevel.values());
        logLevelCombo.getSelectionModel().select(LogLevel.EVENT);
        logLevelCombo.valueProperty().addListener((obs, old, level) ->
                filteredLog.setPredicate(e -> level == null || e.level().isAtLeast(level)));
        filteredLog.setPredicate(e -> e.level().isAtLeast(LogLevel.EVENT));

        Button clear = new Button(I18n.t("bench.log.clear"));
        clear.getStyleClass().add("bench-button");
        clear.setOnAction(e -> logEntries.clear());

        Label logHeader = new Label(I18n.t("bench.log"));
        logHeader.getStyleClass().add("eyebrow");
        HBox head = new HBox(8, logHeader, spacer(), label("bench.log.level"), logLevelCombo, clear);
        head.setAlignment(Pos.CENTER_LEFT);

        logList.getStyleClass().add("log-list");
        logList.setCellFactory(v -> new ListCell<>() {
            private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
            @Override protected void updateItem(LogEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                getStyleClass().removeIf(c -> c.startsWith("log-"));
                if (empty || entry == null) {
                    setText(null);
                } else {
                    setText(TIME.format(LocalTime.ofInstant(entry.timestamp(),
                            java.time.ZoneId.systemDefault()))
                            + "  [" + entry.level() + "]  " + entry.message());
                    getStyleClass().add("log-" + entry.level().name().toLowerCase(Locale.ROOT));
                }
            }
        });

        VBox logPane = new VBox(6, head, logList);
        VBox.setVgrow(logList, Priority.ALWAYS);
        logPane.getStyleClass().add("bench-log-pane");
        return logPane;
    }

    // ---------- behavior ----------

    private void chooseWorkingDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.t("bench.workingDir"));
        if (workingDir != null && Files.isDirectory(workingDir)) {
            chooser.setInitialDirectory(workingDir.toFile());
        }
        java.io.File dir = chooser.showDialog(getScene().getWindow());
        if (dir != null) {
            setWorkingDir(dir.toPath());
        }
    }

    void setWorkingDir(Path dir) {
        this.workingDir = dir;
        workingDirLabel.setText(dir.toAbsolutePath().toString());
        prefs.put("workingDir", dir.toAbsolutePath().toString());
        refreshFiles();
    }

    private void refreshFiles() {
        dataFiles.clear();
        templateFiles.clear();
        if (workingDir == null || !Files.isDirectory(workingDir)) {
            return;
        }
        List<Path> xml = new ArrayList<>();
        List<Path> templates = new ArrayList<>();
        try (Stream<Path> stream = Files.list(workingDir)) {
            stream.filter(Files::isRegularFile).sorted().forEach(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".xml")) {
                    xml.add(p);
                } else if (!name.startsWith(".")) {
                    templates.add(p);
                }
            });
        } catch (IOException e) {
            log(LogLevel.ERROR, I18n.t("bench.error.listDir", e.getMessage()));
        }
        dataFiles.setAll(xml);
        templateFiles.setAll(templates);
    }

    private void refreshFormats() {
        ReportEngine engine = engineCombo.getValue();
        if (engine == null) {
            return;
        }
        OutputFormat previous = formatCombo.getValue();
        formatCombo.getItems().setAll(engine.supportedFormats().stream().sorted().toList());
        if (previous != null && formatCombo.getItems().contains(previous)) {
            formatCombo.setValue(previous);
        } else {
            formatCombo.setValue(OutputFormat.PDF);
        }
    }

    /** Runs the current selection; used by both the bench Start button and app-bar Run & Preview. */
    public void startRun() {
        ReportEngine engine = engineCombo.getValue();
        Path template = templateList.getSelectionModel().getSelectedItem();
        Path data = dataList.getSelectionModel().getSelectedItem();
        OutputFormat format = formatCombo.getValue();

        if (template == null) {
            log(LogLevel.ERROR, I18n.t("bench.error.noTemplate"));
            return;
        }
        boolean isFo = template.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".fo");
        if (data == null && !isFo) {
            log(LogLevel.ERROR, I18n.t("bench.error.noData"));
            return;
        }

        Path outputDir = workingDir.resolve("output");
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log(LogLevel.ERROR, e.getMessage());
            return;
        }
        String baseName = template.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path output = outputDir.resolve(baseName + "." + format.extension());

        // XLIFF: BIP applies it natively at run time; FOP gets a pre-translated template (F-9.5).
        Path effectiveTemplate = template;
        Path xliff = xliffField.getText().isBlank() ? null : Path.of(xliffField.getText().trim());
        if (xliff != null && engine.id() != dev.stylus.engine.api.EngineId.BIP) {
            try {
                effectiveTemplate = dev.stylus.app.run.RunService.applyXliffToTemplate(template, xliff);
                log(LogLevel.PROCEDURE, I18n.t("bench.xliff.applied", xliff.getFileName()));
            } catch (Exception ex) {
                log(LogLevel.ERROR, I18n.t("bench.xliff.failed", ex.getMessage()));
                return;
            }
        }

        RunRequest.Builder request = RunRequest.builder()
                .template(effectiveTemplate)
                .data(data)
                .format(format)
                .output(output);
        Path activeConfig = configPane.activeConfigFor(engine.id());
        // Per-run template parameters (F-5.21): FOP takes them as XSLT params; BIP reads them
        // from the config's xslt.* channel, so fold them into a temp copy of the active config.
        if (!runParameters.isEmpty()) {
            if (engine.id() == dev.stylus.engine.api.EngineId.BIP) {
                try {
                    dev.stylus.config.XdoConfig merged = activeConfig != null
                            ? dev.stylus.config.XdoConfig.load(activeConfig)
                            : new dev.stylus.config.XdoConfig();
                    runParameters.forEach((k, v) -> merged.setProperty("xslt." + k, v));
                    Path temp = Files.createTempFile("stylus-params-", ".cfg");
                    temp.toFile().deleteOnExit();
                    merged.save(temp);
                    activeConfig = temp;
                } catch (Exception ex) {
                    log(LogLevel.ERROR, ex.getMessage());
                    return;
                }
            } else {
                request.parameters(runParameters);
            }
            log(LogLevel.PROCEDURE, I18n.t("bench.parameters.used", runParameters.size()));
        }
        if (activeConfig != null) {
            request.engineConfig(activeConfig);
            log(LogLevel.PROCEDURE, I18n.t("bench.run.config", activeConfig.getFileName()));
        }
        String tag = localeField.getText();
        if (tag != null && !tag.isBlank()) {
            request.outputLocale(Locale.forLanguageTag(tag.trim()));
        }
        if (xliff != null && engine.id() == dev.stylus.engine.api.EngineId.BIP) {
            request.xliff(xliff);
        }
        if (!styleTemplateField.getText().isBlank()) {
            request.styleTemplate(Path.of(styleTemplateField.getText().trim()));
        }

        log(LogLevel.EVENT, I18n.t("bench.run.started", template.getFileName(),
                engine.displayName(), format));
        startButton.setDisable(true);
        cancelButton.setDisable(false);
        progress.setVisible(true);
        progress.setProgress(-1);
        timeLabel.setText("");

        runService.run(engine, request.build(), this::log, result -> {
            startButton.setDisable(false);
            cancelButton.setDisable(true);
            progress.setVisible(false);
            timeLabel.setText(result.duration().toMillis() + " ms");
            if (result.success()) {
                onOutputReady.accept(result.output(), format);
            }
        });
    }

    /** Name/value rows for xslt parameters, kept for the session (F-5.21). */
    private void editParameters() {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog =
                new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("bench.parameters"));
        javafx.scene.layout.VBox rows = new javafx.scene.layout.VBox(6);
        java.util.List<TextField[]> fields = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, String> addRow = (name, value) -> {
            TextField n = new TextField(name);
            n.setPromptText(I18n.t("bench.parameters.name"));
            TextField v = new TextField(value);
            v.setPromptText(I18n.t("bench.parameters.value"));
            fields.add(new TextField[] {n, v});
            rows.getChildren().add(new HBox(6, n, v));
        };
        runParameters.forEach(addRow);
        if (runParameters.isEmpty()) {
            addRow.accept("", "");
        }
        Button more = new Button("+");
        more.getStyleClass().add("bench-button");
        more.setOnAction(e -> addRow.accept("", ""));
        rows.getChildren().add(more);
        dialog.getDialogPane().setContent(rows);
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        dialog.showAndWait().ifPresent(button -> {
            if (button != javafx.scene.control.ButtonType.OK) {
                return;
            }
            runParameters.clear();
            for (TextField[] pair : fields) {
                if (!pair[0].getText().isBlank()) {
                    runParameters.put(pair[0].getText().strip(), pair[1].getText());
                }
            }
            parametersButton.setText(runParameters.isEmpty()
                    ? I18n.t("bench.parameters")
                    : I18n.t("bench.parameters") + " (" + runParameters.size() + ")");
        });
    }

    private void cancelRun() {
        runService.cancel();
        startButton.setDisable(false);
        cancelButton.setDisable(true);
        progress.setVisible(false);
        log(LogLevel.EVENT, I18n.t("bench.run.cancelled"));
    }

    /** True when a template row is selected (drives Run & Preview enablement). */
    public boolean hasRunnableSelection() {
        return templateList.getSelectionModel().getSelectedItem() != null;
    }

    private void log(LogEntry entry) {
        logEntries.add(entry);
        if (!filteredLog.isEmpty()) {
            logList.scrollTo(filteredLog.size() - 1);
        }
    }

    private void log(LogLevel level, String message) {
        if (Platform.isFxApplicationThread()) {
            log(LogEntry.of(level, message));
        } else {
            Platform.runLater(() -> log(LogEntry.of(level, message)));
        }
    }

    // ---------- helpers ----------

    private Button fileBrowseButton(TextField target, String... patterns) {
        Button b = new Button("…");
        b.getStyleClass().add("bench-button");
        b.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(String.join(", ", patterns), patterns));
            if (workingDir != null && Files.isDirectory(workingDir)) {
                chooser.setInitialDirectory(workingDir.toFile());
            }
            java.io.File f = chooser.showOpenDialog(getScene().getWindow());
            if (f != null) {
                target.setText(f.getAbsolutePath());
            }
        });
        return b;
    }

    private static ListCell<Path> pathCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        };
    }

    private static Label label(String key) {
        Label l = new Label(I18n.t(key));
        l.getStyleClass().add("bench-label");
        return l;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
