package dev.stylus.app.run;

import dev.stylus.codegen.XslReader;
import dev.stylus.xliff.XliffFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bench's FOP XLIFF path (F-9.5): template → XLIFF skeleton → edit → translated template. */
class XliffTemplateFlowTest {

    @TempDir
    Path tmp;

    @Test
    void designedSampleTranslatesEndToEnd() throws Exception {
        Path template = Path.of("..", "samples", "invoice-designed.xsl").normalize();
        String source = Files.readString(template);
        var doc = new XslReader().read(source, "invoice-designed");
        assertTrue(!doc.isFullyOpaque(), "sample must be designer-readable");

        String skeleton = XliffFile.generate(doc, "en", "nl");
        assertTrue(skeleton.contains("<source>PRODUCT</source>"), "table header not extracted");

        Path xliff = Files.writeString(tmp.resolve("invoice-nl.xlf"),
                skeleton.replace("<target>PRODUCT</target>", "<target>ARTIKEL</target>"));

        Path translated = RunService.applyXliffToTemplate(template, xliff);
        String result = Files.readString(translated);
        assertTrue(result.contains("ARTIKEL"), "translation not applied");
        assertTrue(!result.contains(">PRODUCT<"), "source header should be replaced");
    }
}
