package dev.stylus.codegen;

import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.RunLogListener;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import dev.stylus.engine.fop.FopEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generated code is not just golden-file-pretty — it must actually render (F-11.5). */
class GeneratedTemplateRendersTest {

    @TempDir
    Path tmp;

    @Test
    void pageCountAndImageRenderThroughFop() throws Exception {
        var doc = RoundTripTest.m6Model();
        doc.imports().clear(); // the render needs resolvable imports; geometry is tested above
        doc.touch();
        Path template = tmp.resolve("m6.xsl");
        Files.writeString(template, new XslWriter().write(doc));
        Path data = Path.of(getClass().getResource("/invoice.xml").toURI());
        Path out = tmp.resolve("m6.pdf");

        RunResult result = new FopEngine().run(RunRequest.builder()
                        .template(template)
                        .data(data)
                        .format(OutputFormat.PDF)
                        .output(out)
                        .build(),
                RunLogListener.NULL);

        assertTrue(result.success(), () -> "render failed: "
                + result.failure().map(Throwable::toString).orElse("?") + " log=" + result.log());
        try (PDDocument doc2 = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(doc2);
            assertTrue(text.contains("Page 1 of 1"), "page-count citation did not resolve: " + text);
            assertTrue(text.contains("Body line"), "body missing");
        }
    }

    @Test
    void conditionalPageMastersRenderThroughFop() throws Exception {
        Path template = tmp.resolve("masters.xsl");
        Files.writeString(template, new XslWriter().write(RoundTripTest.mastersModel()));
        Path data = Path.of(getClass().getResource("/invoice.xml").toURI());
        Path out = tmp.resolve("masters.pdf");

        RunResult result = new FopEngine().run(RunRequest.builder()
                        .template(template)
                        .data(data)
                        .format(OutputFormat.PDF)
                        .output(out)
                        .build(),
                RunLogListener.NULL);

        assertTrue(result.success(), () -> "render failed: "
                + result.failure().map(Throwable::toString).orElse("?") + " log=" + result.log());
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertTrue(new PDFTextStripper().getText(doc).contains("Body"));
        }
    }

    @Test
    void generatedInvoiceTemplateRendersRealPdf() throws Exception {
        String xsl = new XslWriter().write(RoundTripTest.invoiceModel());
        Path template = tmp.resolve("generated-invoice.xsl");
        Files.writeString(template, xsl);
        Path data = Path.of(getClass().getResource("/invoice.xml").toURI());
        Path out = tmp.resolve("generated-invoice.pdf");

        RunResult result = new FopEngine().run(RunRequest.builder()
                        .template(template)
                        .data(data)
                        .format(OutputFormat.PDF)
                        .output(out)
                        .build(),
                RunLogListener.NULL);

        assertTrue(result.success(), () -> "render failed: "
                + result.failure().map(Throwable::toString).orElse("?") + " log=" + result.log());
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Widget"), "table row missing");
            assertTrue(text.contains("Quarterly Invoice Summary"), "title param missing");
            assertTrue(text.contains("Jansen & Zonen"), "group header missing");
            assertTrue(text.contains("High-value order"), "conditional band missing");
            assertTrue(text.contains("1,099.00"), "number mask not applied");
        }
    }
}
