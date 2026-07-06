package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import dev.stylus.app.Theme;
import dev.stylus.engine.bip.BipInstallation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The ⌘, settings dialog (macOS convention): UI language (F-9.2, applied live), theme, and
 * the BI Publisher installation folder (F-12.3 discovery UI). Returns the chosen values;
 * the application coordinator persists them and rebuilds the shell.
 */
public final class PreferencesDialog {

    /** What the user chose; {@code bipHome} is null when the field is empty. */
    public record Result(Locale locale, Theme theme, Path bipHome) { }

    public static Optional<Result> show(Window owner, Locale currentLocale, Theme currentTheme,
                                        String currentBipHome) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("prefs.title"));
        dialog.initOwner(owner);

        ComboBox<Locale> language = new ComboBox<>();
        language.getItems().setAll(Locale.ENGLISH, new Locale("nl"));
        language.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Locale locale) {
                if (locale == null) {
                    return "";
                }
                return "nl".equals(locale.getLanguage())
                        ? I18n.t("prefs.language.nl") : I18n.t("prefs.language.en");
            }
            @Override public Locale fromString(String s) {
                return null;
            }
        });
        language.setValue("nl".equals(currentLocale.getLanguage())
                ? new Locale("nl") : Locale.ENGLISH);

        ComboBox<Theme> theme = new ComboBox<>();
        theme.getItems().setAll(Theme.LIGHT, Theme.DARK);
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Theme t) {
                if (t == null) {
                    return "";
                }
                return t == Theme.DARK ? I18n.t("prefs.theme.dark") : I18n.t("prefs.theme.light");
            }
            @Override public Theme fromString(String s) {
                return null;
            }
        });
        theme.setValue(currentTheme);

        TextField bipHome = new TextField(currentBipHome == null ? "" : currentBipHome);
        bipHome.setPromptText(I18n.t("prefs.bipHome.prompt"));
        bipHome.setPrefWidth(320);
        Label bipStatus = new Label("");
        bipStatus.getStyleClass().add("props-note");
        Runnable probe = () -> {
            String text = bipHome.getText();
            if (text == null || text.isBlank()) {
                bipStatus.setText("");
                return;
            }
            Path dir = Path.of(text.strip());
            Optional<BipInstallation> found = Files.isDirectory(dir)
                    ? BipInstallation.discover(dir) : Optional.empty();
            bipStatus.setText(found.isPresent()
                    ? I18n.t("prefs.bipHome.found", found.get().version())
                    : I18n.t("prefs.bipHome.none"));
        };
        bipHome.textProperty().addListener((obs, old, v) -> probe.run());
        probe.run();
        Button browse = new Button("…");
        browse.getStyleClass().add("bench-button");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(I18n.t("prefs.bipHome"));
            java.io.File dir = chooser.showDialog(owner);
            if (dir != null) {
                bipHome.setText(dir.getAbsolutePath());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.add(fieldLabel("prefs.language"), 0, 0);
        grid.add(language, 1, 0);
        grid.add(fieldLabel("prefs.theme"), 0, 1);
        grid.add(theme, 1, 1);
        grid.add(fieldLabel("prefs.bipHome"), 0, 2);
        javafx.scene.layout.HBox bipRow = new javafx.scene.layout.HBox(6, bipHome, browse);
        bipRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(bipRow, 1, 2);
        grid.add(bipStatus, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new Result(language.getValue(), theme.getValue(),
                        bipHome.getText().isBlank() ? null : Path.of(bipHome.getText().strip()))
                : null);
        return dialog.showAndWait();
    }

    private static Label fieldLabel(String key) {
        Label label = new Label(I18n.t(key));
        label.getStyleClass().add("bench-label");
        return label;
    }

    private PreferencesDialog() { }
}
