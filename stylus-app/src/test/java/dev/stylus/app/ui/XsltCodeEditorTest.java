package dev.stylus.app.ui;

import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Highlighting spans must cover the text exactly and classify xsl:/fo: tags (F-1.35). */
class XsltCodeEditorTest {

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- a comment -->
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                  <fo:block font-size="10pt">Hello <xsl:value-of select="Name"/></fo:block>
                </fo:root>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void spansCoverTheWholeText() {
        StyleSpans<Collection<String>> spans = XsltCodeEditor.computeHighlighting(SAMPLE);
        int total = 0;
        for (var span : spans) {
            total += span.getLength();
        }
        assertEquals(SAMPLE.length(), total, "spans must add up to the text length");
    }

    @Test
    void classifiesXslFoAndPlainTags() {
        StyleSpans<Collection<String>> spans = XsltCodeEditor.computeHighlighting(SAMPLE);
        var classes = new java.util.HashSet<String>();
        spans.forEach(span -> classes.addAll(span.getStyle()));
        assertTrue(classes.contains("code-tag-xsl"), "xsl: tags must have their own class");
        assertTrue(classes.contains("code-tag-fo"), "fo: tags must have their own class");
        assertTrue(classes.contains("code-comment"), "comments must be classified");
        assertTrue(classes.contains("code-attr"), "attributes must be classified");
        assertTrue(classes.contains("code-value"), "attribute values must be classified");
    }

    @Test
    void survivesUnbalancedInput() {
        for (String broken : List.of("<", "<xsl:template", "text only", "<a b=\"c>", "")) {
            StyleSpans<Collection<String>> spans = XsltCodeEditor.computeHighlighting(broken);
            int total = 0;
            for (var span : spans) {
                total += span.getLength();
            }
            assertEquals(broken.length(), total, "broken input: " + broken);
        }
    }
}
