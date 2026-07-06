package dev.stylus.model;

/** The two output modes per template (F-4.1/F-4.2). */
public enum OutputMode {
    /** Paginated XSL-FO — WYSIWYG page canvas (A4/Letter/custom). */
    PIXEL_PERFECT,
    /** Unlimited-width HTML — fluid canvas, no page bands. */
    WEB
}
