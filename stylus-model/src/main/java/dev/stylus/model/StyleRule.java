package dev.stylus.model;

/**
 * One conditional-format rule (F-1.29): when {@code testExpr} (XPath, evaluated in the band's
 * group/row context) is true, the set values of {@code style} override the base style. Later
 * rules win on conflict — XSLT attribute re-addition replaces the earlier value.
 */
public record StyleRule(String testExpr, StyleProps style) {

    public StyleRule {
        style = style == null ? StyleProps.NONE : style;
    }
}
