package dev.stylus.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The {@code xdoxslt:} extension-function catalog (doc 05, F-3.1): every callable function with
 * category, insert snippet and signature line — feeds the engine-aware expression palette
 * (F-1.31) and the capability warnings (BIP-only, doc 07). Names verified against
 * {@code XSLTFunctions.class} in the 12c jars.
 */
public final class XdoFunctionCatalog {

    /** One palette entry. {@code snippet} is inserted at the caret; {@code signature} is the tooltip. */
    public record Function(String name, String category, String snippet, String signature) { }

    public static final String NAMESPACE_PREFIX = "xdoxslt";

    private static final XdoFunctionCatalog INSTANCE = new XdoFunctionCatalog();

    private final List<Function> functions;
    private final List<String> categories;

    private XdoFunctionCatalog() {
        List<Function> list = new ArrayList<>();
        Set<String> cats = new LinkedHashSet<>();
        try (InputStream in = XdoFunctionCatalog.class.getResourceAsStream("xdoxslt-functions.tsv")) {
            if (in == null) {
                throw new IllegalStateException("xdoxslt-functions.tsv missing from classpath");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t");
                list.add(new Function(parts[0], parts[1], parts[2], parts[3]));
                cats.add(parts[1]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.functions = Collections.unmodifiableList(list);
        this.categories = List.copyOf(cats);
    }

    public static XdoFunctionCatalog instance() {
        return INSTANCE;
    }

    public List<Function> all() {
        return functions;
    }

    /** Category ids in catalog order (date, number, string, aggregate, variable, barcode, diagnostics). */
    public List<String> categories() {
        return categories;
    }

    public List<Function> byCategory(String category) {
        return functions.stream().filter(f -> f.category().equals(category)).toList();
    }

    public List<Function> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).strip();
        if (q.isEmpty()) {
            return functions;
        }
        return functions.stream()
                .filter(f -> f.name().toLowerCase(Locale.ROOT).contains(q)
                        || f.signature().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }
}
