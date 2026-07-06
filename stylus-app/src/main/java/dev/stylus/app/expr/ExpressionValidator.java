package dev.stylus.app.expr;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import javax.xml.transform.stream.StreamSource;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * Live expression validation + preview (F-1.32): compiles the XPath with Saxon and, when
 * sample data is loaded, evaluates it in the current group context (F-1.26). The sample
 * document is cached per file.
 */
public final class ExpressionValidator {

    public record Result(boolean valid, String message, String preview, boolean bipOnly) {

        public static Result ok(String preview) {
            return new Result(true, null, preview, false);
        }

        /** Uses xdoxslt: extension functions — valid, but only the BIP engine can run/preview it. */
        public static Result okBipOnly() {
            return new Result(true, null, null, true);
        }

        public static Result invalid(String message) {
            return new Result(false, message, null, false);
        }
    }

    private static final String XDOXSLT_NS =
            dev.stylus.engine.api.CapabilityMatrix.NS_XDOXSLT;

    private final Processor processor = new Processor(false);
    private Path cachedFile;
    private XdmNode cachedDoc;

    public Result validate(String expression, Path sampleXml, List<String> contextChain) {
        if (expression == null || expression.isBlank()) {
            return Result.invalid("empty expression");
        }
        boolean usesXdoxslt = expression.contains("xdoxslt:");
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareNamespace("xdoxslt", XDOXSLT_NS);
        try {
            compiler.compile(expression);
        } catch (SaxonApiException e) {
            if (usesXdoxslt && isUnknownFunction(e)) {
                // Extension functions live in the Oracle runtime, not in Saxon:
                // syntactically checked, semantically deferred to the BIP engine (doc 07).
                return Result.okBipOnly();
            }
            return Result.invalid(shortMessage(e));
        }

        if (sampleXml == null) {
            return Result.ok(null); // syntax-only: no sample loaded
        }
        try {
            XdmItem context = document(sampleXml);
            for (String step : contextChain) {
                XdmValue value = compiler.evaluate(step, context);
                if (value.size() == 0) {
                    return Result.ok(null); // context path yields nothing in this sample
                }
                context = value.itemAt(0);
            }
            XdmValue value = compiler.evaluate(expression, context);
            return Result.ok(previewOf(value));
        } catch (SaxonApiException e) {
            // Compiles but fails dynamically against this sample — still applicable.
            return Result.ok(null);
        } catch (Exception e) {
            return Result.ok(null);
        }
    }

    public record RowSample(String value, boolean matched) { }

    /** F-1.28 data probe: total row count in context plus the first sampled rows. */
    public record DataProbe(int total, List<RowSample> rows) { }

    /**
     * Data-tab probe (F-1.28): resolves the context chain (first match per step, all matches
     * for the last step = the rows), then evaluates {@code valueExpr} per row and whether
     * {@code testExpr} (nullable — conditional-format rules) matches. Null when no sample is
     * loaded or the probe fails — callers show a hint instead.
     */
    public DataProbe probe(String valueExpr, String testExpr, Path sampleXml,
                           List<String> contextChain, int limit) {
        if (sampleXml == null) {
            return null;
        }
        try {
            XPathCompiler compiler = processor.newXPathCompiler();
            compiler.declareNamespace("xdoxslt", XDOXSLT_NS);
            XdmItem context = document(sampleXml);
            XdmValue rowSet;
            if (contextChain.isEmpty()) {
                rowSet = context;
            } else {
                for (int i = 0; i < contextChain.size() - 1; i++) {
                    XdmValue value = compiler.evaluate(contextChain.get(i), context);
                    if (value.size() == 0) {
                        return new DataProbe(0, List.of());
                    }
                    context = value.itemAt(0);
                }
                rowSet = compiler.evaluate(contextChain.get(contextChain.size() - 1), context);
            }
            List<RowSample> rows = new java.util.ArrayList<>();
            for (XdmItem row : rowSet) {
                if (rows.size() >= limit) {
                    break;
                }
                String value;
                try {
                    value = previewOf(compiler.evaluate(valueExpr, row));
                } catch (SaxonApiException e) {
                    value = "?";
                }
                boolean matched = false;
                if (testExpr != null && !testExpr.isBlank()) {
                    try {
                        var selector = compiler.compile(testExpr).load();
                        selector.setContextItem(row);
                        matched = selector.effectiveBooleanValue();
                    } catch (Exception e) {
                        // xdoxslt or dynamic error — rule can't be probed locally
                    }
                }
                rows.add(new RowSample(value, matched));
            }
            return new DataProbe(rowSet.size(), rows);
        } catch (Exception e) {
            return null;
        }
    }

    private XdmNode document(Path file) throws SaxonApiException {
        if (!file.equals(cachedFile)) {
            DocumentBuilder builder = processor.newDocumentBuilder();
            cachedDoc = builder.build(new StreamSource(file.toFile()));
            cachedFile = file;
        }
        return cachedDoc;
    }

    private static String previewOf(XdmValue value) {
        if (value.size() == 0) {
            return "()";
        }
        StringJoiner joiner = new StringJoiner(" · ");
        int shown = 0;
        for (XdmItem item : value) {
            String s = item.getStringValue().strip();
            if (s.length() > 60) {
                s = s.substring(0, 60) + "…";
            }
            joiner.add(s);
            if (++shown == 3) {
                break;
            }
        }
        String preview = joiner.toString();
        return value.size() > 3 ? preview + " (+" + (value.size() - 3) + ")" : preview;
    }

    /** XPST0017 = "cannot find a matching function" — the signature-unknown static error. */
    private static boolean isUnknownFunction(SaxonApiException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("XPST0017") || message.contains("Cannot find a")
                || message.contains("unknown function");
    }

    private static String shortMessage(SaxonApiException e) {
        String message = e.getMessage() == null ? "invalid expression" : e.getMessage();
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
