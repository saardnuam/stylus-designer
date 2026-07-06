package dev.stylus.xliff;

import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.OutputMode;
import dev.stylus.model.PageSetup;
import dev.stylus.model.ReportDocument;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.TableBand;
import dev.stylus.model.TableColumn;
import dev.stylus.model.TextRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generate → edit → apply must translate exactly the addressed strings (F-9.5). */
class XliffFileTest {

    @TempDir
    Path tmp;

    private static ReportDocument doc() {
        ReportDocument doc = new ReportDocument("Demo", OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.pageFooter().add(new TextRun("Confidential"));
        doc.bands().add(new StaticBand(
                List.of(new TextRun("Hello "), FieldToken.of("Name")), StyleProps.NONE));
        doc.bands().add(new GroupBand("Rows/Row", List.of(), List.of(
                new TableBand(".", List.of(
                        TableColumn.of("PRODUCT", 1, FieldToken.of("P")))))));
        return doc;
    }

    @Test
    void generateListsAllTranslatableStrings() {
        String xliff = XliffFile.generate(doc(), "en", "nl");
        assertTrue(xliff.contains("<source>Confidential</source>"));
        assertTrue(xliff.contains("<source>Hello </source>"));
        assertTrue(xliff.contains("<source>PRODUCT</source>"));
        assertTrue(xliff.contains("target-language=\"nl\""));
        assertTrue(!xliff.contains("Name"), "field tokens are not translatable text");
    }

    @Test
    void editedTargetsApplyByStableId() throws Exception {
        ReportDocument original = doc();
        String xliff = XliffFile.generate(original, "en", "nl")
                .replace("<target>Confidential</target>", "<target>Vertrouwelijk</target>")
                .replace("<target>Hello </target>", "<target>Hallo </target>")
                .replace("<target>PRODUCT</target>", "<target>PRODUCT (NL)</target>");
        Path file = Files.writeString(tmp.resolve("demo.xlf"), xliff);

        Map<String, String> targets = XliffFile.readTargets(file);
        XliffFile.apply(original, targets);

        assertEquals("Vertrouwelijk", ((TextRun) original.pageFooter().get(0)).text());
        StaticBand hello = (StaticBand) original.bands().get(0);
        assertEquals("Hallo ", ((TextRun) hello.content().get(0)).text());
        assertEquals(FieldToken.of("Name"), hello.content().get(1), "tokens untouched");
        GroupBand group = (GroupBand) original.bands().get(1);
        TableBand table = (TableBand) group.children().get(0);
        assertEquals("PRODUCT (NL)", table.columns().get(0).header());
        assertTrue(original.isModified(), "apply must mark the document modified");
    }
}
