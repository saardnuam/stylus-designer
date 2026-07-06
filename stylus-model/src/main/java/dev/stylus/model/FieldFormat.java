package dev.stylus.model;

/**
 * Field formatting (F-1.26): data type + optional mask. NUMBER masks are format-number()
 * pictures; DATE masks stay engine-neutral strings until M5 wires xdoxslt/format-date.
 */
public record FieldFormat(DataType dataType, String mask) {

    public static final FieldFormat TEXT = new FieldFormat(DataType.TEXT, null);

    public static FieldFormat number(String mask) {
        return new FieldFormat(DataType.NUMBER, mask);
    }

    public boolean hasMask() {
        return mask != null && !mask.isBlank();
    }
}
