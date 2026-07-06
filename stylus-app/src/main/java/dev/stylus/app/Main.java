package dev.stylus.app;

/**
 * Plain launcher so the app runs from a classpath (non-modular) jar without the
 * "JavaFX runtime components are missing" launcher check tripping.
 */
public final class Main {

    public static void main(String[] args) {
        StylusApp.launch(StylusApp.class, args);
    }

    private Main() { }
}
