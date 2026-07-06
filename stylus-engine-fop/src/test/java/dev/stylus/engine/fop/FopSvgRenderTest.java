package dev.stylus.engine.fop;

import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.RunLogListener;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Instream SVG must actually paint through FOP+Batik (F-7.2), not just not-crash. */
class FopSvgRenderTest {

    private static final String FO_WITH_SVG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <fo:layout-master-set>
                <fo:simple-page-master master-name="p" page-width="210mm" page-height="297mm"
                                       margin="10mm">
                  <fo:region-body/>
                </fo:simple-page-master>
              </fo:layout-master-set>
              <fo:page-sequence master-reference="p">
                <fo:flow flow-name="xsl-region-body">
                  <fo:block>
                    <fo:instream-foreign-object content-width="80mm" content-height="80mm">
                      <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                        <rect x="0" y="0" width="100" height="100" fill="#CC1111"/>
                      </svg>
                    </fo:instream-foreign-object>
                  </fo:block>
                </fo:flow>
              </fo:page-sequence>
            </fo:root>
            """;

    @TempDir
    Path tmp;

    @Test
    void instreamSvgPaintsRedThroughBatik() throws Exception {
        Path fo = Files.writeString(tmp.resolve("svg.fo"), FO_WITH_SVG);
        Path out = tmp.resolve("svg.pdf");

        RunResult result = new FopEngine().run(RunRequest.builder()
                        .template(fo)
                        .format(OutputFormat.PDF)
                        .output(out)
                        .build(),
                RunLogListener.NULL);

        assertTrue(result.success(), () -> "render failed: "
                + result.failure().map(Throwable::toString).orElse("?") + " log=" + result.log());
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            BufferedImage page = new PDFRenderer(doc).renderImage(0);
            // Sample inside the 80mm square (margins 10mm → ~28px at 72dpi; sample at 100,100).
            int rgb = page.getRGB(100, 100);
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            assertTrue(red > 150 && green < 100,
                    () -> "expected the SVG rect's red at (100,100), got rgb=0x"
                            + Integer.toHexString(rgb));
        }
    }
}
