package dev.stylus.xliff;

import dev.stylus.model.Band;
import dev.stylus.model.ConditionalBand;
import dev.stylus.model.GroupBand;
import dev.stylus.model.ImageBand;
import dev.stylus.model.InlineNode;
import dev.stylus.model.OpaqueBand;
import dev.stylus.model.ReportDocument;
import dev.stylus.model.StaticBand;
import dev.stylus.model.TableBand;
import dev.stylus.model.TableColumn;
import dev.stylus.model.TextRun;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * XLIFF 1.2 workflow (F-9.5): {@link #generate} walks every translatable literal in a document
 * (page header/footer text, static-band text runs, table headers and cell text) into
 * trans-units with stable position-path ids; {@link #apply} plays edited targets back into an
 * equal document. BIP applies XLIFF natively at run time (setXLIFF); for FOP the bench
 * pre-translates the template with this class.
 */
public final class XliffFile {

    public static final String NS = "urn:oasis:names:tc:xliff:document:1.2";

    // ---------- generate ----------

    /** XLIFF 1.2 text for all translatable strings, targets pre-filled with the source. */
    public static String generate(ReportDocument doc, String sourceLang, String targetLang) {
        StringBuilder out = new StringBuilder(4 * 1024);
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<xliff version=\"1.2\" xmlns=\"").append(NS).append("\">\n")
           .append("  <file source-language=\"").append(escapeAttr(sourceLang))
           .append("\" target-language=\"").append(escapeAttr(targetLang))
           .append("\" datatype=\"xml\" original=\"").append(escapeAttr(doc.title()))
           .append("\">\n")
           .append("    <body>\n");
        walk(doc, (id, text) -> out.append("      <trans-unit id=\"").append(escapeAttr(id))
                .append("\">\n")
                .append("        <source>").append(escapeText(text)).append("</source>\n")
                .append("        <target>").append(escapeText(text)).append("</target>\n")
                .append("      </trans-unit>\n"));
        out.append("    </body>\n")
           .append("  </file>\n")
           .append("</xliff>\n");
        return out.toString();
    }

    // ---------- apply ----------

    /** Reads {@code id → target} from an XLIFF 1.2 file (namespace-tolerant). */
    public static Map<String, String> readTargets(Path xliff) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var dom = dbf.newDocumentBuilder().parse(Files.newInputStream(xliff));
        Map<String, String> targets = new LinkedHashMap<>();
        NodeList units = dom.getElementsByTagNameNS("*", "trans-unit");
        for (int i = 0; i < units.getLength(); i++) {
            Element unit = (Element) units.item(i);
            NodeList target = unit.getElementsByTagNameNS("*", "target");
            if (target.getLength() == 1) {
                targets.put(unit.getAttribute("id"), target.item(0).getTextContent());
            }
        }
        return targets;
    }

    /** Replaces translatable strings by id; ids not present keep their source text. */
    public static void apply(ReportDocument doc, Map<String, String> targets) {
        replaceInlines(doc.pageHeader(), "h", targets);
        replaceInlines(doc.pageFooter(), "f", targets);
        List<Band> bands = doc.bands();
        for (int i = 0; i < bands.size(); i++) {
            bands.set(i, translateBand(bands.get(i), "b." + i, targets));
        }
        doc.touch();
    }

    // ---------- the shared walk (ids must match between generate and apply) ----------

    private static void walk(ReportDocument doc, BiConsumer<String, String> unit) {
        collectInlines(doc.pageHeader(), "h", unit);
        collectInlines(doc.pageFooter(), "f", unit);
        List<Band> bands = doc.bands();
        for (int i = 0; i < bands.size(); i++) {
            collectBand(bands.get(i), "b." + i, unit);
        }
    }

    private static void collectBand(Band band, String id, BiConsumer<String, String> unit) {
        switch (band) {
            case StaticBand s -> collectInlines(s.content(), id, unit);
            case GroupBand g -> {
                for (int i = 0; i < g.children().size(); i++) {
                    collectBand(g.children().get(i), id + "." + i, unit);
                }
            }
            case TableBand t -> {
                for (int c = 0; c < t.columns().size(); c++) {
                    TableColumn column = t.columns().get(c);
                    unit.accept(id + ".th" + c, column.header());
                    collectInlines(column.cell(), id + ".td" + c, unit);
                }
            }
            case ConditionalBand cond -> {
                for (int i = 0; i < cond.then().size(); i++) {
                    collectBand(cond.then().get(i), id + ".t" + i, unit);
                }
                for (int i = 0; i < cond.otherwise().size(); i++) {
                    collectBand(cond.otherwise().get(i), id + ".e" + i, unit);
                }
            }
            case ImageBand img -> { }
            case OpaqueBand o -> { }
        }
    }

    private static void collectInlines(List<InlineNode> nodes, String id,
                                       BiConsumer<String, String> unit) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) instanceof TextRun text && !text.text().isBlank()) {
                unit.accept(id + "." + i, text.text());
            } else if (nodes.get(i) instanceof dev.stylus.model.XslTextInline literal
                    && !literal.text().isBlank()) {
                unit.accept(id + "." + i, literal.text());
            }
        }
    }

    private static Band translateBand(Band band, String id, Map<String, String> targets) {
        return switch (band) {
            case StaticBand s ->
                    new StaticBand(translateInlines(s.content(), id, targets), s.style(), s.rules());
            case GroupBand g -> {
                List<Band> children = new ArrayList<>();
                for (int i = 0; i < g.children().size(); i++) {
                    children.add(translateBand(g.children().get(i), id + "." + i, targets));
                }
                yield new GroupBand(g.selectXPath(), g.sortKeys(), children);
            }
            case TableBand t -> {
                List<TableColumn> columns = new ArrayList<>();
                for (int c = 0; c < t.columns().size(); c++) {
                    TableColumn column = t.columns().get(c);
                    columns.add(new TableColumn(
                            targets.getOrDefault(id + ".th" + c, column.header()),
                            column.widthWeight(), column.align(),
                            translateInlines(column.cell(), id + ".td" + c, targets),
                            column.rules()));
                }
                yield new TableBand(t.rowXPath(), columns);
            }
            case ConditionalBand cond -> {
                List<Band> then = new ArrayList<>();
                for (int i = 0; i < cond.then().size(); i++) {
                    then.add(translateBand(cond.then().get(i), id + ".t" + i, targets));
                }
                List<Band> otherwise = new ArrayList<>();
                for (int i = 0; i < cond.otherwise().size(); i++) {
                    otherwise.add(translateBand(cond.otherwise().get(i), id + ".e" + i, targets));
                }
                yield new ConditionalBand(cond.testExpr(), then, otherwise);
            }
            case ImageBand img -> img;
            case OpaqueBand o -> o;
        };
    }

    private static List<InlineNode> translateInlines(List<InlineNode> nodes, String id,
                                                     Map<String, String> targets) {
        List<InlineNode> result = new ArrayList<>(nodes);
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) instanceof TextRun text) {
                String translated = targets.get(id + "." + i);
                if (translated != null && !translated.equals(text.text())) {
                    result.set(i, new TextRun(translated));
                }
            } else if (result.get(i) instanceof dev.stylus.model.XslTextInline literal) {
                String translated = targets.get(id + "." + i);
                if (translated != null && !translated.equals(literal.text())) {
                    result.set(i, new dev.stylus.model.XslTextInline(translated));
                }
            }
        }
        return result;
    }

    private static void replaceInlines(List<InlineNode> nodes, String id,
                                       Map<String, String> targets) {
        List<InlineNode> translated = translateInlines(nodes, id, targets);
        nodes.clear();
        nodes.addAll(translated);
    }

    private static String escapeText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }

    private XliffFile() { }
}
