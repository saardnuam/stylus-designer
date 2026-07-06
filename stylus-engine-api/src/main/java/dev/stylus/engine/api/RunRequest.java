package dev.stylus.engine.api;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one engine run needs (docs/03 preview pipeline): template + data + target format,
 * plus the optional Template-Viewer-parity inputs — engine config file (xdo.cfg / fop.xconf),
 * output locale (F-5.3), XLIFF translation (F-5.4), style template (F-5.5) and XSLT parameters
 * (F-5.21). Immutable; build via {@link #builder()}.
 */
public final class RunRequest {

    private final Path template;
    private final Path data;
    private final Path output;
    private final OutputFormat format;
    private final Locale outputLocale;          // nullable
    private final Map<String, String> parameters;
    private final Path engineConfig;            // nullable — xdo.cfg or fop.xconf
    private final Path xliff;                   // nullable
    private final Path styleTemplate;           // nullable

    private RunRequest(Builder b) {
        this.template = Objects.requireNonNull(b.template, "template");
        this.data = b.data; // may be null: pure-FO rendering of a .fo file needs no XML
        this.output = Objects.requireNonNull(b.output, "output");
        this.format = Objects.requireNonNull(b.format, "format");
        this.outputLocale = b.outputLocale;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(b.parameters));
        this.engineConfig = b.engineConfig;
        this.xliff = b.xliff;
        this.styleTemplate = b.styleTemplate;
    }

    public Path template() { return template; }
    public Optional<Path> data() { return Optional.ofNullable(data); }
    public Path output() { return output; }
    public OutputFormat format() { return format; }
    public Optional<Locale> outputLocale() { return Optional.ofNullable(outputLocale); }
    public Map<String, String> parameters() { return parameters; }
    public Optional<Path> engineConfig() { return Optional.ofNullable(engineConfig); }
    public Optional<Path> xliff() { return Optional.ofNullable(xliff); }
    public Optional<Path> styleTemplate() { return Optional.ofNullable(styleTemplate); }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path template;
        private Path data;
        private Path output;
        private OutputFormat format;
        private Locale outputLocale;
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private Path engineConfig;
        private Path xliff;
        private Path styleTemplate;

        public Builder template(Path template) { this.template = template; return this; }
        public Builder data(Path data) { this.data = data; return this; }
        public Builder output(Path output) { this.output = output; return this; }
        public Builder format(OutputFormat format) { this.format = format; return this; }
        public Builder outputLocale(Locale locale) { this.outputLocale = locale; return this; }
        public Builder parameter(String name, String value) { this.parameters.put(name, value); return this; }
        public Builder parameters(Map<String, String> params) { this.parameters.putAll(params); return this; }
        public Builder engineConfig(Path config) { this.engineConfig = config; return this; }
        public Builder xliff(Path xliff) { this.xliff = xliff; return this; }
        public Builder styleTemplate(Path styleTemplate) { this.styleTemplate = styleTemplate; return this; }

        public RunRequest build() {
            return new RunRequest(this);
        }
    }
}
