package dev.stylus.model;

/**
 * In-flow content inside a band/block: literal text, bound field tokens (F-1.19), page-number
 * tokens (F-1.22) or preserved-but-unmapped XSLT/FO (N7).
 */
public sealed interface InlineNode
        permits TextRun, FieldToken, PageNumberToken, PageCountToken, XslTextInline, OpaqueInline {
}
