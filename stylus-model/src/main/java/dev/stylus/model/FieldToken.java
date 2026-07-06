package dev.stylus.model;

import java.util.Objects;

/**
 * A bound data field — the `⟨ … ⟩` chip on the canvas (F-1.19): an XPath relative to the
 * enclosing group context plus formatting.
 */
public record FieldToken(String xpath, FieldFormat format) implements InlineNode {

    public FieldToken {
        Objects.requireNonNull(xpath);
        format = format == null ? FieldFormat.TEXT : format;
    }

    public static FieldToken of(String xpath) {
        return new FieldToken(xpath, FieldFormat.TEXT);
    }
}
