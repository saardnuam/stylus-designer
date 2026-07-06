package dev.stylus.model;

import java.util.List;
import java.util.Objects;

/**
 * A detail table (F-1.18/F-1.24): header row from column labels, one body row per node of
 * {@code rowXPath} ("repeats per X").
 */
public record TableBand(
        String rowXPath,
        List<TableColumn> columns) implements Band {

    public TableBand {
        Objects.requireNonNull(rowXPath);
        columns = List.copyOf(columns);
    }
}
