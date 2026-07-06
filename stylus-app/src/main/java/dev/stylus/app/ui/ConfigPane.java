package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import dev.stylus.config.PropertyCatalog;
import dev.stylus.config.XdoConfig;
import dev.stylus.config.XdoConfigException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * xdo.cfg configuration editor (F-5.16..F-5.23): load/validate/reload/save BIP config files,
 * grouped + searchable property table over the full doc-04 catalog with free-form custom keys,
 * quick switching between recent configs (F-5.18), plus the fop.xconf path for the FOP engine
 * (F-5.22 minimal). The active files feed every bench run.
 */
final class ConfigPane extends VBox {

    /** One editable row: catalog property or file-set/custom key. */
    public static final class Row {
        final String name;
        final String group;
        final boolean known;
        final SimpleStringProperty value = new SimpleStringProperty("");

        Row(String name, String group, boolean known) {
            this.name = name;
            this.group = group;
            this.known = known;
        }

        public String getName() { return name; }
        public String getGroup() { return group; }
        public SimpleStringProperty valueProperty() { return value; }
    }

    private final Preferences prefs = Preferences.userNodeForPackage(ConfigPane.class);
    private final ComboBox<String> configSwitcher = new ComboBox<>();
    private final TextField fopXconfField = new TextField();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> filteredRows = new FilteredList<>(rows);
    private final TableView<Row> table = new TableView<>(filteredRows);
    private final TextField search = new TextField();
    private final CheckBox onlySet = new CheckBox(I18n.t("config.onlySet"));
    private final Label statusLabel = new Label();

    private XdoConfig config = new XdoConfig();
    private Path configFile;

