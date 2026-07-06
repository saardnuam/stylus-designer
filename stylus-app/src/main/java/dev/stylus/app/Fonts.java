package dev.stylus.app;

import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Loads the bundled UI fonts (F-1.9): Hanken Grotesk (UI) + JetBrains Mono (code/tokens).
 * Both are SIL OFL licensed — license texts ship next to the TTFs in /fonts.
 */
final class Fonts {

    private static final String[] FILES = {
            "HankenGrotesk-Regular.ttf",
            "HankenGrotesk-Medium.ttf",
            "HankenGrotesk-SemiBold.ttf",
            "HankenGrotesk-Bold.ttf",
            "HankenGrotesk-ExtraBold.ttf",
            "JetBrainsMono-Regular.ttf",
            "JetBrainsMono-Medium.ttf",
            "JetBrainsMono-SemiBold.ttf",
            "JetBrainsMono-Bold.ttf",
    };

    static void loadAll() {
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/dev/stylus/app/fonts/" + file)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (Exception ignored) {
                // Missing font → JavaFX falls back to the platform default; purely cosmetic.
            }
        }
    }

    private Fonts() { }
}
