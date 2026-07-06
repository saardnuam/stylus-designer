package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.app.expr.ExpressionValidator;
import dev.stylus.model.Band;
import dev.stylus.model.ConditionalBand;
import dev.stylus.model.DataType;
import dev.stylus.model.FieldFormat;
import dev.stylus.model.FieldToken;
import dev.stylus.model.GroupBand;
import dev.stylus.model.InlineNode;
import dev.stylus.model.ModelEdits;
import dev.stylus.model.OpaqueBand;
import dev.stylus.model.SortKey;
import dev.stylus.model.StaticBand;
import dev.stylus.model.StyleProps;
import dev.stylus.model.StyleRule;
import dev.stylus.model.TableBand;
import dev.stylus.model.TableColumn;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Right pane — properties panel with live editing (M5, F-1.25/F-1.26): three working tabs.
 * Properties = data binding + ƒx editor, format, sort, condition, delete. Style = static-band
 * style controls + the conditional-format rule builder (F-1.29). Data = live sample values in
 * the selection's group context with rule-match highlighting (F-1.28). Edits replace the
 * immutable node via {@link ModelEdits} and re-select the replacement.
 */
final class PropertiesPane extends VBox {

    private static final int PROBE_ROWS = 10;

    private final DesignerState state;
    private final ExpressionEditorOverlay exprEditor;
    private final ExpressionValidator validator = new ExpressionValidator();
    private final Label glyph = new Label("");
    private final Label title = new Label(I18n.t("props.header.empty"));
    private final Label foChip = new Label("");
    private final Label subtitle = new Label("");
    private final HBox breadcrumb = new HBox(4);
    private final VBox body = new VBox(10);
    private final List<ToggleButton> tabButtons = new ArrayList<>();
    private int activeTab = 0;

