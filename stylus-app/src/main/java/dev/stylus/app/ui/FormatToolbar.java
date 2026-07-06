package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.model.FieldToken;
import dev.stylus.model.ModelEdits;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Toolbar row 1 — text formatting (42px, F-1.4): bold/italic/underline/strike, font family,
 * size, text + highlight color and alignment act on the selected static band (or the band
 * containing the selected field token) through the same immutable-replace path as the
 * properties panel. Lists and table borders remain disabled chrome (no model counterpart).
 */
final class FormatToolbar extends HBox {

    private final DesignerState state;
    private final ToggleButton bold = toggle("B", "toolbar.bold", "glyph-bold");
    private final ToggleButton italic = toggle("I", "toolbar.italic", "glyph-italic");
    private final ToggleButton underline = toggle("U", "toolbar.underline", "glyph-underline");
    private final ToggleButton strike = toggle("S", "toolbar.strike", "glyph-strike");
    private final ComboBox<String> fontFamily = new ComboBox<>();
    private final ComboBox<String> fontSize = new ComboBox<>();
    private final ColorPicker textColor = new ColorPicker();
    private final ColorPicker highlight = new ColorPicker();
    private final ToggleButton alignLeft = toggle("⯇", "toolbar.align.left", "glyph-align");
    private final ToggleButton alignCenter = toggle("☰", "toolbar.align.center", "glyph-align");
    private final ToggleButton alignRight = toggle("⯈", "toolbar.align.right", "glyph-align");

    private boolean refreshing;

    FormatToolbar(DesignerState state) {
        this.state = state;
        getStyleClass().add("toolbar-row1");
        setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> paragraphStyle = combo(I18n.t("toolbar.paragraphStyle"), 128);

        fontFamily.setEditable(true);
        fontFamily.getItems().setAll("", "Hanken Grotesk", "Helvetica", "Times New Roman",
                "Courier New", "Arial");
        fontFamily.setPrefWidth(138);
        fontFamily.getStyleClass().add("toolbar-combo");

        fontSize.getItems().setAll("", "8", "9", "10", "11", "12", "14", "18", "24");
        fontSize.setPrefWidth(64);
        fontSize.getStyleClass().add("toolbar-combo");

        textColor.getStyleClass().addAll("glyph-button", "toolbar-color");
        textColor.setTooltip(new Tooltip(I18n.t("toolbar.textColor")));
        highlight.getStyleClass().addAll("glyph-button", "toolbar-color");
        highlight.setTooltip(new Tooltip(I18n.t("toolbar.highlight")));
        highlight.setValue(Color.TRANSPARENT);

        ToggleGroup alignment = new ToggleGroup();
        alignLeft.setToggleGroup(alignment);
        alignCenter.setToggleGroup(alignment);
        alignRight.setToggleGroup(alignment);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.control.Label formatLabel = new javafx.scene.control.Label(I18n.t("toolbar.format"));
        formatLabel.getStyleClass().add("toolbar-eyebrow");

        getChildren().addAll(
                paragraphStyle, fontFamily, fontSize,
                divider(),
                bold, italic, underline, strike,
                textColor, highlight,
                divider(),
                alignLeft, alignCenter, alignRight,
                divider(),
                glyph("•≡", "toolbar.list.bullet", "glyph-list"),
                glyph("⊞", "toolbar.table", "glyph-table"),
                spacer,
                formatLabel);

        bold.setOnAction(e -> apply());
        italic.setOnAction(e -> apply());
        underline.setOnAction(e -> apply());
        strike.setOnAction(e -> apply());
        fontFamily.setOnAction(e -> apply());
        fontSize.valueProperty().addListener((obs, old, v) -> apply());
        textColor.setOnAction(e -> apply());
        highlight.setOnAction(e -> apply());
        alignLeft.setOnAction(e -> apply());
        alignCenter.setOnAction(e -> apply());
        alignRight.setOnAction(e -> apply());

        state.onSelectionChanged(this::refreshFromSelection);
        state.onDocumentChanged(this::refreshFromSelection);
        refreshFromSelection();
    }

