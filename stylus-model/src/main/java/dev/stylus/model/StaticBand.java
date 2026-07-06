package dev.stylus.model;

import java.util.List;

/**
 * A static block of inline content (title band, free text, footers with aggregates), with an
 * optional list of conditional-format rules (F-1.29) applied on top of the base style.
 */
public record StaticBand(List<InlineNode> content, StyleProps style, List<StyleRule> rules)
        implements Band {

    public StaticBand {
        content = List.copyOf(content);
        style = style == null ? StyleProps.NONE : style;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public StaticBand(List<InlineNode> content, StyleProps style) {
        this(content, style, List.of());
    }

    public static StaticBand text(String text) {
        return new StaticBand(List.of(new TextRun(text)), StyleProps.NONE);
    }
}
