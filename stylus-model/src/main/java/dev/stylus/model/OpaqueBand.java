package dev.stylus.model;

/**
 * Hand-written or unrecognized XSLT/FO preserved verbatim (N7): renders as a generic
 * "XSLT block" band on the canvas and is re-emitted exactly as stored. {@code xml} is the
 * serialized source of the subtree.
 */
public record OpaqueBand(String xml) implements Band {
}
