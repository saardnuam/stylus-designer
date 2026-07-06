package dev.stylus.engine.api;

/** Per-engine support level for an XSL-FO element or attribute (doc 07 legend). */
public enum ElementSupport {
    SUPPORTED,
    /** Works with quirks or only in some engine versions — amber badge (F-1.50). */
    PARTIAL,
    /** Dropped or rejected by the engine — red badge. */
    UNSUPPORTED,
    /** Not in the matrix; treated as supported but flagged for the probe. */
    UNKNOWN
}
