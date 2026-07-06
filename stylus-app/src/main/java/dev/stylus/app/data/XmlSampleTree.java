package dev.stylus.app.data;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses a sample XML file into the data-source tree (F-1.10): merged structure with inferred
 * node kinds (element/text/attribute/number/date), repeating-group detection (F-1.11/F-1.14)
 * and sample values. Same-named siblings collapse into one node with {@code repeating=true}.
 */
public final class XmlSampleTree {

    public enum Kind { ELEMENT, TEXT, NUMBER, DATE, ATTRIBUTE }

    /** One merged tree node. {@code xpath} is absolute (document-rooted). */
    public record TreeNode(
            String name,
            String xpath,
            Kind kind,
            boolean repeating,
            int occurrences,
            String sampleValue,
            List<TreeNode> children) {

        public boolean isLeaf() {
            return children.isEmpty();
        }
    }

    public record Parsed(TreeNode root, int totalElements, int groupCount) { }

    private static final Pattern NUMBER =
            Pattern.compile("^-?\\d{1,3}(,\\d{3})*(\\.\\d+)?$|^-?\\d+(\\.\\d+)?$");
    private static final Pattern DATE = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}([T ].*)?$|^\\d{2}[-/]\\d{2}[-/]\\d{4}$");

    private int totalElements;
    private int groupCount;

    public static Parsed parse(Path xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document doc = dbf.newDocumentBuilder().parse(xmlFile.toFile());

        XmlSampleTree tree = new XmlSampleTree();
        Element rootEl = doc.getDocumentElement();
        TreeNode root = tree.merge(rootEl.getNodeName(), List.of(rootEl),
                "/" + rootEl.getNodeName(), false);
        return new Parsed(root, tree.totalElements, tree.groupCount);
    }

    /**
     * Merges all occurrences of one element name (across every parent occurrence) into a
     * single structural node. {@code repeating} is decided by the parent: true when any
     * single parent held this name more than once.
     */
    private TreeNode merge(String name, List<Element> occurrences, String xpath, boolean repeating) {
        totalElements += occurrences.size();
        if (repeating) {
            groupCount++;
        }

        // Union of attributes over all occurrences (first value wins as sample).
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Element el : occurrences) {
            NamedNodeMap attrs = el.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                attributes.putIfAbsent(attrs.item(i).getNodeName(), attrs.item(i).getNodeValue());
            }
        }

        // Union of child elements in first-seen order.
        Map<String, List<Element>> childGroups = new LinkedHashMap<>();
        for (Element el : occurrences) {
            NodeList kids = el.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (kids.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element kid = (Element) kids.item(i);
                    childGroups.computeIfAbsent(kid.getNodeName(), k -> new ArrayList<>()).add(kid);
                }
            }
        }

        List<TreeNode> children = new ArrayList<>();
        attributes.forEach((attrName, value) -> children.add(new TreeNode(
                "@" + attrName, xpath + "/@" + attrName, Kind.ATTRIBUTE,
                false, occurrences.size(), value, List.of())));
        childGroups.forEach((childName, elements) -> children.add(
                merge(childName, elements, xpath + "/" + childName,
                        hasSameNameSiblings(occurrences, childName))));

        Kind kind = Kind.ELEMENT;
        String sample = null;
        if (childGroups.isEmpty()) {
            String text = occurrences.get(0).getTextContent().trim();
            sample = text.length() > 40 ? text.substring(0, 40) + "…" : text;
            kind = inferKind(text);
        }
        return new TreeNode(name, xpath, kind, repeating, occurrences.size(), sample, children);
    }

    private static boolean hasSameNameSiblings(List<Element> parents, String childName) {
        for (Element parent : parents) {
            int count = 0;
            NodeList kids = parent.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (kids.item(i).getNodeType() == Node.ELEMENT_NODE
                        && kids.item(i).getNodeName().equals(childName)) {
                    if (++count > 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Kind inferKind(String text) {
        if (text.isEmpty()) {
            return Kind.TEXT;
        }
        if (NUMBER.matcher(text).matches()) {
            return Kind.NUMBER;
        }
        if (DATE.matcher(text).matches()) {
            return Kind.DATE;
        }
        return Kind.TEXT;
    }
}
