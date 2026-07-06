package dev.stylus.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Root of the report document model (docs/03 §document model): page geometry, page header/
 * footer content, the band stack, and stylesheet parameters. Bands and inline nodes are
 * immutable records; edits replace nodes in the mutable {@code bands} list.
 *
 * When a template was opened from source but could not be recognized at all, the document is
 * a single opaque band and {@link #originalSource()} carries the exact file text — saving an
 * unchanged document reproduces it byte-identically (N7).
 */
public final class ReportDocument {

    private String title;
    private OutputMode outputMode;
    private PageSetup pageSetup;
    private final List<InlineNode> pageHeader = new ArrayList<>();
    private final List<InlineNode> pageFooter = new ArrayList<>();
    private final List<Band> bands = new ArrayList<>();
    private final Map<String, String> parameters = new LinkedHashMap<>(); // name → default
    private final List<String> imports = new ArrayList<>(); // xsl:import hrefs (F-6.2)
    // Conditional page layout (F-2.26/F-2.27): empty = single-master mode using pageSetup.
    private final List<PageMaster> pageMasters = new ArrayList<>();
    private final List<MasterSelector> masterSelectors = new ArrayList<>();
    private String rootMatch = "/";
    private String originalSource;
    private boolean modified;

    public ReportDocument(String title, OutputMode outputMode, PageSetup pageSetup) {
        this.title = Objects.requireNonNull(title);
        this.outputMode = Objects.requireNonNull(outputMode);
        this.pageSetup = Objects.requireNonNull(pageSetup);
    }

    /** A new, empty pixel-perfect A4 document. */
    public static ReportDocument empty() {
        return new ReportDocument("Untitled", OutputMode.PIXEL_PERFECT, PageSetup.A4);
    }

    public String title() { return title; }
    public OutputMode outputMode() { return outputMode; }
    public PageSetup pageSetup() { return pageSetup; }
    public List<InlineNode> pageHeader() { return pageHeader; }
    public List<InlineNode> pageFooter() { return pageFooter; }
    public List<Band> bands() { return bands; }
    public Map<String, String> parameters() { return parameters; }
    public List<String> imports() { return imports; }

    /** Named masters for conditional page layout; empty = single master from pageSetup. */
    public List<PageMaster> pageMasters() { return pageMasters; }
    /** Condition rows (first match wins); only meaningful when pageMasters is non-empty. */
    public List<MasterSelector> masterSelectors() { return masterSelectors; }

    /**
     * The root template's match pattern: "/" (default) or an element-rooted "/RootName" — the
     * context all top-level band paths are relative to. Read from opened templates; the writer
     * re-emits it so element-rooted files keep their path semantics.
     */
    public String rootMatch() { return rootMatch; }
    public void setRootMatch(String rootMatch) { this.rootMatch = Objects.requireNonNull(rootMatch); }

    public void setTitle(String title) { this.title = Objects.requireNonNull(title); touch(); }
    public void setOutputMode(OutputMode outputMode) { this.outputMode = Objects.requireNonNull(outputMode); touch(); }
    public void setPageSetup(PageSetup pageSetup) { this.pageSetup = Objects.requireNonNull(pageSetup); touch(); }

    /** Exact source text this document was read from; null for new documents. */
    public String originalSource() { return originalSource; }
    public void setOriginalSource(String source) { this.originalSource = source; }

    /** True once the model diverged from {@link #originalSource()}. */
    public boolean isModified() { return modified; }
    public void touch() { this.modified = true; }
    public void markSaved() { this.modified = false; }

    /** True when nothing was recognized: exactly one opaque band and no other content. */
    public boolean isFullyOpaque() {
        return bands.size() == 1 && bands.get(0) instanceof OpaqueBand
                && pageHeader.isEmpty() && pageFooter.isEmpty();
    }
}
