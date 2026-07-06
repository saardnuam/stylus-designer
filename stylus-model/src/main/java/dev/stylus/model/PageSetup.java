package dev.stylus.model;

/**
 * Page geometry for pixel-perfect mode — the model behind one fo:simple-page-master plus its
 * regions (F-2.7). M6 extends this into the full layout-master-set with conditional page
 * masters (F-2.26/F-2.27); until then a document has exactly one master.
 * All lengths in millimetres.
 */
public record PageSetup(
        double pageWidthMm,
        double pageHeightMm,
        double marginTopMm,
        double marginBottomMm,
        double marginLeftMm,
        double marginRightMm,
        double bodyTopMm,          // fo:region-body margin-top
        double bodyBottomMm,       // fo:region-body margin-bottom
        double beforeExtentMm,     // fo:region-before extent
        double afterExtentMm) {    // fo:region-after extent

    public PageSetup(double pageWidthMm, double pageHeightMm, double marginTopMm,
                     double marginBottomMm, double marginLeftMm, double marginRightMm) {
        this(pageWidthMm, pageHeightMm, marginTopMm, marginBottomMm, marginLeftMm,
                marginRightMm, 10, 10, 8, 8);
    }

    public static final PageSetup A4 = new PageSetup(210, 297, 15, 15, 20, 20);
    public static final PageSetup LETTER = new PageSetup(215.9, 279.4, 15, 15, 20, 20);

    public PageSetup landscape() {
        return new PageSetup(pageHeightMm, pageWidthMm,
                marginTopMm, marginBottomMm, marginLeftMm, marginRightMm,
                bodyTopMm, bodyBottomMm, beforeExtentMm, afterExtentMm);
    }
}
