package dev.stylus.model;

import java.util.List;
import java.util.Objects;

/**
 * A repeating group — xsl:for-each over {@code selectXPath} (F-1.18 group bands, F-1.14):
 * child bands render once per node; sort keys become xsl:sort (sort pill).
 */
public record GroupBand(
        String selectXPath,
        List<SortKey> sortKeys,
        List<Band> children) implements Band {

    public GroupBand {
        Objects.requireNonNull(selectXPath);
        sortKeys = List.copyOf(sortKeys);
        children = List.copyOf(children);
    }

    public static GroupBand of(String selectXPath, Band... children) {
        return new GroupBand(selectXPath, List.of(), List.of(children));
    }
}
