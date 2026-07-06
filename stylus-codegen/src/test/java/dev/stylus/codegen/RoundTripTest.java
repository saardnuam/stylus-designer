package dev.stylus.codegen;

import dev.stylus.model.Band;
import dev.stylus.model.ConditionalBand;
import dev.stylus.model.DataType;
import dev.stylus.model.FieldFormat;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.OpaqueBand;
import dev.stylus.model.OutputMode;
import dev.stylus.model.PageNumberToken;
import dev.stylus.model.PageSetup;
import dev.stylus.model.ReportDocument;
import dev.stylus.model.SortKey;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.StyleRule;
import dev.stylus.model.TableBand;
import dev.stylus.model.TableColumn;
import dev.stylus.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.stylus.codegen.GoldenFiles.assertMatchesGolden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The N7 test wall: writer output is deterministic (golden), reader maps it back to an equal
 * model, unmodified documents save byte-identically, unknown constructs survive as opaque
 * nodes, and unrecognizable files round-trip as exact bytes.
 */
class RoundTripTest {

    private final XslWriter writer = new XslWriter();
    private final XslReader reader = new XslReader();

    /** The rich reference document: params, header/footer, groups, sorts, table, conditional. */
    static ReportDocument invoiceModel() {
        ReportDocument doc = new ReportDocument("Quarterly Invoice Summary",
                OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.parameters().put("reportTitle", "Quarterly Invoice Summary");

        doc.pageHeader().add(new FieldToken("/InvoiceData/Company/Name", FieldFormat.TEXT));
        doc.pageFooter().add(new TextRun("Confidential — Finance · Page "));
        doc.pageFooter().add(new PageNumberToken());

        StaticBand title = new StaticBand(
                List.of(new FieldToken("$reportTitle", FieldFormat.TEXT)),
                new StyleProps(true, null, "18", null, "center"));

        TableBand lineItems = new TableBand("LineItem", List.of(
                TableColumn.of("PRODUCT", 2.4, new FieldToken("Product", FieldFormat.TEXT)),
                new TableColumn("QTY", 0.7, "right", List.of(new FieldToken("Qty", FieldFormat.TEXT))),
                new TableColumn("UNIT PRICE", 1, "right",
                        List.of(new FieldToken("UnitPrice", FieldFormat.number("#,##0.00")))),
                new TableColumn("AMOUNT", 1, "right",
                        List.of(new FieldToken("Amount", FieldFormat.number("#,##0.00"))),
                        List.of(new StyleRule("Amount > 500",
                                new StyleProps(true, null, null, "#B42318", null))))));

        ConditionalBand highValue = new ConditionalBand("sum(LineItem/Amount) > 1000",
                List.of(new StaticBand(List.of(new TextRun("★ High-value order")),
                        new StyleProps(true, null, null, "#C0392B", null))),
                List.of(new StaticBand(List.of(new TextRun("Standard order")), StyleProps.NONE)));

        GroupBand orders = new GroupBand("Orders/Order", List.of(),
                List.of(new StaticBand(
                                List.of(new TextRun("Order "), FieldToken.of("OrderID"),
                                        new TextRun(" · "), FieldToken.of("OrderDate")),
                                StyleProps.ofBold(),
                                List.of(new StyleRule("count(LineItem) > 2",
                                        new StyleProps(null, true, null, null, null)))),
                        lineItems,
                        highValue));

        // Document-rooted: the surrounding template matches "/", not the root element.
        GroupBand customers = new GroupBand("/InvoiceData/Customers/Customer",
                List.of(new SortKey("Name", true, DataType.TEXT)),
                List.of(new StaticBand(
                                List.of(FieldToken.of("Name"), new TextRun(" · "), FieldToken.of("Region")),
                                new StyleProps(true, null, "13", "#5145E8", null)),
                        orders));

        doc.bands().add(title);
        doc.bands().add(customers);
        doc.touch();
        return doc;
    }

    @Test
    void writesInvoiceGolden() {
        assertMatchesGolden("invoice-model.xsl", writer.write(invoiceModel()));
    }

    @Test
    void readerReproducesTheModel() {
        ReportDocument original = invoiceModel();
        String source = writer.write(original);

        ReportDocument read = reader.read(source, "ignored");

        assertEquals(original.pageSetup(), read.pageSetup());
        assertEquals(original.parameters(), read.parameters());
        assertEquals(original.pageHeader(), read.pageHeader());
        assertEquals(original.pageFooter(), read.pageFooter());
        assertEquals(original.bands(), read.bands());
        assertEquals(OutputMode.PIXEL_PERFECT, read.outputMode());
    }

    /** Conditional page layout (F-2.26/F-2.27): first page wider margins, then odd/even. */
    static ReportDocument mastersModel() {
        ReportDocument doc = new ReportDocument("Masters", OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.pageMasters().add(new dev.stylus.model.PageMaster("cover",
                new PageSetup(210, 297, 40, 25, 25, 25)));
        doc.pageMasters().add(new dev.stylus.model.PageMaster("odd",
                new PageSetup(210, 297, 15, 15, 25, 15)));
        doc.pageMasters().add(new dev.stylus.model.PageMaster("even",
                new PageSetup(210, 297, 15, 15, 15, 25)));
        doc.masterSelectors().add(new dev.stylus.model.MasterSelector("cover", "first", null, null));
        doc.masterSelectors().add(new dev.stylus.model.MasterSelector("even", null, "even", null));
        doc.masterSelectors().add(dev.stylus.model.MasterSelector.any("odd"));
        doc.bands().add(StaticBand.text("Body"));
        doc.touch();
        return doc;
    }

    @Test
    void conditionalPageMastersRoundTrip() {
        ReportDocument doc = mastersModel();
        String source = writer.write(doc);
        assertMatchesGolden("masters-model.xsl", source);

        assertTrue(source.contains("<fo:page-sequence-master master-name=\"stylus-pages\">"));
        assertTrue(source.contains(
                "<fo:conditional-page-master-reference master-reference=\"cover\" page-position=\"first\"/>"));
        assertTrue(source.contains("master-reference=\"even\" odd-or-even=\"even\"/>"));
        assertTrue(source.contains("<fo:page-sequence master-reference=\"stylus-pages\">"));

        ReportDocument read = reader.read(source, "Masters");
        assertEquals(doc.pageMasters(), read.pageMasters());
        assertEquals(doc.masterSelectors(), read.masterSelectors());
        assertEquals(doc.pageMasters().get(0).geometry(), read.pageSetup(),
                "canvas geometry = first master");
        assertEquals(source, writer.write(read), "unmodified re-save byte-identical");

        read.bands().add(StaticBand.text("more"));
        read.touch();
        String regenerated = writer.write(read);
        ReportDocument again = reader.read(regenerated, "Masters");
        assertEquals(read.pageMasters(), again.pageMasters());
        assertEquals(regenerated, regen(again), "fixed point with conditional masters");
    }

    @Test
    void xslTextEmitsNumericCharacterReferences() {
        ReportDocument doc = new ReportDocument("Text", OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.bands().add(new StaticBand(List.of(
                new TextRun("Total"),
                new dev.stylus.model.XslTextInline(" € "),   // nbsp € nbsp
                FieldToken.of("Amount"),
                new dev.stylus.model.XslTextInline("  exact  spaces\t")), StyleProps.NONE));
        doc.touch();

        String source = writer.write(doc);
        assertTrue(source.contains("<xsl:text>&#xA0;€&#xA0;</xsl:text>"),
                "invisible characters must be visible as &#x…; references");
        assertTrue(source.contains("<xsl:text>  exact  spaces&#x9;</xsl:text>"),
                "whitespace-exact text with tab as reference");

        ReportDocument read = reader.read(source, "Text");
        assertEquals(doc.bands(), read.bands(), "xsl:text must map back whitespace-exact");
        assertEquals(source, writer.write(read), "unmodified re-save byte-identical");

        read.bands().add(StaticBand.text("more"));
        read.touch();
        ReportDocument again = reader.read(writer.write(read), "Text");
        assertEquals(read.bands(), again.bands(), "fixed point with xsl:text");
    }

    @Test
    void typographyPropsRoundTrip() {
        ReportDocument doc = new ReportDocument("Type", OutputMode.PIXEL_PERFECT, PageSetup.A4);
        StyleProps rich = new StyleProps(true, null, "12", "#222222", "center",
                "Helvetica", "#FFF6D9", true, true, "1.5");
        doc.bands().add(new StaticBand(List.of(new TextRun("Styled")), rich,
                List.of(new StyleRule("x > 1",
                        new StyleProps(null, null, null, null, null,
                                null, "#FDEBE9", true, null, null)))));
        doc.touch();

        String source = writer.write(doc);
        assertTrue(source.contains("font-family=\"Helvetica\""), "font-family missing");
        assertTrue(source.contains("background-color=\"#FFF6D9\""), "background missing");
        assertTrue(source.contains("text-decoration=\"underline line-through\""),
                "combined decoration missing");
        assertTrue(source.contains("line-height=\"1.5\""), "line-height missing");

        ReportDocument read = reader.read(source, "Type");
        assertEquals(doc.bands(), read.bands(), "typography must map back exactly");
        assertEquals(source, writer.write(read), "unmodified re-save must be byte-identical");

        read.bands().add(StaticBand.text("more"));
        read.touch();
        ReportDocument again = reader.read(writer.write(read), "Type");
        assertEquals(read.bands(), again.bands(), "fixed point with typography");
    }

    @Test
    void conditionalFormatRulesEmitAsLeadingAttributeSetters() {
        String source = writer.write(invoiceModel());
        assertTrue(source.contains("<xsl:if test=\"Amount &gt; 500\">"
                        + "<xsl:attribute name=\"font-weight\">bold</xsl:attribute>"
                        + "<xsl:attribute name=\"color\">#B42318</xsl:attribute></xsl:if>"),
                "F-1.29 rules must emit as leading xsl:if attribute setters");
        // Reader mapping back to StyleRules is covered by readerReproducesTheModel().
    }

    @Test
    void unmodifiedDocumentSavesByteIdentically() {
        String source = writer.write(invoiceModel());
        ReportDocument read = reader.read(source, "t");

        assertTrue(!read.isModified());
        assertEquals(source, writer.write(read), "unmodified doc must re-save as exact bytes");
    }

    @Test
    void modifiedDocumentRegeneratesDeterministically() {
        String source = writer.write(invoiceModel());
        ReportDocument read = reader.read(source, "t");

        read.bands().add(StaticBand.text("Appended line"));
        read.touch();
        String regenerated = writer.write(read);

        assertNotEquals(source, regenerated);
        assertTrue(regenerated.contains("Appended line"));

        // and the regenerated form reads back to the same model → stable from then on
        ReportDocument again = reader.read(regenerated, "t");
        assertEquals(read.bands(), again.bands());
        assertEquals(regenerated, regen(again), "second generation must be a fixed point");
    }

    private String regen(ReportDocument doc) {
        doc.touch();
        return writer.write(doc);
    }

    @Test
    void handWrittenTemplateStaysByteIdentical() {
        String handWritten = """
                <?xml version="1.0"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <!-- deliberately outside the designer subset -->
                  <xsl:key name="k" match="Item" use="@id"/>
                  <xsl:template match="/"><xsl:apply-templates/></xsl:template>
                  <xsl:template match="Item">
                      <weird   spacing='single quotes'   />
                  </xsl:template>
                </xsl:stylesheet>
                """;

        ReportDocument doc = reader.read(handWritten, "weird");

        assertTrue(doc.isFullyOpaque(), "must fall back to fully-opaque");
        assertEquals(handWritten, writer.write(doc), "byte-identical round-trip (N7)");
    }

    @Test
    void nonXmlContentStaysByteIdentical() {
        String notXml = "this is not XML at all { %% }";
        ReportDocument doc = reader.read(notXml, "junk");
        assertEquals(notXml, writer.write(doc));
    }

    @Test
    void unknownConstructInsideRecognizedFileBecomesOpaqueBandAndSurvives() {
        String source = writer.write(invoiceModel());
        // Inject a construct the designer does not map (block-container) into the flow.
        String withContainer = source.replace(
                "        </fo:flow>",
                "          <fo:block-container position=\"absolute\" top=\"10mm\"><fo:block>fixed</fo:block></fo:block-container>\n"
                        + "        </fo:flow>");

        ReportDocument doc = reader.read(withContainer, "t");
        List<Band> bands = doc.bands();
        assertInstanceOf(OpaqueBand.class, bands.get(bands.size() - 1),
                "unmapped construct must become an opaque band");

        // Unmodified → still byte-identical.
        assertEquals(withContainer, writer.write(doc));

        // Modified → opaque subtree is preserved verbatim in the regenerated output.
        doc.bands().add(StaticBand.text("after"));
        doc.touch();
        String regenerated = writer.write(doc);
        assertTrue(regenerated.contains("<fo:block-container"),
                "opaque content lost on regeneration");
        assertTrue(regenerated.contains("position=\"absolute\""));

        ReportDocument again = reader.read(regenerated, "t");
        assertEquals(doc.bands(), again.bands(), "opaque band must survive a second round-trip");
    }

    /** M6 features together: image band, subtemplate import, page-count in the footer. */
    static ReportDocument m6Model() {
        ReportDocument doc = new ReportDocument("M6 features",
                OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.imports().add("common/branding.xsl");
        doc.pageFooter().add(new TextRun("Page "));
        doc.pageFooter().add(new PageNumberToken());
        doc.pageFooter().add(new TextRun(" of "));
        doc.pageFooter().add(new dev.stylus.model.PageCountToken());
        doc.bands().add(new dev.stylus.model.ImageBand(TINY_PNG_DATA_URI, 12.0, null));
        doc.bands().add(StaticBand.text("Body line"));
        doc.touch();
        return doc;
    }

    /** 1×1 red PNG — keeps the golden self-contained and FOP-renderable. */
    static final String TINY_PNG_DATA_URI = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842"
            + "iQAAAABJRU5ErkJggg==";

    @Test
    void m6FeaturesWriteReadAndStayStable() {
        ReportDocument doc = m6Model();
        String source = writer.write(doc);
        assertMatchesGolden("m6-model.xsl", source);

        assertTrue(source.contains("<xsl:import href=\"common/branding.xsl\"/>"), "import lost");
        assertTrue(source.contains("<fo:page-number-citation ref-id=\"stylus-last\"/>"),
                "page-count citation missing");
        assertTrue(source.contains("<fo:block id=\"stylus-last\"/>"), "citation anchor missing");
        assertTrue(source.contains("fo:external-graphic"), "image missing");

        ReportDocument read = reader.read(source, "M6 features");
        assertEquals(doc.imports(), read.imports());
        assertEquals(doc.pageFooter(), read.pageFooter());
        assertEquals(doc.bands(), read.bands());
        assertEquals(source, writer.write(read), "unmodified must re-save byte-identically");

        read.bands().add(StaticBand.text("more"));
        read.touch();
        String regenerated = writer.write(read);
        ReportDocument again = reader.read(regenerated, "M6 features");
        assertEquals(regenerated, regen(again), "regeneration must be a fixed point");
    }

    @Test
    void elementRootedTemplateIsRecognized() {
        String source = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- element-rooted, custom regions, fancy header — the real-world shape -->
                <xsl:stylesheet version="1.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                  <xsl:output method="xml" indent="yes"/>
                  <xsl:template match="/InvoiceData">
                    <fo:root>
                      <fo:layout-master-set>
                        <fo:simple-page-master master-name="a4"
                                               page-width="210mm" page-height="297mm"
                                               margin-top="15mm" margin-bottom="15mm"
                                               margin-left="20mm" margin-right="20mm">
                          <fo:region-body margin-top="12mm" margin-bottom="12mm"/>
                          <fo:region-before extent="10mm"/>
                          <fo:region-after extent="10mm"/>
                        </fo:simple-page-master>
                      </fo:layout-master-set>
                      <fo:page-sequence master-reference="a4">
                        <fo:static-content flow-name="xsl-region-before">
                          <fo:block font-size="8pt" text-align-last="justify">fancy<fo:leader leader-pattern="space"/>header</fo:block>
                        </fo:static-content>
                        <fo:flow flow-name="xsl-region-body">
                          <fo:block font-weight="bold">Title</fo:block>
                          <xsl:for-each select="Customers/Customer">
                            <fo:block><xsl:value-of select="Name"/></fo:block>
                          </xsl:for-each>
                        </fo:flow>
                      </fo:page-sequence>
                    </fo:root>
                  </xsl:template>
                </xsl:stylesheet>
                """;

        ReportDocument doc = reader.read(source, "invoices");

        assertTrue(!doc.isFullyOpaque(), "element-rooted template must be recognized");
        assertEquals("/InvoiceData", doc.rootMatch());
        assertEquals(12, doc.pageSetup().bodyTopMm(), "custom region-body must map");
        assertEquals(10, doc.pageSetup().beforeExtentMm(), "custom region-before must map");
        // Fancy header (text-align-last) is preserved wholesale, not mapped:
        assertEquals(1, doc.pageHeader().size());
        assertInstanceOf(dev.stylus.model.OpaqueInline.class, doc.pageHeader().get(0));
        // The group structure is live:
        assertInstanceOf(GroupBand.class, doc.bands().get(1));
        assertEquals("Customers/Customer", ((GroupBand) doc.bands().get(1)).selectXPath());

        // Unmodified: exact bytes (N7).
        assertEquals(source, writer.write(doc));

        // Modified: match + regions + wholesale header survive, and regeneration is stable.
        doc.bands().add(StaticBand.text("added"));
        doc.touch();
        String regenerated = writer.write(doc);
        assertTrue(regenerated.contains("match=\"/InvoiceData\""), "root match lost");
        assertTrue(regenerated.contains("margin-top=\"12mm\""), "region geometry lost");
        assertTrue(regenerated.contains("text-align-last=\"justify\""), "opaque header lost");
        ReportDocument again = reader.read(regenerated, "invoices");
        assertEquals(doc.bands(), again.bands());
        assertEquals(regenerated, regen(again), "regeneration must be a fixed point");
    }

    @Test
    void unknownAttributesForceOpaquePreservation() {
        String source = writer.write(invoiceModel());

        // disable-output-escaping on a value-of → that inline must not map to a FieldToken.
        String doe = source.replace(
                "<xsl:value-of select=\"OrderDate\"/>",
                "<xsl:value-of select=\"OrderDate\" disable-output-escaping=\"yes\"/>");
        ReportDocument read = reader.read(doe, "t");
        read.bands().add(StaticBand.text("edit"));
        read.touch();
        assertTrue(writer.write(read).contains("disable-output-escaping=\"yes\""),
                "unknown value-of attribute lost on regeneration");

        // an attribute on fo:table outside the writer shape → whole table stays opaque.
        String spaced = source.replace(
                "<fo:table table-layout=\"fixed\" width=\"100%\">",
                "<fo:table table-layout=\"fixed\" width=\"100%\" space-before=\"1mm\">");
        ReportDocument readSpaced = reader.read(spaced, "t");
        readSpaced.bands().add(StaticBand.text("edit"));
        readSpaced.touch();
        assertTrue(writer.write(readSpaced).contains("space-before=\"1mm\""),
                "unknown table attribute lost on regeneration");

        // an empty block with an id is content, not the writer's placeholder.
        String anchored = source.replace(
                "        </fo:flow>",
                "          <fo:block id=\"last\"/>\n        </fo:flow>");
        ReportDocument readAnchored = reader.read(anchored, "t");
        readAnchored.bands().add(StaticBand.text("edit"));
        readAnchored.touch();
        assertTrue(writer.write(readAnchored).contains("id=\"last\""),
                "id'd empty block lost on regeneration");
    }

    @Test
    void webModeRoundTrips() {
        ReportDocument doc = new ReportDocument("Web report", OutputMode.WEB, PageSetup.A4);
        doc.bands().add(new StaticBand(List.of(new TextRun("Hello "), FieldToken.of("Name")),
                StyleProps.ofBold(),
                List.of(new StyleRule("Name = 'VIP'",
                        new StyleProps(null, null, null, "#B42318", null)))));
        doc.bands().add(new TableBand("Rows/Row", List.of(
                TableColumn.of("COL", 1, FieldToken.of("Value")))));
        doc.touch();

        String source = writer.write(doc);
        assertMatchesGolden("web-model.xsl", source);

        ReportDocument read = reader.read(source, "t");
        assertEquals(OutputMode.WEB, read.outputMode());
        assertEquals("Web report", read.title());
        assertEquals(doc.bands().size(), read.bands().size());
        assertEquals(source, writer.write(read), "unmodified web doc re-saves identically");
    }
}
