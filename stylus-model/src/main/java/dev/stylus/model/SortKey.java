package dev.stylus.model;

/** One xsl:sort inside a group band (F-1.18 sort pill, F-2.3). */
public record SortKey(String selectXPath, boolean ascending, DataType dataType) {

    public static SortKey by(String xpath) {
        return new SortKey(xpath, true, DataType.TEXT);
    }
}
