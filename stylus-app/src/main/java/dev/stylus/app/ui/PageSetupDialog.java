package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.model.MasterSelector;
import dev.stylus.model.PageMaster;
import dev.stylus.model.PageSetup;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Page setup (F-2.7) + conditional page masters (F-2.26/F-2.27): single-layout geometry, or
 * named masters with first/last/rest/only × odd/even × blank selector rows (first match wins).
 */
final class PageSetupDialog {

    /** Mutable working copy of one master while the dialog is open. */
    private static final class MasterRow {
        String name;
        PageSetup geometry;

        MasterRow(String name, PageSetup geometry) {
            this.name = name;
            this.geometry = geometry;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class SelectorRow {
        final ComboBox<String> master = new ComboBox<>();
        final ComboBox<String> position = options("any", "first", "last", "rest", "only");
        final ComboBox<String> oddEven = options("any", "odd", "even");
        final ComboBox<String> blank = options("any", "blank", "not-blank");
    }

    static void show(Window owner, DesignerState state) {
        var doc = state.document();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("pageSetup.title"));
        dialog.initOwner(owner);

        ToggleGroup mode = new ToggleGroup();
        RadioButton single = new RadioButton(I18n.t("pageSetup.single"));
        RadioButton conditional = new RadioButton(I18n.t("pageSetup.conditional"));
        single.setToggleGroup(mode);
        conditional.setToggleGroup(mode);
        boolean multi = !doc.pageMasters().isEmpty();
        (multi ? conditional : single).setSelected(true);

        // --- single mode: one geometry form ---
        GeometryForm singleForm = new GeometryForm();
        singleForm.load(doc.pageSetup());

        // --- conditional mode: master list + geometry + selector rows ---
        List<MasterRow> masters = new ArrayList<>();
        if (multi) {
            for (PageMaster master : doc.pageMasters()) {
                masters.add(new MasterRow(master.name(), master.geometry()));
            }
        } else {
            masters.add(new MasterRow("first", doc.pageSetup()));
            masters.add(new MasterRow("rest", doc.pageSetup()));
        }
        ListView<MasterRow> masterList = new ListView<>();
        masterList.getItems().setAll(masters);
        masterList.setPrefSize(140, 170);
        TextField masterName = new TextField();
        masterName.setPrefWidth(140);
        GeometryForm masterForm = new GeometryForm();

        VBox selectorRows = new VBox(6);
        List<SelectorRow> selectors = new ArrayList<>();
        Runnable masterNames = () -> {
            List<String> names = masterList.getItems().stream().map(m -> m.name).toList();
            for (SelectorRow row : selectors) {
                String value = row.master.getValue();
                row.master.getItems().setAll(names);
                if (names.contains(value)) {
                    row.master.setValue(value);
                } else if (!names.isEmpty()) {
                    row.master.setValue(names.get(0));
                }
            }
        };
        java.util.function.BiConsumer<String, MasterSelector> addSelectorRow = (name, sel) -> {
            SelectorRow row = new SelectorRow();
            row.master.getItems().setAll(
                    masterList.getItems().stream().map(m -> m.name).toList());
            row.master.setValue(name);
            if (sel != null) {
                row.position.setValue(sel.pagePosition() == null ? "any" : sel.pagePosition());
                row.oddEven.setValue(sel.oddOrEven() == null ? "any" : sel.oddOrEven());
                row.blank.setValue(sel.blankOrNot() == null ? "any" : sel.blankOrNot());
            }
            Button remove = new Button("✕");
            remove.getStyleClass().add("danger-button");
            HBox box = new HBox(6, row.master, row.position, row.oddEven, row.blank, remove);
            box.setAlignment(Pos.CENTER_LEFT);
            remove.setOnAction(e -> {
                selectors.remove(row);
                selectorRows.getChildren().remove(box);
            });
            selectors.add(row);
            selectorRows.getChildren().add(box);
        };
        if (multi) {
            for (MasterSelector sel : doc.masterSelectors()) {
                addSelectorRow.accept(sel.masterName(), sel);
            }
        } else {
            addSelectorRow.accept("first", new MasterSelector("first", "first", null, null));
            addSelectorRow.accept("rest", null);
        }

        // master list plumbing: selection ↔ form
        final MasterRow[] current = {null};
        Runnable storeCurrent = () -> {
            if (current[0] != null) {
                PageSetup geometry = masterForm.read();
                if (geometry != null) {
                    current[0].geometry = geometry;
                }
                if (!masterName.getText().isBlank()) {
                    current[0].name = masterName.getText().strip();
                    masterList.refresh();
                    masterNames.run();
                }
            }
        };
        masterList.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            storeCurrent.run();
            current[0] = row;
            if (row != null) {
                masterName.setText(row.name);
                masterForm.load(row.geometry);
            }
        });
        masterList.getSelectionModel().selectFirst();
        Button addMaster = new Button(I18n.t("pageSetup.addMaster"));
        addMaster.getStyleClass().add("bench-button");
        addMaster.setOnAction(e -> {
            MasterRow row = new MasterRow("master" + (masterList.getItems().size() + 1),
                    doc.pageSetup());
            masterList.getItems().add(row);
            masterList.getSelectionModel().select(row);
            masterNames.run();
        });
        Button removeMaster = new Button("✕");
        removeMaster.getStyleClass().add("danger-button");
        removeMaster.setOnAction(e -> {
            MasterRow row = masterList.getSelectionModel().getSelectedItem();
            if (row != null && masterList.getItems().size() > 1) {
                current[0] = null;
                masterList.getItems().remove(row);
                masterList.getSelectionModel().selectFirst();
                masterNames.run();
            }
        });