    ConfigPane() {
        getStyleClass().add("bench-file-pane");
        setSpacing(8);

        // --- file row: switcher + browse/reload/save ---
        configSwitcher.setPromptText(I18n.t("config.noFile"));
        configSwitcher.setPrefWidth(340);
        configSwitcher.getItems().setAll(recentConfigs());
        configSwitcher.setOnAction(e -> {
            String chosen = configSwitcher.getValue();
            if (chosen != null && !chosen.isBlank() && (configFile == null
                    || !configFile.toString().equals(chosen))) {
                loadConfig(Path.of(chosen));
            }
        });

        Button browse = benchButton(I18n.t("bench.browse"), () -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("config.open"));
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("xdo.cfg", "*.cfg", "*.xml"),
                    new FileChooser.ExtensionFilter(I18n.t("dialog.filter.all"), "*.*"));
            java.io.File f = chooser.showOpenDialog(getScene().getWindow());
            if (f != null) {
                loadConfig(f.toPath());
            }
        });
        Button reload = benchButton(I18n.t("config.reload"), () -> {
            if (configFile != null) {
                loadConfig(configFile);
            }
        });
        Button save = benchButton(I18n.t("config.save"), this::saveConfig);
        Button saveAs = benchButton(I18n.t("config.saveAs"), this::saveConfigAs);
        Button fonts = benchButton(I18n.t("config.fonts"), this::editFonts);

        Label configLabel = new Label(I18n.t("config.file"));
        configLabel.getStyleClass().add("bench-label");
        HBox fileRow = new HBox(8, configLabel, configSwitcher, browse, reload, save, saveAs,
                fonts, statusLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        statusLabel.getStyleClass().add("mono-readout");

        // --- fop.xconf row (F-5.22 minimal: path used by FOP runs) ---
        Label fopLabel = new Label(I18n.t("config.fopXconf"));
        fopLabel.getStyleClass().add("bench-label");
        fopXconfField.setPromptText(I18n.t("config.fopXconf.prompt"));
        fopXconfField.setPrefWidth(340);
        fopXconfField.setText(prefs.get("fopXconf", ""));
        fopXconfField.textProperty().addListener((obs, old, text) ->
                prefs.put("fopXconf", text == null ? "" : text));
        Button fopBrowse = benchButton("…", () -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("fop.xconf", "*.xconf", "*.xml"));
            java.io.File f = chooser.showOpenDialog(getScene().getWindow());
            if (f != null) {
                fopXconfField.setText(f.getAbsolutePath());
            }
        });
        HBox fopRow = new HBox(8, fopLabel, fopXconfField, fopBrowse);
        fopRow.setAlignment(Pos.CENTER_LEFT);

        // --- filter row ---
        search.setPromptText(I18n.t("config.search"));
        search.getStyleClass().add("tree-search");
        search.setPrefWidth(220);
        search.textProperty().addListener((obs, old, text) -> refilter());
        onlySet.setOnAction(e -> refilter());
        Button addCustom = benchButton(I18n.t("config.addCustom"), this::addCustomProperty);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox filterRow = new HBox(8, search, onlySet, spacer, addCustom);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // --- property table (F-5.16) ---
        TableColumn<Row, String> nameCol = new TableColumn<>(I18n.t("config.col.property"));
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        nameCol.setPrefWidth(280);
        TableColumn<Row, String> valueCol = new TableColumn<>(I18n.t("config.col.value"));
        valueCol.setCellValueFactory(cd -> cd.getValue().valueProperty());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(e -> e.getRowValue().valueProperty().set(e.getNewValue()));
        valueCol.setPrefWidth(260);
        TableColumn<Row, String> groupCol = new TableColumn<>(I18n.t("config.col.group"));
        groupCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getGroup()));
        groupCol.setPrefWidth(120);
        table.getColumns().setAll(List.of(nameCol, valueCol, groupCol));
        table.setEditable(true);
        table.getStyleClass().add("config-table");
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(fileRow, fopRow, filterRow, table);

        seedRows();
        String last = prefs.get("lastConfig", null);
        if (last != null && Files.isRegularFile(Path.of(last))) {
            loadConfig(Path.of(last));
        }
    }

    // ---------- run integration ----------

    /** Active config file for a bench run of the given engine (null = engine defaults). */
    Path activeConfigFor(dev.stylus.engine.api.EngineId engine) {
        if (engine == dev.stylus.engine.api.EngineId.BIP) {
            return configFile;
        }
        String xconf = fopXconfField.getText();
        if (xconf != null && !xconf.isBlank() && Files.isRegularFile(Path.of(xconf.trim()))) {
            return Path.of(xconf.trim());
        }
        return null;
    }

    // ---------- load/save ----------

    private void seedRows() {
        rows.clear();
        PropertyCatalog.instance().all().values().forEach(entry ->
                rows.add(new Row(entry.name(), entry.group(), true)));
        refilter();
    }

    private void loadConfig(Path file) {
        try {
            XdoConfig loaded = XdoConfig.load(file);
            this.config = loaded;
            this.configFile = file;
            seedRows();
            loaded.properties().forEach(this::setRowValue);
            statusLabel.setText(I18n.t("config.loaded", loaded.properties().size()));
            rememberConfig(file);
        } catch (XdoConfigException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void setRowValue(String name, String value) {
        for (Row row : rows) {
            if (row.name.equals(name)) {
                row.value.set(value);
                return;
            }
        }
        Row custom = new Row(name, PropertyCatalog.instance().groupOf(name),
                PropertyCatalog.instance().isKnown(name));
        custom.value.set(value);
        rows.add(custom);
    }

    private void saveConfig() {
        if (configFile == null) {
            saveConfigAs();
            return;
        }
        writeTo(configFile);
    }

    private void saveConfigAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("config.saveAs"));
        chooser.setInitialFileName(configFile != null
                ? configFile.getFileName().toString() : "xdo.cfg");
        java.io.File f = chooser.showSaveDialog(getScene().getWindow());
        if (f != null) {
            configFile = f.toPath();
            writeTo(configFile);
            rememberConfig(configFile);
        }
    }

    private void writeTo(Path file) {
        try {
            // Session values → config model: only non-blank values are written (F-5.19).
            XdoConfig out = new XdoConfig();
            for (Row row : rows) {
                String value = row.value.get();
                if (value != null && !value.isBlank()) {
                    out.setProperty(row.name, value.strip());
                }
            }
            config.fonts().forEach(out::addFont); // maintained via the Fonts… dialog (F-5.20)
            out.save(file);
            statusLabel.setText(I18n.t("config.saved", file.getFileName()));
        } catch (XdoConfigException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    // ---------- font mappings (F-5.20: xdo.cfg <fonts> section) ----------

    /** One editable font-mapping row in the dialog. */
    public static final class FontRow {
        final SimpleStringProperty family = new SimpleStringProperty("");
        final SimpleStringProperty style = new SimpleStringProperty("normal");
        final SimpleStringProperty weight = new SimpleStringProperty("normal");
        final SimpleStringProperty path = new SimpleStringProperty("");

        public SimpleStringProperty familyProperty() { return family; }
        public SimpleStringProperty styleProperty() { return style; }
        public SimpleStringProperty weightProperty() { return weight; }
        public SimpleStringProperty pathProperty() { return path; }
    }

    private void editFonts() {
        ObservableList<FontRow> fontRows = FXCollections.observableArrayList();
        for (dev.stylus.config.FontMapping mapping : config.fonts()) {
            FontRow row = new FontRow();
            row.family.set(mapping.family());
            row.style.set(mapping.style());
            row.weight.set(mapping.weight());
            row.path.set(mapping.truetypePath());
            fontRows.add(row);
        }

        TableView<FontRow> fontTable = new TableView<>(fontRows);
        fontTable.setEditable(true);
        fontTable.getStyleClass().add("config-table");
        fontTable.getColumns().setAll(List.of(
                fontColumn("config.fonts.family", FontRow::familyProperty, 140),
                fontColumn("config.fonts.style", FontRow::styleProperty, 80),
                fontColumn("config.fonts.weight", FontRow::weightProperty, 80),
                fontColumn("config.fonts.path", FontRow::pathProperty, 320)));
        fontTable.setPrefSize(640, 260);

        Button add = benchButton(I18n.t("config.fonts.add"), () -> {
            FontRow row = new FontRow();
            fontRows.add(row);
            fontTable.getSelectionModel().select(row);
        });
        Button remove = benchButton(I18n.t("config.fonts.remove"), () -> {
            FontRow selected = fontTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                fontRows.remove(selected);
            }
        });
        Button browseTtf = benchButton("…", () -> {
            FontRow selected = fontTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("TrueType", "*.ttf", "*.ttc", "*.otf"));
            java.io.File f = chooser.showOpenDialog(getScene().getWindow());
            if (f != null) {
                selected.path.set(f.getAbsolutePath());
            }
        });
        Label hint = new Label(I18n.t("config.fonts.hint"));
        hint.getStyleClass().add("props-note");
        HBox buttons = new HBox(8, add, remove, browseTtf, hint);
        buttons.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog =
                new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("config.fonts.title"));
        dialog.getDialogPane().setContent(new VBox(8, fontTable, buttons));
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());

        dialog.showAndWait().ifPresent(button -> {
            if (button == javafx.scene.control.ButtonType.OK) {
                for (dev.stylus.config.FontMapping mapping : List.copyOf(config.fonts())) {
                    config.removeFont(mapping);
                }
                for (FontRow row : fontRows) {
                    if (!row.family.get().isBlank() && !row.path.get().isBlank()) {
                        config.addFont(new dev.stylus.config.FontMapping(
                                row.family.get().strip(), row.style.get().strip(),
                                row.weight.get().strip(), row.path.get().strip()));
                    }
                }
                statusLabel.setText(I18n.t("config.fonts.applied", config.fonts().size()));
            }
        });
    }

    private TableColumn<FontRow, String> fontColumn(String key,
            java.util.function.Function<FontRow, SimpleStringProperty> property, int width) {
        TableColumn<FontRow, String> column = new TableColumn<>(I18n.t(key));
        column.setCellValueFactory(cd -> property.apply(cd.getValue()));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        column.setOnEditCommit(e -> property.apply(e.getRowValue()).set(e.getNewValue()));
        column.setPrefWidth(width);
        return column;
    }

    private void addCustomProperty() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle(I18n.t("config.addCustom"));
        dialog.setHeaderText(I18n.t("config.addCustom.header"));
        dialog.setContentText(I18n.t("config.addCustom.prompt"));
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank() && rows.stream().noneMatch(r -> r.name.equals(name.strip()))) {
                Row row = new Row(name.strip(),
                        PropertyCatalog.instance().groupOf(name.strip()),
                        PropertyCatalog.instance().isKnown(name.strip()));
                rows.add(row);
                refilter();
                table.getSelectionModel().select(row);
                table.scrollTo(row);
            }
        });
    }

    // ---------- helpers ----------

    private void refilter() {
        String query = search.getText() == null
                ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        boolean setOnly = onlySet.isSelected();
        filteredRows.setPredicate(row -> {
            if (setOnly && (row.value.get() == null || row.value.get().isBlank())) {
                return false;
            }
            return query.isEmpty()
                    || row.name.toLowerCase(Locale.ROOT).contains(query)
                    || row.group.toLowerCase(Locale.ROOT).contains(query);
        });
    }

    private List<String> recentConfigs() {
        String joined = prefs.get("recentConfigs", "");
        return joined.isBlank() ? List.of() : Arrays.asList(joined.split("\n"));
    }

    private void rememberConfig(Path file) {
        prefs.put("lastConfig", file.toAbsolutePath().toString());
        Set<String> recent = new LinkedHashSet<>();
        recent.add(file.toAbsolutePath().toString());
        recent.addAll(recentConfigs());
        List<String> trimmed = new ArrayList<>(recent).subList(0, Math.min(recent.size(), 8));
        prefs.put("recentConfigs", String.join("\n", trimmed));
        configSwitcher.getItems().setAll(trimmed);
        configSwitcher.setValue(file.toAbsolutePath().toString());
    }

    private static Button benchButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("bench-button");
        b.setOnAction(e -> action.run());
        return b;
    }
}
