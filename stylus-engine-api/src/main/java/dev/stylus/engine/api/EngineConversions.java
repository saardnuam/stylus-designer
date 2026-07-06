package dev.stylus.engine.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Template-conversion & FO-debug tools an engine may expose (F-5.11..F-5.15) — the Template
 * Viewer "Tools" menu. BIP wraps RTFProcessor & friends; FOP exposes none of the conversions.
 * All methods throw {@link UnsupportedOperationException} unless overridden; each returns the
 * written output path.
 */
public interface EngineConversions {

    /** RTF template → XSL (F-5.15). */
    default Path rtfToXsl(Path rtf, Path outputXsl) {
        throw new UnsupportedOperationException("rtfToXsl");
    }

    /** RTF template → XSL + XLIFF translation skeleton (F-5.15). */
    default Path rtfToXslAndXliff(Path rtf, Path outputXsl, Path outputXliff) {
        throw new UnsupportedOperationException("rtfToXslAndXliff");
    }

    /** Excel (XLS) template → XSL (F-5.15). */
    default Path excelToXsl(Path excel, Path outputXsl) {
        throw new UnsupportedOperationException("excelToXsl");
    }

    /**
     * eText (EFT) template → XSL (F-5.15). The Oracle processor generates the XSL while running
     * the template, so a sample data file is required; the rendered eText output is discarded.
     */
    default Path etextToXsl(Path etext, Path sampleData, Path outputXsl) {
        throw new UnsupportedOperationException("etextToXsl");
    }

    /** XPT (BI Publisher layout) template → XSL-FO stylesheet (F-5.15). */
    default Path xptToXsl(Path xpt, Path outputXsl) {
        throw new UnsupportedOperationException("xptToXsl");
    }

    /** Merge multiple XSL-FO files into one (F-5.12). */
    default Path mergeFo(List<Path> foFiles, Path mergedFo) {
        throw new UnsupportedOperationException("mergeFo");
    }

    /** Copy the XSL and inject profiling/stopwatch instrumentation into the copy (F-5.13). */
    default Path injectProfiling(Path xsl, Path instrumentedXsl) {
        throw new UnsupportedOperationException("injectProfiling");
    }
}
