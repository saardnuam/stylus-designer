package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.model.Band;
import dev.stylus.model.ConditionalBand;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.ModelEdits;
import dev.stylus.model.SortKey;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.TextRun;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Supplier;

/**
 * Toolbar row 2 — XSLT insert actions (F-1.5). M5: Field / For-each / Condition / Choose /
 * Sort / XPath are live — they append bands (into the selected group when one is selected)
 * or act on the selection; every item is live.
 */
final class InsertToolbar extends HBox {

    private final DesignerState state;
    private final ExpressionEditorOverlay exprEditor;

    InsertToolbar(DesignerState state, ExpressionEditorOverlay exprEditor) {
        this.state = state;
        this.exprEditor = exprEditor;
        getStyleClass().add("toolbar-row2");
        setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(
                live("+", I18n.t("insert.field"), "cat-accent", true, this::insertField),
                live("⟳", I18n.t("insert.forEach"), "cat-structure", false, this::insertForEach),
                live("if", I18n.t("insert.condition"), "cat-condition", false,
                        () -> insertConditional(false)),
                live("⑂", I18n.t("insert.choose"), "cat-condition", false,
                        () -> insertConditional(true)),
                live("$x", I18n.t("insert.variable"), "cat-data", false, this::insertVariable),
                divider(),
                foElementMenu(),
                live("ƒx", I18n.t("insert.xpath"), "cat-code", false, this::insertXPath),
                live("</>", I18n.t("insert.xslt"), "cat-code", false, this::insertXslt),
                live("„a“", I18n.t("insert.xslText"), "cat-code", false, this::insertXslText),
                live("⊕ƒ", I18n.t("insert.callTemplate"), "cat-code", false, this::insertCallTemplate),
                live("#", I18n.t("insert.number"), "cat-data", false, this::insertNumber),
                live("◷", I18n.t("insert.date"), "cat-data", false, this::insertDate),
                live("⇅", I18n.t("insert.sort"), "cat-structure", false, this::addSort),
                live("⧉", I18n.t("insert.image"), "cat-data", false, this::insertImage),
                live("⤓", I18n.t("insert.pageBreak"), "cat-structure", false, this::insertPageBreak));
    }

    // ---------- FO element menu (design fundamental §1.8: authors insert *named* FO elements) ----------

    /**
     * The fo: dropdown — every entry inserts a concrete formatting object. Mapped elements
     * (block, table, page-number tokens) become editable bands/tokens; the rest insert as
     * preserved skeletons the author refines in the Code view (N7-safe).
     */
    private javafx.scene.control.MenuButton foElementMenu() {
        javafx.scene.control.MenuButton menu =
                new javafx.scene.control.MenuButton("fo:  " + I18n.t("insert.fo"));
        menu.getStyleClass().addAll("pill-button", "cat-structure");
        menu.setFocusTraversable(false);

        menu.getItems().addAll(
                foItem("fo:block", () -> appendBand(() -> StaticBand.text("…"))),
                foItem("fo:inline", () -> insertInline(() -> new dev.stylus.model.OpaqueInline(
                        "<fo:inline font-style=\"italic\">…</fo:inline>"))),
                foItem("fo:block-container", () -> appendBand(() -> new dev.stylus.model.OpaqueBand(
                        "<fo:block-container position=\"absolute\" top=\"10mm\" left=\"10mm\""
                                + " width=\"60mm\" height=\"20mm\"><fo:block>…</fo:block>"
                                + "</fo:block-container>"))),
                foItem("fo:table", () -> appendBand(() -> new dev.stylus.model.TableBand("Row",
                        List.of(dev.stylus.model.TableColumn.of("A", 1,
                                        FieldToken.of(I18n.t("insert.field.placeholder"))),
                                dev.stylus.model.TableColumn.of("B", 1,
                                        FieldToken.of(I18n.t("insert.field.placeholder"))))))),
                foItem("fo:list-block", () -> appendBand(() -> new dev.stylus.model.OpaqueBand(
                        "<fo:list-block provisional-distance-between-starts=\"6mm\">"
                                + "<fo:list-item><fo:list-item-label end-indent=\"label-end()\">"
                                + "<fo:block>•</fo:block></fo:list-item-label>"
                                + "<fo:list-item-body start-indent=\"body-start()\">"
                                + "<fo:block>…</fo:block></fo:list-item-body>"
                                + "</fo:list-item></fo:list-block>"))),
                foItem("fo:leader", () -> insertInline(() -> new dev.stylus.model.OpaqueInline(
                        "<fo:leader leader-pattern=\"dots\"/>"))),
                foItem("fo:basic-link", () -> insertInline(() -> new dev.stylus.model.OpaqueInline(
                        "<fo:basic-link external-destination=\"url('https://example.org')\""
                                + " color=\"#1D4ED8\">…</fo:basic-link>"))),
                foItem("fo:footnote", () -> insertInline(() -> new dev.stylus.model.OpaqueInline(
                        "<fo:footnote><fo:inline baseline-shift=\"super\" font-size=\"6pt\">1"
                                + "</fo:inline><fo:footnote-body><fo:block font-size=\"8pt\">…"
                                + "</fo:block></fo:footnote-body></fo:footnote>"))),
                foItem("fo:page-number", () -> insertInline(dev.stylus.model.PageNumberToken::new)),
                foItem("fo:page-number-citation", () ->
                        insertInline(dev.stylus.model.PageCountToken::new)));
        return menu;
    }

