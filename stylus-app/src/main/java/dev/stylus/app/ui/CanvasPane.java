package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.model.Band;
import dev.stylus.model.FieldFormat;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.OutputMode;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.TextRun;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Center — canvas region (handoff §5): ruler/output strip + scrollable paper that renders the
 * document model as bands (F-1.17/F-1.18). Tree drops append fields/groups (F-1.13/F-1.14);
 * clicking selects (F-1.20). The view switch swaps paper ↔ code ↔ preview in place.
 */
final class CanvasPane extends VBox {

    private static final double PAPER_WIDTH = 760;
    private static final double PAPER_MIN_HEIGHT = 980;

    private final DesignerState state;
    private final BandRenderer renderer;
    private final StackPane viewStack = new StackPane();
    private final ScrollPane designView;
    private final XsltCodeEditor codeView;
    private final PreviewPane previewPane;
    private final VBox bandBox = new VBox(8);
    private final ToggleButton pixelPerfect;
    private final ToggleButton web;
    private final DropCaret dropCaret = new DropCaret();
    private final List<javafx.scene.Node> bandNodes = new java.util.ArrayList<>();

    CanvasPane(DesignerState state, PreviewPane previewPane) {
        this.state = state;
        this.renderer = new BandRenderer(state);
        this.previewPane = previewPane;
        getStyleClass().add("canvas-pane");

        // Ruler / output strip (F-1.16)
        ToggleGroup modes = new ToggleGroup();
        pixelPerfect = mode(I18n.t("canvas.mode.pixelPerfect"), modes, true);
        web = mode(I18n.t("canvas.mode.web"), modes, false);
        pixelPerfect.setOnAction(e -> switchMode(OutputMode.PIXEL_PERFECT));
        web.setOnAction(e -> switchMode(OutputMode.WEB));
        HBox modeSwitch = new HBox(pixelPerfect, web);
        modeSwitch.getStyleClass().add("segmented");

        Region ruler = new Region();
        ruler.getStyleClass().add("ruler");
        HBox.setHgrow(ruler, Priority.ALWAYS);
        Label widthReadout = new Label(I18n.t("canvas.pageWidth"));
        widthReadout.getStyleClass().add("mono-readout");

        // ⧉ FO-structure outlines (F-1.46): hairline borders on every FO element node.
        ToggleButton structure = new ToggleButton("⧉");
        structure.getStyleClass().add("segment");
        structure.setTooltip(new javafx.scene.control.Tooltip(I18n.t("canvas.structure.tooltip")));
        structure.setOnAction(e -> {
            if (structure.isSelected()) {
                bandBox.getStyleClass().add("fo-structure");
            } else {
                bandBox.getStyleClass().remove("fo-structure");
            }
        });

        javafx.scene.control.Button pageSetup = new javafx.scene.control.Button("▤");
        pageSetup.getStyleClass().add("segment");
        pageSetup.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pageSetup.title")));
        pageSetup.setOnAction(e -> PageSetupDialog.show(getScene().getWindow(), state));

        HBox rulerStrip = new HBox(modeSwitch, structure, pageSetup, ruler, widthReadout);
        rulerStrip.getStyleClass().add("ruler-strip");
        rulerStrip.setAlignment(Pos.CENTER_LEFT);
        rulerStrip.setSpacing(8);

        // Paper (F-1.17): bands render inside; margin guides sit around them.
        bandBox.getStyleClass().add("paper");
        bandBox.setPrefWidth(PAPER_WIDTH);
        bandBox.setMaxWidth(PAPER_WIDTH);
        bandBox.setMinHeight(PAPER_MIN_HEIGHT);

        VBox paperHolder = new VBox(bandBox);
        paperHolder.setAlignment(Pos.TOP_CENTER);
        paperHolder.getStyleClass().add("paper-holder");

        designView = new ScrollPane(paperHolder);
        designView.setFitToWidth(true);
        designView.getStyleClass().add("canvas-scroll");

        // Drops insert at the caret position (F-1.21).
        bandBox.setOnDragOver(this::acceptStylusDrag);
        bandBox.setOnDragDropped(this::handleDrop);
        bandBox.setOnDragExited(e -> dropCaret.hide(bandBox));

        // Click on empty paper clears the selection.
        bandBox.setOnMouseClicked(e -> state.select(null));

        codeView = new XsltCodeEditor();

        previewPane.setVisible(false);
        viewStack.getChildren().addAll(designView, codeView, previewPane);
        VBox.setVgrow(viewStack, Priority.ALWAYS);

        getChildren().addAll(rulerStrip, viewStack);

        state.onDocumentChanged(this::rebuildPaper);
        state.onSelectionChanged(this::rebuildPaper);
        rebuildPaper();
        showView(View.DESIGN);
    }

