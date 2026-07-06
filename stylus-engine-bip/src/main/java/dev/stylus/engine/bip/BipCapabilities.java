package dev.stylus.engine.bip;

import dev.stylus.engine.api.CapabilityMatrix;
import dev.stylus.engine.api.OutputFormat;

import java.util.Map;
import java.util.Set;

import static dev.stylus.engine.api.ElementSupport.PARTIAL;
import static dev.stylus.engine.api.ElementSupport.SUPPORTED;
import static dev.stylus.engine.api.ElementSupport.UNSUPPORTED;

/**
 * BIP column of the engine capability matrix — seeded from doc 07 (BIP implements a subset of
 * XSL-FO 1.1). The output-format set is NOT taken from here at runtime: {@link BipEngine} probes
 * the loaded FOProcessor for its actual format constants (F-2.28).
 */
final class BipCapabilities {

    private BipCapabilities() { }

    static CapabilityMatrix matrix(Set<OutputFormat> probedFormats) {
        return new CapabilityMatrix(
                probedFormats,
                Set.of(CapabilityMatrix.NS_XDOFO, CapabilityMatrix.NS_XDOXSLT),
                Map.ofEntries(
                        Map.entry("layout-master-set", SUPPORTED),
                        Map.entry("simple-page-master", SUPPORTED),
                        Map.entry("page-sequence-master", SUPPORTED),
                        Map.entry("conditional-page-master-reference", SUPPORTED),
                        Map.entry("region-body", SUPPORTED),
                        Map.entry("block", SUPPORTED),
                        Map.entry("inline", SUPPORTED),
                        Map.entry("block-container", SUPPORTED),
                        Map.entry("inline-container", PARTIAL),
                        Map.entry("wrapper", SUPPORTED),
                        Map.entry("character", SUPPORTED),
                        Map.entry("table", SUPPORTED),
                        Map.entry("retrieve-table-marker", UNSUPPORTED),
                        Map.entry("list-block", SUPPORTED),
                        Map.entry("external-graphic", SUPPORTED),
                        Map.entry("instream-foreign-object", SUPPORTED),
                        Map.entry("page-number", SUPPORTED),
                        Map.entry("page-number-citation", SUPPORTED),
                        // Empirically verified against 12c jars: "element
                        // fo:page-number-citation-last is not supported yet."
                        Map.entry("page-number-citation-last", UNSUPPORTED),
                        Map.entry("leader", SUPPORTED),
                        Map.entry("marker", PARTIAL),
                        Map.entry("retrieve-marker", PARTIAL),
                        Map.entry("footnote", PARTIAL),
                        Map.entry("float", UNSUPPORTED),
                        Map.entry("basic-link", SUPPORTED),
                        Map.entry("bookmark-tree", PARTIAL),
                        Map.entry("index-key-reference", UNSUPPORTED),
                        Map.entry("change-bar-begin", UNSUPPORTED),
                        Map.entry("change-bar-end", UNSUPPORTED)));
    }
}
