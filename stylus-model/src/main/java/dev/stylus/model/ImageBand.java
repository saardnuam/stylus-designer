package dev.stylus.model;

/**
 * An image band (F-7.1/F-7.2): external graphic referenced by URL/path — a file relative to
 * the template, an absolute URL, or a data: URI (also how SVG files are placed). Optional
 * display size in millimetres; null = intrinsic size.
 */
public record ImageBand(String src, Double widthMm, Double heightMm) implements Band {

    public static ImageBand of(String src) {
        return new ImageBand(src, null, null);
    }
}
