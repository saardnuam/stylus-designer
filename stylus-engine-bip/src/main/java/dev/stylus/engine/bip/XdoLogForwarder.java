package dev.stylus.engine.bip;

import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.RunLogListener;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OutputStream sink for {@code oracle.xdo.common.log.XDOLogImpl.setDestination}: splits the
 * stream into lines, parses the {@code [timestamp][module][LEVEL] message} prefix and forwards
 * each line to the run log at the mapped Template Viewer level (F-5.9/F-5.10).
 */
final class XdoLogForwarder extends OutputStream {

    private static final Pattern XDO_LINE =
            Pattern.compile("^\\[[^\\]]*\\]\\[[^\\]]*\\]\\[([A-Z]+)\\]\\s?(.*)$");

    private final RunLogListener listener;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);

    XdoLogForwarder(RunLogListener listener) {
        this.listener = listener;
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            flushLine();
        } else if (b != '\r') {
            buffer.write(b);
        }
    }

    @Override
    public void close() {
        flushLine();
    }

    private void flushLine() {
        if (buffer.size() == 0) {
            return;
        }
        String line = buffer.toString(StandardCharsets.UTF_8);
        buffer.reset();
        if (line.isBlank()) {
            return;
        }
        Matcher m = XDO_LINE.matcher(line);
        if (m.matches()) {
            listener.log(levelOf(m.group(1)), m.group(2));
        } else {
            listener.log(LogLevel.STATEMENT, line);
        }
    }

    private static LogLevel levelOf(String token) {
        return switch (token) {
            case "ERROR", "UNEXPECTED" -> LogLevel.ERROR;
            case "EXCEPTION" -> LogLevel.EXCEPTION;
            case "EVENT" -> LogLevel.EVENT;
            case "PROCEDURE" -> LogLevel.PROCEDURE;
            default -> LogLevel.STATEMENT;
        };
    }
}
