package dev.stylus.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model of a BI Publisher {@code xdo.cfg} file (doc 04): ordered properties + font mappings.
 * Load validates the {@code http://xmlns.oracle.com/oxp/config/} namespace exactly like the
 * Template Viewer does (F-5.17); save writes back a clean, stable document (F-5.19).
 */
public final class XdoConfig {

    public static final String NAMESPACE = "http://xmlns.oracle.com/oxp/config/";

    private final Map<String, String> properties = new LinkedHashMap<>();
    private final List<FontMapping> fonts = new ArrayList<>();
    private String version = "1.0.0";

    // ---------- access ----------

    public Map<String, String> properties() {
        return Collections.unmodifiableMap(properties);
    }

    public String property(String name) {
        return properties.get(name);
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public void removeProperty(String name) {
        properties.remove(name);
    }

    public List<FontMapping> fonts() {
        return Collections.unmodifiableList(fonts);
    }

    public void addFont(FontMapping font) {
        fonts.add(font);
    }

    public void removeFont(FontMapping font) {
        fonts.remove(font);
    }

    public String version() {
        return version;
    }

    /**
     * Template parameters: every {@code xslt.<name>} property, mapped to bare parameter names
     * (F-5.21). Includes built-ins like {@code _XDOLOCALE}.
     */
    public Map<String, String> templateParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        properties.forEach((k, v) -> {
            if (k.startsWith("xslt.")) {
                params.put(k.substring("xslt.".length()), v);
            }
        });
        return params;
    }

    // ---------- load / save ----------

    public static XdoConfig load(Path file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = dbf.newDocumentBuilder().parse(file.toFile());
            return fromDocument(doc, file);
        } catch (XdoConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new XdoConfigException("Cannot read configuration file " + file + ": "
                    + e.getMessage(), e);
        }
    }

    private static XdoConfig fromDocument(Document doc, Path file) {
        Element root = doc.getDocumentElement();
        if (root == null || !NAMESPACE.equals(root.getNamespaceURI())
                || !"config".equals(root.getLocalName())) {
            // Template Viewer parity: reject non-BIP config files with a clear message.
            throw new XdoConfigException(
                    "This file is not a BI Publisher configuration file: " + file);
        }
        XdoConfig config = new XdoConfig();
        String v = root.getAttribute("version");
        if (!v.isBlank()) {
            config.version = v;
        }

        NodeList propertyNodes = root.getElementsByTagNameNS(NAMESPACE, "property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element p = (Element) propertyNodes.item(i);
            String name = p.getAttribute("name");
            if (!name.isBlank()) {
                config.properties.put(name, p.getTextContent().trim());
            }
        }

        NodeList fontNodes = root.getElementsByTagNameNS(NAMESPACE, "font");
        for (int i = 0; i < fontNodes.getLength(); i++) {
            Element f = (Element) fontNodes.item(i);
            String path = null;
            NodeList truetype = f.getElementsByTagNameNS(NAMESPACE, "truetype");
            if (truetype.getLength() > 0) {
                path = ((Element) truetype.item(0)).getAttribute("path");
            }
            config.fonts.add(new FontMapping(
                    f.getAttribute("family"), f.getAttribute("style"),
                    f.getAttribute("weight"), path));
        }
        return config;
    }

    public void save(Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NAMESPACE, "config");
            root.setAttribute("version", version);
            doc.appendChild(root);

            Element props = doc.createElementNS(NAMESPACE, "properties");
            root.appendChild(props);
            properties.forEach((name, value) -> {
                Element p = doc.createElementNS(NAMESPACE, "property");
                p.setAttribute("name", name);
                p.setTextContent(value);
                props.appendChild(p);
            });

            if (!fonts.isEmpty()) {
                Element fontsEl = doc.createElementNS(NAMESPACE, "fonts");
                root.appendChild(fontsEl);
                for (FontMapping font : fonts) {
                    Element f = doc.createElementNS(NAMESPACE, "font");
                    f.setAttribute("family", font.family());
                    f.setAttribute("style", font.style());
                    f.setAttribute("weight", font.weight());
                    if (font.truetypePath() != null) {
                        Element tt = doc.createElementNS(NAMESPACE, "truetype");
                        tt.setAttribute("path", font.truetypePath());
                        f.appendChild(tt);
                    }
                    fontsEl.appendChild(f);
                }
            }

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.transform(new DOMSource(doc), new StreamResult(out));
        } catch (IOException e) {
            throw new XdoConfigException("Cannot write " + file + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new XdoConfigException("Cannot serialize configuration: " + e.getMessage(), e);
        }
    }
}
