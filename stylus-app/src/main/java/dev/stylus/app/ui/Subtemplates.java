package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Discovery of callable named templates in the document's subtemplate imports (F-6.5). */
final class Subtemplates {

    record NamedTemplate(String name, List<String> params, String source) {

        String signature() {
            return name + "(" + String.join(", ", params) + ")";
        }
    }

    static List<NamedTemplate> discover(DesignerState state) {
        List<NamedTemplate> found = new ArrayList<>();
        Path base = state.templateFile() != null ? state.templateFile().getParent() : null;
        for (String href : state.document().imports()) {
            try {
                Path file = base != null ? base.resolve(href) : Path.of(href);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                var dom = dbf.newDocumentBuilder().parse(file.toFile());
                var templates = dom.getElementsByTagNameNS(
                        "http://www.w3.org/1999/XSL/Transform", "template");
                for (int i = 0; i < templates.getLength(); i++) {
                    var template = (org.w3c.dom.Element) templates.item(i);
                    String name = template.getAttribute("name");
                    if (name.isBlank()) {
                        continue;
                    }
                    List<String> params = new ArrayList<>();
                    var kids = template.getChildNodes();
                    for (int k = 0; k < kids.getLength(); k++) {
                        if (kids.item(k) instanceof org.w3c.dom.Element el
                                && "param".equals(el.getLocalName())) {
                            params.add(el.getAttribute("name"));
                        }
                    }
                    found.add(new NamedTemplate(name, params, href));
                }
            } catch (Exception ignored) {
                // unreadable import — nothing to offer from it
            }
        }
        return found;
    }

    private Subtemplates() { }
}
