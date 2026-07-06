package dev.stylus.app.expr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-1.32/F-3.1: live validation incl. the xdoxslt: BIP-only path. Headless (no JavaFX). */
class ExpressionValidatorTest {

    private final ExpressionValidator validator = new ExpressionValidator();

    @TempDir
    Path tmp;

    private Path sample() throws Exception {
        Path xml = tmp.resolve("s.xml");
        Files.writeString(xml, """
                <Data><Row><Amount>25.50</Amount></Row><Row><Amount>1099</Amount></Row></Data>
                """);
        return xml;
    }

    @Test
    void validatesAndPreviewsInContext() throws Exception {
        var result = validator.validate("sum(Row/Amount)", sample(), List.of("/Data"));
        assertTrue(result.valid());
        assertFalse(result.bipOnly());
        assertEquals("1124.5", result.preview());
    }

    @Test
    void syntaxErrorsAreInvalid() throws Exception {
        var result = validator.validate("sum(Row/Amount", sample(), List.of());
        assertFalse(result.valid());
        assertTrue(result.message() != null && !result.message().isBlank());
    }

    @Test
    void xdoxsltFunctionsAreValidButBipOnly() throws Exception {
        var result = validator.validate(
                "xdoxslt:xdo_format_date(xdoxslt:sysdate(), 'DD-MON-YYYY', 'nl-NL')",
                sample(), List.of());
        assertTrue(result.valid(), "xdoxslt must not read as a syntax error");
        assertTrue(result.bipOnly(), "must flag BIP-only");
    }

    @Test
    void brokenXdoxsltSyntaxIsStillInvalid() throws Exception {
        var result = validator.validate("xdoxslt:sysdate(", sample(), List.of());
        assertFalse(result.valid());
    }

    @Test
    void syntaxOnlyWithoutSample() {
        var result = validator.validate("count(Row)", null, List.of());
        assertTrue(result.valid());
        assertEquals(null, result.preview());
    }
}
