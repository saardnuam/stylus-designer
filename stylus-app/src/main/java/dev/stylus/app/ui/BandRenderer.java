package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.model.Band;
import dev.stylus.model.ConditionalBand;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.InlineNode;
import dev.stylus.model.OpaqueBand;
import dev.stylus.model.OpaqueInline;
import dev.stylus.model.PageNumberToken;
import dev.stylus.model.ImageBand;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.TableBand;
import dev.stylus.model.TableColumn;
import dev.stylus.model.TextRun;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Renders model bands as canvas nodes per the design handoff §5 (F-1.17..F-1.24): group cards
 * with FOR-EACH header bars and sort pills, detail tables with a sample row, amber conditional
 * bands, mono field chips `⟨ … ⟩`, opaque "XSLT block" bands. Clicking any band selects it
 * (F-1.20) — the live approximation; Preview stays engine truth.
 */
final class BandRenderer {

    private final DesignerState state;

    BandRenderer(DesignerState state) {
        this.state = state;
    }

    Node render(Band band) {
        Node node = switch (band) {
            case StaticBand b -> renderStatic(b);
            case GroupBand g -> renderGroup(g);
            case TableBand t -> renderTable(t);
            case ConditionalBand c -> renderConditional(c);
            case ImageBand img -> renderImage(img);
            case OpaqueBand o -> renderOpaque(o);
        };
        node.getStyleClass().add("canvas-band");
        if (state.selection() == band) {
            node.getStyleClass().add("selected");
        }
        node.setOnMouseClicked(e -> {
            state.select(band);
            e.consume();
        });
        return node;
    }

    // ---------- band kinds ----------

    private Node renderStatic(StaticBand band) {
        FlowPane flow = inlineFlow(band.content());
        applyStyle(flow, band.style());
        if (!band.rules().isEmpty()) {
            Label rules = new Label("◈ " + band.rules().size());
            rules.getStyleClass().add("rules-chip");
            rules.setTooltip(new javafx.scene.control.Tooltip(
                    I18n.t("props.rules.count", band.rules().size())));
            flow.getChildren().add(rules);
        }
        VBox box = new VBox(flow);
        box.getStyleClass().add("static-band");
        if ("center".equals(band.style() == null ? null : band.style().textAlign())) {
            flow.setAlignment(Pos.CENTER);
        } else if ("right".equals(band.style() == null ? null : band.style().textAlign())) {
            flow.setAlignment(Pos.CENTER_RIGHT);
        }
        return box;
    }

    private Node renderGroup(GroupBand group) {
        Label loop = new Label("⟳");
        loop.getStyleClass().add("group-loop-icon");
        Label eyebrow = new Label(I18n.t("canvas.band.forEach"));
        eyebrow.getStyleClass().add("band-eyebrow-label");
        Label path = new Label(group.selectXPath());
        path.getStyleClass().add("group-path");

        HBox header = new HBox(7, loop, eyebrow, path);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("group-band-header");

        if (!group.sortKeys().isEmpty()) {
            Label sort = new Label("sort by " + group.sortKeys().get(0).selectXPath()
                    + (group.sortKeys().get(0).ascending() ? " ▲" : " ▼"));
            sort.getStyleClass().add("sort-pill");
            header.getChildren().add(sort);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label repeat = new Label("↻");
        repeat.getStyleClass().add("group-repeat");
        header.getChildren().addAll(spacer, repeat);

        VBox body = new VBox(6);
        body.getStyleClass().add("group-band-body");
        java.util.List<javafx.scene.Node> childNodes = new java.util.ArrayList<>();
        if (group.children().isEmpty()) {
            body.getChildren().add(dropHint());
        } else {
            for (Band child : group.children()) {
                javafx.scene.Node node = render(child);
                childNodes.add(node);
                body.getChildren().add(node);
            }
        }

        // Drops into the group body insert at the caret with group-relative paths (F-1.13/21).
        DropCaret caret = new DropCaret();
        body.setOnDragOver(e -> {
            if (e.getDragboard().hasString() && DragPayload.isStylus(e.getDragboard().getString())) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                caret.showAt(body, childNodes, caret.indexFor(childNodes, e.getY()));
            }
            e.consume();
        });
        body.setOnDragExited(e -> caret.hide(body));
        body.setOnDragDropped(e -> {
            caret.hide(body);
            Band child = DropFactory.bandFor(e.getDragboard().getString(),
                    absoluteContextOf(group));
            boolean done = child != null;
            if (done) {
                int index = Math.min(caret.indexFor(childNodes, e.getY()), group.children().size());
                java.util.List<Band> children = new java.util.ArrayList<>(group.children());
                children.add(index, child);
                GroupBand replacement = new GroupBand(group.selectXPath(), group.sortKeys(), children);
                if (dev.stylus.model.ModelEdits.replaceBand(state.document(), group, replacement)) {
                    state.documentEdited();
                    state.select(child);
                }
            }
            e.setDropCompleted(done);
            e.consume();
        });

        VBox card = new VBox(header, body);
        card.getStyleClass().add("group-band");
        return card;
    }

