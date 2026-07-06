package dev.stylus.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEditsTest {

    private ReportDocument nestedDoc(FieldToken deepField, ConditionalBand cond) {
        ReportDocument doc = ReportDocument.empty();
        TableBand table = new TableBand("LineItem", List.of(
                new TableColumn("A", 1, null, List.of(deepField))));
        GroupBand inner = new GroupBand("Orders/Order", List.of(), List.of(table, cond));
        GroupBand outer = new GroupBand("/Data/Customer", List.of(SortKey.by("Name")),
                List.of(StaticBand.text("head"), inner));
        doc.bands().add(outer);
        return doc;
    }

    @Test
    void replacesDeepInlineRebuildingSpine() {
        FieldToken old = FieldToken.of("Amount");
        ConditionalBand cond = new ConditionalBand("x", List.of(), List.of());
        ReportDocument doc = nestedDoc(old, cond);

        FieldToken updated = new FieldToken("Amount", FieldFormat.number("#,##0.00"));
        assertTrue(ModelEdits.replaceInline(doc, old, updated));

        GroupBand outer = (GroupBand) doc.bands().get(0);
        GroupBand inner = (GroupBand) outer.children().get(1);
        TableBand table = (TableBand) inner.children().get(0);
        assertEquals(updated, table.columns().get(0).cell().get(0));
        // untouched siblings survive
        assertEquals(StaticBand.text("head"), outer.children().get(0));
        assertEquals("x", ((ConditionalBand) inner.children().get(1)).testExpr());
    }

    @Test
    void replacesAndRemovesNestedBands() {
        FieldToken field = FieldToken.of("Amount");
        ConditionalBand cond = new ConditionalBand("x", List.of(), List.of());
        ReportDocument doc = nestedDoc(field, cond);

        ConditionalBand newCond = new ConditionalBand("Amount > 10", List.of(), List.of());
        assertTrue(ModelEdits.replaceBand(doc, cond, newCond));
        GroupBand inner = (GroupBand) ((GroupBand) doc.bands().get(0)).children().get(1);
        assertEquals("Amount > 10", ((ConditionalBand) inner.children().get(1)).testExpr());

        assertTrue(ModelEdits.removeBand(doc, inner.children().get(0)));
        inner = (GroupBand) ((GroupBand) doc.bands().get(0)).children().get(1);
        assertEquals(1, inner.children().size());

        assertFalse(ModelEdits.removeBand(doc, cond), "stale reference must not match");
    }

    @Test
    void contextChainReflectsEnclosingGroups() {
        FieldToken field = FieldToken.of("Amount");
        ConditionalBand cond = new ConditionalBand("x", List.of(), List.of());
        ReportDocument doc = nestedDoc(field, cond);

        assertEquals(List.of("/Data/Customer", "Orders/Order", "LineItem"),
                ModelEdits.contextChain(doc, field));
        assertEquals(List.of("/Data/Customer"),
                ModelEdits.contextChain(doc,
                        ((GroupBand) doc.bands().get(0)).children().get(1)));
        assertEquals(List.of(), ModelEdits.contextChain(doc, doc.bands().get(0)));
    }
}
