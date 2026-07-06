package dev.stylus.engine.fop;

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

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FopEngineTest {

    @TempDir
    Path tmp;

    static Path samples;
    static final FopEngine engine = new FopEngine();

    @BeforeAll
    static void locateSamples() throws URISyntaxException {
        samples = Path.of(FopEngineTest.class.getResource("/samples/invoice.xml").toURI())
                .getParent();
    }

    private RunRequest.Builder base(String template, OutputFormat format, String outName) {
        return RunRequest.builder()
                .template(samples.resolve(template))
                .data(samples.resolve("invoice.xml"))
                .format(format)
                .output(tmp.resolve(outName));
    }

    @Test
    void rendersPdfWithExpectedText() throws Exception {
        RunResult result = engine.run(
                base("invoice-fo.xsl", OutputFormat.PDF, "out.pdf").build(), RunLogListener.NULL);

        assertTrue(result.success(), () -> "run failed: " + result.failure().map(Throwable::toString).orElse("?"));
        assertTrue(Files.size(result.output()) > 1000, "PDF suspiciously small");
        String text = pdfText(result.output());
        assertTrue(text.contains("Widget"), "line item missing");
        assertTrue(text.contains("Acme B.V."), "page header missing");
        assertTrue(text.contains("Quarterly Invoice Summary"), "default title param missing");
        assertTrue(text.contains("1.161,50") || text.contains("1,161.50"), "customer total missing: " + text);
    }

    @Test
    void honorsStylesheetParameters() throws Exception {
        RunResult result = engine.run(
                base("invoice-fo.xsl", OutputFormat.PDF, "titled.pdf")
                        .parameter("reportTitle", "Omzet Q3").build(),
                RunLogListener.NULL);

        assertTrue(result.success());
        assertTrue(pdfText(result.output()).contains("Omzet Q3"), "parameter not applied");
    }

    @Test
    void generatesIntermediateFo() throws Exception {
        RunResult result = engine.run(
                base("invoice-fo.xsl", OutputFormat.FO, "out.fo").build(), RunLogListener.NULL);

        assertTrue(result.success());
        String fo = Files.readString(result.output());
        assertTrue(fo.contains("<fo:root"), "not XSL-FO output");
        assertTrue(fo.contains("Widget"), "data not applied");
    }

    @Test
    void rendersFoFileDirectlyWithoutData() throws Exception {
        // First produce a .fo, then render it without any XML data (Template Viewer parity).
        RunResult fo = engine.run(
                base("invoice-fo.xsl", OutputFormat.FO, "direct.fo").build(), RunLogListener.NULL);
        assertTrue(fo.success());

        RunResult pdf = engine.run(RunRequest.builder()
                        .template(fo.output())
                        .format(OutputFormat.PDF)
                        .output(tmp.resolve("direct.pdf"))
                        .build(),
                RunLogListener.NULL);
        assertTrue(pdf.success(), () -> "FO render failed: " + pdf.failure().map(Throwable::toString).orElse("?"));
        assertTrue(pdfText(pdf.output()).contains("Widget"));
    }

    @Test
    void rendersHtmlDirectly() throws Exception {
        RunResult result = engine.run(
                base("invoice-html.xsl", OutputFormat.HTML, "out.html").build(), RunLogListener.NULL);

        assertTrue(result.success());
        String html = Files.readString(result.output());
        assertTrue(html.contains("<table"), "no table in HTML");
        assertTrue(html.contains("Widget"));
    }

    @Test
    void acceptsFopXconf() throws Exception {
        RunResult result = engine.run(
                base("invoice-fo.xsl", OutputFormat.PDF, "xconf.pdf")
                        .engineConfig(samples.resolve("minimal-fop.xconf")).build(),
                RunLogListener.NULL);
        assertTrue(result.success(), () -> "xconf run failed: " + result.failure().map(Throwable::toString).orElse("?"));
    }

    @Test
    void reportsFailureAsResultNotException() {
        RunResult result = engine.run(
                base("invoice-fo.xsl", OutputFormat.PDF, "missing.pdf")
                        .data(samples.resolve("does-not-exist.xml")).build(),
                RunLogListener.NULL);

        assertFalse(result.success());
        assertTrue(result.log().stream().anyMatch(e -> e.level() == LogLevel.ERROR));
    }

    @Test
    void capabilitiesDeclareFoxNamespace() {
        assertEquals(EngineIdOf(), engine.id());
        assertTrue(engine.capabilities().extensionNamespaces()
                .contains(dev.stylus.engine.api.CapabilityMatrix.NS_FOX));
        assertTrue(engine.isAvailable());
    }

    private static dev.stylus.engine.api.EngineId EngineIdOf() {
        return dev.stylus.engine.api.EngineId.FOP;
    }

    private static String pdfText(Path pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
