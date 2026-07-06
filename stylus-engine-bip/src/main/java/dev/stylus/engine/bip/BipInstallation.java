package dev.stylus.engine.bip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * A user-supplied local BI Publisher / Template Viewer installation (F-12.3).
 * We validate the directory actually contains the core jars and never copy them anywhere
 * (hard rule 1) — the engine loads them in place via an isolated classloader.
 */
public final class BipInstallation {

    /** Jar that must exist for the adapter to work at all. */
    private static final String CORE_JAR = "xdocore.jar";
    /** Strongly expected companions; missing ones are reported but not fatal. */
    private static final List<String> EXPECTED_JARS = List.of(
            "xdoparser.jar", "xmlparserv2.jar", "i18nAPI_v3.jar");

    private final Path directory;
    private final List<Path> jars;
    private final String version;

    private BipInstallation(Path directory, List<Path> jars, String version) {
        this.directory = directory;
        this.jars = List.copyOf(jars);
        this.version = version;
    }

    /**
     * Validates {@code directory} (or its {@code jlib}/{@code lib} subdirectory) as a BIP
     * installation and returns it, or empty when no {@code xdocore.jar} is found.
     */
    public static Optional<BipInstallation> discover(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return Optional.empty();
        }
        for (Path candidate : List.of(directory, directory.resolve("jlib"), directory.resolve("lib"))) {
            Optional<BipInstallation> found = scan(candidate);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<BipInstallation> scan(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .forEach(jars::add);
        } catch (IOException e) {
            return Optional.empty();
        }
        Path core = jars.stream()
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(CORE_JAR))
                .findFirst()
                .orElse(null);
        if (core == null) {
            return Optional.empty();
        }
        return Optional.of(new BipInstallation(dir, jars, readVersion(core)));
    }

    private static String readVersion(Path xdocore) {
        try (JarFile jar = new JarFile(xdocore.toFile())) {
            var manifest = jar.getManifest();
            if (manifest != null) {
                for (String key : List.of("Implementation-Version", "Specification-Version",
                        "Oracle-Version")) {
                    String v = manifest.getMainAttributes().getValue(key);
                    if (v != null && !v.isBlank()) {
                        return v;
                    }
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "unknown version";
    }

    public Path directory() {
        return directory;
    }

    public List<Path> jars() {
        return jars;
    }

    public String version() {
        return version;
    }

    /** Expected-but-missing companion jars — surfaced as a warning in the discovery UI. */
    public List<String> missingCompanions() {
        List<String> names = jars.stream()
                .map(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))
                .toList();
        return EXPECTED_JARS.stream()
                .filter(expected -> !names.contains(expected.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
