package dev.stylus.codegen;

import dev.stylus.model.Band;
import dev.stylus.model.ConditionalBand;
import dev.stylus.model.DataType;
import dev.stylus.model.FieldFormat;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.ImageBand;
import dev.stylus.model.InlineNode;
import dev.stylus.model.OpaqueBand;
import dev.stylus.model.OpaqueInline;
import dev.stylus.model.OutputMode;
import dev.stylus.model.PageCountToken;
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
import dev.stylus.model.XslTextInline;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XSL → model reader (round-trip N7). Recognition is deliberately strict: it maps exactly the
 * shapes {@link XslWriter} emits; any element, attribute or structure outside that subset
 * becomes an opaque node that re-emits its serialized source. A document whose top-level
 * structure cannot be mapped at all becomes one opaque band carrying the *exact original text*,
 * which the writer reproduces byte-identically.
 */
public final class XslReader {

    private static final String XSL_NS = "http://www.w3.org/1999/XSL/Transform";
    private static final String FO_NS = "http://www.w3.org/1999/XSL/Format";

    private static final Pattern FORMAT_NUMBER =
            Pattern.compile("^format-number\\((.+?),\\s*'([^']*)'\\)$");
    private static final Pattern FONT_SIZE_PT = Pattern.compile("^([0-9.]+)pt$");
    private static final Set<String> KNOWN_BLOCK_ATTRS = Set.of(
            "font-weight", "font-style", "font-size", "color", "text-align",
            "font-family", "background-color", "text-decoration", "line-height");

