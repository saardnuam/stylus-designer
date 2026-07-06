package dev.stylus.model;

/**
 * A literal {@code <xsl:text>} node (F-2.3 "text"): whitespace-exact text that is never
 * normalized, the vehicle for special characters. The writer emits invisible/ambiguous
 * characters as numeric character references ({@code &#xA0;}) so they stay visible and
 * editable in the code view.
 */
public record XslTextInline(String text) implements InlineNode {
}
