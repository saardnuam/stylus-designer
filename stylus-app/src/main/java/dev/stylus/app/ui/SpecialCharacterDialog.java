package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Special-character picker: common typographic/report characters as a click grid plus a free
 * hex code-point field. Returns the chosen character; the code view shows it as a numeric
 * character reference ({@code &#xA0;}) via the writer's deterministic escaping.
 */
final class SpecialCharacterDialog {

    /** codepoint, short label shown in the grid ("" = show the glyph itself), name. */
    private record Entry(int codePoint, String label, String name) { }

    private static final Entry[] ENTRIES = {
        new Entry(0x00A0, "NBSP", "no-break space"),
        new Entry(0x202F, "NNBSP", "narrow no-break space"),
        new Entry(0x2009, "THIN", "thin space"),
        new Entry(0x2002, "EN SP", "en space"),
        new Entry(0x2003, "EM SP", "em space"),
        new Entry(0x200B, "ZWSP", "zero-width space"),
        new Entry(0x00AD, "SHY", "soft hyphen"),
        new Entry(0x2011, "NB-HY", "non-breaking hyphen"),
        new Entry(0x2013, "", "en dash"),
        new Entry(0x2014, "", "em dash"),
        new Entry(0x2212, "", "minus sign"),
        new Entry(0x2022, "", "bullet"),
        new Entry(0x00B7, "", "middle dot"),
        new Entry(0x2026, "", "ellipsis"),
        new Entry(0x2018, "", "left single quote"),
        new Entry(0x2019, "", "right single quote"),
        new Entry(0x201C, "", "left double quote"),
        new Entry(0x201D, "", "right double quote"),
        new Entry(0x201E, "", "low double quote"),
        new Entry(0x00AB, "", "left guillemet"),
        new Entry(0x00BB, "", "right guillemet"),
        new Entry(0x20AC, "", "euro sign"),
        new Entry(0x00A3, "", "pound sign"),
        new Entry(0x00A5, "", "yen sign"),
        new Entry(0x00A2, "", "cent sign"),
        new Entry(0x00A9, "", "copyright"),
        new Entry(0x00AE, "", "registered"),
        new Entry(0x2122, "", "trademark"),
        new Entry(0x00A7, "", "section sign"),
        new Entry(0x00B6, "", "pilcrow"),
        new Entry(0x2020, "", "dagger"),
        new Entry(0x00B0, "", "degree"),
        new Entry(0x00B1, "", "plus-minus"),
        new Entry(0x00D7, "", "multiplication"),
        new Entry(0x00F7, "", "division"),
        new Entry(0x00BC, "", "one quarter"),
        new Entry(0x00BD, "", "one half"),
        new Entry(0x00BE, "", "three quarters"),
        new Entry(0x2030, "", "per mille"),
        new Entry(0x00B5, "", "micro"),
        new Entry(0x2190, "", "left arrow"),
        new Entry(0x2192, "", "right arrow"),
        new Entry(0x2713, "", "check mark"),
        new Entry(0x2717, "", "ballot x"),
        new Entry(0x2605, "", "black star"),
    };

    /** Shows the picker; returns the chosen character (as a string) when confirmed. */
    static Optional<String> pick(Window owner) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("chars.title"));
        dialog.initOwner(owner);

        FlowPane grid = new FlowPane(4, 4);
        grid.setPrefWrapLength(460);
        for (Entry entry : ENTRIES) {
            String glyph = new String(Character.toChars(entry.codePoint()));
            Button b = new Button(entry.label().isEmpty() ? glyph : entry.label());
            b.getStyleClass().add("char-cell");
            b.setTooltip(new Tooltip(entry.name() + " — U+"
                    + hex(entry.codePoint()) + " (&#x" + hex(entry.codePoint()) + ";)"));
            b.setOnAction(e -> {
                dialog.setResult(glyph);
                dialog.close();
            });
            grid.getChildren().add(b);
        }

        TextField hexField = new TextField();
        hexField.setPromptText(I18n.t("chars.hex.prompt"));
        hexField.setPrefWidth(160);
        Button insertHex = new Button(I18n.t("chars.insert"));
        insertHex.getStyleClass().add("bench-button");
        Runnable applyHex = () -> {
            try {
                int cp = Integer.parseInt(hexField.getText().strip()
                        .replaceFirst("(?i)^(U\\+|&#x|0x)", "").replaceFirst(";$", ""), 16);
                if (Character.isValidCodePoint(cp) && cp > 0) {
                    dialog.setResult(new String(Character.toChars(cp)));
                    dialog.close();
                }
            } catch (NumberFormatException ignored) {
                // leave the dialog open for a correction
            }
        };
        insertHex.setOnAction(e -> applyHex.run());
        hexField.setOnAction(e -> applyHex.run());
        HBox hexRow = new HBox(6, hexField, insertHex);

        dialog.getDialogPane().setContent(new VBox(10, grid, hexRow));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }

    private static String hex(int codePoint) {
        return Integer.toHexString(codePoint).toUpperCase();
    }

    private SpecialCharacterDialog() { }
}
