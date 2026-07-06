package dev.stylus.engine.fop;

import dev.stylus.engine.api.EngineException;
import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.RunLogListener;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;

import javax.xml.transform.stream.StreamSource;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * XSLT stage of the FOP pipeline (docs/03 preview pipeline): runs the template against the
 * sample XML with Saxon-HE, supporting XSLT 1.0/2.0/3.0 (F-2.1/F-2.2). Produces either the
 * intermediate XSL-FO (F-5.11) or, for web mode, the HTML output directly (F-4.2).
 */
final class SaxonTransformer {

    private final Processor processor = new Processor(false);

    /** Transform to a string (XSL-FO intermediate). */
    String transformToString(Path template, Path data, Map<String, String> parameters,
                             RunLogListener log) {
        StringWriter writer = new StringWriter(64 * 1024);
        Serializer out = processor.newSerializer(writer);
        transform(template, data, parameters, out, log);
        return writer.toString();
    }

    /** Transform straight to a file (HTML web output — serialization per the template's xsl:output). */
    void transformToFile(Path template, Path data, Map<String, String> parameters,
                         Path outputFile, RunLogListener log) {
        Serializer out = processor.newSerializer(outputFile.toFile());
        transform(template, data, parameters, out, log);
    }

    private void transform(Path template, Path data, Map<String, String> parameters,
                           Serializer out, RunLogListener log) {
        try {
            XsltCompiler compiler = processor.newXsltCompiler();
            XsltExecutable executable = compiler.compile(new StreamSource(template.toFile()));
            Xslt30Transformer transformer = executable.load30();

            if (!parameters.isEmpty()) {
                Map<QName, XdmValue> params = new HashMap<>();
                parameters.forEach((k, v) -> params.put(new QName(k), new XdmAtomicValue(v)));
                transformer.setStylesheetParameters(params);
            }
            transformer.setMessageHandler(message ->
                    log.log(LogLevel.EVENT, "xsl:message: " + message.getContent().getStringValue()));

            transformer.applyTemplates(new StreamSource(data.toFile()), out);
        } catch (SaxonApiException e) {
            throw new EngineException("XSLT transform failed: " + e.getMessage(), e);
        }
    }
}
