package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The full XSLT source editor (F-1.35): RichTextFX CodeArea with line numbers and XML/XSLT
 * syntax highlighting (xsl: and fo: elements get their own colors), plus live validation
 * (F-1.37): debounced well-formedness check (SAX, precise line) and Saxon XSLT compilation,
 * with the offending line marked and the message shown in the strip below.
 */
final class XsltCodeEditor extends BorderPane {

    private static final Pattern XML_TAG = Pattern.compile(
            "(?<COMMENT><!--[\\s\\S]*?-->)"
                    + "|(?<CDATA><!\\[CDATA\\[[\\s\\S]*?]]>)"
                    + "|(?<INSTRUCTION><\\?[\\s\\S]*?\\?>)"
                    + "|(?<ELEMENT></?\\h*([\\w:.-]+)(?:[^<>\"']|\"[^\"]*\"|'[^']*')*\\h*/?>)");
    private static final Pattern ATTRIBUTES =
            Pattern.compile("([\\w:.-]+)\\h*(=)\\h*(\"[^\"]*\"|'[^']*')");
    private static final Pattern ERROR_LINE = Pattern.compile("line (\\d+)");

    private final CodeArea codeArea = new CodeArea();
    private final Label validationStrip = new Label();
    private final ExecutorService validator =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stylus-code-validate");
                t.setDaemon(true);
                return t;
            });
    private final Processor saxon = new Processor(false);
    private final AtomicLong generation = new AtomicLong();
    private int markedLine = -1;

    XsltCodeEditor() {
        getStyleClass().add("code-editor");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("code-editor-area");

        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(120))
                .subscribe(ignore -> applyHighlighting());
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
                .subscribe(ignore -> validateAsync());

        validationStrip.getStyleClass().add("code-validation");
        validationStrip.setMaxWidth(Double.MAX_VALUE);

        setCenter(new VirtualizedScrollPane<>(codeArea));
        setBottom(validationStrip);
    }

    // ---------- API kept TextArea-shaped for the Shell ----------

    String getText() {
        return codeArea.getText();
    }

    void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
        codeArea.getUndoManager().forgetHistory();
        codeArea.moveTo(0);
        codeArea.requestFollowCaret();
    }

    void undo() {
        codeArea.undo();
    }

    void redo() {
        codeArea.redo();
    }

    // ---------- syntax highlighting (F-1.35) ----------

    private void applyHighlighting() {
        String text = codeArea.getText();
        codeArea.setStyleSpans(0, computeHighlighting(text));
    }

    static StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        Matcher matcher = XML_TAG.matcher(text);
        int last = 0;
        while (matcher.find()) {
            spans.add(Collections.emptyList(), matcher.start() - last);
            if (matcher.group("COMMENT") != null) {
                spans.add(List.of("code-comment"), matcher.end() - matcher.start());
            } else if (matcher.group("CDATA") != null || matcher.group("INSTRUCTION") != null) {
                spans.add(List.of("code-cdata"), matcher.end() - matcher.start());
            } else {
                styleElement(spans, matcher.group("ELEMENT"));
            }
            last = matcher.end();
        }
        spans.add(Collections.emptyList(), text.length() - last);
        return spans.create();
    }

    /** One element: brackets + name (xsl:/fo:-aware) + attribute name/=/value runs. */
    private static void styleElement(StyleSpansBuilder<Collection<String>> spans, String element) {
        Matcher name = Pattern.compile("^(</?\\h*)([\\w:.-]+)").matcher(element);
        if (!name.find()) {
            spans.add(List.of("code-tag"), element.length());
            return;
        }
        String tagClass = name.group(2).startsWith("xsl:") ? "code-tag-xsl"
                : name.group(2).startsWith("fo:") ? "code-tag-fo" : "code-tag";
        spans.add(List.of("code-bracket"), name.group(1).length());
        spans.add(List.of(tagClass), name.group(2).length());

        int cursor = name.end();
        Matcher attr = ATTRIBUTES.matcher(element);
        while (attr.find(cursor)) {
            spans.add(Collections.emptyList(), attr.start() - cursor);
            spans.add(List.of("code-attr"), attr.group(1).length());
            spans.add(List.of("code-bracket"), attr.group(2).length());
            spans.add(List.of("code-value"), attr.group(3).length());
            cursor = attr.end();
        }
        spans.add(List.of("code-bracket"), element.length() - cursor);
    }

    // ---------- live validation (F-1.37) ----------

    private record Verdict(boolean valid, int line, String message) { }

    private void validateAsync() {
        String text = codeArea.getText();
        long ticket = generation.incrementAndGet();
        if (text.isBlank()) {
            showVerdict(new Verdict(true, -1, null));
            return;
        }
        validator.submit(() -> {
            Verdict verdict = validate(text);
            if (generation.get() == ticket) {
                Platform.runLater(() -> {
                    if (generation.get() == ticket) {
                        showVerdict(verdict);
                    }
                });
            }
        });
    }

    private Verdict validate(String text) {
        // 1: well-formedness with precise position
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            dbf.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
        } catch (SAXParseException e) {
            return new Verdict(false, e.getLineNumber(), e.getMessage());
        } catch (Exception e) {
            return new Verdict(false, -1, String.valueOf(e.getMessage()));
        }
        // 2: XSLT compilation (only when it looks like a stylesheet — plain FO files skip)
        if (!text.contains("XSL/Transform")) {
            return new Verdict(true, -1, null);
        }
        try {
            saxon.newXsltCompiler().compile(new StreamSource(new StringReader(text)));
            return new Verdict(true, -1, null);
        } catch (SaxonApiException e) {
            String message = e.getMessage() == null ? "invalid XSLT" : e.getMessage();
            Matcher m = ERROR_LINE.matcher(message);
            int line = m.find() ? Integer.parseInt(m.group(1)) : -1;
            int newline = message.indexOf('\n');
            return new Verdict(false, line, newline > 0 ? message.substring(0, newline) : message);
        }
    }

    private void showVerdict(Verdict verdict) {
        if (markedLine >= 0 && markedLine < codeArea.getParagraphs().size()) {
            codeArea.setParagraphStyle(markedLine, Collections.emptyList());
        }
        markedLine = -1;
        validationStrip.getStyleClass().removeAll("code-validation-ok", "code-validation-error");
        if (verdict.valid()) {
            validationStrip.setText(I18n.t("code.valid"));
            validationStrip.getStyleClass().add("code-validation-ok");
            return;
        }
        validationStrip.getStyleClass().add("code-validation-error");
        if (verdict.line() > 0 && verdict.line() <= codeArea.getParagraphs().size()) {
            markedLine = verdict.line() - 1;
            codeArea.setParagraphStyle(markedLine, List.of("code-error-line"));
            validationStrip.setText(I18n.t("code.invalidLine", verdict.line(), verdict.message()));
        } else {
            validationStrip.setText(I18n.t("code.invalid", verdict.message()));
        }
    }
}
