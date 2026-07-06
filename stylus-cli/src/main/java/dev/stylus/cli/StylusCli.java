package dev.stylus.cli;

import dev.stylus.engine.api.EngineId;
import dev.stylus.engine.api.LogLevel;
import dev.stylus.engine.api.OutputFormat;
import dev.stylus.engine.api.ReportEngine;
import dev.stylus.engine.api.RunRequest;
import dev.stylus.engine.api.RunResult;
import dev.stylus.engine.fop.FopEngine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless renderer (roadmap M1) — also what CI uses for rendering smoke tests (F-11.5).
 *
 * <pre>
 * stylus render --template FILE --format FMT --output FILE [--data FILE]
 *               [--engine fop] [--param name=value]... [--locale ll-TT]
 *               [--config FILE] [--verbose error|exception|event|procedure|statement]
 * stylus engines
 * stylus formats [--engine fop]
 * </pre>
 */
public final class StylusCli {

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    static int execute(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return args.length == 0 ? 2 : 0;
        }
        try {
            return switch (args[0]) {
                case "render" -> render(tail(args));
                case "engines" -> engines();
                case "formats" -> formats(tail(args));
                default -> {
                    System.err.println("Unknown command: " + args[0]);
                    printUsage();
                    yield 2;
                }
            };
        } catch (CliArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static int render(String[] args) {
        RunRequest.Builder request = RunRequest.builder();
        EngineId engineId = EngineId.FOP;
        LogLevel verbosity = LogLevel.EVENT;
        Path bipHome = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--template" -> request.template(Path.of(value(args, ++i, arg)));
                case "--data" -> request.data(Path.of(value(args, ++i, arg)));
                case "--output" -> request.output(Path.of(value(args, ++i, arg)));
                case "--format" -> request.format(parseFormat(value(args, ++i, arg)));
                case "--engine" -> engineId = parseEngine(value(args, ++i, arg));
                case "--bip-home" -> bipHome = Path.of(value(args, ++i, arg));
                case "--config" -> request.engineConfig(Path.of(value(args, ++i, arg)));
                case "--xliff" -> request.xliff(Path.of(value(args, ++i, arg)));
                case "--locale" -> request.outputLocale(Locale.forLanguageTag(value(args, ++i, arg)));
                case "--param" -> {
                    String[] kv = value(args, ++i, arg).split("=", 2);
                    if (kv.length != 2) {
                        throw new CliArgumentException("--param expects name=value, got: " + args[i]);
                    }
                    request.parameter(kv[0], kv[1]);
                }
                case "--verbose" -> verbosity = parseLevel(value(args, ++i, arg));
                default -> throw new CliArgumentException("Unknown option: " + arg);
            }
        }

        ReportEngine engine = engineFor(engineId, bipHome);
        if (!engine.isAvailable()) {
            System.err.println("Engine " + engineId + " is not available on this machine.");
            return 3;
        }

        LogLevel threshold = verbosity;
        RunRequest req;
        try {
            req = request.build();
        } catch (NullPointerException e) {
            throw new CliArgumentException("Missing required option --" + e.getMessage()
                    + " (see --help)");
        }

        RunResult result = engine.run(req, entry -> {
            if (entry.level().isAtLeast(threshold)) {
                System.err.println(entry);
            }
        });

        if (result.success()) {
            System.out.println(result.output().toAbsolutePath().toString());
            return 0;
        }
        System.err.println("Run failed: "
                + result.failure().map(Throwable::getMessage).orElse("unknown error"));
        return 1;
    }

    private static int engines() {
        for (ReportEngine engine : allEngines(null)) {
            System.out.printf("%-4s %-40s %s%n", engine.id(), engine.displayName(),
                    engine.isAvailable() ? "available" : "not configured");
        }
        if (allEngines(null).size() == 1) {
            System.out.println("BIP  (point --bip-home/STYLUS_BIP_HOME at a local"
                    + " BI Publisher installation to enable)");
        }
        return 0;
    }

    private static int formats(String[] args) {
        EngineId engineId = EngineId.FOP;
        Path bipHome = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine" -> engineId = parseEngine(value(args, ++i, "--engine"));
                case "--bip-home" -> bipHome = Path.of(value(args, ++i, "--bip-home"));
                default -> throw new CliArgumentException("Unknown option: " + args[i]);
            }
        }
        ReportEngine engine = engineFor(engineId, bipHome);
        engine.supportedFormats().stream()
                .map(f -> f.name().toLowerCase(Locale.ROOT))
                .sorted()
                .forEach(System.out::println);
        return 0;
    }

    private static List<ReportEngine> allEngines(Path bipHome) {
        List<ReportEngine> engines = new ArrayList<>();
        engines.add(new FopEngine());
        dev.stylus.engine.bip.BipLocator.locate(bipHome).ifPresent(installation -> {
            try {
                engines.add(new dev.stylus.engine.bip.BipEngine(installation));
            } catch (RuntimeException e) {
                System.err.println("BIP installation rejected: " + e.getMessage());
            }
        });
        return engines;
    }

    private static ReportEngine engineFor(EngineId id, Path bipHome) {
        return allEngines(bipHome).stream()
                .filter(e -> e.id() == id)
                .findFirst()
                .orElseThrow(() -> new CliArgumentException("Engine " + id + " is not available"
                        + (id == EngineId.BIP ? " — pass --bip-home or set STYLUS_BIP_HOME" : "")));
    }

    private static OutputFormat parseFormat(String value) {
        try {
            return OutputFormat.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CliArgumentException("Unknown format: " + value);
        }
    }

    private static EngineId parseEngine(String value) {
        try {
            return EngineId.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CliArgumentException("Unknown engine: " + value);
        }
    }

    private static LogLevel parseLevel(String value) {
        try {
            return LogLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CliArgumentException("Unknown log level: " + value);
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new CliArgumentException("Option " + option + " needs a value");
        }
        return args[index];
    }

    private static String[] tail(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }

    private static void printUsage() {
        System.err.println("""
                Stylus CLI — render XSLT/XSL-FO templates headlessly

                Commands:
                  render   --template FILE --format FMT --output FILE [--data FILE]
                           [--engine fop|bip] [--bip-home DIR] [--param name=value]...
                           [--locale ll-TT] [--config fop.xconf|xdo.cfg]
                           [--verbose error|exception|event|procedure|statement]
                  engines  list engines and availability
                  formats  [--engine fop|bip] [--bip-home DIR] list output formats
                """);
    }

    private static final class CliArgumentException extends RuntimeException {
        CliArgumentException(String message) {
            super(message);
        }
    }

    private StylusCli() { }
}
