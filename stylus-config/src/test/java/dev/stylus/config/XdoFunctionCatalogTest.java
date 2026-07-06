package dev.stylus.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XdoFunctionCatalogTest {

    private final XdoFunctionCatalog catalog = XdoFunctionCatalog.instance();

    @Test
    void coversTheDocumentedFunctionLibrary() {
        assertTrue(catalog.all().size() >= 75,
                "expected the doc-05 function set, got " + catalog.all().size());
        assertEquals(java.util.List.of(
                        "date", "number", "string", "aggregate", "variable", "barcode", "diagnostics"),
                catalog.categories());
    }

    @Test
    void snippetsAreCallableXdoxsltExpressions() {
        for (XdoFunctionCatalog.Function f : catalog.all()) {
            assertTrue(f.snippet().startsWith("xdoxslt:" + f.name() + "("),
                    "snippet mismatch for " + f.name() + ": " + f.snippet());
            assertTrue(f.snippet().endsWith(")"), "unclosed snippet for " + f.name());
            assertTrue(f.signature().contains(f.name()), "signature missing name: " + f.name());
        }
    }

    @Test
    void searchFindsByNameAndSignature() {
        assertTrue(catalog.search("barcode").stream()
                .anyMatch(f -> f.name().equals("format_barcode")));
        assertTrue(catalog.search("running").stream()
                .anyMatch(f -> f.name().equals("set_variable")), "search must scan signatures");
        assertEquals(catalog.all().size(), catalog.search("").size());
    }

    @Test
    void byCategorySlicesCleanly() {
        int total = catalog.categories().stream()
                .mapToInt(c -> catalog.byCategory(c).size())
                .sum();
        assertEquals(catalog.all().size(), total);
        assertTrue(catalog.byCategory("date").size() >= 20, "date group unexpectedly small");
    }
}
