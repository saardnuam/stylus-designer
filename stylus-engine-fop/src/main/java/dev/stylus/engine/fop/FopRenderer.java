package dev.stylus.engine.fop;

import dev.stylus.engine.api.EngineException;
import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.RunLogListener;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.model.EventSeverity;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * FO → paginated output via Apache FOP (F-4.4). Honors an optional fop.xconf (F-2.23/F-5.22);
 * FOP events are forwarded to the run log mapped onto the Template-Viewer level scale (F-5.10).
 */
final class FopRenderer {

    private static final Map<OutputFormat, String> MIME = Map.of(
            OutputFormat.PDF, MimeConstants.MIME_PDF,
            OutputFormat.POSTSCRIPT, MimeConstants.MIME_POSTSCRIPT,
            OutputFormat.PCL, MimeConstants.MIME_PCL,
            OutputFormat.AFP, MimeConstants.MIME_AFP,
            OutputFormat.PNG, MimeConstants.MIME_PNG,
            OutputFormat.TIFF, MimeConstants.MIME_TIFF,
            OutputFormat.TEXT, MimeConstants.MIME_PLAIN_TEXT,
            OutputFormat.IF, MimeConstants.MIME_FOP_IF);

    static boolean canRender(OutputFormat format) {
        return MIME.containsKey(format);
    }

    void render(String fo, Path baseDir, Path xconf, OutputFormat format, Path outputFile,
                RunLogListener log) {
        String mime = MIME.get(format);
        if (mime == null) {
            throw new EngineException("FOP cannot render format " + format);
        }
        try {
            FopFactory factory = xconf != null
                    ? FopFactory.newInstance(xconf.toFile())
                    : FopFactory.newInstance(baseDir.toUri());
            FOUserAgent agent = factory.newFOUserAgent();
            agent.getEventBroadcaster().addEventListener(event -> forward(event, log));

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                Fop fop = factory.newFop(mime, agent, out);
                Transformer identity = TransformerFactory.newInstance().newTransformer();
                identity.transform(
                        new StreamSource(new StringReader(fo)),
                        new SAXResult(fop.getDefaultHandler()));
            }
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("FOP rendering failed: " + e.getMessage(), e);
        }
    }

    private void forward(Event event, RunLogListener log) {
        EventSeverity severity = event.getSeverity();
        LogLevel level;
        if (severity == EventSeverity.FATAL || severity == EventSeverity.ERROR) {
            level = LogLevel.ERROR;
        } else if (severity == EventSeverity.WARN) {
            level = LogLevel.EVENT;
        } else {
            level = LogLevel.STATEMENT;
        }
        log.log(level, "FOP: " + EventFormatter.format(event));
    }
}
