# 04 — BI Publisher Configuration (`xdo.cfg`) Property Catalog

Extracted from `oracle/xdo/common/config/PropertyConstants.class` in
`lib/bip/xdocore.jar` (from Template Viewer 12, BIP 12c build 2018) — **200 property
constants**. This is the checklist for F-5.16…F-5.23: the Settings UI must offer all of these,
grouped as below, with defaults + inline help, plus free-form key/value entry for anything newer.

Authoritative descriptions: *Oracle Fusion Middleware Report Designer's Guide for BI Publisher*,
"Setting Runtime Properties" appendix. Verify defaults against the loaded engine at runtime.

## File format

```xml
<config version="1.0.0" xmlns="http://xmlns.oracle.com/oxp/config/">
  <properties>
    <property name="system-temp-dir">/tmp</property>
    <property name="pdf-security">true</property>
    <property name="xslt._XDOTIMEZONE">Europe/Amsterdam</property> <!-- Java TZ ID -->
  </properties>
  <fonts>
    <font family="MyBarcode" style="normal" weight="normal">
      <truetype path="/path/to/font.ttf"/>
    </font>
  </fonts>
</config>
```

Validation rule (Template Viewer parity): reject files without the `oxp/config` namespace or a
configuration entry ("This file is not BI Publisher configuration file…").

## Prefix conventions (dynamic keys)

| Prefix | Meaning |
|---|---|
| `xslt.<name>` | Passed to the stylesheet as `xsl:param` — **this is the template-parameter mechanism** (F-5.21). Built-ins: `_XDOLOCALE`, `_XDOTIMEZONE` (Java TZ ID), `_XDOCALENDAR`, `_XDOCURRENCIES`, `_XDODFOVERRIDE` |
| `user-variable.<name>` | User variables readable via `xdoxslt:get_variable` |
| `font.<family>.<style>.<weight>` | Font registration (also via `<fonts>` section) |
| `font-substitute.<family>` | Font substitution mapping |
| `currency-format.<code>` | Per-currency format masks |
| `bidi-chartype.<…>` | BiDi character-type overrides |

## Properties by group

### System / engine
`system-temp-dir` · `system-cache-page-size` · `system-compressed-tmp-file` · `xdo-debug-level`
(ERROR/EXCEPTION/EVENT/PROCEDURE/STATEMENT — drives the log pane, F-5.9) · `xdo-number-rounding` ·
`hide-bip-version-number` · `make-accessible` · `digit-substitution` · `document-generation-uri` ·
`mini-scheduler-avg-job-time` · `mini-scheduler-max-job-count`

### XSLT processing
`xslt1.0-compatibility` · `xslt-xdoparser` · `xslt-scalable` · `xslt-runtime-optimization` ·
`xslt-xpath-optimization` · `xslt-forward-read` · `xslt-do-import` · `xslt-date-conversion-timezone`

### XML parsing / security
`xml-ignore-doctype` · `xml-preserve-whitespace` · `xdk-ignore-invalid-char` ·
`xdk-secure-io-mode` · `xdk-secure-allow-access` · `xdk-secure-prohibit-access`

### FO processing
`fo-chunk-size` · `fo-multi-threads` · `fo-correct-indent-inheritance` · `fo-extended-linebreaking` ·
`fo-fixed-lineheight` · `fo-image-handling-ver` · `fo-keep-empty-inline` ·
`fo-merge-conflict-resolution` · `fo-not-break-on-chars` · `fo-preserve-whitespace` ·
`fo-prevent-variable-header` · `fo-report-timezone` · `fo-soften-page-break` ·
`fo-space-handling-ver` · `fo-trim-leading-whitespaces` · `fo-external-link-base-url` ·
`fo-external-link-target`

### PDF output
`pdf-version` · `pdf-compression` · `pdf-font-embedding` · `pdf-document-title` ·
`pdf-display-doc-title` · `pdf-pagemode` · `pdf-hide-menubar` · `pdf-hide-toolbar` ·
`pdf-map-to-single-byte` · `pdf-replace-smartquotes` · `pdf-tagged-output` (accessibility) ·
`pdf-enable-accessibility` · `pdf-bidi-unicode-version` · `pdf-fileid` · `pdf-number-of-copies` ·
`pdf-print-duplex` · `pdf-print-scaling` · `pdf-max-objects-in-stream` · `pdf-use-object-stream` ·
`pdf-use-xref-stream` · `pdf-use-one-resources`

### PDF security & permissions
`pdf-security` · `pdf-encryption-level` · `pdf-open-password` · `pdf-permissions-password` ·
`pdf-permissions` · `pdf-changes-allowed` · `pdf-printing-allowed` · `pdf-no-printing` ·
`pdf-no-changing-the-document` · `pdf-enable-copying` · `pdf-no-accff` · `pdf-no-cceda`

