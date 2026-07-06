package dev.stylus.engine.api;

import java.util.Map;
import java.util.Set;

/**
 * What an engine supports (F-2.25/F-2.28, reference model doc 07): output formats, extension
 * namespaces it understands, and per-FO-element support. Seeded statically per engine; the BIP
 * matrix is refined at runtime by a capability probe against the user's installation.
 *
 * @param formats              output formats the engine can produce
 * @param extensionNamespaces  extension namespace URIs understood (fox:, xdofo:, xdoxslt:)
 * @param foElements           local FO element name (e.g. "footnote") → support level;
 *                             elements absent from the map are {@link ElementSupport#UNKNOWN}
 */
public record CapabilityMatrix(
        Set<OutputFormat> formats,
        Set<String> extensionNamespaces,
        Map<String, ElementSupport> foElements) {

    public ElementSupport supportFor(String foLocalName) {
        return foElements.getOrDefault(foLocalName, ElementSupport.UNKNOWN);
    }

    /** Well-known extension namespace URIs (doc 07 §extension namespaces). */
    public static final String NS_FOX = "http://xmlgraphics.apache.org/fop/extensions";
    public static final String NS_XDOFO = "http://xmlns.oracle.com/oxp/fo/extensions";
    public static final String NS_XDOXSLT =
            "http://www.oracle.com/XSL/Transform/java/oracle.apps.xdo.template.rtf.XSLTFunctions";
}
