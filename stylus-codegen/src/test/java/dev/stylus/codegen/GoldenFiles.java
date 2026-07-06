package dev.stylus.codegen;

import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Golden-file harness (F-11.4). Expected files live in src/test/resources/golden (path passed
 * via the {@code golden.dir} system property so update mode can write back into the source tree).
 *
 * Update goldens after an intentional codegen change with:
 *   ./gradlew :stylus-codegen:test -Dgolden.update=true
 * (hard rule 4: commit golden updates together with the codegen change).
 */
final class GoldenFiles {

    private static final Path GOLDEN_DIR = Path.of(System.getProperty("golden.dir", "src/test/resources/golden"));
    private static final Path ACTUAL_DIR = Path.of(System.getProperty("golden.actual.dir", "build/golden-actual"));
    private static final boolean UPDATE = Boolean.getBoolean("golden.update");

    static void assertMatchesGolden(String name, String actual) {
        try {
            Path expectedFile = GOLDEN_DIR.resolve(name);
            if (UPDATE) {
                Files.createDirectories(expectedFile.getParent());
                Files.writeString(expectedFile, actual);
                System.out.println("[golden] updated " + expectedFile);
                return;
            }
            if (!Files.exists(expectedFile)) {
                Path saved = saveActual(name, actual);
                throw new AssertionFailedError("Missing golden file " + expectedFile
                        + " — actual output saved to " + saved
                        + ". Run with -Dgolden.update=true to create it.");
            }
            String expected = Files.readString(expectedFile);
            if (!expected.equals(actual)) {
                Path saved = saveActual(name, actual);
                throw new AssertionFailedError(
                        "Output differs from golden " + expectedFile + " (actual: " + saved
                                + "; -Dgolden.update=true after intentional changes)",
                        expected, actual);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path saveActual(String name, String actual) throws IOException {
        Path out = ACTUAL_DIR.resolve(name);
        Files.createDirectories(out.getParent());
        Files.writeString(out, actual);
        return out;
    }

    private GoldenFiles() { }
}