    /** Parses template source into a model; never throws for unrecognized content (N7). */
    public ReportDocument read(String source, String fallbackTitle) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document dom = dbf.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new StringReader(source)));
            return recognize(dom, source, fallbackTitle);
        } catch (Exception e) {
            // Not even well-formed XML for us — fully opaque, byte-preserved.
            return opaqueDocument(source, fallbackTitle);
        }
    }

    /** "/" or a single element-rooted step like "/InvoiceData" (F-2.1 real-world templates). */
    private static final Pattern ROOT_MATCH = Pattern.compile("^/([A-Za-z_][\\w.-]*)?$");

    private ReportDocument recognize(Document dom, String source, String title) {
        Element stylesheet = dom.getDocumentElement();
        if (!isXsl(stylesheet, "stylesheet") && !isXsl(stylesheet, "transform")) {
            return opaqueDocument(source, title);
        }
        // version 1.0 + namespace declarations only; anything else (2.0, exclude-result-prefixes
        // …) is outside the regenerable subset.
        var stylesheetAttrs = stylesheet.getAttributes();
        for (int i = 0; i < stylesheetAttrs.getLength(); i++) {
            Node attr = stylesheetAttrs.item(i);
            boolean nsDecl = attr.getNodeName().equals("xmlns")
                    || attr.getNodeName().startsWith("xmlns:");
            boolean version = attr.getNodeName().equals("version")
                    && "1.0".equals(attr.getNodeValue());
            if (!nsDecl && !version) {
                return opaqueDocument(source, title);
            }
        }

        Element rootTemplate = null;
        List<Element> params = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        for (Element child : childElements(stylesheet)) {
            if (isXsl(child, "import") && hasOnlyAttrs(child, Set.of("href"))
                    && isEmptyElement(child)) {
                imports.add(child.getAttribute("href"));
                continue;
            }
            if (isXsl(child, "output") && isWriterOutputShape(child)) {
                continue; // serialization handled by the writer
            }
            if (isXsl(child, "param") && hasOnlyAttrs(child, Set.of("name"))
                    && childElements(child).isEmpty()) {
                params.add(child);
                continue;
            }
            if (isXsl(child, "template") && hasOnlyAttrs(child, Set.of("match"))
                    && ROOT_MATCH.matcher(child.getAttribute("match")).matches()
                    && rootTemplate == null) {
                rootTemplate = child;
                continue;
            }
            // Named templates, imports, keys … — out of the M4 subset: keep whole file opaque.
            return opaqueDocument(source, title);
        }
        if (rootTemplate == null) {
            return opaqueDocument(source, title);
        }

        List<Element> templateChildren = childElements(rootTemplate);
        if (templateChildren.size() != 1) {
            return opaqueDocument(source, title);
        }
        Element root = templateChildren.get(0);

        ReportDocument doc;
        if (isFo(root, "root") && root.getAttributes().getLength() == 0) {
            doc = recognizeFo(root, source, title);
        } else if ("html".equals(root.getLocalName()) && root.getNamespaceURI() == null) {
            doc = recognizeHtml(root, source, title);
        } else {
            return opaqueDocument(source, title);
        }
        if (doc == null) {
            return opaqueDocument(source, title);
        }
        doc.setRootMatch(rootTemplate.getAttribute("match"));
        doc.imports().addAll(imports);
        for (Element param : params) {
            doc.parameters().put(param.getAttribute("name"), param.getTextContent());
        }
        doc.setOriginalSource(source);
        doc.markSaved();
        return doc;
    }

    /** Exactly the writer's xsl:output form for either mode; anything richer → opaque. */
    private static boolean isWriterOutputShape(Element output) {
        if (!hasOnlyAttrs(output, Set.of("method", "indent"))) {
            return false;
        }
        String method = output.getAttribute("method");
        return ("xml".equals(method) || "html".equals(method))
                && "yes".equals(output.getAttribute("indent"));
    }

    // ---------- FO recognition ----------

    /** Returns null when the structure falls outside the recognized subset. */
    private ReportDocument recognizeFo(Element foRoot, String source, String title) {
        List<Element> rootKids = childElements(foRoot);
        if (rootKids.size() != 2 || !isFo(rootKids.get(0), "layout-master-set")
                || !isFo(rootKids.get(1), "page-sequence")
                || !hasOnlyAttrs(rootKids.get(1), Set.of("master-reference"))) {
            return null;
        }
        LayoutMasters layout = readLayoutMasters(rootKids.get(0));
        if (layout == null
                || !layout.sequenceRef().equals(rootKids.get(1).getAttribute("master-reference"))) {
            return null;
        }

        ReportDocument doc = new ReportDocument(title, OutputMode.PIXEL_PERFECT, layout.canvas());
        doc.pageMasters().addAll(layout.masters());
        doc.masterSelectors().addAll(layout.selectors());

        for (Element sequenceChild : childElements(rootKids.get(1))) {
            if (isFo(sequenceChild, "static-content")
                    && hasOnlyAttrs(sequenceChild, Set.of("flow-name"))) {
                String flowName = sequenceChild.getAttribute("flow-name");
                List<InlineNode> content = readStaticContent(sequenceChild);
                if ("xsl-region-before".equals(flowName)) {
                    doc.pageHeader().addAll(content);
                } else if ("xsl-region-after".equals(flowName)) {
                    doc.pageFooter().addAll(content);
                } else {
                    return null;
                }
            } else if (isFo(sequenceChild, "flow")
                    && "xsl-region-body".equals(sequenceChild.getAttribute("flow-name"))
                    && hasOnlyAttrs(sequenceChild, Set.of("flow-name"))) {
                for (Element bandEl : childElements(sequenceChild)) {
                    if (isFo(bandEl, "block") && isEmptyElement(bandEl)
                            && bandEl.getAttributes().getLength() == 0) {
                        continue; // the writer's empty-flow placeholder (attribute-less only!)
                    }
                    if (isPageCountAnchor(bandEl)) {
                        continue; // the writer's citation anchor — re-emitted when tokens exist
                    }
                    doc.bands().add(readBand(bandEl));
                }
            } else {
                return null;
            }
        }
        return doc;
    }

    /**
     * Header/footer content. The writer's exact shape (one fo:block with only font-size="8pt")
     * maps to inline nodes; any richer region is preserved wholesale as one opaque inline,
     * which the writer re-emits verbatim inside the static-content.
     */
    private List<InlineNode> readStaticContent(Element staticContent) {
        List<Element> blocks = childElements(staticContent);
        if (blocks.size() == 1 && isFo(blocks.get(0), "block")
                && "8pt".equals(blocks.get(0).getAttribute("font-size"))
                && hasOnlyAttrs(blocks.get(0), Set.of("font-size"))) {
            Optional<List<InlineNode>> content = readInlines(blocks.get(0));
            if (content.isPresent()) {
                return content.get();
            }
        }
        StringBuilder wholesale = new StringBuilder();
        for (Element block : blocks) {
            wholesale.append(XmlFragments.serialize(block));
        }
        return List.of(new OpaqueInline(wholesale.toString()));
    }

    private static final Set<String> MASTER_ATTRS = Set.of("master-name", "page-width",
            "page-height", "margin-top", "margin-bottom", "margin-left", "margin-right");
    private static final Set<String> SELECTOR_ATTRS = Set.of("master-reference",
            "page-position", "odd-or-even", "blank-or-not-blank");

    /** Parsed layout-master-set: canvas geometry, multi-master lists, sequence reference. */
    private record LayoutMasters(PageSetup canvas, List<dev.stylus.model.PageMaster> masters,
                                 List<dev.stylus.model.MasterSelector> selectors,
                                 String sequenceRef) { }

    /**
     * Single master (the writer's simple mode) or masters + one page-sequence-master with
     * conditional references (F-2.26/F-2.27); anything else — page-sequence-masters with other
     * sub-sequences, extra attributes — returns null and the document stays opaque.
     */
    private LayoutMasters readLayoutMasters(Element layoutMasterSet) {
        if (layoutMasterSet.getAttributes().getLength() != 0) {
            return null;
        }
        List<dev.stylus.model.PageMaster> masters = new ArrayList<>();
        Element sequenceMaster = null;
        for (Element child : childElements(layoutMasterSet)) {
            if (isFo(child, "simple-page-master")) {
                PageSetup geometry = readPageMaster(child);
                if (geometry == null) {
                    return null;
                }
                masters.add(new dev.stylus.model.PageMaster(
                        child.getAttribute("master-name"), geometry));
            } else if (isFo(child, "page-sequence-master") && sequenceMaster == null
                    && hasOnlyAttrs(child, Set.of("master-name"))) {
                sequenceMaster = child;
            } else {
                return null;
            }
        }
        if (masters.isEmpty()) {
            return null;
        }

        if (sequenceMaster == null) {
            if (masters.size() != 1) {
                return null; // several masters but nothing selecting between them
            }
            return new LayoutMasters(masters.get(0).geometry(), List.of(), List.of(),
                    masters.get(0).name());
        }

        List<Element> sequenceKids = childElements(sequenceMaster);
        if (sequenceKids.size() != 1
                || !isFo(sequenceKids.get(0), "repeatable-page-master-alternatives")
                || sequenceKids.get(0).getAttributes().getLength() != 0) {
            return null;
        }
        List<dev.stylus.model.MasterSelector> selectors = new ArrayList<>();
        for (Element ref : childElements(sequenceKids.get(0))) {
            if (!isFo(ref, "conditional-page-master-reference")
                    || !hasOnlyAttrs(ref, SELECTOR_ATTRS) || !isEmptyElement(ref)) {
                return null;
            }
            selectors.add(new dev.stylus.model.MasterSelector(
                    ref.getAttribute("master-reference"),
                    blankToNull(ref.getAttribute("page-position")),
                    blankToNull(ref.getAttribute("odd-or-even")),
                    blankToNull(ref.getAttribute("blank-or-not-blank"))));
        }
        if (selectors.isEmpty()) {
            return null;
        }
        return new LayoutMasters(masters.get(0).geometry(), masters, selectors,
                sequenceMaster.getAttribute("master-name"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private PageSetup readPageMaster(Element m) {
        if (!hasOnlyAttrs(m, MASTER_ATTRS)) {
            return null;
        }
        List<Element> regions = childElements(m);
        if (regions.size() != 3
                || !isFo(regions.get(0), "region-body")
                || !hasOnlyAttrs(regions.get(0), Set.of("margin-top", "margin-bottom"))
                || !isFo(regions.get(1), "region-before")
                || !hasOnlyAttrs(regions.get(1), Set.of("extent"))
                || !isFo(regions.get(2), "region-after")
                || !hasOnlyAttrs(regions.get(2), Set.of("extent"))) {
            return null;
        }
        try {
            return new PageSetup(
                    mm(m.getAttribute("page-width")),
                    mm(m.getAttribute("page-height")),
                    mm(m.getAttribute("margin-top")),
                    mm(m.getAttribute("margin-bottom")),
                    mm(m.getAttribute("margin-left")),
                    mm(m.getAttribute("margin-right")),
                    mm(regions.get(0).getAttribute("margin-top")),
                    mm(regions.get(0).getAttribute("margin-bottom")),
                    mm(regions.get(1).getAttribute("extent")),
                    mm(regions.get(2).getAttribute("extent")));
        } catch (NumberFormatException e) {
            return null; // non-mm units → outside the subset for now
        }
    }

    private Band readBand(Element el) {
        if (isXsl(el, "for-each") && hasOnlyAttrs(el, Set.of("select"))) {
            List<SortKey> sorts = new ArrayList<>();
            List<Band> children = new ArrayList<>();
            for (Element child : childElements(el)) {
                if (isXsl(child, "sort")) {
                    if (!hasOnlyAttrs(child, Set.of("select", "order", "data-type"))
                            || !isEmptyElement(child)) {
                        return new OpaqueBand(XmlFragments.serialize(el));
                    }
                    sorts.add(new SortKey(
                            child.getAttribute("select"),
                            !"descending".equals(child.getAttribute("order")),
                            "number".equals(child.getAttribute("data-type"))
                                    ? DataType.NUMBER : DataType.TEXT));
                } else {
                    children.add(readBand(child));
                }
            }
            return new GroupBand(el.getAttribute("select"), sorts, children);
        }

        if (isFo(el, "table")) {
            TableBand table = readTable(el);
            return table != null ? table : new OpaqueBand(XmlFragments.serialize(el));
        }

        if (isFo(el, "block")) {
            ImageBand image = readImage(el);
            if (image != null) {
                return image;
            }
            StyleProps style = readStyle(el);
            if (style == null) {
                return new OpaqueBand(XmlFragments.serialize(el));
            }
            return readBlockContent(el)
                    .<Band>map(bc -> new StaticBand(bc.inlines(), style, bc.rules()))
                    .orElseGet(() -> new OpaqueBand(XmlFragments.serialize(el)));
        }

        if (isXsl(el, "if") && hasOnlyAttrs(el, Set.of("test"))) {
            List<Band> then = new ArrayList<>();
            for (Element child : childElements(el)) {
                then.add(readBand(child));
            }
            return new ConditionalBand(el.getAttribute("test"), then, List.of());
        }

        if (isXsl(el, "choose") && el.getAttributes().getLength() == 0) {
            List<Element> kids = childElements(el);
            if (kids.size() == 2
                    && isXsl(kids.get(0), "when") && hasOnlyAttrs(kids.get(0), Set.of("test"))
                    && isXsl(kids.get(1), "otherwise")
                    && kids.get(1).getAttributes().getLength() == 0) {
                List<Band> then = new ArrayList<>();
                for (Element child : childElements(kids.get(0))) {
                    then.add(readBand(child));
                }
                List<Band> otherwise = new ArrayList<>();
                for (Element child : childElements(kids.get(1))) {
                    otherwise.add(readBand(child));
                }
                return new ConditionalBand(kids.get(0).getAttribute("test"), then, otherwise);
            }
            return new OpaqueBand(XmlFragments.serialize(el));
        }

        return new OpaqueBand(XmlFragments.serialize(el));
    }

    private static final Pattern URL_SRC = Pattern.compile("^url\\('(.+)'\\)$");

    /** The writer's image shape: attribute-less block with one external-graphic; else null. */
    private ImageBand readImage(Element block) {
        if (block.getAttributes().getLength() != 0) {
            return null;
        }
        List<Element> kids = childElements(block);
        if (kids.size() != 1 || !isFo(kids.get(0), "external-graphic")
                || !block.getTextContent().isBlank()) {
            return null;
        }
        Element graphic = kids.get(0);
        if (!hasOnlyAttrs(graphic, Set.of("src", "content-width", "content-height"))
                || !childElements(graphic).isEmpty()) {
            return null;
        }
        Matcher src = URL_SRC.matcher(graphic.getAttribute("src"));
        if (!src.matches()) {
            return null;
        }
        try {
            return new ImageBand(src.group(1),
                    optionalMm(graphic.getAttribute("content-width")),
                    optionalMm(graphic.getAttribute("content-height")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double optionalMm(String value) {
        return value == null || value.isBlank() ? null : mm(value);
    }

    /** The writer's end-of-flow page-count anchor: {@code <fo:block id="stylus-last"/>}. */
    private boolean isPageCountAnchor(Element el) {
        return isFo(el, "block") && isEmptyElement(el)
                && "stylus-last".equals(el.getAttribute("id"))
                && hasOnlyAttrs(el, Set.of("id"));
    }

    /** Recognizes exactly the writer's table shape; null on any deviation. */
    private TableBand readTable(Element table) {
        if (!"fixed".equals(table.getAttribute("table-layout"))
                || !"100%".equals(table.getAttribute("width"))
                || !hasOnlyAttrs(table, Set.of("table-layout", "width"))) {
            return null;
        }
        List<Double> weights = new ArrayList<>();
        Element header = null;
        Element body = null;
        for (Element child : childElements(table)) {
            if (isFo(child, "table-column") && hasOnlyAttrs(child, Set.of("column-width"))) {
                Matcher m = Pattern.compile("^proportional-column-width\\(([0-9.]+)\\)$")
                        .matcher(child.getAttribute("column-width"));
                if (!m.matches()) {
                    return null;
                }
                weights.add(Double.parseDouble(m.group(1)));
            } else if (isFo(child, "table-header") && child.getAttributes().getLength() == 0
                    && header == null) {
                header = child;
            } else if (isFo(child, "table-body") && child.getAttributes().getLength() == 0
                    && body == null) {
                body = child;
            } else {
                return null;
            }
        }
        if (header == null || body == null || weights.isEmpty()) {
            return null;
        }

        List<Element> headerRows = childElements(header);
        if (headerRows.size() != 1
                || !"bold".equals(headerRows.get(0).getAttribute("font-weight"))
                || !hasOnlyAttrs(headerRows.get(0), Set.of("font-weight"))) {
            return null;
        }
        List<String> headers = new ArrayList<>();
        List<String> aligns = new ArrayList<>();
        for (Element cell : childElements(headerRows.get(0))) {
            List<Element> blocks = childElements(cell);
            if (!isFo(cell, "table-cell") || cell.getAttributes().getLength() != 0
                    || blocks.size() != 1 || !isFo(blocks.get(0), "block")
                    || !hasOnlyAttrs(blocks.get(0), Set.of("text-align"))) {
                return null;
            }
            headers.add(blocks.get(0).getTextContent());
            String align = blocks.get(0).getAttribute("text-align");
            aligns.add(align.isBlank() ? null : align);
        }

        List<Element> bodyKids = childElements(body);
        if (bodyKids.size() != 1 || !isXsl(bodyKids.get(0), "for-each")
                || !hasOnlyAttrs(bodyKids.get(0), Set.of("select"))) {
            return null;
        }
        Element forEach = bodyKids.get(0);
        List<Element> rowKids = childElements(forEach);
        if (rowKids.size() != 1 || !isFo(rowKids.get(0), "table-row")
                || rowKids.get(0).getAttributes().getLength() != 0) {
            return null;
        }
        List<BlockContent> cells = new ArrayList<>();
        for (Element cell : childElements(rowKids.get(0))) {
            List<Element> blocks = childElements(cell);
            if (!isFo(cell, "table-cell") || cell.getAttributes().getLength() != 0
                    || blocks.size() != 1 || !isFo(blocks.get(0), "block")
                    || !hasOnlyAttrs(blocks.get(0), Set.of("text-align"))) {
                return null;
            }
            Optional<BlockContent> content = readBlockContent(blocks.get(0));
            if (content.isEmpty()) {
                return null;
            }
            cells.add(content.get());
        }

        if (headers.size() != weights.size() || cells.size() != weights.size()) {
            return null;
        }
        List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            columns.add(new TableColumn(headers.get(i), weights.get(i), aligns.get(i),
                    cells.get(i).inlines(), cells.get(i).rules()));
        }
        return new TableBand(forEach.getAttribute("select"), columns);
    }

    // ---------- inline + style recognition ----------

    private record BlockContent(List<StyleRule> rules, List<InlineNode> inlines) { }

    /**
     * Block content where conditional-format rules are supported (static bands, table cells):
     * leading {@code xsl:if}-with-attributes children (the writer's F-1.29 shape) map to
     * StyleRules; the rest maps as inline content. Rule-shaped ifs appearing after content
     * stay opaque inlines — preserved but not editable as rules.
     */
    private Optional<BlockContent> readBlockContent(Element block) {
        List<StyleRule> rules = new ArrayList<>();
        NodeList kids = block.getChildNodes();
        int from = 0;
        while (from < kids.getLength()) {
            Node kid = kids.item(from);
            if (kid.getNodeType() == Node.ELEMENT_NODE) {
                StyleRule rule = parseRule((Element) kid);
                if (rule == null) {
                    break;
                }
                rules.add(rule);
                from++;
            } else if (kid.getNodeType() == Node.TEXT_NODE && kid.getTextContent().isBlank()
                    && kid.getTextContent().contains("\n") && nextElementIsRule(kids, from)) {
                from++; // indentation between rules (never a deliberate single space)
            } else {
                break;
            }
        }
        return readInlines(block, from).map(inlines -> new BlockContent(rules, inlines));
    }

    private boolean nextElementIsRule(NodeList kids, int from) {
        for (int i = from + 1; i < kids.getLength(); i++) {
            Node kid = kids.item(i);
            if (kid.getNodeType() == Node.TEXT_NODE && kid.getTextContent().isBlank()) {
                continue;
            }
            return kid.getNodeType() == Node.ELEMENT_NODE && parseRule((Element) kid) != null;
        }
        return false;
    }

    /** An {@code xsl:if} containing only recognized {@code xsl:attribute} settings; else null. */
    private StyleRule parseRule(Element el) {
        if (!isXsl(el, "if") || !hasOnlyAttrs(el, Set.of("test"))
                || el.getAttribute("test").isBlank()) {
            return null;
        }
        StyleAccum accum = new StyleAccum();
        boolean any = false;
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node kid = kids.item(i);
            if (kid.getNodeType() == Node.TEXT_NODE && kid.getTextContent().isBlank()) {
                continue;
            }
            if (kid.getNodeType() != Node.ELEMENT_NODE) {
                return null;
            }
            Element attr = (Element) kid;
            if (!isXsl(attr, "attribute") || !hasOnlyAttrs(attr, Set.of("name"))
                    || !childElements(attr).isEmpty()
                    || !accum.accept(attr.getAttribute("name"), attr.getTextContent())) {
                return null;
            }
            any = true;
        }
        return any ? new StyleRule(el.getAttribute("test"), accum.toStyle()) : null;
    }

    /** Maps mixed content to inline nodes; empty Optional when a child defies mapping. */
    private Optional<List<InlineNode>> readInlines(Element block) {
        return readInlines(block, 0);
    }

    private Optional<List<InlineNode>> readInlines(Element block, int from) {
        List<InlineNode> nodes = new ArrayList<>();
        NodeList kids = block.getChildNodes();
        for (int i = from; i < kids.getLength(); i++) {
            Node kid = kids.item(i);
            if (kid.getNodeType() == Node.TEXT_NODE || kid.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = normalizeSpace(kid.getTextContent(), i == 0, i == kids.getLength() - 1);
                if (!text.isEmpty()) {
                    nodes.add(new TextRun(text));
                }
            } else if (kid.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) kid;
                if (isXsl(el, "value-of") && isEmptyElement(el)
                        && hasOnlyAttrs(el, Set.of("select"))) {
                    nodes.add(toFieldToken(el.getAttribute("select")));
                } else if (isFo(el, "page-number") && isEmptyElement(el)
                        && el.getAttributes().getLength() == 0) {
                    nodes.add(new PageNumberToken());
                } else if (isFo(el, "page-number-citation") && isEmptyElement(el)
                        && "stylus-last".equals(el.getAttribute("ref-id"))
                        && hasOnlyAttrs(el, Set.of("ref-id"))) {
                    nodes.add(new PageCountToken());
                } else if (isXsl(el, "text") && el.getAttributes().getLength() == 0
                        && childElements(el).isEmpty()) {
                    // whitespace-exact literal; NCRs were decoded by the parser (F-2.3 text)
                    nodes.add(new XslTextInline(el.getTextContent()));
                } else {
                    nodes.add(new OpaqueInline(XmlFragments.serialize(el)));
                }
            } else if (kid.getNodeType() == Node.COMMENT_NODE) {
                nodes.add(new OpaqueInline(XmlFragments.serialize(kid)));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(nodes);
    }

    private FieldToken toFieldToken(String select) {
        Matcher m = FORMAT_NUMBER.matcher(select.trim());
        if (m.matches()) {
            return new FieldToken(m.group(1).trim(), FieldFormat.number(m.group(2)));
        }
        return FieldToken.of(select);
    }

    /** Maps block attributes to StyleProps; null when an attribute is outside the subset. */
    private StyleProps readStyle(Element block) {
        StyleAccum accum = new StyleAccum();
        var attrs = block.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (!accum.accept(attr.getNodeName(), attr.getNodeValue())) {
                return null;
            }
        }
        return accum.toStyle();
    }

    /** Accumulates the recognized style-attribute subset; shared by block attrs and rule ifs. */
    private static final class StyleAccum {
        private Boolean bold;
        private Boolean italic;
        private String fontSize;
        private String color;
        private String align;
        private String fontFamily;
        private String background;
        private Boolean underline;
        private Boolean strike;
        private String lineHeight;

        boolean accept(String name, String value) {
            if (!KNOWN_BLOCK_ATTRS.contains(name)) {
                return false;
            }
            switch (name) {
                case "font-weight" -> {
                    if (!"bold".equals(value)) {
                        return false;
                    }
                    bold = true;
                }
                case "font-style" -> {
                    if (!"italic".equals(value)) {
                        return false;
                    }
                    italic = true;
                }
                case "font-size" -> {
                    Matcher m = FONT_SIZE_PT.matcher(value);
                    if (!m.matches()) {
                        return false;
                    }
                    fontSize = m.group(1);
                }
                case "color" -> color = value;
                case "text-align" -> align = value;
                case "font-family" -> fontFamily = value;
                case "background-color" -> background = value;
                case "text-decoration" -> {
                    // exactly the writer's forms; anything richer (blink…) stays opaque
                    switch (value) {
                        case "underline" -> underline = true;
                        case "line-through" -> strike = true;
                        case "underline line-through" -> {
                            underline = true;
                            strike = true;
                        }
                        default -> {
                            return false;
                        }
                    }
                }
                case "line-height" -> lineHeight = value;
                default -> {
                    return false;
                }
            }
            return true;
        }

        StyleProps toStyle() {
            return new StyleProps(bold, italic, fontSize, color, align,
                    fontFamily, background, underline, strike, lineHeight);
        }
    }

    // ---------- html recognition (web mode, minimal M4 subset) ----------

    private ReportDocument recognizeHtml(Element html, String source, String title) {
        ReportDocument doc = new ReportDocument(title, OutputMode.WEB, PageSetup.A4);
        Element body = null;
        for (Element child : childElements(html)) {
            if ("head".equals(child.getLocalName())) {
                NodeList titles = child.getElementsByTagName("title");
                if (titles.getLength() == 1) {
                    doc.setTitle(titles.item(0).getTextContent());
                }
            } else if ("body".equals(child.getLocalName())) {
                body = child;
            } else {
                return null;
            }
        }
        if (body == null) {
            return null;
        }
        for (Element bandEl : childElements(body)) {
            doc.bands().add(readBand(bandEl)); // divs/unknowns fall to OpaqueBand — fine
        }
        // Title assignment above ran before bands; clear the modified flag noise:
        doc.markSaved();
        return doc;
    }

    // ---------- helpers ----------

    private ReportDocument opaqueDocument(String source, String title) {
        ReportDocument doc = new ReportDocument(title, OutputMode.PIXEL_PERFECT, PageSetup.A4);
        doc.bands().add(new OpaqueBand(source));
        doc.setOriginalSource(source);
        doc.markSaved();
        return doc;
    }

    private static boolean isXsl(Element el, String localName) {
        return XSL_NS.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName());
    }

    private static boolean isFo(Element el, String localName) {
        return FO_NS.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName());
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i).getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) kids.item(i));
            }
        }
        return elements;
    }

    private static boolean isEmptyElement(Element el) {
        return childElements(el).isEmpty() && el.getTextContent().isBlank();
    }

    /**
     * True when every attribute of {@code el} is in {@code allowed} (namespace declarations
     * excluded). The strictness workhorse: an element carrying any attribute the writer would
     * not re-emit must stay opaque, or regeneration would silently drop it (N7).
     */
    private static boolean hasOnlyAttrs(Element el, Set<String> allowed) {
        var attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            String name = attrs.item(i).getNodeName();
            if (name.equals("xmlns") || name.startsWith("xmlns:")) {
                continue;
            }
            if (!allowed.contains(name)) {
                return false;
            }
        }
        return true;
    }

    /** "215.9mm" → 215.9; anything not in mm is outside the recognized subset. */
    private static double mm(String value) {
        if (!value.endsWith("mm")) {
            throw new NumberFormatException("not a mm length: " + value);
        }
        return Double.parseDouble(value.substring(0, value.length() - 2));
    }

    /** Collapse indentation whitespace in mixed content; keep single interior spaces. */
    private static String normalizeSpace(String text, boolean first, boolean last) {
        String collapsed = text.replaceAll("\\s+", " ");
        if (first) {
            collapsed = collapsed.stripLeading();
        }
        if (last) {
            collapsed = collapsed.stripTrailing();
        }
        return collapsed;
    }
}
