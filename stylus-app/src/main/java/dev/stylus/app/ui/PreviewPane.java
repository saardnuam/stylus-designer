package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import dev.stylus.engine.api.OutputFormat;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Preview view (F-4.6): engine truth, not the canvas approximation. PDF renders as page images
 * via PDFBox; HTML loads in a WebView; every other format shows an export/open bar (F-4.7/F-4.8).
 */
final class PreviewPane extends VBox {

    private static final float PDF_RENDER_DPI = 110;
    private static final int MAX_PREVIEW_PAGES = 50;

    private final HostServices hostServices;
    private final Label status = new Label();
    private final VBox pageBox = new VBox(18);
    private final ScrollPane scroll = new ScrollPane(pageBox);
    private final WebView webView = new WebView();
    private final Button exportButton = new Button(I18n.t("preview.export"));
    private final Button openButton = new Button(I18n.t("preview.open"));

    private Path currentOutput;

    PreviewPane(HostServices hostServices) {
        this.hostServices = hostServices;
        getStyleClass().add("preview-pane");

        status.getStyleClass().add("preview-status");
        exportButton.getStyleClass().add("bench-button");
        openButton.getStyleClass().add("bench-button");
        exportButton.setDisable(true);
        openButton.setDisable(true);
        exportButton.setOnAction(e -> exportOutput());
        openButton.setOnAction(e -> {
            if (currentOutput != null) {
                hostServices.showDocument(currentOutput.toUri().toString());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, status, spacer, exportButton, openButton);
        bar.getStyleClass().add("preview-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        pageBox.setAlignment(Pos.TOP_CENTER);
        pageBox.getStyleClass().add("preview-pages");
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("canvas-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        showPlaceholder();
        getChildren().addAll(bar, scroll);
    }

    void showPlaceholder() {
        currentOutput = null;
        exportButton.setDisable(true);
        openButton.setDisable(true);
        status.setText("");
        webView.getEngine().loadContent("");
        Label placeholder = new Label(I18n.t("preview.placeholder"));
        placeholder.getStyleClass().add("empty-state");
        pageBox.getChildren().setAll(placeholder);
        if (getChildren().size() > 1) {
            getChildren().set(1, scroll);
        }
    }

    /** Displays a finished run's output. Called on the FX thread. */
    void showOutput(Path output, OutputFormat format) {
        currentOutput = output;
        exportButton.setDisable(false);
        openButton.setDisable(false);
        status.setText(output.getFileName().toString());

        switch (format) {
            case PDF -> showPdf(output);
            case HTML, MHTML -> showHtml(output);
            case FO, IF, TEXT, ETEXT -> showText(output);
            default -> showSavedOnly(output);
        }
    }

    private void showPdf(Path pdf) {
        getChildren().set(1, scroll);
        Label rendering = new Label(I18n.t("preview.rendering"));
        rendering.getStyleClass().add("empty-state");
        pageBox.getChildren().setAll(rendering);

        Thread renderThread = new Thread(() -> {
            try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                PDFRenderer renderer = new PDFRenderer(doc);
                int pages = Math.min(doc.getNumberOfPages(), MAX_PREVIEW_PAGES);
                var images = new java.util.ArrayList<javafx.scene.image.Image>(pages);
                for (int i = 0; i < pages; i++) {
                    BufferedImage img = renderer.renderImageWithDPI(i, PDF_RENDER_DPI);
                    images.add(SwingFXUtils.toFXImage(img, null));
                }
                int total = doc.getNumberOfPages();
                Platform.runLater(() -> {
                    pageBox.getChildren().clear();
                    for (var image : images) {
                        ImageView view = new ImageView(image);
                        view.setPreserveRatio(true);
                        view.setFitWidth(Math.min(image.getWidth(), 900));
                        view.getStyleClass().add("preview-page");
                        VBox holder = new VBox(view);
                        holder.getStyleClass().add("preview-page-holder");
                        holder.setAlignment(Pos.CENTER);
                        pageBox.getChildren().add(holder);
                    }
                    status.setText(I18n.t("preview.pageCount", pdf.getFileName(), total));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Label error = new Label(I18n.t("preview.error", e.getMessage()));
                    error.getStyleClass().add("empty-state");
                    pageBox.getChildren().setAll(error);
                });
            }
        }, "stylus-pdf-preview");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void showHtml(Path html) {
        webView.getEngine().load(html.toUri().toString());
        getChildren().set(1, webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
    }

    private void showText(Path file) {
        try {
            javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(
                    Files.readString(file));
            area.setEditable(false);
            area.getStyleClass().add("code-view");
            getChildren().set(1, area);
            VBox.setVgrow(area, Priority.ALWAYS);
        } catch (Exception e) {
            showSavedOnly(file);
        }
    }

    private void showSavedOnly(Path output) {
        getChildren().set(1, scroll);
        Label saved = new Label(I18n.t("preview.savedTo", output.toAbsolutePath()));
        saved.getStyleClass().add("empty-state");
        saved.setWrapText(true);
        pageBox.getChildren().setAll(saved);
    }

    private void exportOutput() {
        if (currentOutput == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("preview.export"));
        chooser.setInitialFileName(currentOutput.getFileName().toString());
        java.io.File target = chooser.showSaveDialog(getScene().getWindow());
        if (target != null) {
            try {
                Files.copy(currentOutput, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                status.setText(I18n.t("preview.exportedTo", target.getAbsolutePath()));
            } catch (Exception e) {
                status.setText(I18n.t("preview.error", e.getMessage()));
            }
        }
    }
}
