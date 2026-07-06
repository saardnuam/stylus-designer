package dev.stylus.app.ui;

import dev.stylus.app.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * App bar (46px, handoff §1): file name + data source + unsaved dot on the left,
 * Design | Code | Preview segmented switch (F-1.3), bench + theme toggles and
 * Run & Preview (F-1.2) on the right.
 */
final class AppBar extends HBox {

    private final Label fileName = new Label(I18n.t("app.untitledFile"));
    private final Label dataSource = new Label(I18n.t("app.noDataSource"));
    private final Circle unsavedDot = new Circle(3.5);
    private final Map<View, ToggleButton> segments = new EnumMap<>(View.class);

    AppBar(Runnable themeToggle, Consumer<View> onViewSwitch, Runnable onRun, Runnable benchToggle) {
        getStyleClass().add("app-bar");
        setAlignment(Pos.CENTER_LEFT);

        fileName.getStyleClass().add("app-bar-filename");
        dataSource.getStyleClass().add("app-bar-datasource");
        unsavedDot.getStyleClass().add("unsaved-dot");
        unsavedDot.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        ToggleGroup views = new ToggleGroup();
        HBox viewSwitch = new HBox(
                segment(I18n.t("appbar.view.design"), View.DESIGN, views, onViewSwitch, true),
                segment(I18n.t("appbar.view.code"), View.CODE, views, onViewSwitch, false),
                segment(I18n.t("appbar.view.preview"), View.PREVIEW, views, onViewSwitch, false));
        viewSwitch.getStyleClass().add("segmented");

        ToggleButton benchButton = new ToggleButton("▤");
        benchButton.getStyleClass().add("icon-button");
        benchButton.setSelected(true);
        benchButton.setTooltip(new Tooltip(I18n.t("bench.toggle.tooltip")));
        benchButton.setOnAction(e -> benchToggle.run());

        ToggleButton themeButton = new ToggleButton("◐");
        themeButton.getStyleClass().add("icon-button");
        themeButton.setTooltip(new Tooltip(I18n.t("theme.toggle.tooltip")));
        themeButton.setOnAction(e -> themeToggle.run());

        Button run = new Button(I18n.t("appbar.run"));
        run.getStyleClass().add("primary-button");
        run.setOnAction(e -> onRun.run());

        getChildren().addAll(fileName, unsavedDot, dataSource, spacer,
                viewSwitch, benchButton, themeButton, run);
    }

    void setFileName(String name) {
        fileName.setText(name);
    }

    void setDataSourceName(String name) {
        dataSource.setText(name);
    }

    void setUnsaved(boolean unsaved) {
        unsavedDot.setVisible(unsaved);
    }

    /** Programmatic view switch (e.g. jump to Preview after a run). Fires no callback. */
    void selectView(View view) {
        ToggleButton b = segments.get(view);
        if (b != null) {
            b.setSelected(true);
        }
    }

    private ToggleButton segment(String text, View view, ToggleGroup group,
                                 Consumer<View> onSwitch, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("segment");
        b.setToggleGroup(group);
        b.setSelected(selected);
        b.setOnAction(e -> {
            if (!b.isSelected()) {
                b.setSelected(true); // segmented control: one always active
            }
            onSwitch.accept(view);
        });
        segments.put(view, b);
        return b;
    }
}
