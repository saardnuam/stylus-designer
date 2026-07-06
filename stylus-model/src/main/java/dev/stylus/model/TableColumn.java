package dev.stylus.model;

import java.util.List;

/**
 * One column of a detail table (F-1.24): header label, proportional width, the cell content
 * rendered per row and optional conditional-format rules (F-1.29) evaluated in row context.
 */
public record TableColumn(
        String header,
        double widthWeight,
        String align,              // left|center|right (nullable = left)
        List<InlineNode> cell,
        List<StyleRule> rules) {

    public TableColumn {
        cell = List.copyOf(cell);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public TableColumn(String header, double widthWeight, String align, List<InlineNode> cell) {
        this(header, widthWeight, align, cell, List.of());
    }

    public static TableColumn of(String header, double weight, InlineNode... cell) {
        return new TableColumn(header, weight, null, List.of(cell));
    }
}
