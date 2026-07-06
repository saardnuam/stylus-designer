package dev.stylus.engine.bip;

import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.RunLogListener;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test against the developer-local Oracle jars in {@code lib/bip} (gitignored,
 * hard rule 1). Skips entirely when they are absent — CI never sees Oracle code.
 */
class BipEngineIT {

    static Optional<BipInstallation> installation;

    @TempDir
    Path tmp;

    @BeforeAll
    static void discoverLocalJars() {
        // Module dir is stylus-engine-bip/ → the dev fixture lives at ../lib/bip.
        installation = BipInstallation.discover(Path.of("..", "lib", "bip").normalize());
    }

    private BipEngine engine() {
        assumeTrue(installation.isPresent(), "lib/bip not present — skipping BIP integration test");
        return new BipEngine(installation.get());
    }

    @Test
    void probesFormatsFromTheRealFoProcessor() {
        BipEngine engine = engine();
        assertTrue(engine.supportedFormats().contains(OutputFormat.PDF));
        assertTrue(engine.supportedFormats().contains(OutputFormat.RTF));
        assertTrue(engine.displayName().startsWith("Oracle BI Publisher"));
        System.out.println("[BipEngineIT] " + engine.displayName()
                + " formats=" + engine.supportedFormats());
    }

    @Test
    void rendersInvoicePdfThroughOracleRuntime() throws Exception {
        BipEngine engine = engine();
        Path samples = Path.of("..", "samples").normalize();
        Path out = tmp.resolve("bip-invoice.pdf");

        RunResult result = engine.run(RunRequest.builder()
                        .template(samples.resolve("invoice-fo.xsl"))
                        .data(samples.resolve("invoice.xml"))
                        .format(OutputFormat.PDF)
                        .output(out)
                        .build(),
                RunLogListener.NULL);

        assertTrue(result.success(), () -> "BIP run failed: "
                + result.failure().map(Throwable::toString).orElse("?")
                + " log=" + result.log());
        assertTrue(Files.size(out) > 1000, "PDF suspiciously small");
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Widget"), "line item missing from BIP PDF");
            assertTrue(text.contains("Acme B.V."), "header missing from BIP PDF");
        }
    }

    @Test
    void reportsMissingDataAsFailedResult() {
        BipEngine engine = engine();
        RunResult result = engine.run(RunRequest.builder()
                        .template(Path.of("..", "samples", "invoice-fo.xsl").normalize())
                        .data(tmp.resolve("nope.xml"))
                        .format(OutputFormat.PDF)
                        .output(tmp.resolve("nope.pdf"))
                        .build(),
                RunLogListener.NULL);

        assertFalse(result.success());
        assertTrue(result.log().stream().anyMatch(e -> e.level() == LogLevel.ERROR));
    }
}
