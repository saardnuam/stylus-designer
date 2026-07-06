package dev.stylus.engine.bip;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds a BI Publisher installation without any UI (F-12.3; the discovery dialog comes later):
 *  1. {@code -Dstylus.bip.home=…}
 *  2. {@code STYLUS_BIP_HOME} environment variable
 *  3. an explicitly passed directory (app preferences / CLI flag)
 */
public final class BipLocator {

    public static Optional<BipInstallation> locate(Path explicit) {
        Optional<BipInstallation> fromProperty = tryPath(System.getProperty("stylus.bip.home"));
        if (fromProperty.isPresent()) {
            return fromProperty;
        }
        Optional<BipInstallation> fromEnv = tryPath(System.getenv("STYLUS_BIP_HOME"));
        if (fromEnv.isPresent()) {
            return fromEnv;
        }
        return explicit == null ? Optional.empty() : BipInstallation.discover(explicit);
    }

    public static Optional<BipInstallation> locate() {
        return locate(null);
    }

    private static Optional<BipInstallation> tryPath(String path) {
        return path == null || path.isBlank()
                ? Optional.empty()
                : BipInstallation.discover(Path.of(path));
    }

    private BipLocator() { }
}
