package dev.stylus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural edits on the immutable band tree (M5): replace/remove a node anywhere in the
 * document by identity, rebuilding the ancestor spine. Returns whether the target was found;
 * callers fire {@code documentEdited()} on success.
 */
public final class ModelEdits {

    /** Replaces {@code target} (found by identity) with {@code replacement} anywhere. */
    public static boolean replaceBand(ReportDocument doc, Band target, Band replacement) {
        return editBands(doc.bands(), target, replacement);
    }

    /** Removes {@code target} band anywhere in the tree. */
    public static boolean removeBand(ReportDocument doc, Band target) {
        return editBands(doc.bands(), target, null);
    }

    /** Replaces an inline node (field token …) anywhere: bands, table cells, header/footer. */
    public static boolean replaceInline(ReportDocument doc, InlineNode target, InlineNode replacement) {
        if (replaceInList(doc.pageHeader(), target, replacement)
                || replaceInList(doc.pageFooter(), target, replacement)) {
            return true;
        }
        List<Band> bands = doc.bands();
        for (int i = 0; i < bands.size(); i++) {
            Band rebuilt = rebuildForInline(bands.get(i), target, replacement);
            if (rebuilt != null) {
                bands.set(i, rebuilt);
                return true;
            }
        }
        return false;
    }

    // ---------- band walking ----------

    /** Edits {@code target} within a mutable band list; {@code replacement == null} removes. */
    private static boolean editBands(List<Band> bands, Band target, Band replacement) {
        for (int i = 0; i < bands.size(); i++) {
            Band band = bands.get(i);
            if (band == target) {
                if (replacement == null) {
                    bands.remove(i);
                } else {
                    bands.set(i, replacement);
                }
                return true;
            }
            Band rebuilt = rebuildForBand(band, target, replacement);
            if (rebuilt != null) {
                bands.set(i, rebuilt);
                return true;
            }
        }
        return false;
    }

    /** Rebuilds {@code band} if the target band lives somewhere below it; null when absent. */
    private static Band rebuildForBand(Band band, Band target, Band replacement) {
        switch (band) {
            case GroupBand g -> {
                List<Band> children = new ArrayList<>(g.children());
                if (editBands(children, target, replacement)) {
                    return new GroupBand(g.selectXPath(), g.sortKeys(), children);
                }
            }
            case ConditionalBand c -> {
                List<Band> then = new ArrayList<>(c.then());
                if (editBands(then, target, replacement)) {
                    return new ConditionalBand(c.testExpr(), then, c.otherwise());
                }
                List<Band> otherwise = new ArrayList<>(c.otherwise());
                if (editBands(otherwise, target, replacement)) {
                    return new ConditionalBand(c.testExpr(), c.then(), otherwise);
                }
            }
            case StaticBand s -> { }
            case TableBand t -> { }
            case ImageBand i -> { }
            case OpaqueBand o -> { }
        }
        return null;
    }

    /** Rebuilds {@code band} if the target inline lives somewhere below it; null when absent. */
    private static Band rebuildForInline(Band band, InlineNode target, InlineNode replacement) {
        switch (band) {
            case StaticBand s -> {
                List<InlineNode> content = new ArrayList<>(s.content());
                if (replaceInList(content, target, replacement)) {
                    return new StaticBand(content, s.style(), s.rules());
                }
            }
            case TableBand t -> {
                for (int c = 0; c < t.columns().size(); c++) {
                    TableColumn column = t.columns().get(c);
                    List<InlineNode> cell = new ArrayList<>(column.cell());
                    if (replaceInList(cell, target, replacement)) {
                        List<TableColumn> columns = new ArrayList<>(t.columns());
                        columns.set(c, new TableColumn(column.header(), column.widthWeight(),
                                column.align(), cell, column.rules()));
                        return new TableBand(t.rowXPath(), columns);
                    }
                }
            }
            case GroupBand g -> {
                for (int i = 0; i < g.children().size(); i++) {
                    Band rebuilt = rebuildForInline(g.children().get(i), target, replacement);
                    if (rebuilt != null) {
                        List<Band> children = new ArrayList<>(g.children());
                        children.set(i, rebuilt);
                        return new GroupBand(g.selectXPath(), g.sortKeys(), children);
                    }
                }
            }
            case ConditionalBand cond -> {
                for (int i = 0; i < cond.then().size(); i++) {
                    Band rebuilt = rebuildForInline(cond.then().get(i), target, replacement);
                    if (rebuilt != null) {
                        List<Band> then = new ArrayList<>(cond.then());
                        then.set(i, rebuilt);
                        return new ConditionalBand(cond.testExpr(), then, cond.otherwise());
                    }
                }
                for (int i = 0; i < cond.otherwise().size(); i++) {
                    Band rebuilt = rebuildForInline(cond.otherwise().get(i), target, replacement);
                    if (rebuilt != null) {
                        List<Band> otherwise = new ArrayList<>(cond.otherwise());
                        otherwise.set(i, rebuilt);
                        return new ConditionalBand(cond.testExpr(), cond.then(), otherwise);
                    }
                }
            }
            case ImageBand i -> { }
            case OpaqueBand o -> { }
        }
        return null;
    }

