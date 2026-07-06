package dev.stylus.model;

/**
 * Style subset the designer maps (F-2.19): any field may be null = "not set, inherit".
 * Codegen only writes attributes for set values, keeping generated FO minimal. The M7 pass
 * added typography: font family, background color, underline/strikethrough, line height —
 * everything beyond this subset lives in the raw-attributes editor / opaque nodes.
 */
public record StyleProps(
        Boolean bold,
        Boolean italic,
        String fontSizePt,     // e.g. "11" (pt implied)
        String color,          // #RRGGBB
        String textAlign,      // left|center|right|justify
        String fontFamily,     // e.g. "Helvetica"
        String background,     // background-color, #RRGGBB
        Boolean underline,     // text-decoration
        Boolean strike,        // text-decoration line-through
        String lineHeight) {   // e.g. "1.4" or "14pt"

    public StyleProps(Boolean bold, Boolean italic, String fontSizePt, String color,
                      String textAlign) {
        this(bold, italic, fontSizePt, color, textAlign, null, null, null, null, null);
    }

    public static final StyleProps NONE = new StyleProps(null, null, null, null, null);

    public boolean isEmpty() {
        return bold == null && italic == null && fontSizePt == null
                && color == null && textAlign == null
                && fontFamily == null && background == null
                && underline == null && strike == null && lineHeight == null;
    }

    public static StyleProps ofBold() {
        return new StyleProps(true, null, null, null, null);
    }
}
