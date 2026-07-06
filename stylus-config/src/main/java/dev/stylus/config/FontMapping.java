package dev.stylus.config;

/** One {@code <font>} entry in xdo.cfg: family/style/weight → TrueType file (F-5.20). */
public record FontMapping(String family, String style, String weight, String truetypePath) {

    public FontMapping {
        style = style == null || style.isBlank() ? "normal" : style;
        weight = weight == null || weight.isBlank() ? "normal" : weight;
    }
}
