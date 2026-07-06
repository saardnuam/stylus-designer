package dev.stylus.app.ui;

import dev.stylus.model.Band;
import dev.stylus.model.FieldFormat;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.InlineNode;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.TextRun;

import java.util.List;

/**
 * Builds bands from tree-drag payloads (F-1.13/F-1.14), relativizing the dragged absolute
 * XPath against the drop target's context so nested drops bind correctly.
 */
final class DropFactory {

    /** Band for a payload dropped into a context; null for foreign payloads. */
    static Band bandFor(String payload, String absoluteContext) {
        if (payload == null) {
            return null;
        }
        if (payload.startsWith(DragPayload.FIELD_PREFIX)) {
            String[] parts = payload.split("\\|");
            String kind = parts[1];
            String xpath = relativize(parts[2], absoluteContext);
            String label = parts[3].replace("@", "") + ": ";
            FieldFormat format = "NUMBER".equals(kind)
                    ? FieldFormat.number("#,##0.00") : FieldFormat.TEXT;
            return new StaticBand(
                    List.of(new TextRun(label), new FieldToken(xpath, format)), StyleProps.NONE);
        }
        if (payload.startsWith(DragPayload.GROUP_PREFIX)) {
            String[] parts = payload.split("\\|");
            String groupPath = relativize(parts[1], absoluteContext);
            String name = parts[2];
            String firstLeaf = parts.length > 3 ? parts[3] : "";
            List<InlineNode> headerContent = firstLeaf.isBlank()
                    ? List.of(new TextRun(name))
                    : List.of(new TextRun(name + " "),
                            FieldToken.of(relativize(firstLeaf, parts[1])));
            return new GroupBand(groupPath, List.of(),
                    List.of(new StaticBand(headerContent, StyleProps.ofBold())));
        }
        return null;
    }

    /** Chain of possibly-relative steps → absolute path ("" context = document root). */
    static String resolveAbsolute(List<String> chain) {
        String absolute = "";
        for (String step : chain) {
            if (step == null || step.isBlank()) {
                continue;
            }
            absolute = step.startsWith("/") ? step : absolute + "/" + step;
        }
        return absolute;
    }

    /** Absolute {@code xpath} expressed relative to {@code absoluteContext} when nested. */
    static String relativize(String xpath, String absoluteContext) {
        if (absoluteContext == null || absoluteContext.isBlank()) {
            return xpath;
        }
        if (xpath.equals(absoluteContext)) {
            return ".";
        }
        if (xpath.startsWith(absoluteContext + "/")) {
            return xpath.substring(absoluteContext.length() + 1);
        }
        return xpath;
    }

    private DropFactory() { }
}
