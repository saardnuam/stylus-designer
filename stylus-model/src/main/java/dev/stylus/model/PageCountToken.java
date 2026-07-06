package dev.stylus.model;

/**
 * Total page count token — "Page X of Y"'s Y (F-1.22/F-2.13). Emitted as a page-number-citation
 * to the writer's end-of-flow anchor: plain fo:page-number-citation works on both FOP and BIP
 * (BIP 12c rejects only the -last variant, doc 07).
 */
public record PageCountToken() implements InlineNode {
}