    /** Absolute XPath of the group's iteration context (enclosing chain + own select). */
    private String absoluteContextOf(GroupBand group) {
        java.util.List<String> chain = new java.util.ArrayList<>(
                dev.stylus.model.ModelEdits.contextChain(state.document(), group));
        chain.add(group.selectXPath());
        return DropFactory.resolveAbsolute(chain);
    }

    private Node renderTable(TableBand table) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("detail-grid");
        double total = table.columns().stream().mapToDouble(TableColumn::widthWeight).sum();
        int col = 0;
        for (TableColumn column : table.columns()) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 * column.widthWeight() / total);
            grid.getColumnConstraints().add(constraints);

            Label header = new Label(column.header());
            header.getStyleClass().add("detail-grid-header");
            header.setMaxWidth(Double.MAX_VALUE);
            grid.add(header, col, 0);

            FlowPane cell = inlineFlow(column.cell());
            cell.getStyleClass().add("detail-grid-cell");
            if ("right".equals(column.align())) {
                cell.setAlignment(Pos.CENTER_RIGHT);
                header.setAlignment(Pos.CENTER_RIGHT);
            } else if ("center".equals(column.align())) {
                cell.setAlignment(Pos.CENTER);
                header.setAlignment(Pos.CENTER);
            }
            grid.add(cell, col, 1);
            col++;
        }
        Label eyebrow = new Label(I18n.t("canvas.band.detail", table.rowXPath()));
        eyebrow.getStyleClass().add("band-eyebrow-label");
        VBox box = new VBox(4, eyebrow, grid);
        box.getStyleClass().add("table-band");
        return box;
    }

    private Node renderConditional(ConditionalBand cond) {
        Label ifPill = new Label(I18n.t("canvas.band.if"));
        ifPill.getStyleClass().add("if-pill");
        Label expr = new Label(cond.testExpr());
        expr.getStyleClass().add("cond-expr");
        HBox header = new HBox(7, ifPill, expr);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, header);
        box.getStyleClass().add("cond-band");
        for (Band child : cond.then()) {
            box.getChildren().add(render(child));
        }
        if (cond.hasElse()) {
            Label elseLabel = new Label(I18n.t("canvas.band.else"));
            elseLabel.getStyleClass().add("else-label");
            box.getChildren().add(elseLabel);
            for (Band child : cond.otherwise()) {
                box.getChildren().add(render(child));
            }
        }
        return box;
    }

    private Node renderOpaque(OpaqueBand band) {
        Label eyebrow = new Label(I18n.t("canvas.band.xslt"));
        eyebrow.getStyleClass().add("band-eyebrow-label");
        String firstLine = band.xml().strip();
        int newline = firstLine.indexOf('\n');
        if (newline > 0) {
            firstLine = firstLine.substring(0, newline) + " …";
        }
        if (firstLine.length() > 90) {
            firstLine = firstLine.substring(0, 90) + "…";
        }
        Label preview = new Label(firstLine);
        preview.getStyleClass().add("opaque-preview");
        VBox box = new VBox(3, eyebrow, preview);
        box.getStyleClass().add("opaque-band");
        return box;
    }

    // ---------- inline content ----------

    /** Renders page header/footer inline rows (dashed bands, F-1.18 #1/#9). */
    Node renderMarginBand(List<InlineNode> content, String labelKey) {
        Label eyebrow = new Label(I18n.t(labelKey));
        eyebrow.getStyleClass().add("band-eyebrow-label");
        FlowPane flow = inlineFlow(content);
        VBox box = new VBox(3, eyebrow, flow);
        box.getStyleClass().addAll("canvas-band", "margin-band");

        // Field drops append tokens to the header/footer list (document context).
        box.setOnDragOver(e -> {
            if (e.getDragboard().hasString()
                    && e.getDragboard().getString().startsWith(DragPayload.FIELD_PREFIX)) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        box.setOnDragDropped(e -> {
            String payload = e.getDragboard().getString();
            boolean done = payload != null && payload.startsWith(DragPayload.FIELD_PREFIX);
            if (done) {
                String[] parts = payload.split("\\|");
                String rootMatch = state.document().rootMatch();
                FieldToken token = FieldToken.of(DropFactory.relativize(parts[2],
                        "/".equals(rootMatch) ? "" : rootMatch));
                content.add(token);
                state.documentEdited();
                state.select(token);
            }
            e.setDropCompleted(done);
            e.consume();
        });
        return box;
    }

    FlowPane inlineFlow(List<InlineNode> nodes) {
        FlowPane flow = new FlowPane(2, 2);
        for (InlineNode node : nodes) {
            flow.getChildren().add(renderInline(node));
        }
        return flow;
    }

    private Node renderInline(InlineNode node) {
        return switch (node) {
            case TextRun t -> {
                Label text = new Label(t.text());
                text.getStyleClass().add("inline-text");
                text.setOnMouseClicked(e -> {
                    state.select(t);
                    e.consume();
                });
                yield text;
            }
            case FieldToken f -> {
                Label chip = new Label("⟨ " + f.xpath() + " ⟩");
                chip.getStyleClass().add("field-token");
                if (f.xpath().startsWith("$")) {
                    chip.getStyleClass().add("field-token-code");
                }
                chip.setOnMouseClicked(e -> {
                    state.select(f);
                    e.consume();
                });
                yield chip;
            }
            case PageNumberToken p -> {
                Label chip = new Label("⟨ page() ⟩");
                chip.getStyleClass().addAll("field-token", "field-token-code");
                yield chip;
            }
            case dev.stylus.model.PageCountToken p -> {
                Label chip = new Label("⟨ page-count() ⟩");
                chip.getStyleClass().addAll("field-token", "field-token-code");
                yield chip;
            }
            case dev.stylus.model.XslTextInline x -> {
                Label run = new Label(x.text().isBlank() ? "␣" : x.text());
                run.getStyleClass().add("xsltext-run");
                run.setTooltip(new javafx.scene.control.Tooltip(
                        "<xsl:text>" + dev.stylus.codegen.XslWriter.ncrEscape(x.text())
                                + "</xsl:text>"));
                run.setOnMouseClicked(e -> {
                    state.select(x);
                    e.consume();
                });
                yield run;
            }
            case OpaqueInline o -> {
                Label chip = new Label("⟨ xslt ⟩");
                chip.getStyleClass().addAll("field-token", "field-token-opaque");
                yield chip;
            }
        };
    }

    /** Image band (F-7.1): live thumbnail when the source resolves, a chip otherwise. */
    private Node renderImage(ImageBand band) {
        javafx.scene.image.Image image = resolveImage(band.src());
        VBox box = new VBox();
        box.getStyleClass().add("image-band");
        if (image != null && !image.isError()) {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
            view.setPreserveRatio(true);
            // ~3.78 px per mm at 96dpi keeps proportions plausible on the schematic canvas.
            view.setFitWidth(band.widthMm() != null ? band.widthMm() * 3.78 : 120);
            box.getChildren().add(view);
        } else {
            Label chip = new Label("⧉ " + band.src());
            chip.getStyleClass().add("image-chip");
            box.getChildren().add(chip);
        }
        return box;
    }

    private javafx.scene.image.Image resolveImage(String src) {
        try {
            if (src.startsWith("data:") || src.contains("://")) {
                return new javafx.scene.image.Image(src, true);
            }
            java.nio.file.Path base = state.templateFile() != null
                    ? state.templateFile().getParent() : null;
            java.nio.file.Path file = base != null
                    ? base.resolve(src) : java.nio.file.Path.of(src);
            if (java.nio.file.Files.isRegularFile(file)) {
                return new javafx.scene.image.Image(file.toUri().toString(), true);
            }
        } catch (Exception ignored) {
            // unresolvable → chip
        }
        return null;
    }

    Node dropHint() {
        Label hint = new Label(I18n.t("canvas.hint.dropFields"));
        hint.getStyleClass().add("drop-hint");
        return hint;
    }

    private void applyStyle(Node node, StyleProps style) {
        if (style == null || style.isEmpty()) {
            return;
        }
        StringBuilder css = new StringBuilder();
        if (Boolean.TRUE.equals(style.bold())) {
            css.append("-fx-font-weight: bold;");
        }
        if (Boolean.TRUE.equals(style.italic())) {
            css.append("-fx-font-style: italic;");
        }
        if (style.fontSizePt() != null) {
            css.append("-fx-font-size: ").append(style.fontSizePt()).append("pt;");
        }
        if (style.fontFamily() != null) {
            css.append("-fx-font-family: \"").append(style.fontFamily()).append("\";");
        }
        if (style.background() != null) {
            css.append("-fx-background-color: ").append(style.background()).append(";");
        }
        boolean underline = Boolean.TRUE.equals(style.underline());
        if (underline) {
            css.append("-fx-underline: true;");
        }
        if (Boolean.TRUE.equals(style.strike())) {
            css.append("-fx-strikethrough: true;");
        }
        node.setStyle(css.toString());
    }
}
