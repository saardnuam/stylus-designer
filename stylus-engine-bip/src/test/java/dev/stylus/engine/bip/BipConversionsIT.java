package dev.stylus.engine.bip;

import dev.stylus.engine.api.EngineConversions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Conversion tools (F-5.12/13/15) against the developer-local Oracle jars — skipped when
 * lib/bip is absent (hard rule 1: CI never sees Oracle code).
 */
class BipConversionsIT {

    /** A minimal WordPad-shaped RTF template with a text-form data tag. */
    private static final String MINI_RTF =
            "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\froman\\fcharset0 Times New Roman;}}"
                    + "\\viewkind4\\uc1\\pard\\sa200\\sl276\\slmult1\\f0\\fs22 "
                    + "Hello <?Name?>!\\par}";

    private static final String MINI_FO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <fo:layout-master-set>
                <fo:simple-page-master master-name="p" page-width="210mm" page-height="297mm">
                  <fo:region-body/>
                </fo:simple-page-master>
              </fo:layout-master-set>
              <fo:page-sequence master-reference="p">
                <fo:flow flow-name="xsl-region-body"><fo:block>%s</fo:block></fo:flow>
              </fo:page-sequence>
            </fo:root>
            """;

    static Optional<BipInstallation> installation;

    @TempDir
    Path tmp;

    @BeforeAll
    static void discoverLocalJars() {
        installation = BipInstallation.discover(Path.of("..", "lib", "bip").normalize());
    }

    private EngineConversions conversions() {
        assumeTrue(installation.isPresent(), "lib/bip not present — skipping BIP integration test");
        return new BipEngine(installation.get()).conversions().orElseThrow();
    }

    @Test
    void rtfTemplateConvertsToXsl() throws Exception {
        EngineConversions tools = conversions();
        Path rtf = Files.writeString(tmp.resolve("hello.rtf"), MINI_RTF);
        Path xsl = tmp.resolve("hello.xsl");

        tools.rtfToXsl(rtf, xsl);

        String generated = Files.readString(xsl);
        assertTrue(generated.contains("stylesheet"), "no stylesheet in generated XSL");
        assertTrue(generated.contains("Name"), "data tag lost in conversion");
    }

    @Test
    void rtfTemplateConvertsToXslPlusXliff() throws Exception {
        EngineConversions tools = conversions();
        Path rtf = Files.writeString(tmp.resolve("hello.rtf"), MINI_RTF);
        Path xsl = tmp.resolve("hello.xsl");
        Path xliff = tmp.resolve("hello.xlf");

        tools.rtfToXslAndXliff(rtf, xsl, xliff);

        assertTrue(Files.size(xsl) > 0, "empty XSL");
        assertTrue(Files.readString(xliff).contains("xliff"), "no XLIFF content");
    }

    @Test
    void mergesTwoFoFilesIntoOne() throws Exception {
        EngineConversions tools = conversions();
        Path one = Files.writeString(tmp.resolve("one.fo"), MINI_FO.formatted("Alpha"));
        Path two = Files.writeString(tmp.resolve("two.fo"), MINI_FO.formatted("Beta"));
        Path merged = tmp.resolve("merged.fo");

        tools.mergeFo(List.of(one, two), merged);

        String content = Files.readString(merged);
        assertTrue(content.contains("Alpha"), "first FO lost in merge");
        assertTrue(content.contains("Beta"), "second FO lost in merge");
    }

    @Test
    void injectsProfilingIntoXsl() throws Exception {
        EngineConversions tools = conversions();
        Path source = Path.of("..", "samples", "invoice-fo.xsl").normalize();
        Path instrumented = tmp.resolve("invoice-profiled.xsl");

        tools.injectProfiling(source, instrumented);

        String result = Files.readString(instrumented);
        assertTrue(result.contains("stylesheet"), "instrumented file is not a stylesheet");
        assertNotEquals(Files.readString(source), result,
                "profiler did not change the stylesheet");
    }
}