    PropertiesPane(DesignerState state, ExpressionEditorOverlay exprEditor) {
        this.state = state;
        this.exprEditor = exprEditor;
        getStyleClass().add("props-pane");

        glyph.getStyleClass().add("props-glyph");
        title.getStyleClass().add("props-title");
        foChip.getStyleClass().add("fo-chip");
        subtitle.getStyleClass().add("props-subtitle");
        HBox titleRow = new HBox(6, title, foChip);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(1, titleRow, subtitle);
        HBox header = new HBox(8, glyph, titles);
        header.getStyleClass().add("pane-header");
        header.setAlignment(Pos.CENTER_LEFT);
        breadcrumb.getStyleClass().add("fo-breadcrumb");
        breadcrumb.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup tabs = new ToggleGroup();
        HBox tabRow = new HBox(
                tab(I18n.t("props.tab.properties"), tabs, 0),
                tab(I18n.t("props.tab.style"), tabs, 1),
                tab(I18n.t("props.tab.data"), tabs, 2));
        tabRow.getStyleClass().add("segmented");

        body.getStyleClass().add("props-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(header, tabRow, breadcrumb, body);

        state.onSelectionChanged(this::refresh);
        refresh();
    }

    private void refresh() {
        body.getChildren().clear();
        breadcrumb.getChildren().clear();
        Object selection = state.selection();

        if (selection == null) {
            header("·", I18n.t("props.header.empty"), "");
            foChip.setText("");
            foChip.setVisible(false);
            Label empty = new Label(I18n.t("props.empty"));
            empty.getStyleClass().add("empty-state");
            empty.setWrapText(true);
            VBox emptyBox = new VBox(empty);
            emptyBox.setAlignment(Pos.CENTER);
            VBox.setVgrow(emptyBox, Priority.ALWAYS);
            body.getChildren().add(emptyBox);
            return;
        }

        // FO element chip (F-1.46) + ancestor breadcrumb (F-1.49)
        foChip.setText(elementNameOf(selection));
        foChip.setVisible(true);
        buildBreadcrumb(selection);

        switch (activeTab) {
            case 0 -> propertiesTab(selection);
            case 1 -> styleTab(selection);
            default -> dataTab(selection);
        }
        addEngineBadge(selection);
    }

    /** Clickable ancestor chain: page-sequence ▸ xsl:for-each ▸ fo:block ▸ … (F-1.49). */
    private void buildBreadcrumb(Object selection) {
        List<Band> ancestors = ModelEdits.ancestorsOf(state.document(), selection);
        for (Band ancestor : ancestors) {
            Label crumb = new Label(elementNameOf(ancestor));
            crumb.getStyleClass().add("crumb");
            crumb.setOnMouseClicked(e -> state.select(ancestor));
            Label sep = new Label("▸");
            sep.getStyleClass().add("crumb-sep");
            breadcrumb.getChildren().addAll(crumb, sep);
        }
        Label current = new Label(elementNameOf(selection));
        current.getStyleClass().addAll("crumb", "crumb-current");
        breadcrumb.getChildren().add(current);
    }

    /** Engine-support badge (F-1.50): red/amber note when the target engine balks. */
    private void addEngineBadge(Object selection) {
        var matrix = state.targetCapabilities();
        String element = elementNameOf(selection);
        if (matrix == null || !element.startsWith("fo:")) {
            return;
        }
        var support = matrix.supportFor(element.substring(3));
        String engine = state.targetEngine().name();
        if (support == dev.stylus.engine.api.ElementSupport.UNSUPPORTED) {
            Label badge = new Label(I18n.t("props.engine.unsupported", element, engine));
            badge.getStyleClass().add("engine-badge-error");
            badge.setWrapText(true);
            body.getChildren().add(0, badge);
        } else if (support == dev.stylus.engine.api.ElementSupport.PARTIAL) {
            Label badge = new Label(I18n.t("props.engine.partial", element, engine));
            badge.getStyleClass().add("engine-badge-warn");
            badge.setWrapText(true);
            body.getChildren().add(0, badge);
        }
    }

    /** The concrete FO/XSL element a selection stands for — the §1.8 identity. */
    static String elementNameOf(Object selection) {
        return switch (selection) {
            case StaticBand s -> "fo:block";
            case TableBand t -> "fo:table";
            case GroupBand g -> "xsl:for-each";
            case ConditionalBand c -> c.hasElse() ? "xsl:choose" : "xsl:if";
            case dev.stylus.model.ImageBand i -> "fo:external-graphic";
            case OpaqueBand o -> firstTagOf(o.xml());
            case FieldToken f -> "xsl:value-of";
            case dev.stylus.model.PageNumberToken p -> "fo:page-number";
            case dev.stylus.model.PageCountToken p -> "fo:page-number-citation";
            case dev.stylus.model.TextRun tr -> "#text";
            case dev.stylus.model.XslTextInline x -> "xsl:text";
            case dev.stylus.model.OpaqueInline o -> firstTagOf(o.xml());
            default -> "";
        };
    }

    private static String firstTagOf(String xml) {
        var m = java.util.regex.Pattern.compile("<\\s*([\\w:.-]+)").matcher(xml);
        return m.find() ? m.group(1) : "xml";
    }

    // ---------- Properties tab (per-kind editors) ----------

    private void propertiesTab(Object selection) {
        switch (selection) {
            case FieldToken field -> editField(field);
            case GroupBand group -> editGroup(group);
            case TableBand table -> editTable(table);
            case ConditionalBand cond -> editConditional(cond);
            case StaticBand block -> editStatic(block);
            case dev.stylus.model.TextRun text -> editTextRun(text);
            case dev.stylus.model.XslTextInline literal -> editXslText(literal);
            case dev.stylus.model.ImageBand image -> editImage(image);
            case OpaqueBand opaque -> editOpaque(opaque);
            default -> { }
        }
    }

    private void editField(FieldToken field) {
        header("#", I18n.t("props.type.field"),
                field.format().dataType().name().toLowerCase());

        TextField xpath = textField(field.xpath());
        Button fx = fxButton(field.xpath(), applied ->
                replaceToken(field, new FieldToken(applied, field.format())));
        xpath.setOnAction(e -> replaceToken(field,
                new FieldToken(xpath.getText().strip(), field.format())));
        body.getChildren().add(section("props.binding", row(grow(xpath), fx),
                hint("props.binding.hint")));

        ComboBox<DataType> type = new ComboBox<>();
        type.getItems().setAll(DataType.values());
        type.setValue(field.format().dataType());
        TextField mask = textField(field.format().mask() == null ? "" : field.format().mask());
        mask.setPromptText("#,##0.00");
        mask.setDisable(field.format().dataType() != DataType.NUMBER);
        type.valueProperty().addListener((obs, old, t) -> replaceToken(field,
                new FieldToken(field.xpath(), new FieldFormat(t,
                        t == DataType.NUMBER ? emptyToNull(mask.getText()) : null))));
        mask.setOnAction(e -> replaceToken(field, new FieldToken(field.xpath(),
                new FieldFormat(field.format().dataType(), emptyToNull(mask.getText())))));
        body.getChildren().add(section("props.format", row(type, grow(mask))));

        body.getChildren().add(deleteButton("props.delete.token",
                () -> ModelEdits.replaceInline(state.document(), field, null)));
    }

    private void editGroup(GroupBand group) {
        header("⟳", I18n.t("props.type.group"), "for-each");

        TextField path = textField(group.selectXPath());
        Button fx = fxButton(group.selectXPath(), applied -> replaceBand(group,
                new GroupBand(applied, group.sortKeys(), group.children())));
        path.setOnAction(e -> replaceBand(group,
                new GroupBand(path.getText().strip(), group.sortKeys(), group.children())));
        body.getChildren().add(section("props.binding", row(grow(path), fx)));

        SortKey sort = group.sortKeys().isEmpty() ? null : group.sortKeys().get(0);
        TextField sortField = textField(sort == null ? "" : sort.selectXPath());
        sortField.setPromptText(I18n.t("props.sort.prompt"));
        ToggleButton asc = new ToggleButton(sort == null || sort.ascending() ? "▲" : "▼");
        asc.getStyleClass().add("bench-button");
        asc.setSelected(sort == null || sort.ascending());
        asc.setOnAction(e -> asc.setText(asc.isSelected() ? "▲" : "▼"));
        Runnable applySort = () -> {
            String expr = sortField.getText().strip();
            List<SortKey> keys = expr.isEmpty()
                    ? List.of()
                    : List.of(new SortKey(expr, asc.isSelected(), DataType.TEXT));
            replaceBand(group, new GroupBand(group.selectXPath(), keys, group.children()));
        };
        sortField.setOnAction(e -> applySort.run());
        asc.selectedProperty().addListener((obs, old, v) -> applySort.run());
        body.getChildren().add(section("props.sort", row(grow(sortField), asc)));

        body.getChildren().add(deleteButton("props.delete.band",
                () -> ModelEdits.removeBand(state.document(), group)));
    }

    private void editTable(TableBand table) {
        header("⊞", I18n.t("props.type.table"), "detail");

        TextField path = textField(table.rowXPath());
        Button fx = fxButton(table.rowXPath(), applied ->
                replaceBand(table, new TableBand(applied, table.columns())));
        path.setOnAction(e -> replaceBand(table,
                new TableBand(path.getText().strip(), table.columns())));
        body.getChildren().add(section("props.binding", row(grow(path), fx)));
        body.getChildren().add(section("props.columns",
                value(String.valueOf(table.columns().size()))));

        body.getChildren().add(deleteButton("props.delete.band",
                () -> ModelEdits.removeBand(state.document(), table)));
    }

    private void editConditional(ConditionalBand cond) {
        header("if", I18n.t("props.type.condition"),
                cond.hasElse() ? "choose / otherwise" : "if");

        TextField expr = textField(cond.testExpr());
        Button fx = fxButton(cond.testExpr(), applied -> replaceBand(cond,
                new ConditionalBand(applied, cond.then(), cond.otherwise())));
        expr.setOnAction(e -> replaceBand(cond,
                new ConditionalBand(expr.getText().strip(), cond.then(), cond.otherwise())));
        body.getChildren().add(section("props.condition", row(grow(expr), fx)));

        body.getChildren().add(deleteButton("props.delete.band",
                () -> ModelEdits.removeBand(state.document(), cond)));
    }

    private void editStatic(StaticBand block) {
        header("¶", I18n.t("props.type.static"), "block");

        body.getChildren().add(section("props.content",
                value(String.valueOf(block.content().size()))));

        Label rulesBadge = new Label(I18n.t("props.rules.count", block.rules().size()));
        rulesBadge.getStyleClass().add("rules-badge");
        rulesBadge.setOnMouseClicked(e -> switchTab(1));
        body.getChildren().add(section("props.rules", rulesBadge));

        body.getChildren().add(deleteButton("props.delete.band",
                () -> ModelEdits.removeBand(state.document(), block)));
    }

    private void editTextRun(dev.stylus.model.TextRun text) {
        header("a", I18n.t("props.type.text"), "#text");
        TextField field = textField(text.text());
        field.setOnAction(e -> replaceToken(text,
                new dev.stylus.model.TextRun(field.getText())));
        body.getChildren().add(section("props.text", row(grow(field))));
        body.getChildren().add(deleteButton("props.delete.token",
                () -> ModelEdits.replaceInline(state.document(), text, null)));
    }

    private void editXslText(dev.stylus.model.XslTextInline literal) {
        header("„a“", I18n.t("props.type.xslText"), "xsl:text");

        Label code = value("<xsl:text>"
                + dev.stylus.codegen.XslWriter.ncrEscape(literal.text()) + "</xsl:text>");
        code.setWrapText(true);
        Button edit = new Button(I18n.t("props.text.edit"));
        edit.getStyleClass().add("bench-button");
        edit.setOnAction(e -> XslTextDialog.edit(getScene().getWindow(), literal.text())
                .ifPresent(text -> replaceToken(literal,
                        new dev.stylus.model.XslTextInline(text))));
        body.getChildren().add(section("props.text", code, edit));

        body.getChildren().add(deleteButton("props.delete.token",
                () -> ModelEdits.replaceInline(state.document(), literal, null)));
    }

    private void editImage(dev.stylus.model.ImageBand image) {
        header("⧉", I18n.t("props.type.image"), "external-graphic");

        TextField src = textField(image.src());
        src.setOnAction(e -> replaceBand(image, new dev.stylus.model.ImageBand(
                src.getText().strip(), image.widthMm(), image.heightMm())));
        body.getChildren().add(section("props.image.src", row(grow(src)),
                hint("props.image.src.hint")));

        TextField width = textField(image.widthMm() == null ? "" : mmText(image.widthMm()));
        width.setPromptText("mm");
        width.setPrefWidth(64);
        TextField height = textField(image.heightMm() == null ? "" : mmText(image.heightMm()));
        height.setPromptText("mm");
        height.setPrefWidth(64);
        Runnable applySize = () -> replaceBand(image, new dev.stylus.model.ImageBand(
                image.src(), parseMm(width.getText()), parseMm(height.getText())));
        width.setOnAction(e -> applySize.run());
        height.setOnAction(e -> applySize.run());
        body.getChildren().add(section("props.image.size", row(width, height)));

        body.getChildren().add(deleteButton("props.delete.band",
                () -> ModelEdits.removeBand(state.document(), image)));
    }

    private static String mmText(Double value) {
        return value == Math.rint(value) ? Long.toString(value.longValue()) : value.toString();
    }

    private static Double parseMm(String text) {
        try {
            return text == null || text.isBlank() ? null : Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void editOpaque(OpaqueBand opaque) {
        header("</>", I18n.t("props.type.opaque"), "xslt");
        Label note = new Label(I18n.t("props.opaque.note"));
        note.getStyleClass().add("props-note");
        note.setWrapText(true);
        body.getChildren().add(note);

        // Raw editor (F-1.50): the element's exact markup, editable in place.
        javafx.scene.control.TextArea xml = new javafx.scene.control.TextArea(opaque.xml());
        xml.setPrefRowCount(8);
        xml.setWrapText(true);
        xml.getStyleClass().add("raw-xml-editor");
        Button apply = new Button(I18n.t("expr.apply"));
        apply.getStyleClass().add("bench-button");
        apply.setOnAction(e -> {
            if (!xml.getText().isBlank()) {
                replaceBand(opaque, new OpaqueBand(xml.getText()));
            }
        });
        body.getChildren().add(section("props.rawXml", xml, apply));

        body.getChildren().add(deleteButton("props.delete.band",
                () -> ModelEdits.removeBand(state.document(), opaque)));
    }

    // ---------- Style tab (F-1.27 subset + F-1.29 rule builder) ----------

    private void styleTab(Object selection) {
        if (!(selection instanceof StaticBand block)) {
            headerFor(selection);
            body.getChildren().add(hint("props.style.none"));
            return;
        }
        header("¶", I18n.t("props.type.static"), "block");
        StyleProps style = block.style();

        ToggleButton bold = styleToggle("B", Boolean.TRUE.equals(style.bold()));
        bold.setStyle("-fx-font-weight: bold;");
        ToggleButton italic = styleToggle("I", Boolean.TRUE.equals(style.italic()));
        italic.setStyle("-fx-font-style: italic;");
        TextField size = textField(style.fontSizePt() == null ? "" : style.fontSizePt());
        size.setPromptText("pt");
        size.setPrefWidth(52);
        TextField color = textField(style.color() == null ? "" : style.color());
        color.setPromptText("#RRGGBB");
        color.setPrefWidth(84);
        ComboBox<String> align = new ComboBox<>();
        align.getItems().setAll("", "left", "center", "right");
        align.setValue(style.textAlign() == null ? "" : style.textAlign());

        ComboBox<String> family = new ComboBox<>();
        family.setEditable(true);
        family.getItems().setAll("", "Hanken Grotesk", "Helvetica", "Times New Roman",
                "Courier New", "Arial");
        family.setValue(style.fontFamily() == null ? "" : style.fontFamily());
        family.setPrefWidth(150);
        ToggleButton underline = styleToggle("U", Boolean.TRUE.equals(style.underline()));
        ToggleButton strike = styleToggle("S", Boolean.TRUE.equals(style.strike()));
        TextField background = textField(style.background() == null ? "" : style.background());
        background.setPromptText(I18n.t("props.style.background"));
        background.setPrefWidth(84);
        TextField lineHeight = textField(style.lineHeight() == null ? "" : style.lineHeight());
        lineHeight.setPromptText(I18n.t("props.style.lineHeight"));
        lineHeight.setPrefWidth(56);

        Runnable apply = () -> replaceBand(block, new StaticBand(block.content(), new StyleProps(
                bold.isSelected() ? true : null,
                italic.isSelected() ? true : null,
                emptyToNull(size.getText()),
                emptyToNull(color.getText()),
                emptyToNull(align.getValue()),
                emptyToNull(family.getValue()),
                emptyToNull(background.getText()),
                underline.isSelected() ? true : null,
                strike.isSelected() ? true : null,
                emptyToNull(lineHeight.getText())), block.rules()));
        bold.setOnAction(e -> apply.run());
        italic.setOnAction(e -> apply.run());
        size.setOnAction(e -> apply.run());
        color.setOnAction(e -> apply.run());
        align.valueProperty().addListener((obs, old, v) -> apply.run());
        family.setOnAction(e -> apply.run());
        underline.setOnAction(e -> apply.run());
        strike.setOnAction(e -> apply.run());
        background.setOnAction(e -> apply.run());
        lineHeight.setOnAction(e -> apply.run());

        body.getChildren().add(section("props.style",
                row(bold, italic, size, color, align),
                row(family, underline, strike, background, lineHeight)));

        body.getChildren().add(rulesBuilder(block));
    }

    /** The F-1.29 rule builder: condition + style effects per rule, add/remove. */
    private VBox rulesBuilder(StaticBand block) {
        List<Supplier<StyleRule>> collectors = new ArrayList<>();
        Runnable apply = () -> replaceBand(block, new StaticBand(block.content(), block.style(),
                collectors.stream().map(Supplier::get).toList()));

        VBox rows = new VBox(6);
        for (StyleRule rule : block.rules()) {
            TextField test = textField(rule.testExpr());
            test.setPromptText(I18n.t("props.rules.prompt"));
            Button fx = new Button("ƒx");
            fx.getStyleClass().add("fx-button");
            fx.setOnAction(e -> exprEditor.open(test.getText(),
                    ModelEdits.contextChain(state.document(), block), applied -> {
                        test.setText(applied);
                        apply.run();
                    }));
            ToggleButton bold = styleToggle("B", Boolean.TRUE.equals(rule.style().bold()));
            ToggleButton italic = styleToggle("I", Boolean.TRUE.equals(rule.style().italic()));
            TextField color = textField(rule.style().color() == null ? "" : rule.style().color());
            color.setPromptText("#RRGGBB");
            color.setPrefWidth(76);
            Button remove = new Button("✕");
            remove.getStyleClass().add("danger-button");

            Supplier<StyleRule> collector = () -> new StyleRule(test.getText().strip(),
                    new StyleProps(bold.isSelected() ? true : null,
                            italic.isSelected() ? true : null,
                            rule.style().fontSizePt(),
                            emptyToNull(color.getText()),
                            rule.style().textAlign(),
                            rule.style().fontFamily(),
                            rule.style().background(),
                            rule.style().underline(),
                            rule.style().strike(),
                            rule.style().lineHeight()));
            collectors.add(collector);
            remove.setOnAction(e -> {
                collectors.remove(collector);
                apply.run();
            });
            test.setOnAction(e -> apply.run());
            bold.setOnAction(e -> apply.run());
            italic.setOnAction(e -> apply.run());
            color.setOnAction(e -> apply.run());

            rows.getChildren().add(row(grow(test), fx, bold, italic, color, remove));
        }

        Button add = new Button(I18n.t("props.rules.add"));
        add.getStyleClass().add("bench-button");
        add.setOnAction(e -> {
            List<StyleRule> extended = new ArrayList<>(block.rules());
            extended.add(new StyleRule("true()", StyleProps.ofBold()));
            replaceBand(block, new StaticBand(block.content(), block.style(), extended));
        });
        rows.getChildren().add(add);

        return section("props.rules", rows);
    }

    // ---------- Data tab (F-1.28 live sample values) ----------

    private record ProbeSpec(String valueExpr, String testExpr, List<String> chain) { }

    private void dataTab(Object selection) {
        headerFor(selection);
        Path sample = state.sampleFile();
        if (sample == null) {
            body.getChildren().add(hint("props.data.noSample"));
            return;
        }
        ProbeSpec spec = probeSpecFor(selection);
        if (spec == null) {
            body.getChildren().add(hint("props.data.noBinding"));
            return;
        }
        ExpressionValidator.DataProbe probe =
                validator.probe(spec.valueExpr(), spec.testExpr(), sample, spec.chain(), PROBE_ROWS);
        if (probe == null) {
            body.getChildren().add(hint("props.data.noSample"));
            return;
        }

        body.getChildren().add(section("props.data.values",
                value(I18n.t("props.data.summary", probe.total()))));

        VBox rows = new VBox(3);
        long hits = 0;
        for (ExpressionValidator.RowSample row : probe.rows()) {
            Label label = new Label((row.matched() ? "✓ " : "· ") + row.value());
            label.getStyleClass().add("data-row");
            if (row.matched()) {
                label.getStyleClass().add("data-row-hit");
                hits++;
            }
            rows.getChildren().add(label);
        }
        body.getChildren().add(rows);

        if (spec.testExpr() != null) {
            body.getChildren().add(value(
                    I18n.t("props.data.matches", hits, probe.rows().size())));
        }
    }

    /** Maps the selection to what the data tab evaluates; null = nothing bindable. */
    private ProbeSpec probeSpecFor(Object selection) {
        var doc = state.document();
        return switch (selection) {
            case FieldToken f -> new ProbeSpec(f.xpath(), orExpr(rulesAround(f)),
                    ModelEdits.contextChain(doc, f));
            case GroupBand g -> {
                List<String> chain = new ArrayList<>(ModelEdits.contextChain(doc, g));
                chain.add(g.selectXPath());
                yield new ProbeSpec("normalize-space(.)", null, chain);
            }
            case TableBand t -> {
                List<String> chain = new ArrayList<>(ModelEdits.contextChain(doc, t));
                chain.add(t.rowXPath());
                String rules = orExpr(t.columns().stream()
                        .flatMap(c -> c.rules().stream()).toList());
                yield new ProbeSpec("normalize-space(.)", rules, chain);
            }
            case ConditionalBand c -> new ProbeSpec("normalize-space(.)", c.testExpr(),
                    ModelEdits.contextChain(doc, c));
            case StaticBand s -> {
                List<String> chain = ModelEdits.contextChain(doc, s);
                if (chain.isEmpty() && s.rules().isEmpty()) {
                    yield null;
                }
                yield new ProbeSpec("normalize-space(.)", orExpr(s.rules()), chain);
            }
            default -> null;
        };
    }

    /** Conditional rules of the static band or table column containing this token. */
    private List<StyleRule> rulesAround(FieldToken token) {
        return rulesAround(state.document().bands(), token);
    }

    private static List<StyleRule> rulesAround(List<Band> bands, FieldToken token) {
        for (Band band : bands) {
            switch (band) {
                case StaticBand s -> {
                    if (s.content().stream().anyMatch(n -> n == token)) {
                        return s.rules();
                    }
                }
                case TableBand t -> {
                    for (TableColumn column : t.columns()) {
                        if (column.cell().stream().anyMatch(n -> n == token)) {
                            return column.rules();
                        }
                    }
                }
                case dev.stylus.model.ImageBand img -> { }
                case GroupBand g -> {
                    List<StyleRule> found = rulesAround(g.children(), token);
                    if (!found.isEmpty()) {
                        return found;
                    }
                }
                case ConditionalBand c -> {
                    List<StyleRule> found = rulesAround(c.then(), token);
                    if (found.isEmpty()) {
                        found = rulesAround(c.otherwise(), token);
                    }
                    if (!found.isEmpty()) {
                        return found;
                    }
                }
                case OpaqueBand o -> { }
            }
        }
        return List.of();
    }

    private static String orExpr(List<StyleRule> rules) {
        if (rules.isEmpty()) {
            return null;
        }
        return rules.stream()
                .filter(r -> !r.testExpr().isBlank())
                .map(r -> "(" + r.testExpr() + ")")
                .collect(Collectors.joining(" or "));
    }

    // ---------- edit plumbing ----------

    private void replaceToken(InlineNode target, InlineNode replacement) {
        if (ModelEdits.replaceInline(state.document(), target, replacement)) {
            state.documentEdited();
            state.select(replacement);
        }
    }

    private void replaceBand(Band target, Band replacement) {
        if (ModelEdits.replaceBand(state.document(), target, replacement)) {
            state.documentEdited();
            state.select(replacement);
        }
    }

    private Button deleteButton(String key, Runnable action) {
        Button delete = new Button(I18n.t(key));
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> {
            action.run();
            state.documentEdited();
            state.select(null);
        });
        return delete;
    }

    private Button fxButton(String initial, java.util.function.Consumer<String> onApply) {
        Button fx = new Button("ƒx");
        fx.getStyleClass().add("fx-button");
        fx.setOnAction(e -> exprEditor.open(initial,
                ModelEdits.contextChain(state.document(), state.selection()), onApply));
        return fx;
    }

    // ---------- small UI helpers ----------

    private void header(String g, String t, String s) {
        glyph.setText(g);
        title.setText(t);
        subtitle.setText(s);
    }

    private void headerFor(Object selection) {
        switch (selection) {
            case FieldToken f -> header("#", I18n.t("props.type.field"),
                    f.format().dataType().name().toLowerCase());
            case GroupBand g -> header("⟳", I18n.t("props.type.group"), "for-each");
            case TableBand t -> header("⊞", I18n.t("props.type.table"), "detail");
            case ConditionalBand c -> header("if", I18n.t("props.type.condition"),
                    c.hasElse() ? "choose / otherwise" : "if");
            case StaticBand s -> header("¶", I18n.t("props.type.static"), "block");
            case dev.stylus.model.TextRun tr -> header("a", I18n.t("props.type.text"), "#text");
            case dev.stylus.model.XslTextInline x -> header("„a“", I18n.t("props.type.xslText"),
                    "xsl:text");
            case dev.stylus.model.ImageBand img -> header("⧉", I18n.t("props.type.image"),
                    "external-graphic");
            case OpaqueBand o -> header("</>", I18n.t("props.type.opaque"), "xslt");
            default -> header("·", I18n.t("props.header.empty"), "");
        }
    }

    private void switchTab(int index) {
        activeTab = index;
        tabButtons.get(index).setSelected(true);
        refresh();
    }

    private VBox section(String labelKey, javafx.scene.Node... nodes) {
        Label label = new Label(I18n.t(labelKey));
        label.getStyleClass().add("eyebrow");
        VBox box = new VBox(4);
        box.getChildren().add(label);
        box.getChildren().addAll(nodes);
        return box;
    }

    private static HBox row(javafx.scene.Node... nodes) {
        HBox row = new HBox(6, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static TextField textField(String text) {
        TextField field = new TextField(text);
        field.getStyleClass().add("props-field");
        return field;
    }

    private static TextField grow(TextField field) {
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private static ToggleButton styleToggle(String text, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("bench-button");
        b.setSelected(selected);
        return b;
    }

    private Label value(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("props-value");
        return label;
    }

    private Label hint(String key) {
        Label label = new Label(I18n.t(key));
        label.getStyleClass().add("props-note");
        label.setWrapText(true);
        return label;
    }

    private static String emptyToNull(String s) {
        return s == null || s.strip().isEmpty() ? null : s.strip();
    }

    private ToggleButton tab(String text, ToggleGroup group, int index) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("segment");
        b.setToggleGroup(group);
        b.setSelected(index == activeTab);
        b.setOnAction(e -> {
            if (!b.isSelected()) {
                b.setSelected(true);
            }
            if (activeTab != index) {
                activeTab = index;
                refresh();
            }
        });
        tabButtons.add(b);
        return b;
    }
}
