package dev.stylus.app.ui;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The drop-position insertion caret (F-1.21): a thin accent line shown in the gap where a drag
 * would insert. One instance per drop container; the same index math is reused on drop.
 */
final class DropCaret {

    private final Region line = new Region();
    private int lastIndex = -1;

    DropCaret() {
        line.getStyleClass().add("drop-caret");
        line.setMinHeight(3);
        line.setMaxHeight(3);
        line.setMouseTransparent(true);
    }

    /**
     * Insertion index for a drag at {@code y} (container coords) among the item nodes.
     * {@code itemNodes} must never contain the caret line itself.
     */
    int indexFor(List<Node> itemNodes, double y) {
        int index = 0;
        for (Node node : itemNodes) {
            var bounds = node.getBoundsInParent();
            if (y < bounds.getMinY() + bounds.getHeight() / 2) {
                return index;
            }
            index++;
        }
        return index;
    }

    /** Shows the caret at the gap for {@code index}; itemNodes must be children of container. */
    void showAt(VBox container, List<Node> itemNodes, int index) {
        if (index == lastIndex && container.getChildren().contains(line)) {
            return;
        }
        container.getChildren().remove(line);
        int childPos;
        if (itemNodes.isEmpty()) {
            childPos = container.getChildren().size();
        } else if (index < itemNodes.size()) {
            childPos = container.getChildren().indexOf(itemNodes.get(index));
        } else {
            childPos = container.getChildren().indexOf(itemNodes.get(itemNodes.size() - 1)) + 1;
        }
        if (childPos < 0) {
            childPos = container.getChildren().size();
        }
        container.getChildren().add(childPos, line);
        lastIndex = index;
    }

    void hide(VBox container) {
        container.getChildren().remove(line);
        lastIndex = -1;
    }
}
