package dev.stylus.codegen;

import dev.stylus.model.OutputMode;
import dev.stylus.model.PageSetup;
import dev.stylus.model.ReportDocument;
import org.junit.jupiter.api.Test;

import static dev.stylus.codegen.GoldenFiles.assertMatchesGolden;

class XslWriterGoldenTest {

    private final XslWriter writer = new XslWriter();

    @Test
    void emptyPixelPerfectA4() {
        ReportDocument doc = ReportDocument.empty();
        assertMatchesGolden("empty-a4.xsl", writer.write(doc));
    }

    @Test
    void emptyPixelPerfectLetterLandscape() {
        ReportDocument doc = new ReportDocument("Letter landscape",
                OutputMode.PIXEL_PERFECT, PageSetup.LETTER.landscape());
        assertMatchesGolden("empty-letter-landscape.xsl", writer.write(doc));
    }

    @Test
    void emptyWebMode() {
        ReportDocument doc = new ReportDocument("Web report", OutputMode.WEB, PageSetup.A4);
        assertMatchesGolden("empty-web.xsl", writer.write(doc));
    }
}