        Label selectorsLabel = new Label(I18n.t("pageSetup.selectors"));
        selectorsLabel.getStyleClass().add("eyebrow");
        Label hint = new Label(I18n.t("pageSetup.hint"));
        hint.getStyleClass().add("props-note");
        hint.setWrapText(true);

        VBox conditionalBox = new VBox(8,
                new HBox(10, new VBox(6, masterList, new HBox(6, addMaster, removeMaster)),
                        new VBox(6, masterName, masterForm)),
                selectorsLabel, selectorRows, hint);

        VBox content = new VBox(12, new HBox(14, single, conditional));
        Runnable syncMode = () -> {
            content.getChildren().setAll(new HBox(14, single, conditional),
                    single.isSelected() ? singleForm : conditionalBox);
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        };
        single.setOnAction(e -> syncMode.run());
        conditional.setOnAction(e -> syncMode.run());
        content.getChildren().add(single.isSelected() ? singleForm : conditionalBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        dialog.showAndWait().ifPresent(button -> {
            if (button != ButtonType.OK) {
                return;
            }
            if (single.isSelected()) {
                PageSetup geometry = singleForm.read();
                if (geometry != null) {
                    doc.pageMasters().clear();
                    doc.masterSelectors().clear();
                    doc.setPageSetup(geometry);
                    state.documentEdited();
                }
                return;
            }
            storeCurrent.run();
            if (masterList.getItems().isEmpty()) {
                return;
            }
            doc.pageMasters().clear();
            for (MasterRow row : masterList.getItems()) {
                doc.pageMasters().add(new PageMaster(row.name, row.geometry));
            }
            doc.masterSelectors().clear();
            for (SelectorRow row : selectors) {
                if (row.master.getValue() != null) {
                    doc.masterSelectors().add(new MasterSelector(
                            row.master.getValue(),
                            anyToNull(row.position.getValue()),
                            anyToNull(row.oddEven.getValue()),
                            anyToNull(row.blank.getValue())));
                }
            }
            doc.setPageSetup(masterList.getItems().get(0).geometry);
            state.documentEdited();
        });
    }

    private static String anyToNull(String value) {
        return value == null || "any".equals(value) ? null : value;
    }

    private static ComboBox<String> options(String... values) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(values);
        combo.setValue(values[0]);
        return combo;
    }

    /** Ten mm fields for one master's geometry. */
    private static final class GeometryForm extends GridPane {
        private final TextField[] fields = new TextField[10];
        private static final String[] KEYS = {
            "pageSetup.width", "pageSetup.height",
            "pageSetup.marginTop", "pageSetup.marginBottom",
            "pageSetup.marginLeft", "pageSetup.marginRight",
            "pageSetup.bodyTop", "pageSetup.bodyBottom",
            "pageSetup.beforeExtent", "pageSetup.afterExtent",
        };

        GeometryForm() {
            setHgap(8);
            setVgap(6);
            for (int i = 0; i < 10; i++) {
                Label label = new Label(I18n.t(KEYS[i]));
                label.getStyleClass().add("bench-label");
                fields[i] = new TextField();
                fields[i].setPrefWidth(64);
                add(label, (i % 2) * 2, i / 2);
                add(fields[i], (i % 2) * 2 + 1, i / 2);
            }
        }

        void load(PageSetup p) {
            double[] values = {p.pageWidthMm(), p.pageHeightMm(), p.marginTopMm(),
                    p.marginBottomMm(), p.marginLeftMm(), p.marginRightMm(),
                    p.bodyTopMm(), p.bodyBottomMm(), p.beforeExtentMm(), p.afterExtentMm()};
            for (int i = 0; i < 10; i++) {
                fields[i].setText(values[i] == Math.rint(values[i])
                        ? Long.toString((long) values[i]) : Double.toString(values[i]));
            }
        }

        /** Parsed geometry, or null when a field is not a number (dialog stays open-ish). */
        PageSetup read() {
            try {
                double[] v = new double[10];
                for (int i = 0; i < 10; i++) {
                    v[i] = Double.parseDouble(fields[i].getText().strip());
                }
                return new PageSetup(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private PageSetupDialog() { }
}
