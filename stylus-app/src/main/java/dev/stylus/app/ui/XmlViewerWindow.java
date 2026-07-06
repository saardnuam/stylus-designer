package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only XML viewer for double-clicked data files (F-5.7). */
final class XmlViewerWindow {

    private static final long MAX_BYTES = 4 * 1024 * 1024;

    static void open(Path file, Scene owner) {
        String content;
        try {
            if (Files.size(file) > MAX_BYTES) {
                content = I18n.t("viewer.tooLarge", file.getFileName(), Files.size(file) / (1024 * 1024));
            } else {
                content = Files.readString(file);
            }
        } catch (Exception e) {
            content = I18n.t("viewer.error", e.getMessage());
        }

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.getStyleClass().add("code-view");

        Stage stage = new Stage();
        stage.setTitle(file.getFileName().toString());
        Scene scene = new Scene(area, 720, 640);
        scene.getStylesheets().setAll(owner.getStylesheets());
        stage.setScene(scene);
        stage.show();
    }

    private XmlViewerWindow() { }
}
