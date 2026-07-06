package dev.stylus.app.ui;

import dev.stylus.app.DesignerState;
import dev.stylus.app.I18n;
import dev.stylus.app.data.XmlSampleTree;
import dev.stylus.app.data.XmlSampleTree.Kind;
import dev.stylus.app.data.XmlSampleTree.TreeNode;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Left pane — data source tree (F-1.10..F-1.15): type glyphs, GROUP badges, search filter,
 * Structure/All-fields tabs, drag sources for canvas drops, footer stats.
 */
final class DataSourcePane extends VBox {

    private final DesignerState state;
    private final TreeView<TreeNode> tree = new TreeView<>();
    private final TextField search = new TextField();
    private final Label footer = new Label(I18n.t("tree.footer.empty"));
    private final Label empty = new Label(I18n.t("tree.empty"));
    private boolean flatMode;

    DataSourcePane(DesignerState state) {
        this.state = state;
        getStyleClass().add("tree-pane");

        Label eyebrow = new Label(I18n.t("tree.header"));
        eyebrow.getStyleClass().add("eyebrow");
        Label badge = new Label(I18n.t("tree.badge.xml"));
        badge.getStyleClass().addAll("badge", "badge-teal");
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        HBox header = new HBox(eyebrow, headSpacer, badge);
        header.getStyleClass().add("pane-header");
        header.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText(I18n.t("tree.search.placeholder"));
        search.getStyleClass().add("tree-search");
        search.textProperty().addListener((obs, old, text) -> rebuild());

        ToggleGroup tabs = new ToggleGroup();
        ToggleButton structure = tab(I18n.t("tree.tab.structure"), tabs, true);
        ToggleButton allFields = tab(I18n.t("tree.tab.allFields"), tabs, false);
        structure.setOnAction(e -> { structure.setSelected(true); flatMode = false; rebuild(); });
        allFields.setOnAction(e -> { allFields.setSelected(true); flatMode = true; rebuild(); });
        HBox tabRow = new HBox(structure, allFields);
        tabRow.getStyleClass().add("segmented");

        tree.setShowRoot(true);
        tree.setCellFactory(v -> new DataCell());
        tree.getStyleClass().add("data-tree");
        VBox.setVgrow(tree, Priority.ALWAYS);
        tree.setVisible(false);

        empty.getStyleClass().add("empty-state");
        empty.setWrapText(true);
        VBox emptyBox = new VBox(empty);
        emptyBox.setAlignment(Pos.CENTER);
        VBox.setVgrow(emptyBox, Priority.ALWAYS);

        footer.getStyleClass().add("pane-footer");

        getChildren().addAll(header, search, tabRow, emptyBox, footer);

        state.onSampleChanged(this::onSample);
    }

    private void onSample() {
        if (state.sample() == null) {
            return;
        }
        if (getChildren().get(3) != tree) {
            getChildren().set(3, tree);
            tree.setVisible(true);
        }
        footer.setText(I18n.t("tree.footer.stats",
                state.sample().totalElements(), state.sample().groupCount()));
        rebuild();
    }

    private void rebuild() {
        XmlSampleTree.Parsed sample = state.sample();
        if (sample == null) {
            return;
        }
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (flatMode) {
            TreeItem<TreeNode> root = new TreeItem<>(sample.root());
            addLeavesFlat(sample.root(), root, query);
            tree.setRoot(root);
            root.setExpanded(true);
        } else {
            TreeItem<TreeNode> root = buildItem(sample.root(), query);
            tree.setRoot(root != null ? root : new TreeItem<>(sample.root()));
            expandAll(tree.getRoot());
        }
    }

    /** Structure view: keep nodes that match or have matching descendants. */
    private TreeItem<TreeNode> buildItem(TreeNode node, String query) {
        boolean selfMatch = query.isEmpty()
                || node.name().toLowerCase(Locale.ROOT).contains(query);
        TreeItem<TreeNode> item = new TreeItem<>(node);
        for (TreeNode child : node.children()) {
            TreeItem<TreeNode> childItem = buildItem(child, query);
            if (childItem != null) {
                item.getChildren().add(childItem);
            }
        }
        return selfMatch || !item.getChildren().isEmpty() ? item : null;
    }

    private void addLeavesFlat(TreeNode node, TreeItem<TreeNode> root, String query) {
        for (TreeNode child : node.children()) {
            if (child.isLeaf()) {
                if (query.isEmpty() || child.name().toLowerCase(Locale.ROOT).contains(query)) {
                    root.getChildren().add(new TreeItem<>(child));
                }
            } else {
                addLeavesFlat(child, root, query);
            }
        }
    }

    private void expandAll(TreeItem<TreeNode> item) {
        item.setExpanded(true);
        item.getChildren().forEach(this::expandAll);
    }

    private static ToggleButton tab(String text, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("segment");
        b.setToggleGroup(group);
        b.setSelected(selected);
        return b;
    }

    /** Tree cell: kind glyph + name + GROUP badge + drag handle; drag source (F-1.13/F-1.14). */
    private final class DataCell extends TreeCell<TreeNode> {

        @Override
        protected void updateItem(TreeNode node, boolean isEmpty) {
            super.updateItem(node, isEmpty);
            if (isEmpty || node == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label glyph = new Label(glyphFor(node));
            glyph.getStyleClass().addAll("tree-glyph", glyphClass(node));

            Label name = new Label(node.name());
            name.getStyleClass().add("tree-name");

            HBox row = new HBox(6, glyph, name);
            row.setAlignment(Pos.CENTER_LEFT);

            if (node.repeating()) {
                Label group = new Label(I18n.t("tree.groupBadge"));
                group.getStyleClass().addAll("badge", "badge-group");
                Label count = new Label("↻ " + node.occurrences() + "×");
                count.getStyleClass().add("tree-count");
                row.getChildren().addAll(group, count);
                row.getStyleClass().add("tree-row-group");
            }
            if (node.isLeaf()) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label handle = new Label("⋮⋮");
                handle.getStyleClass().add("tree-drag-handle");
                row.getChildren().addAll(spacer, handle);
            }
            setGraphic(row);
            setText(null);

            setOnDragDetected(e -> {
                TreeNode item = getItem();
                if (item == null || (!item.isLeaf() && !item.repeating())) {
                    return;
                }
                Dragboard db = startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(item.isLeaf()
                        ? DragPayload.field(item)
                        : DragPayload.group(item, firstLeafOf(item)));
                db.setContent(content);
                e.consume();
            });
        }

        private String firstLeafOf(TreeNode group) {
            for (TreeNode child : group.children()) {
                if (child.isLeaf() && child.kind() != Kind.ATTRIBUTE) {
                    return child.xpath();
                }
            }
            return null;
        }

        private String glyphFor(TreeNode node) {
            return switch (node.kind()) {
                case ELEMENT -> "▤";
                case TEXT -> "Aa";
                case NUMBER -> "#";
                case DATE -> "◷";
                case ATTRIBUTE -> "@";
            };
        }

        private String glyphClass(TreeNode node) {
            return switch (node.kind()) {
                case ELEMENT -> "glyph-element";
                case TEXT -> "glyph-text";
                case NUMBER, ATTRIBUTE -> "glyph-data";
                case DATE -> "glyph-date";
            };
        }
    }
}
