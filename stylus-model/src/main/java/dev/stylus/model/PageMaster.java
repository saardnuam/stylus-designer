package dev.stylus.model;

import java.util.Objects;

/** One named fo:simple-page-master in a multi-master layout (F-2.26). */
public record PageMaster(String name, PageSetup geometry) {

    public PageMaster {
        Objects.requireNonNull(name);
        Objects.requireNonNull(geometry);
    }
}
