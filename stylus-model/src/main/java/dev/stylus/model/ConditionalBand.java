package dev.stylus.model;

import java.util.List;
import java.util.Objects;

/**
 * Conditional content (F-1.18 amber bands): xsl:choose/when/otherwise, or xsl:if when
 * {@code otherwise} is empty.
 */
public record ConditionalBand(
        String testExpr,
        List<Band> then,
        List<Band> otherwise) implements Band {

    public ConditionalBand {
        Objects.requireNonNull(testExpr);
        then = List.copyOf(then);
        otherwise = List.copyOf(otherwise);
    }

    public boolean hasElse() {
        return !otherwise.isEmpty();
    }
}
