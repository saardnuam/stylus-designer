package dev.stylus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XdoConfigTest {

    @TempDir
    Path tmp;

    @Test
    void loadsPropertiesFontsAndParameters() throws Exception {
        Path file = tmp.resolve("xdo.cfg");
        Files.writeString(file, """
                <config version="1.0.0" xmlns="http://xmlns.oracle.com/oxp/config/">
                  <properties>
                    <property name="xdo-debug-level">STATEMENT</property>
                    <property name="pdf-security">true</property>
                    <property name="xslt._XDOLOCALE">nl-NL</property>
                    <property name="xslt.reportTitle">Omzet</property>
                  </properties>
                  <fonts>
                    <font family="Barcode" style="normal" weight="normal">
                      <truetype path="/fonts/barcode.ttf"/>
                    </font>
                  </fonts>
                </config>
                """);

        XdoConfig config = XdoConfig.load(file);

        assertEquals("STATEMENT", config.property("xdo-debug-level"));
        assertEquals(4, config.properties().size());
        assertEquals(1, config.fonts().size());
        assertEquals("/fonts/barcode.ttf", config.fonts().get(0).truetypePath());
        assertEquals(Map.of("_XDOLOCALE", "nl-NL", "reportTitle", "Omzet"),
                config.templateParameters());
    }

    @Test
    void rejectsNonBipConfig() throws Exception {
        Path file = tmp.resolve("other.xml");
        Files.writeString(file, "<config><properties/></config>"); // no oxp namespace

        XdoConfigException e = assertThrows(XdoConfigException.class, () -> XdoConfig.load(file));
        assertTrue(e.getMessage().contains("not a BI Publisher configuration file"));
    }

    @Test
    void roundTripsThroughSave() throws Exception {
        XdoConfig config = new XdoConfig();
        config.setProperty("xdo-debug-level", "EVENT");
        config.setProperty("xslt._XDOTIMEZONE", "Europe/Amsterdam");
        config.addFont(new FontMapping("Mono", "italic", "bold", "/x/mono.ttf"));

        Path file = tmp.resolve("out.cfg");
        config.save(file);
        XdoConfig reloaded = XdoConfig.load(file);

        assertEquals(config.properties(), reloaded.properties());
        assertEquals(config.fonts(), reloaded.fonts());
    }

    @Test
    void catalogCoversTheDocumentedProperties() {
        PropertyCatalog catalog = PropertyCatalog.instance();

        assertTrue(catalog.all().size() >= 190,
                "expected ~194 catalogued properties, got " + catalog.all().size());
        assertEquals("system", catalog.groupOf("xdo-debug-level"));
        assertEquals("pdf-security", catalog.groupOf("pdf-encryption-level"));
        assertEquals("xliff", catalog.groupOf("xliff-trans-expansion"));
        assertTrue(catalog.isKnown("xslt.myParam"), "dynamic prefix must be known");
        assertEquals("dynamic-xslt", catalog.groupOf("xslt.myParam"));
        assertTrue(catalog.isKnown("font-substitute.Arial"));
        assertEquals("unknown", catalog.groupOf("some-new-13c-property"));
    }
}
