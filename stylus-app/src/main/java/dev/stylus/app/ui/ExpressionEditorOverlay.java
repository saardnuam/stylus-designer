package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.app.expr.ExpressionValidator;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Expression editor overlay (F-1.30..F-1.34): bottom-anchored card with a dark code surface
 * (both themes), function palette chips, live ✓/✗ validation and a computed preview against
 * the loaded sample in the current group context. Apply hands the expression back to the
 * opener; ✕ dismisses.
 */
final class ExpressionEditorOverlay extends VBox {

    /** Palette chips: label → snippet (cursor lands after insert). xdoxslt group joins per engine in M6. */
    private static final String[][] PALETTE = {
            {"sum()", "sum()"},
            {"count()", "count()"},
            {"if…then…else", "if () then  else "},
            {"concat()", "concat(, )"},
            {"format-number()", "format-number(, '#,##0.00')"},
            {"position()", "position()"},
            {"contains()", "contains(, '')"},
    };

    private final DesignerState state;
    private final ExpressionValidator validator = new ExpressionValidator();
    private final TextArea code = new TextArea();
    private final Label status = new Label();
    private final Label preview = new Label();
    private final Button apply = new Button(I18n.t("expr.apply"));

    // BIP extended-function palette (F-3.1/F-1.31): visible when the BIP engine is targeted.
    private final VBox bipSection = new VBox(6);
    private final javafx.scene.control.TextField bipSearch = new javafx.scene.control.TextField();
    private final javafx.scene.control.ComboBox<String> bipCategory = new javafx.scene.control.ComboBox<>();

    /** Localized label for a doc-05 function category; falls back to the raw name. */
    private static String categoryLabel(String raw) {
        String label = I18n.t("expr.palette.cat." + raw);
        return label.startsWith("!") ? raw : label;
    }
    private final FlowPane bipChips = new FlowPane(6, 6);

    private List<String> contextChain = List.of();
    private Consumer<String> onApply;

    ExpressionEditorOverlay(DesignerState state) {
        this.state = state;
        getStyleClass().add("expr-overlay");
        setVisible(false);
        setManaged(false);
        setMaxWidth(460);
        setMaxHeight(Region.USE_PREF_SIZE);

        Label title = new Label(I18n.t("expr.title"));
        title.getStyleClass().add("expr-title");
        Label version = new Label("XPath 1.0");
        version.getStyleClass().add("badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().add("icon-button");
        close.setOnAction(e -> hide());
        HBox header = new HBox(8, title, version, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);

        code.getStyleClass().add("expr-code");
        code.setPrefRowCount(4);
        code.setWrapText(true);
        code.textProperty().addListener((obs, old, text) -> revalidate());

        FlowPane palette = new FlowPane(6, 6);
        palette.getStyleClass().add("expr-palette");
        for (String[] chip : PALETTE) {
            Button b = new Button(chip[0]);
            b.getStyleClass().add("expr-chip");
            b.setOnAction(e -> code.insertText(code.getCaretPosition(), chip[1]));
            palette.getChildren().add(b);
        }

        buildBipSection();

        status.getStyleClass().add("expr-status");
        preview.getStyleClass().add("expr-preview");
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(e -> {
            if (onApply != null) {
                onApply.accept(code.getText().strip());
            }
            hide();
        });
        HBox footer = new HBox(8, status, preview, footerSpacer, apply);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(header, code, palette, bipSection, footer);

        state.onTargetEngineChanged(this::refreshBipVisibility);
        refreshBipVisibility();
    }

    // ---------- BIP extended functions (doc 05 catalog) ----------

    private void buildBipSection() {
        Label bipTitle = new Label(I18n.t("expr.palette.bip"));
        bipTitle.getStyleClass().add("eyebrow");

        bipCategory.getItems().add(I18n.t("expr.palette.allCategories"));
        // categories are localized for display; filtering compares localized labels
        bipCategory.getItems().addAll(
                dev.stylus.config.XdoFunctionCatalog.instance().categories().stream()
                        .map(ExpressionEditorOverlay::categoryLabel).toList());
        bipCategory.getSelectionModel().selectFirst();
        bipCategory.valueProperty().addListener((obs, old, v) -> refreshBipChips());

        bipSearch.setPromptText(I18n.t("expr.palette.search"));
        bipSearch.getStyleClass().add("tree-search");
        HBox.setHgrow(bipSearch, Priority.ALWAYS);
        bipSearch.textProperty().addListener((obs, old, v) -> refreshBipChips());

        HBox filterRow = new HBox(6, bipTitle, bipCategory, bipSearch);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        bipChips.getStyleClass().add("expr-palette");
        javafx.scene.control.ScrollPane chipScroll = new javafx.scene.control.ScrollPane(bipChips);
        chipScroll.setFitToWidth(true);
        chipScroll.setPrefViewportHeight(84);
        chipScroll.getStyleClass().add("expr-chip-scroll");

        bipSection.getChildren().addAll(filterRow, chipScroll);
        refreshBipChips();
    }

    private void refreshBipChips() {
        var catalog = dev.stylus.config.XdoFunctionCatalog.instance();
        String category = bipCategory.getValue();
        boolean allCategories = category == null
                || category.equals(I18n.t("expr.palette.allCategories"));
        String query = bipSearch.getText() == null ? "" : bipSearch.getText();

        bipChips.getChildren().clear();
        catalog.search(query).stream()
                .filter(f -> allCategories || categoryLabel(f.category()).equals(category))
                .forEach(f -> {
                    Button chip = new Button(f.name() + "()");
                    chip.getStyleClass().addAll("expr-chip", "expr-chip-bip");
                    chip.setTooltip(new javafx.scene.control.Tooltip(f.signature()));
                    chip.setOnAction(e -> code.insertText(code.getCaretPosition(), f.snippet()));
                    bipChips.getChildren().add(chip);
                });
    }

    private void refreshBipVisibility() {
        boolean bip = state.targetEngine() == dev.stylus.engine.api.EngineId.BIP;
        bipSection.setVisible(bip);
        bipSection.setManaged(bip);
    }

    /** Opens the editor with an initial expression and the group-context chain (F-1.26). */
    void open(String initial, List<String> contextChain, Consumer<String> onApply) {
        this.contextChain = contextChain;
        this.onApply = onApply;
        code.setText(initial == null ? "" : initial);
        setVisible(true);
        setManaged(true);
        code.requestFocus();
        code.positionCaret(code.getText().length());
        revalidate();
    }

    void hide() {
        setVisible(false);
        setManaged(false);
    }

    private void revalidate() {
        ExpressionValidator.Result result = validator.validate(
                code.getText().strip(),
                state.sampleFile(),
                contextChain);
        if (result.valid()) {
            status.setText(result.bipOnly() ? I18n.t("expr.validBip") : I18n.t("expr.valid"));
            status.getStyleClass().removeAll("expr-status-error");
            status.getStyleClass().add("expr-status-ok");
            if (result.bipOnly() && state.targetEngine() != dev.stylus.engine.api.EngineId.BIP) {
                preview.setText(I18n.t("expr.bipOnFop"));
            } else {
                preview.setText(result.preview() == null
                        ? "" : I18n.t("expr.preview", result.preview()));
            }
            apply.setDisable(false);
        } else {
            status.setText(I18n.t("expr.invalid", result.message()));
            status.getStyleClass().removeAll("expr-status-ok");
            status.getStyleClass().add("expr-status-error");
            preview.setText("");
            apply.setDisable(true);
        }
    }
}
