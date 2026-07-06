package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Status bar (32px, F-1.6): validity + FO version left; output format chips + zoom right.
 * M0: static chrome; live validity arrives with the code view (M4), chips switch renderers in M2.
 */
final class StatusBar extends HBox {

    StatusBar() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);

        Label valid = new Label("● " + I18n.t("status.valid"));
        valid.getStyleClass().add("status-valid");

        Label foVersion = new Label(I18n.t("status.foVersion"));
        foVersion.getStyleClass().add("mono-readout");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label outputLabel = new Label(I18n.t("status.output"));
        outputLabel.getStyleClass().add("status-label");

        HBox chips = new HBox(
                chip("PDF", true),
                chip("HTML", false),
                chip("RTF", false),
                chip("XLSX", false));
        chips.getStyleClass().add("format-chips");
        chips.setAlignment(Pos.CENTER_LEFT);

        Label zoom = new Label(I18n.t("status.zoom"));
        zoom.getStyleClass().add("mono-readout");

        getChildren().addAll(valid, foVersion, spacer, outputLabel, chips, zoom);
    }

    private static Label chip(String text, boolean active) {
        Label c = new Label(text);
        c.getStyleClass().add("format-chip");
        if (active) {
            c.getStyleClass().add("format-chip-active");
        }
        return c;
    }
}
