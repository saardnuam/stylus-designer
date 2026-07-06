package dev.stylus.app;

import javafx.scene.Scene;

import java.util.Objects;

/** Light (1A) / dark (2A) theming per the design handoff — one toggle, paper stays white (F-1.7). */
public enum Theme {
    LIGHT("/dev/stylus/app/css/theme-light.css"),
    DARK("/dev/stylus/app/css/theme-dark.css");

    public static final String BASE_CSS = "/dev/stylus/app/css/base.css";

    private final String stylesheet;

    Theme(String stylesheet) {
        this.stylesheet = stylesheet;
    }

    public Theme other() {
        return this == LIGHT ? DARK : LIGHT;
    }

    /** Applies base + this theme's palette to the scene (replacing any previous theme). */
    public void applyTo(Scene scene) {
        scene.getStylesheets().setAll(css(BASE_CSS), css(stylesheet));
    }

    private static String css(String resource) {
        return Objects.requireNonNull(Theme.class.getResource(resource),
                "Missing stylesheet " + resource).toExternalForm();
    }
}
