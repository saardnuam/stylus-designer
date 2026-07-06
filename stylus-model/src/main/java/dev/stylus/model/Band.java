package dev.stylus.model;

/**
 * One horizontal band on the canvas (F-1.18): static blocks, repeating groups, detail tables,
 * conditional bands — plus opaque bands for anything the designer cannot map (N7).
 * Page header/footer live on {@link ReportDocument} directly (they map to static-content).
 */
public sealed interface Band
        permits StaticBand, GroupBand, TableBand, ConditionalBand, ImageBand, OpaqueBand {
}