    private javafx.scene.control.MenuItem foItem(String element, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(element);
        item.setOnAction(e -> action.run());
        return item;
    }

    /**
     * Inserts inline content into the selected static band (or the band containing the
     * selected token); with no suitable selection a fresh block wraps it.
     */
    private void insertInline(Supplier<dev.stylus.model.InlineNode> factory) {
        Object selection = state.selection();
        StaticBand target = selection instanceof StaticBand band ? band
                : selection instanceof FieldToken token
                        ? ModelEdits.containingStaticBand(state.document(), token)
                        : null;
        dev.stylus.model.InlineNode node = factory.get();
        if (target == null) {
            appendBand(() -> new StaticBand(List.of(node), StyleProps.NONE));
            return;
        }
        java.util.List<dev.stylus.model.InlineNode> content =
                new java.util.ArrayList<>(target.content());
        content.add(node);
        StaticBand replacement = new StaticBand(content, target.style(), target.rules());
        if (ModelEdits.replaceBand(state.document(), target, replacement)) {
            state.documentEdited();
            state.select(node);
        }
    }

    /**
     * call-template insert with param UI (F-6.3): callable names are discovered from the
     * document's subtemplate imports; parameters of the chosen template pre-fill the rows.
     */
    private void insertCallTemplate() {
        java.util.Map<String, java.util.List<String>> discovered = discoverNamedTemplates();

        javafx.scene.control.ComboBox<String> name = new javafx.scene.control.ComboBox<>();
        name.setEditable(true);
        name.getItems().setAll(discovered.keySet());
        name.setPrefWidth(240);

        javafx.scene.layout.VBox paramRows = new javafx.scene.layout.VBox(6);
        java.util.List<javafx.scene.control.TextField[]> params = new java.util.ArrayList<>();
        Runnable addParamRow = () -> {
            javafx.scene.control.TextField pName = new javafx.scene.control.TextField();
            pName.setPromptText(I18n.t("insert.callTemplate.param"));
            javafx.scene.control.TextField pValue = new javafx.scene.control.TextField();
            pValue.setPromptText(I18n.t("insert.callTemplate.value"));
            params.add(new javafx.scene.control.TextField[] {pName, pValue});
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(6, pName, pValue);
            paramRows.getChildren().add(row);
        };
        name.valueProperty().addListener((obs, old, chosen) -> {
            paramRows.getChildren().clear();
            params.clear();
            for (String param : discovered.getOrDefault(chosen, java.util.List.of())) {
                addParamRow.run();
                params.get(params.size() - 1)[0].setText(param);
            }
        });
        javafx.scene.control.Button more = new javafx.scene.control.Button("+");
        more.getStyleClass().add("bench-button");
        more.setOnAction(e -> addParamRow.run());

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10,
                name, paramRows, more);
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("insert.callTemplate"));
        dialog.setHeaderText(I18n.t("insert.callTemplate.header"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        dialog.setResultConverter(button ->
                button == javafx.scene.control.ButtonType.OK ? name.getValue() : null);
        dialog.showAndWait().ifPresent(chosen -> {
            if (chosen == null || chosen.isBlank()) {
                return;
            }
            StringBuilder xml = new StringBuilder("<xsl:call-template name=\"")
                    .append(chosen.strip()).append("\">");
            boolean any = false;
            for (javafx.scene.control.TextField[] pair : params) {
                if (!pair[0].getText().isBlank()) {
                    xml.append("<xsl:with-param name=\"").append(pair[0].getText().strip())
                       .append("\" select=\"").append(pair[1].getText().strip()).append("\"/>");
                    any = true;
                }
            }
            String result = any
                    ? xml + "</xsl:call-template>"
                    : "<xsl:call-template name=\"" + chosen.strip() + "\"/>";
            appendBand(() -> new dev.stylus.model.OpaqueBand(result));
        });
    }

    /** Named templates (+ their param names) in the document's imports. */
    private java.util.Map<String, java.util.List<String>> discoverNamedTemplates() {
        java.util.Map<String, java.util.List<String>> found = new java.util.LinkedHashMap<>();
        for (Subtemplates.NamedTemplate t : Subtemplates.discover(state)) {
            found.put(t.name(), t.params());
        }
        return found;
    }

    /** xsl:text literal with special characters (emitted as &#x…; references). */
    private void insertXslText() {
        XslTextDialog.edit(getScene().getWindow(), "").ifPresent(text -> {
            if (!text.isEmpty()) {
                insertInline(() -> new dev.stylus.model.XslTextInline(text));
            }
        });
    }

    /** xsl:variable declaration — usable from any ƒx expression as $name. */
    private void insertVariable() {
        javafx.scene.control.Dialog<String[]> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("insert.variable"));
        TextFieldPair pair = new TextFieldPair(
                I18n.t("insert.variable.name"), I18n.t("insert.variable.select"));
        dialog.getDialogPane().setContent(pair);
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        dialog.setResultConverter(button -> button == javafx.scene.control.ButtonType.OK
                ? new String[] {pair.first.getText().strip(), pair.second.getText().strip()}
                : null);
        dialog.showAndWait().ifPresent(nv -> {
            if (!nv[0].isBlank()) {
                appendBand(() -> new dev.stylus.model.OpaqueBand(
                        "<xsl:variable name=\"" + nv[0] + "\" select=\"" + nv[1] + "\"/>"));
            }
        });
    }

    private static final class TextFieldPair extends javafx.scene.layout.VBox {
        final javafx.scene.control.TextField first = new javafx.scene.control.TextField();
        final javafx.scene.control.TextField second = new javafx.scene.control.TextField();

        TextFieldPair(String firstPrompt, String secondPrompt) {
            super(8);
            first.setPromptText(firstPrompt);
            second.setPromptText(secondPrompt);
            getChildren().addAll(first, second);
        }
    }

    /** Date token, engine-aware: xdoxslt:sysdate on BIP, XPath 2 current-date on FOP/Saxon. */
    private void insertDate() {
        String xpath = state.targetEngine() == dev.stylus.engine.api.EngineId.BIP
                ? "xdoxslt:sysdate('YYYY-MM-DD')"
                : "format-date(current-date(), '[Y0001]-[M01]-[D01]')";
        appendBand(() -> new StaticBand(List.of(FieldToken.of(xpath)), StyleProps.NONE));
    }

    private void insertNumber() {
        appendBand(() -> new StaticBand(List.of(new FieldToken(
                I18n.t("insert.field.placeholder"),
                dev.stylus.model.FieldFormat.number("#,##0.00"))), StyleProps.NONE));
    }

    // ---------- actions ----------

    private void insertField() {
        appendBand(() -> new StaticBand(
                List.of(FieldToken.of(I18n.t("insert.field.placeholder"))), StyleProps.NONE));
    }

    private void insertForEach() {
        appendBand(() -> new GroupBand("/", List.of(), List.of()));
    }

    private void insertConditional(boolean withElse) {
        appendBand(() -> new ConditionalBand("true()",
                List.of(StaticBand.text("…")),
                withElse ? List.of(StaticBand.text("…")) : List.of()));
    }

    private void insertXPath() {
        exprEditor.open("", ModelEdits.contextChain(state.document(), state.selection()),
                expr -> appendBand(() -> new StaticBand(
                        List.of(FieldToken.of(expr)), StyleProps.NONE)));
    }

    /** Raw XSLT/FO snippet → opaque band, preserved verbatim (F-6.3 call-template path). */
    private void insertXslt() {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(I18n.t("insert.xslt"));
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea();
        area.setPromptText(I18n.t("insert.xslt.prompt"));
        area.setPrefSize(480, 200);
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        dialog.setResultConverter(button ->
                button == javafx.scene.control.ButtonType.OK ? area.getText() : null);
        dialog.showAndWait().ifPresent(xml -> {
            if (!xml.isBlank()) {
                appendBand(() -> new dev.stylus.model.OpaqueBand(xml.strip()));
            }
        });
    }

    /** Image band (F-7.1/F-7.2): path relativized against the template location. */
    private void insertImage() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(I18n.t("insert.image"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                I18n.t("dialog.filter.images"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.svg"));
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        java.nio.file.Path image = file.toPath();
        if (image.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")
                && offerSvgEmbed(image)) {
            return;
        }
        java.nio.file.Path base = state.templateFile() != null
                ? state.templateFile().getParent() : null;
        String src = base != null && image.startsWith(base)
                ? base.relativize(image).toString().replace('\\', '/')
                : image.toUri().toString();
        appendBand(() -> dev.stylus.model.ImageBand.of(src));
    }

    /**
     * SVG choice (F-7.2): embed as instream-foreign-object (self-contained, needed for BIP
     * servers without file access) or link like any image. True = embedded, handled here.
     */
    private boolean offerSvgEmbed(java.nio.file.Path svg) {
        javafx.scene.control.Alert choice = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        choice.setTitle(I18n.t("insert.image"));
        choice.setHeaderText(I18n.t("insert.svg.question"));
        javafx.scene.control.ButtonType embed =
                new javafx.scene.control.ButtonType(I18n.t("insert.svg.embed"));
        javafx.scene.control.ButtonType link =
                new javafx.scene.control.ButtonType(I18n.t("insert.svg.link"));
        choice.getButtonTypes().setAll(embed, link, javafx.scene.control.ButtonType.CANCEL);
        var answer = choice.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL);
        if (answer == link) {
            return false;
        }
        if (answer != embed) {
            return true; // cancelled — swallow the insert entirely
        }
        try {
            String content = java.nio.file.Files.readString(svg);
            // Strip any XML prolog; the SVG element goes inline into the FO tree.
            content = content.replaceFirst("^\\s*<\\?xml[^>]*\\?>\\s*", "").strip();
            String xml = "<fo:block><fo:instream-foreign-object content-width=\"50mm\">"
                    + content + "</fo:instream-foreign-object></fo:block>";
            appendBand(() -> new dev.stylus.model.OpaqueBand(xml));
        } catch (Exception ex) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    ex.getMessage()).showAndWait();
        }
        return true;
    }

    private void insertPageBreak() {
        appendBand(() -> new dev.stylus.model.OpaqueBand("<fo:block break-before=\"page\"/>"));
    }

    /** Adds a default sort key to the selected group (edit it in the properties panel). */
    private void addSort() {
        if (state.selection() instanceof GroupBand group && group.sortKeys().isEmpty()) {
            GroupBand replacement = new GroupBand(group.selectXPath(),
                    List.of(SortKey.by(".")), group.children());
            if (ModelEdits.replaceBand(state.document(), group, replacement)) {
                state.documentEdited();
                state.select(replacement);
            }
        }
    }

    /** Appends into the selected group band when one is selected, else at document level. */
    private void appendBand(Supplier<Band> factory) {
        Band band = factory.get();
        if (state.selection() instanceof GroupBand group) {
            java.util.ArrayList<Band> children = new java.util.ArrayList<>(group.children());
            children.add(band);
            GroupBand replacement = new GroupBand(group.selectXPath(), group.sortKeys(), children);
            if (ModelEdits.replaceBand(state.document(), group, replacement)) {
                state.documentEdited();
                state.select(band);
                return;
            }
        }
        state.document().bands().add(band);
        state.documentEdited();
        state.select(band);
    }

    // ---------- buttons ----------

    private Button live(String glyphText, String text, String categoryClass, boolean emphasized,
                        Runnable action) {
        Button b = pill(glyphText, text, categoryClass, emphasized);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button stub(String glyphText, String text, String categoryClass) {
        Button b = pill(glyphText, text, categoryClass, false);
        b.setDisable(true); // M6: variable, raw XSLT, number/date tokens, page break
        return b;
    }

    private static Button pill(String glyphText, String text, String categoryClass, boolean emphasized) {
        Button b = new Button(glyphText + "  " + text);
        b.getStyleClass().addAll("pill-button", categoryClass);
        if (emphasized) {
            b.getStyleClass().add("pill-emphasized");
        }
        b.setFocusTraversable(false);
        return b;
    }

    private static Separator divider() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.getStyleClass().add("toolbar-divider");
        return s;
    }
}
