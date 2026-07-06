package dev.stylus.engine.bip;

import dev.stylus.engine.api.EngineConversions;
import dev.stylus.engine.api.EngineException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Template-conversion tools (F-5.12/13/15) over the isolated BIP classloader — the exact call
 * sequences Template Viewer 12 performs (verified against tmplviewer.jar bytecode):
 * RTFProcessor for RTF→XSL(+XLIFF), Excel2XSLParser, EFTProcessor (setXSL = XSL output path),
 * OnlineReportProcessor.convertXmlDesignToXslfo for XPT, FOUtility.mergeFOs and
 * XSLTProfiler.main("layout", xsl) for in-place profiling injection.
 */
final class BipConversions implements EngineConversions {

    private final ChildFirstClassLoader loader;

    BipConversions(ChildFirstClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public Path rtfToXsl(Path rtf, Path outputXsl) {
        return inLoader("rtfToXsl", () -> {
            Object processor = construct("oracle.xdo.template.RTFProcessor", rtf.toString());
            call(processor, "setOutput", outputXsl.toString());
            processor.getClass().getMethod("process").invoke(processor);
            return outputXsl;
        });
    }

    @Override
    public Path rtfToXslAndXliff(Path rtf, Path outputXsl, Path outputXliff) {
        return inLoader("rtfToXslAndXliff", () -> {
            Object processor = construct("oracle.xdo.template.RTFProcessor", rtf.toString());
            call(processor, "setOutput", outputXsl.toString());
            call(processor, "setXLIFFOutput", outputXliff.toString());
            processor.getClass().getMethod("process").invoke(processor);
            return outputXsl;
        });
    }

    @Override
    public Path excelToXsl(Path excel, Path outputXsl) {
        return inLoader("excelToXsl", () -> {
            Object parser = loader.loadClass("oracle.xdo.template.excel.Excel2XSLParser")
                    .getDeclaredConstructor().newInstance();
            call(parser, "setTemplate", excel.toString());
            call(parser, "setXSLOutput", outputXsl.toString());
            parser.getClass().getMethod("process").invoke(parser);
            return outputXsl;
        });
    }

    @Override
    public Path etextToXsl(Path etext, Path sampleData, Path outputXsl) {
        return inLoader("etextToXsl", () -> {
            Path discarded = Files.createTempFile("stylus-etext", ".out");
            try {
                Object processor = loader.loadClass("oracle.xdo.template.EFTProcessor")
                        .getDeclaredConstructor().newInstance();
                call(processor, "setTemplate", etext.toString());
                call(processor, "setXSL", outputXsl.toString());
                call(processor, "setData", sampleData.toString());
                call(processor, "setOutput", discarded.toString());
                processor.getClass().getMethod("process").invoke(processor);
                return outputXsl;
            } finally {
                Files.deleteIfExists(discarded);
            }
        });
    }

    @Override
    public Path xptToXsl(Path xpt, Path outputXsl) {
        return inLoader("xptToXsl", () -> {
            Class<?> orp = loader.loadClass("oracle.xdo.template.OnlineReportProcessor");
            Object instance = orp.getMethod("instance").invoke(null);
            Object context = orp.getMethod("createReportContext", String.class)
                    .invoke(instance, "ctxName");
            Class<?> contextType =
                    loader.loadClass("oracle.xdo.template.online.model.api.XDOReportContext");
            Object engine = orp.getMethod("createReportEngine", contextType)
                    .invoke(instance, context);
            try (InputStream in = new FileInputStream(xpt.toFile());
                 OutputStream out = new FileOutputStream(outputXsl.toFile())) {
                Object xslDoc = engine.getClass()
                        .getMethod("convertXmlDesignToXslfo", InputStream.class)
                        .invoke(engine, in);
                xslDoc.getClass().getMethod("print", OutputStream.class).invoke(xslDoc, out);
            }
            return outputXsl;
        });
    }

    @Override
    public Path mergeFo(List<Path> foFiles, Path mergedFo) {
        return inLoader("mergeFo", () -> {
            String[] paths = foFiles.stream().map(Path::toString).toArray(String[]::new);
            Object merged = loader.loadClass("oracle.xdo.template.fo.util.FOUtility")
                    .getMethod("mergeFOs", String[].class)
                    .invoke(null, (Object) paths);
            try (InputStream in = (InputStream) merged) {
                Files.copy(in, mergedFo, StandardCopyOption.REPLACE_EXISTING);
            }
            return mergedFo;
        });
    }

    @Override
    public Path injectProfiling(Path xsl, Path instrumentedXsl) {
        return inLoader("injectProfiling", () -> {
            // addProfile4Layout: stylesheet text in → instrumented stylesheet text out
            // (addProfile4EText is the variant for eText-generated XSL).
            Object profiler = loader.loadClass("oracle.xdo.common.xml.XSLTProfiler")
                    .getDeclaredConstructor().newInstance();
            String instrumented = (String) profiler.getClass()
                    .getMethod("addProfile4Layout", String.class)
                    .invoke(profiler, Files.readString(xsl));
            Files.writeString(instrumentedXsl, instrumented);
            return instrumentedXsl;
        });
    }

    // ---------- plumbing ----------

    /** Runs a conversion with the BIP loader as context classloader; unwraps failures. */
    private Path inLoader(String operation, Callable<Path> job) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            return job.call();
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : e;
            throw new EngineException("BI Publisher " + operation + " failed: " + cause, cause);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private Object construct(String className, String arg) throws Exception {
        return loader.loadClass(className).getConstructor(String.class).newInstance(arg);
    }

    private void call(Object target, String method, String arg) throws Exception {
        target.getClass().getMethod(method, String.class).invoke(target, arg);
    }
}
