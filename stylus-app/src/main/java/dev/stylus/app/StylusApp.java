package dev.stylus.app;

import dev.stylus.app.run.EngineRegistry;
import dev.stylus.app.ui.PreferencesDialog;
import dev.stylus.app.ui.Shell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Application entry: builds the 3-pane shell (F-1.1), applies the persisted theme/language,
 * and coordinates the ⌘, preferences dialog — a language change rebuilds the shell in place
 * (F-9.2) while the document, sample and undo history survive in the shared DesignerState.
 */
public final class StylusApp extends Application {

    private final Preferences prefs = Preferences.userNodeForPackage(StylusApp.class);
    private final DesignerState state = new DesignerState();
    private Theme theme = Theme.LIGHT;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Fonts.loadAll();
        String savedLocale = prefs.get("locale", null);
        if (savedLocale != null) {
            I18n.setLocale(Locale.forLanguageTag(savedLocale));
        }
        theme = "DARK".equals(prefs.get("theme", "LIGHT")) ? Theme.DARK : Theme.LIGHT;

        Scene scene = new Scene(buildShell(), 1500, 900);
        theme.applyTo(scene);
        stage.setTitle(I18n.t("app.title"));
        stage.setMinWidth(1100);
        stage.setMinHeight(680);
        // Window icon; on macOS this also becomes the Dock icon for dev launches.
        for (String size : new String[] {"32", "64", "128", "256", "512"}) {
            var icon = getClass().getResourceAsStream("/dev/stylus/app/icons/stylus-" + size + ".png");
            if (icon != null) {
                stage.getIcons().add(new javafx.scene.image.Image(icon));
            }
        }
        stage.setScene(scene);
        stage.show();
    }

    private Shell buildShell() {
        return new Shell(state, this::toggleTheme, this::showPreferences, getHostServices());
    }

    private void showPreferences() {
        PreferencesDialog.show(stage, I18n.currentLocale(), theme,
                prefs.get("bipHome", null)).ifPresent(this::applyPreferences);
    }

    private void applyPreferences(PreferencesDialog.Result result) {
        boolean localeChanged =
                !result.locale().getLanguage().equals(I18n.currentLocale().getLanguage());
        boolean bipChanged = result.bipHome() != null
                && !result.bipHome().toString().equals(prefs.get("bipHome", null));

        prefs.put("locale", result.locale().toLanguageTag());
        prefs.put("theme", result.theme().name());
        theme = result.theme();
        if (result.bipHome() != null) {
            prefs.put("bipHome", result.bipHome().toString());
            EngineRegistry.rememberBipHome(result.bipHome());
        }

        if (localeChanged) {
            I18n.setLocale(result.locale());
        }
        if (localeChanged || bipChanged) {
            // Rebuild the shell: new labels everywhere / engines re-detected; state survives.
            state.detachUi();
            stage.getScene().setRoot(buildShell());
            stage.setTitle(I18n.t("app.title"));
        }
        theme.applyTo(stage.getScene());
    }

    private void toggleTheme() {
        theme = theme.other();
        prefs.put("theme", theme.name());
        javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .map(w -> w.getScene())
                .filter(s -> s != null)
                .forEach(theme::applyTo);
    }
}
