package dev.stylus.codegen;

import org.w3c.dom.Node;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/** Serializes DOM subtrees for opaque nodes: no added indentation, no XML declaration (N7). */
final class XmlFragments {

    private static final TransformerFactory FACTORY = TransformerFactory.newInstance();

    static String serialize(Node node) {
        try {
            Transformer t = FACTORY.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(node), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize XML fragment: " + e.getMessage(), e);
        }
    }

    private XmlFragments() { }
}
