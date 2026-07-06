package dev.stylus.app.run;

import dev.stylus.engine.api.EngineId;
import dev.stylus.engine.api.ReportEngine;
import dev.stylus.engine.bip.BipEngine;
import dev.stylus.engine.bip.BipInstallation;
import dev.stylus.engine.bip.BipLocator;
import dev.stylus.engine.fop.FopEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * The engines this installation can use. FOP is always there (F-12.4); BIP appears when an
 * installation is found via -Dstylus.bip.home / STYLUS_BIP_HOME / remembered preference /
 * the developer fixture in lib/bip (F-12.3 — the discovery dialog arrives with the settings UI).
 */
public final class EngineRegistry {

    private static final String PREF_BIP_HOME = "bipHome";

    private final List<ReportEngine> engines;

    public EngineRegistry() {
        List<ReportEngine> list = new ArrayList<>();
        list.add(new FopEngine());
        locateBip().ifPresent(installation -> {
            try {
                list.add(new BipEngine(installation));
            } catch (RuntimeException e) {
                // Invalid/partial installation → stay FOP-only, never crash the app (F-12.4).
                System.err.println("BIP installation rejected: " + e.getMessage());
            }
        });
        this.engines = List.copyOf(list);
    }

    private Optional<BipInstallation> locateBip() {
        Preferences prefs = Preferences.userNodeForPackage(EngineRegistry.class);
        String remembered = prefs.get(PREF_BIP_HOME, null);
        Optional<BipInstallation> located = BipLocator.locate(
                remembered == null ? null : Path.of(remembered));
        if (located.isPresent()) {
            return located;
        }
        // Developer convenience: the local reference jars next to the project (gitignored).
        for (Path devFixture : List.of(Path.of("lib", "bip"), Path.of("..", "lib", "bip"))) {
            if (Files.isDirectory(devFixture)) {
                Optional<BipInstallation> dev = BipInstallation.discover(devFixture.normalize());
                if (dev.isPresent()) {
                    return dev;
                }
            }
        }
        return Optional.empty();
    }

    /** Remember a user-chosen BIP directory for future sessions (settings UI hook). */
    public static void rememberBipHome(Path dir) {
        Preferences.userNodeForPackage(EngineRegistry.class)
                .put(PREF_BIP_HOME, dir.toAbsolutePath().toString());
    }

    public List<ReportEngine> all() {
        return engines;
    }

    public Optional<ReportEngine> byId(EngineId id) {
        return engines.stream().filter(e -> e.id() == id).findFirst();
    }

    /** The default engine for new runs: FOP (always available, F-12.4). */
    public ReportEngine defaultEngine() {
        return engines.get(0);
    }
}
