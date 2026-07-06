package dev.stylus.engine.fop;

import dev.stylus.engine.api.CapabilityMatrix;
import dev.stylus.engine.api.ElementSupport;
import dev.stylus.engine.api.OutputFormat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static dev.stylus.engine.api.ElementSupport.PARTIAL;
import static dev.stylus.engine.api.ElementSupport.SUPPORTED;

/** FOP column of the engine capability matrix — seeded from doc 07 (fixed: we bundle FOP). */
final class FopCapabilities {

    private FopCapabilities() { }

    static final Set<OutputFormat> FORMATS = EnumSet.of(
            OutputFormat.PDF, OutputFormat.HTML, OutputFormat.FO, OutputFormat.IF,
            OutputFormat.POSTSCRIPT, OutputFormat.PCL, OutputFormat.AFP,
            OutputFormat.PNG, OutputFormat.TIFF, OutputFormat.TEXT);

    static CapabilityMatrix matrix() {
        return new CapabilityMatrix(
                FORMATS,
                Set.of(CapabilityMatrix.NS_FOX),
                Map.ofEntries(
                        Map.entry("layout-master-set", SUPPORTED),
                        Map.entry("simple-page-master", SUPPORTED),
                        Map.entry("page-sequence-master", SUPPORTED),
                        Map.entry("conditional-page-master-reference", SUPPORTED),
                        Map.entry("region-body", SUPPORTED),
                        Map.entry("block", SUPPORTED),
                        Map.entry("inline", SUPPORTED),
                        Map.entry("block-container", SUPPORTED),
                        Map.entry("inline-container", SUPPORTED),
                        Map.entry("wrapper", SUPPORTED),
                        Map.entry("character", SUPPORTED),
                        Map.entry("table", SUPPORTED),
                        Map.entry("retrieve-table-marker", SUPPORTED),
                        Map.entry("list-block", SUPPORTED),
                        Map.entry("external-graphic", SUPPORTED),
                        Map.entry("instream-foreign-object", SUPPORTED),
                        Map.entry("page-number", SUPPORTED),
                        Map.entry("page-number-citation", SUPPORTED),
                        Map.entry("page-number-citation-last", SUPPORTED),
                        Map.entry("leader", SUPPORTED),
                        Map.entry("marker", SUPPORTED),
                        Map.entry("retrieve-marker", SUPPORTED),
                        Map.entry("footnote", SUPPORTED),
                        Map.entry("float", PARTIAL),          // side floats incomplete in FOP
                        Map.entry("basic-link", SUPPORTED),
                        Map.entry("bookmark-tree", SUPPORTED),
                        Map.entry("index-key-reference", PARTIAL),
                        Map.entry("change-bar-begin", PARTIAL),
                        Map.entry("change-bar-end", PARTIAL)));
    }
}