    private static boolean replaceInList(List<InlineNode> list, InlineNode target,
                                         InlineNode replacement) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                if (replacement == null) {
                    list.remove(i);
                } else {
                    list.set(i, replacement);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * The chain of enclosing group/row XPaths for a band or inline node — the group context an
     * expression evaluates in (F-1.26 "relative to current group context"). For element-rooted
     * templates the root match ("/InvoiceData") is the implicit first step.
     */
    public static List<String> contextChain(ReportDocument doc, Object target) {
        List<String> chain = new ArrayList<>();
        if (!"/".equals(doc.rootMatch())) {
            chain.add(doc.rootMatch());
        }
        if (findContext(doc.bands(), target, chain)) {
            return chain;
        }
        return List.of();
    }

    private static boolean findContext(List<Band> bands, Object target, List<String> chain) {
        for (Band band : bands) {
            if (band == target) {
                return true;
            }
            switch (band) {
                case StaticBand s -> {
                    if (s.content().stream().anyMatch(n -> n == target)) {
                        return true;
                    }
                }
                case TableBand t -> {
                    for (TableColumn column : t.columns()) {
                        if (column.cell().stream().anyMatch(n -> n == target)) {
                            chain.add(t.rowXPath());
                            return true;
                        }
                    }
                    if (t == target) {
                        return true;
                    }
                }
                case GroupBand g -> {
                    chain.add(g.selectXPath());
                    if (findContext(g.children(), target, chain)) {
                        return true;
                    }
                    chain.remove(chain.size() - 1);
                }
                case ConditionalBand c -> {
                    if (findContext(c.then(), target, chain)
                            || findContext(c.otherwise(), target, chain)) {
                        return true;
                    }
                }
                case ImageBand i -> { }
                case OpaqueBand o -> { }
            }
        }
        return false;
    }

    /** The static band whose content contains this token, anywhere; null when absent. */
    public static StaticBand containingStaticBand(ReportDocument doc, InlineNode token) {
        return findStatic(doc.bands(), token);
    }

    private static StaticBand findStatic(List<Band> bands, InlineNode token) {
        for (Band band : bands) {
            switch (band) {
                case StaticBand s -> {
                    if (s.content().stream().anyMatch(n -> n == token)) {
                        return s;
                    }
                }
                case GroupBand g -> {
                    StaticBand found = findStatic(g.children(), token);
                    if (found != null) {
                        return found;
                    }
                }
                case ConditionalBand c -> {
                    StaticBand found = findStatic(c.then(), token);
                    if (found == null) {
                        found = findStatic(c.otherwise(), token);
                    }
                    if (found != null) {
                        return found;
                    }
                }
                case TableBand t -> { }
                case ImageBand i -> { }
                case OpaqueBand o -> { }
            }
        }
        return null;
    }

    /**
     * Enclosing bands of {@code target}, outermost first — the FO ancestor breadcrumb
     * (F-1.49). Empty for top-level bands and unknown targets.
     */
    public static List<Band> ancestorsOf(ReportDocument doc, Object target) {
        List<Band> path = new ArrayList<>();
        findPath(doc.bands(), target, path);
        return path;
    }

    private static boolean findPath(List<Band> bands, Object target, List<Band> path) {
        for (Band band : bands) {
            if (band == target) {
                return true;
            }
            path.add(band);
            boolean below = switch (band) {
                case StaticBand s -> s.content().stream().anyMatch(n -> n == target);
                case TableBand t -> t.columns().stream()
                        .anyMatch(c -> c.cell().stream().anyMatch(n -> n == target));
                case GroupBand g -> findPath(g.children(), target, path);
                case ConditionalBand c -> findPath(c.then(), target, path)
                        || findPath(c.otherwise(), target, path);
                case ImageBand i -> false;
                case OpaqueBand o -> false;
            };
            if (below) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }

    // ---------- structural copies (F-1.40 copy/paste needs fresh identities) ----------

    /** Deep copy with fresh node identities — identity-based edits treat it as a new subtree. */
    public static Band copyOf(Band band) {
        return switch (band) {
            case StaticBand s -> new StaticBand(copyInlines(s.content()), s.style(), s.rules());
            case GroupBand g -> new GroupBand(g.selectXPath(), g.sortKeys(),
                    g.children().stream().map(ModelEdits::copyOf).toList());
            case TableBand t -> new TableBand(t.rowXPath(), t.columns().stream()
                    .map(c -> new TableColumn(c.header(), c.widthWeight(), c.align(),
                            copyInlines(c.cell()), c.rules()))
                    .toList());
            case ConditionalBand c -> new ConditionalBand(c.testExpr(),
                    c.then().stream().map(ModelEdits::copyOf).toList(),
                    c.otherwise().stream().map(ModelEdits::copyOf).toList());
            case ImageBand i -> new ImageBand(i.src(), i.widthMm(), i.heightMm());
            case OpaqueBand o -> new OpaqueBand(o.xml());
        };
    }

    public static InlineNode copyOf(InlineNode node) {
        return switch (node) {
            case TextRun t -> new TextRun(t.text());
            case FieldToken f -> new FieldToken(f.xpath(), f.format());
            case PageNumberToken p -> new PageNumberToken();
            case PageCountToken pc -> new PageCountToken();
            case XslTextInline x -> new XslTextInline(x.text());
            case OpaqueInline o -> new OpaqueInline(o.xml());
        };
    }

    private static List<InlineNode> copyInlines(List<InlineNode> nodes) {
        return nodes.stream().map(ModelEdits::copyOf).toList();
    }

    private ModelEdits() { }
}