    // ---------- rendering ----------

    private void rebuildPaper() {
        bandBox.getChildren().clear();
        bandNodes.clear();
        var doc = state.document();

        syncModeToggle(doc.outputMode());

        if (doc.outputMode() == OutputMode.PIXEL_PERFECT) {
            if (doc.pageHeader().isEmpty()) {
                bandBox.getChildren().add(ghostMarginBand("canvas.addHeader", doc.pageHeader()));
            } else {
                bandBox.getChildren().add(
                        renderer.renderMarginBand(doc.pageHeader(), "canvas.band.pageHeader"));
            }
        }
        if (doc.bands().isEmpty()) {
            Label hint = new Label(I18n.t("canvas.empty"));
            hint.getStyleClass().add("empty-state");
            VBox emptyBox = new VBox(hint);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setMinHeight(240);
            bandBox.getChildren().add(emptyBox);
        } else {
            for (Band band : doc.bands()) {
                javafx.scene.Node node = renderer.render(band);
                bandNodes.add(node);
                bandBox.getChildren().add(node);
            }
        }
        if (doc.outputMode() == OutputMode.PIXEL_PERFECT) {
            Region filler = new Region();
            VBox.setVgrow(filler, Priority.ALWAYS);
            bandBox.getChildren().add(filler);
            if (doc.pageFooter().isEmpty()) {
                bandBox.getChildren().add(ghostMarginBand("canvas.addFooter", doc.pageFooter()));
            } else {
                bandBox.getChildren().add(
                        renderer.renderMarginBand(doc.pageFooter(), "canvas.band.pageFooter"));
            }
        }
    }

    /** Slim invitation to create the page header/footer; click seeds an editable text run. */
    private javafx.scene.Node ghostMarginBand(String labelKey,
            List<dev.stylus.model.InlineNode> target) {
        Label ghost = new Label(I18n.t(labelKey));
        ghost.getStyleClass().add("ghost-band");
        ghost.setMaxWidth(Double.MAX_VALUE);
        ghost.setAlignment(Pos.CENTER);
        ghost.setOnMouseClicked(e -> {
            TextRun seed = new TextRun(I18n.t("canvas.marginPlaceholder"));
            target.add(seed);
            state.documentEdited();
            state.select(seed);
            e.consume();
        });
        return ghost;
    }

    private void syncModeToggle(OutputMode mode) {
        pixelPerfect.setSelected(mode == OutputMode.PIXEL_PERFECT);
        web.setSelected(mode == OutputMode.WEB);
    }

    private void switchMode(OutputMode mode) {
        syncModeToggle(mode); // keep exactly one selected
        if (state.document().outputMode() != mode) {
            state.document().setOutputMode(mode);
            state.documentEdited();
        }
    }

    // ---------- drag & drop (F-1.13/F-1.14) ----------

    private void acceptStylusDrag(DragEvent e) {
        if (e.getDragboard().hasString() && DragPayload.isStylus(e.getDragboard().getString())) {
            e.acceptTransferModes(TransferMode.COPY);
            dropCaret.showAt(bandBox, bandNodes, dropCaret.indexFor(bandNodes, e.getY()));
        }
        e.consume();
    }

    private void handleDrop(DragEvent e) {
        dropCaret.hide(bandBox);
        // Top-level drop context: the document root, or the element an element-rooted
        // template matches (paths inside it are relative to that element).
        String rootMatch = state.document().rootMatch();
        Band band = DropFactory.bandFor(e.getDragboard().getString(),
                "/".equals(rootMatch) ? "" : rootMatch);
        if (band != null) {
            int index = Math.min(dropCaret.indexFor(bandNodes, e.getY()),
                    state.document().bands().size());
            state.document().bands().add(index, band);
            state.documentEdited();
            state.select(band);
        }
        e.setDropCompleted(band != null);
        e.consume();
    }

    // ---------- view switch (F-1.3) ----------

    void showView(View view) {
        designView.setVisible(view == View.DESIGN);
        codeView.setVisible(view == View.CODE);
        previewPane.setVisible(view == View.PREVIEW);
    }

    XsltCodeEditor codeArea() {
        return codeView;
    }

    private static ToggleButton mode(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("segment");
        b.setToggleGroup(group);
        b.setSelected(selected);
        return b;
    }
}
