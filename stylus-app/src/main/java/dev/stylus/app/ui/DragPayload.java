package dev.stylus.app.ui;

import dev.stylus.app.data.XmlSampleTree;

/**
 * Dragboard payload for tree → canvas drops (F-1.13/F-1.14), string-encoded:
 * {@code STYLUS-FIELD|kind|xpath|name} or {@code STYLUS-GROUP|xpath|name|firstLeafXPath}.
 */
final class DragPayload {

    static final String FIELD_PREFIX = "STYLUS-FIELD|";
    static final String GROUP_PREFIX = "STYLUS-GROUP|";

    static String field(XmlSampleTree.TreeNode node) {
        return FIELD_PREFIX + node.kind() + "|" + node.xpath() + "|" + node.name();
    }

    static String group(XmlSampleTree.TreeNode node, String firstLeafXPath) {
        return GROUP_PREFIX + node.xpath() + "|" + node.name() + "|"
                + (firstLeafXPath == null ? "" : firstLeafXPath);
    }

    static boolean isStylus(String s) {
        return s != null && (s.startsWith(FIELD_PREFIX) || s.startsWith(GROUP_PREFIX));
    }

    private DragPayload() { }
}