    /** The static band the toolbar acts on for the current selection; null = disabled. */
    private StaticBand target() {
        Object selection = state.selection();
        if (selection instanceof StaticBand band) {
            return band;
        }
        if (selection instanceof FieldToken token) {
            return ModelEdits.containingStaticBand(state.document(), token);
        }
        return null;
    }

    private void refreshFromSelection() {
        StaticBand band = target();
        boolean off = band == null;
        refreshing = true;
        try {
            for (var node : new javafx.scene.Node[] {bold, italic, underline, strike,
                    fontFamily, fontSize, textColor, highlight,
                    alignLeft, alignCenter, alignRight}) {
                node.setDisable(off);
            }
            if (off) {
                bold.setSelected(false);
                italic.setSelected(false);
                underline.setSelected(false);
                strike.setSelected(false);
                fontFamily.setValue("");
                fontSize.setValue("");
                alignLeft.setSelected(false);
                alignCenter.setSelected(false);
                alignRight.setSelected(false);
                return;
            }
            StyleProps style = band.style();
            bold.setSelected(Boolean.TRUE.equals(style.bold()));
            italic.setSelected(Boolean.TRUE.equals(style.italic()));
            underline.setSelected(Boolean.TRUE.equals(style.underline()));
            strike.setSelected(Boolean.TRUE.equals(style.strike()));
            fontFamily.setValue(style.fontFamily() == null ? "" : style.fontFamily());
            fontSize.setValue(style.fontSizePt() == null ? "" : style.fontSizePt());
            textColor.setValue(style.color() == null ? Color.BLACK : Color.web(style.color()));
            highlight.setValue(style.background() == null
                    ? Color.TRANSPARENT : Color.web(style.background()));
            alignLeft.setSelected("left".equals(style.textAlign()));
            alignCenter.setSelected("center".equals(style.textAlign()));
            alignRight.setSelected("right".equals(style.textAlign()));
        } finally {
            refreshing = false;
        }
    }

    private void apply() {
        if (refreshing) {
            return;
        }
        StaticBand band = target();
        if (band == null) {
            return;
        }
        String size = fontSize.getValue();
        String align = alignLeft.isSelected() ? "left"
                : alignCenter.isSelected() ? "center"
                : alignRight.isSelected() ? "right" : null;
        Color color = textColor.getValue();
        String hex = color == null || Color.BLACK.equals(color) ? null : toHex(color);
        Color bg = highlight.getValue();
        String bgHex = bg == null || Color.TRANSPARENT.equals(bg) ? null : toHex(bg);
        String familyValue = fontFamily.getValue();
        StyleProps style = new StyleProps(
                bold.isSelected() ? true : null,
                italic.isSelected() ? true : null,
                size == null || size.isBlank() ? null : size,
                hex,
                align,
                familyValue == null || familyValue.isBlank() ? null : familyValue.strip(),
                bgHex,
                underline.isSelected() ? true : null,
                strike.isSelected() ? true : null,
                band.style().lineHeight());
        StaticBand replacement = new StaticBand(band.content(), style, band.rules());
        if (ModelEdits.replaceBand(state.document(), band, replacement)) {
            Object selection = state.selection();
            state.documentEdited();
            // Keep the token selected when formatting via a token; else select the new band.
            state.select(selection instanceof FieldToken token ? token : replacement);
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private static ComboBox<String> combo(String prompt, double width) {
        ComboBox<String> c = new ComboBox<>();
        c.setPromptText(prompt);
        c.setValue(prompt);
        c.setPrefWidth(width);
        c.getStyleClass().add("toolbar-combo");
        c.setDisable(true); // paragraph styles + font families arrive with F-2.19 typography
        return c;
    }

    private static ToggleButton toggle(String text, String tooltipKey, String extraClass) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().addAll("glyph-button", extraClass);
        b.setTooltip(new Tooltip(I18n.t(tooltipKey)));
        return b;
    }

    private static Button glyph(String text, String tooltipKey, String extraClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("glyph-button", extraClass);
        b.setTooltip(new Tooltip(I18n.t(tooltipKey)));
        b.setDisable(true); // no model counterpart yet
        return b;
    }

    private static Separator divider() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.getStyleClass().add("toolbar-divider");
        return s;
    }
}