### Digital signature
`signature-enable` · `signature-pkcs12-path` · `signature-pkcs12-password` ·
`signature-field-name` · `signature-field-location` · `signature-field-pos-x` ·
`signature-field-pos-y` · `signature-field-width` · `signature-field-height`

### PDF/A · PDF/X
`pdfa-version` · `pdfa-version-id` · `pdfa-document-id` · `pdfa-file-identifier` ·
`pdfa-icc-profile-data` · `pdfa-icc-profile-info` · `pdfa-rendition-class` ·
`pdfx-version` · `pdfx-dest-output-profile-data` · `pdfx-output-condition` · `pdfx-registry-name`

### PDF→print conversions (PostScript/print pipeline)
`pdf2x-copies` · `pdf2x-media-size-name` · `pdf2x-media-tray` · `pdf2x-output-resolution` ·
`pdf2x-output-scale` · `pdf2x-page-height` · `pdf2x-page-width` · `pdf2x-page-orientation` ·
`pdf2x-page-ranges` · `pdf2x-sheet-collate` · `pdf2x-sides` · `pdf2x-ps-cid-font-convtype` ·
`pdf2image-max-buffersize`

### PCL output
`pcl-color-supported` · `pcl-edge-to-edge-supported` · `pcl-fontmap-config-file` ·
`pcl-graphics-resolution` · `pcl-page-scale` · `pcl-x-adjustment` · `pcl-y-adjustment`

### RTF / DOCX output
`rtf-output-default-font` · `rtf-protect-forms` · `rtf-track-changes` · `rtf-checkbox-glyph` ·
`rtf-enable-widow-orphan` · `rtf-adj-table-border-overlap` · `rtf-extract-attribute-sets` ·
`rtf-rewrite-xpath` · `rtf-xslfo-version` · `docx-output-default-font` · `docx-protect-forms` ·
`docx-track-changes`

### HTML output (key group for **Web · Unlimited width** mode)
`html-outputtype` · `html-output-charset` · `html-output-body-only` ·
`html-output-width-in-percentage` · `html-css-base-uri` · `html-css-dir` · `html-css-embedding` ·
`html-image-base-uri` · `html-image-dir` · `html-image-acl-id` · `html-use-data-uri` ·
`html-use-svg` · `html-enable-horiz-table-scroll` · `html-keep-original-table-width` ·
`html-reduce-padding` · `html-replace-smartquotes` · `html-show-header` · `html-show-footer` ·
`html-suppressed-line-height` · `paginated-html-filepath` · `paginated-html-page-size`

### HTML→FO conversion
`html2fo-base-url` · `html2fo-base-filepath` · `html2fo-enclosed-in-block` · `html2fo-strict-mode` ·
`html2fo-output-body-only` · `html2fo-page-size` · `html2fo-page-width` · `html2fo-page-height` ·
`html2fo-page-orientation` · `html2fo-margin-top` · `html2fo-margin-bottom` · `html2fo-margin-left` ·
`html2fo-margin-right`

### Excel / CSV output
`xlsx-scalable` · `xlsx-show-gridlines` · `xlsx-table-auto-layout` · `xlsx-max-column-width` ·
`xlsx-min-column-width` · `xlsx-min-row-height` · `xlsx-max-nested-table-row-count` ·
`xlsx-keep-values-in-same-column` · `xlsx-page-break-as-new-sheet` · `csv-delimiter` ·
`csv-output-bom` · `flattenxml-max-rows` · `flattenxml-trim-whitespaces`

### eText output
`etext-bigdecimal-mode` · `etext-sequence-id` · `etext-utf8-bom`

### PowerPoint / Flash (legacy)
`pptx-native-chart` · `flash-width` · `flash-height` · `flash-frame-width` · `flash-frame-height` ·
`flash-page-width` · `flash-page-height` · `flash-startx` · `flash-starty` · `flash-update-framesize`

### Translation / XLIFF (F-9.5, F-9.6)
`xdo.xliff.source` · `xliff-trans-expansion` · `xliff-trans-keyword` · `xliff-trans-max-length` ·
`xliff-trans-min-length` · `xliff-trans-null` · `xliff-trans-symbol`

## UI requirements recap

- Settings tab: grouped tree/table (Key | Value), search, modified-indicator, per-session vs
  write-back-to-file editing, **Default / Reload / Browse** buttons (Template Viewer parity).
- Multiple named `xdo.cfg` files per project with quick switching (F-5.18).
- Fonts editor as a first-class panel (family/style/weight → TTF path).
- Parameter editor: `xslt.*` and `user-variable.*` rows surfaced separately as "Template
  parameters", with per-run overrides.
- FOP mirror: same UX for `fop.xconf` (fonts, renderers, PDF/A, encryption) when FOP engine active.
