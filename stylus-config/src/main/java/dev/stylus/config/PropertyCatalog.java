package dev.stylus.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog of known xdo.cfg properties (doc 04, F-5.23): all named 12c property constants
 * with their group, plus the dynamic-prefix conventions. Unknown properties remain settable as
 * free key/value pairs — the catalog only powers grouping, completion and inline help.
 */
public final class PropertyCatalog {

    /** One known property. Group ids match the doc 04 sections (i18n keys derive from them). */
    public record Entry(String name, String group) { }

    /** Dynamic-key prefixes (doc 04): everything after the prefix is user-defined. */
    public static final List<String> DYNAMIC_PREFIXES = List.of(
            "xslt.", "user-variable.", "font.", "font-substitute.",
            "currency-format.", "bidi-chartype.");

    private static final PropertyCatalog INSTANCE = new PropertyCatalog();

    private final Map<String, Entry> entries;

    private PropertyCatalog() {
        Map<String, Entry> map = new LinkedHashMap<>();
        try (InputStream in = PropertyCatalog.class.getResourceAsStream("xdo-properties.tsv")) {
            if (in == null) {
                throw new IllegalStateException("xdo-properties.tsv missing from classpath");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t");
                map.put(parts[0], new Entry(parts[0], parts[1]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.entries = Collections.unmodifiableMap(map);
    }

    public static PropertyCatalog instance() {
        return INSTANCE;
    }

    public Map<String, Entry> all() {
        return entries;
    }

    public Optional<Entry> find(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    /** Known named property, or matches one of the dynamic prefixes. */
    public boolean isKnown(String name) {
        return entries.containsKey(name)
                || DYNAMIC_PREFIXES.stream().anyMatch(name::startsWith);
    }

    /** Group id for a property; dynamic-prefix keys map to their own pseudo-groups. */
    public String groupOf(String name) {
        Entry entry = entries.get(name);
        if (entry != null) {
            return entry.group();
        }
        return DYNAMIC_PREFIXES.stream()
                .filter(name::startsWith)
                .map(p -> "dynamic-" + p.substring(0, p.length() - 1))
                .findFirst()
                .orElse("unknown");
    }
}
