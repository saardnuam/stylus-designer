package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import dev.stylus.codegen.XslWriter;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Editor for {@code <xsl:text>} literals: whitespace-exact text with an Ω special-character
 * picker and a live preview of the emitted form — invisible characters show as numeric
 * character references exactly as they will appear in the code view.
 */
final class XslTextDialog {

    static Optional<String> edit(Window owner, String initial) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("insert.xslText"));
        dialog.setHeaderText(I18n.t("insert.xslText.header"));
        dialog.initOwner(owner);

        TextArea text = new TextArea(initial == null ? "" : initial);
        text.setPrefSize(440, 90);
        text.setWrapText(true);

        Label codePreview = new Label();
        codePreview.getStyleClass().add("ncr-preview");
        codePreview.setWrapText(true);
        Runnable refresh = () -> codePreview.setText(
                "<xsl:text>" + XslWriter.ncrEscape(text.getText()) + "</xsl:text>");
        text.textProperty().addListener((obs, old, v) -> refresh.run());
        refresh.run();

        Button omega = new Button("Ω");
        omega.getStyleClass().add("bench-button");
        omega.setTooltip(new javafx.scene.control.Tooltip(I18n.t("chars.title")));
        omega.setOnAction(e -> SpecialCharacterDialog.pick(owner).ifPresent(ch -> {
            text.insertText(text.getCaretPosition(), ch);
            text.requestFocus();
        }));

        HBox toolRow = new HBox(8, omega, codePreview);
        toolRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(codePreview, Priority.ALWAYS);

        dialog.getDialogPane().setContent(new VBox(8, text, toolRow));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.setResultConverter(button -> button == ButtonType.OK ? text.getText() : null);
        return dialog.showAndWait();
    }

    private XslTextDialog() { }
}
