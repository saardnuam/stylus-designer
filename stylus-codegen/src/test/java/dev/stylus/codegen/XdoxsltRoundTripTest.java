package dev.stylus.codegen;

import dev.stylus.model.ConditionalBand;
import dev.stylus.model.FieldFormat;
import dev.stylus.model.FieldToken;
import dev.stylus.model.OutputMode;
import dev.stylus.model.PageSetup;
import dev.stylus.model.ReportDocument;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-3.1 codegen: documents using xdoxslt: extension functions get the Oracle namespace
 * declared and round-trip cleanly; documents without it stay namespace-free.
 */
class XdoxsltRoundTripTest {

    private final XslWriter writer = new XslWriter();
    private final XslReader reader = new XslReader();

    private ReportDocument docWithXdoxslt() {
        ReportDocument doc = new ReportDocument("BIP functions",
                OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.bands().add(new StaticBand(List.of(
                new TextRun("Printed "),
                FieldToken.of("xdoxslt:xdo_format_date(xdoxslt:sysdate(), 'DD-MON-YYYY', $_XDOLOCALE)")),
                StyleProps.NONE));
        doc.bands().add(new ConditionalBand("xdoxslt:get_variable($_XDOCTX, 'flag') = 1",
                List.of(StaticBand.text("flagged")), List.of()));
        doc.touch();
        return doc;
    }

    @Test
    void declaresNamespaceOnlyWhenUsed() {
        String withFunctions = writer.write(docWithXdoxslt());
        assertTrue(withFunctions.contains(
                        "xmlns:xdoxslt=\"http://www.oracle.com/XSL/Transform/java/oracle.apps.xdo.template.rtf.XSLTFunctions\""),
                "xdoxslt namespace missing");

        ReportDocument plain = ReportDocument.empty();
        plain.bands().add(StaticBand.text("plain"));
        plain.touch();
        assertFalse(writer.write(plain).contains("xdoxslt"),
                "namespace must not leak into plain documents");
    }

    @Test
    void roundTripsWithFunctionsIntact() {
        String source = writer.write(docWithXdoxslt());
        ReportDocument read = reader.read(source, "t");

        assertEquals(docWithXdoxslt().bands(), read.bands());
        assertEquals(source, writer.write(read), "unmodified xdoxslt doc re-saves identically");

        read.bands().add(StaticBand.text("more"));
        read.touch();
        String regenerated = writer.write(read);
        assertTrue(regenerated.contains("xmlns:xdoxslt="), "namespace lost on regeneration");
        assertTrue(regenerated.contains("xdoxslt:get_variable($_XDOCTX, 'flag') = 1"));
    }
}
