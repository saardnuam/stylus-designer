package dev.stylus.engine.api;

/**
 * Every output format either engine can produce (F-4.4 FOP, F-4.5 BIP).
 * Each engine advertises the subset it supports via {@link ReportEngine#supportedFormats()}.
 */
public enum OutputFormat {
    PDF("pdf"),
    /** Unlimited-width web output — produced by the XSLT stage directly, no FO pass (F-4.2). */
    HTML("html"),
    RTF("rtf"),
    XLSX("xlsx"),
    PPTX("pptx"),
    MHTML("mhtml"),
    ETEXT("txt"),
    /** Compressed PDF (BIP). */
    PDFZ("pdfz"),
    /** Intermediate XSL-FO — the transform result before rendering (F-5.11 debugging). */
    FO("fo"),
    POSTSCRIPT("ps"),
    PCL("pcl"),
    AFP("afp"),
    PNG("png"),
    TIFF("tif"),
    TEXT("txt"),
    /** FOP intermediate format (area tree XML). */
    IF("if.xml");

    private final String extension;

    OutputFormat(String extension) {
        this.extension = extension;
    }

    /** Default file extension, without leading dot. */
    public String extension() {
        return extension;
    }
}
