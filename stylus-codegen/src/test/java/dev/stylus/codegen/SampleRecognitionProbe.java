package dev.stylus.codegen;

import dev.stylus.model.Band;
import dev.stylus.model.ReportDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Probe: how much of the hand-written sample the reader maps, and that saving stays byte-safe. */
class SampleRecognitionProbe {

    @Test
    void handWrittenInvoiceSampleRoundTripsByteSafe() throws Exception {
        Path sample = Path.of("..", "samples", "invoice-fo.xsl").normalize();
        String source = Files.readString(sample);

        ReportDocument doc = new XslReader().read(source, "invoice");
        System.out.println("[probe] fully opaque: " + doc.isFullyOpaque()
                + ", bands: " + doc.bands().size());
        for (Band band : doc.bands()) {
            System.out.println("[probe]   " + band.getClass().getSimpleName());
        }

        // Element-rooted recognition (match="/InvoiceData"): the customer group is live even
        // though fancy blocks around it stay opaque.
        assertEquals("/InvoiceData", doc.rootMatch());
        org.junit.jupiter.api.Assertions.assertTrue(
                doc.bands().stream().anyMatch(b -> b instanceof dev.stylus.model.GroupBand),
                "hand-written sample should expose its for-each as a live group");

        assertEquals(source, new XslWriter().write(doc), "unmodified must stay byte-identical");
    }
}
