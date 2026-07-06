package dev.stylus.model;

/** Unrecognized inline XSLT/FO, re-emitted as-is (N7). {@code xml} is a serialized fragment. */
public record OpaqueInline(String xml) implements InlineNode {
}
