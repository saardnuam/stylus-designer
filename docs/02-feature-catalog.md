# 02 — Feature Catalog (master checklist)

The single list we build and test against. **Nothing ships as "done" unless its box is checked, and
no feature may be removed from this list — only moved to "parked" with a note.**
IDs are stable; reference them in commits and issues (e.g. `F-2.14`).

Sources: user requirements · `design_handoff_xslt_report_designer/README.md` (UI spec) ·
Template Viewer 12c help + decompiled string resources · `xdocore.jar` property/function extraction.

---

## 1. Designer shell & UX (design handoff — source of truth for visuals)

### 1.1 Application frame
- [x] F-1.1 Three-pane IDE: data-source tree (262px) · canvas (flex) · properties panel (308px)
- [x] F-1.2 App bar: file name + data source indicator, unsaved-changes dot, view switch, Run & Preview
- [x] F-1.3 View switch: **Design | Code | Preview** (segmented control)
- [ ] F-1.4 Toolbar row 1 — text formatting: paragraph style, font family, font size, B/I/U/S, text color, highlight, align L/C/R, bullet list, table/borders
- [ ] F-1.5 Toolbar row 2 — XSLT insert actions: `+ Field`, `For-each`, `if Condition`, `Choose`, `Variable`, `XPath`, raw `XSLT`, `Number` format, `Date`, `Sort`, `Page break`
- [ ] F-1.6 Status bar: template validity indicator, XSL-FO version, output-format chips (PDF/HTML/RTF/XLSX…), zoom control
- [x] F-1.7 Light theme (1A) + dark theme (2A), single toggle; paper stays white in dark mode
- [ ] F-1.8 Themeable accent color; all design tokens per handoff (colors, typography, radius, shadow, 4px spacing)
- [x] F-1.9 UI fonts: Hanken Grotesk (UI) + JetBrains Mono (code/tokens) or platform equivalents

### 1.2 Data-source tree (left pane)
- [x] F-1.10 Load sample XML; parse to schema/tree with inferred node types (element, text, attribute, number, date)
- [x] F-1.11 Type glyphs per node kind; group rows highlighted with GROUP badge
- [x] F-1.12 Search/filter fields; Structure vs All-fields tabs
- [x] F-1.13 Drag handle on every leaf; drag onto canvas inserts a bound field token
- [x] F-1.14 Dragging a container/group creates a for-each band
- [x] F-1.15 Footer stats (row count, group count)

### 1.3 Canvas (center)
- [ ] F-1.16 Output-mode strip: **A4 · Pixel-perfect** vs **Web · Unlimited width** toggle + ruler + page-width readout
- [ ] F-1.17 Paper with margin guides (toggleable), band structure top→bottom
- [x] F-1.18 Band types: page header, report title/static blocks, group (for-each) bands with header bars (path, sort pill, repeat count), nested groups, detail table bands, conditional bands (IF/ELSE, amber tint), group footers with aggregates, page footer
- [x] F-1.19 Field tokens: inline mono chips `⟨ … ⟩`, colored by data category (structure/condition/code/data/string/error)
- [x] F-1.20 Selection model: click token/band → accent halo → populates properties panel
- [x] F-1.21 Drag & drop affordances: blinking insertion caret, "drop to insert" pill, floating drag chip
- [x] F-1.22 Page-number tokens: `page()`, `page-count()`; date tokens `current-date()`
- [ ] F-1.23 Zoom (status bar), scrollable paper region
- [x] F-1.24 Tables: column grid layout, header rows, detail band "repeats per X" labeling

### 1.4 Properties panel (right pane)
- [x] F-1.25 Context header (type glyph, name, band · datatype subtitle); tabs **Properties | Style | Data**
- [ ] F-1.26 Properties tab: data binding (XPath, relative-to-group-context helper), `ƒx Edit` opens expression editor, format (data type, align, number/date mask), conditional-format rules (n-rules badge), suppress-if-empty toggle
- [ ] F-1.27 Style tab: typography (family, size, B/I/U, color), cell (background, border side picker, padding)
- [x] F-1.28 Data tab: live sample values for selected binding; rows matching conditional rules highlighted; match summary
- [x] F-1.29 Conditional formatting: rule builder (condition → style effects), rendered on canvas and in preview

### 1.5 Expression editor
- [ ] F-1.30 Overlay editor (bottom-anchored card), dark code surface in both themes, syntax highlighting (keyword/field/operator/number/function/string), blinking caret
- [x] F-1.31 Function palette chips (engine-aware: XPath std + `xdoxslt:` when BIP targeted) — click to insert
- [x] F-1.32 Live validation (✓ Valid / error state) + live computed preview against sample data
- [ ] F-1.33 XPath version awareness (1.0 for maximum BIP compat; 2.0 chip per handoff — see F-2.2)
- [x] F-1.34 Apply / dismiss; opens from `ƒx Edit` and from the XPath toolbar button

### 1.6 Code view
- [x] F-1.35 Full XSLT source editor: syntax highlighting, folding, search/replace, go-to-line
- [x] F-1.36 Two-way sync with design view; hand-written/unrecognized constructs round-trip untouched (N7)
- [x] F-1.37 Live well-formedness + XSLT validation with inline error markers
- [x] F-1.38 Generated code is clean, indented, commented — readable without the designer

### 1.7 General UX
- [x] F-1.39 Undo/redo (full command history, both views)
- [x] F-1.40 Copy/cut/paste of bands, tokens, selections
- [ ] F-1.41 Keyboard shortcuts for all toolbar actions; menu bar with full command set
- [ ] F-1.42 Autosave / crash recovery
- [ ] F-1.43 Recent files, drag-file-onto-window to open (Template Viewer has drop-target dir selection)
- [ ] F-1.44 Animations: quick transitions (120–160 ms), hover states per handoff

### 1.8 XSL-FO element structure — make the FO tree visible & selectable

**Design fundamental.** XSL-FO is a tree of formatting objects — `fo:block`, `fo:inline`,
`fo:block-container`, `fo:wrapper`, table/list parts, and more — and each is a *different thing*
with a different property set and different pagination behaviour. The author must always be able to
see and know **which FO element they are editing**. The canvas therefore renders the FO structure,
not just the content, and selection is element-level (see design handoff → "FO structure outlines
& element selection").

- [ ] F-1.45 Every recognized XSL-FO element on the canvas (`block`, `inline`, `block-container`,
  `inline-container`, `wrapper`, `character`, `leader`, table/`table-cell`/row parts, list parts,
  `static-content`/region content, `page-number`, …) is drawn with a **very thin (1px) selectable
  outline** — a hairline that is near-invisible at rest, brightens on hover, and shows an **accent
  halo when selected**.
- [x] F-1.46 Clicking an element's outline selects **that FO element** (not only field tokens) and
  populates the right Properties/Style panel with that element's FO properties; the panel header
  shows the FO element type as a mono chip (`fo:block`, `fo:inline`, …) plus its role/band subtitle.
- [ ] F-1.47 Block vs inline is legible at a glance: blocks read as stacked rectangular regions,
  inlines as in-flow runs; containers (`block-container`/`inline-container`) get a distinct outline
  so absolute/relative positioning is obvious.
- [x] F-1.48 **FO structure** view toggle (sibling of the margin-guides toggle): show/hide all
  element outlines at once. Off by default (clean canvas); on for structural editing, when hovering
  any element reveals its type badge.
- [x] F-1.49 **FO ancestor breadcrumb** for the current selection
  (`page-sequence ▸ flow ▸ block ▸ inline`); clicking a crumb selects that ancestor — the primary
  way to grab the wrapping block/container a field sits inside.
- [ ] F-1.50 Selecting an element exposes its **full FO property set** where mapped (Style tab,
  F-2.19) plus a **raw-attributes editor** for anything unmapped, and (F-2.25) an inline
  **engine-support badge** when the element/attribute is unsupported by the active engine
  (e.g. BIP — see doc 07).
- [ ] F-1.51 Opaque/unrecognized nodes (N7) also get a generic outline + type label so hand-written
  FO the designer can't map stays visible and selectable (read-only property view + jump-to-code).

---

## 2. XSLT & XSL-FO language coverage

Principle: **full spec coverage through the code view; visual designer covers the mapped subset**
below. The model preserves anything it does not understand.

### 2.1 XSLT
- [x] F-2.1 XSLT 1.0 output (default when targeting BIP; `xslt1.0-compatibility` respected)
- [ ] F-2.2 XSLT 2.0 support (BIP 12c XDK supports 2.0; FOP via Saxon-HE) — selectable per template
- [ ] F-2.3 Designer-mapped constructs: `value-of`, `for-each`, `if`, `choose/when/otherwise`, `sort`, `variable`, `param`, `text`, `attribute`, `call-template`/`template name`, `import`/`include` (subtemplates), `number`, `format-number()`, `key()`/`generate-id()` grouping (Muenchian for 1.0), `for-each-group` (2.0)
- [ ] F-2.4 Code-view-only but preserved: `apply-templates`/match templates, `copy`, `copy-of`, `element`, `comment`, `processing-instruction`, `message`, `fallback`, `namespace-alias`, `decimal-format`, `output`, `strip-space`/`preserve-space`, `attribute-set`, `function` (2.0), `analyze-string` (2.0)
- [ ] F-2.5 Template parameters: declare `xsl:param` at stylesheet level; designer UI to define name/type/default; values fed at run time (§5.3)
- [ ] F-2.6 XPath expression support matching selected XSLT version incl. axes, predicates, all standard functions

### 2.2 XSL-FO (1.1)
- [ ] F-2.7 Page setup: `simple-page-master` (size, margins, orientation), multiple masters + `page-sequence-master` (first/odd/even/last, repeatable alternatives) — designer UI for page setup incl. A4/Letter/custom, landscape
- [ ] F-2.8 Regions: body, before/after (header/footer bands), start/end (side regions), region precedence, `static-content`, multi-column `region-body` (`column-count`, `column-gap`)
- [ ] F-2.9 Blocks & inlines: `block`, `inline`, `block-container`/`inline-container` (absolute positioning), `wrapper`, `character`
- [ ] F-2.10 Tables: full model (`table`, columns with widths, header/footer/body, row/cell spans, border-collapse/separate, keep-together), designer table editor
- [ ] F-2.11 Lists: `list-block/item/label/body` (bullets, numbering)
- [ ] F-2.12 Graphics: `external-graphic`, `instream-foreign-object` (SVG — §7), scaling/content-width/height
- [ ] F-2.13 Page numbering: `page-number`, `page-number-citation(-last)` (page X of Y), `initial-page-number`, `force-page-count`, format tokens
- [ ] F-2.14 Breaks & keeps: `break-before/after`, `keep-together/with-next/with-previous`, widows/orphans — designer "Page break" + keep controls
- [ ] F-2.15 Running content: `marker`/`retrieve-marker` (dictionary-style running headers)
- [ ] F-2.16 Footnotes (`footnote`, `footnote-body`) and floats (`float`, side floats)
- [ ] F-2.17 Leaders (`leader` — dot fills, rules, TOC lines)
- [ ] F-2.18 Links & navigation: `basic-link` (internal/external), bookmarks (`bookmark-tree`), `fo:index-*` (code view)
- [ ] F-2.19 Full property set via style UI where mapped (fonts, color, background incl. images, borders, padding, text-align/justify, line-height, letter/word spacing, text-decoration, text-transform, hyphenation) — everything else via a raw-attributes editor on any element
- [ ] F-2.20 Writing modes & BiDi: `writing-mode`, `bidi-override`, RTL locales
- [ ] F-2.21 Absolute positioning for pixel-perfect forms (block-container with absolute coordinates)
- [x] F-2.26 **`fo:layout-master-set` editor** — visual editor for the whole page-geometry set:
  define and name multiple `simple-page-master`s (each with its region set), assemble them into
  `fo:page-sequence-master`s, and bind sequences to `fo:page-sequence`s. This is the editable
  machinery behind F-2.7; a template can carry several masters (e.g. cover / body / landscape annex).
- [x] F-2.27 **Conditional pages** — full `conditional-page-master-reference` matrix editing:
  `page-position` (first / last / rest / only), `odd-or-even` (odd / even / any),
  `blank-or-not-blank` (blank / not-blank / any), alongside `single-page-master-reference` and
  `repeatable-page-master-reference` (`maximum-repeats`). Lets the author give different layouts to
  first page vs. odd/even (mirrored margins, duplex) vs. last vs. inserted blank pages, edited
  visually and round-tripped byte-safe (N7).

### 2.3 Engine-specific extensions
- [ ] F-2.22 Apache FOP `fox:` extensions: bookmarks/destinations, `fox:external-document`, transparency, PDF-embedded files (expose in code view + insert menu)
- [ ] F-2.23 FOP configuration file (`fop.xconf`): font discovery/registration, base URLs, renderer options, PDF/A + PDF/X + PDF/UA modes, encryption — editable per project (§5.3 analog)
- [ ] F-2.24 BIP `xdofo:` constructs and `xdoxslt:` functions — full catalog in doc 05, surfaced in function palette and insert menu when BIP engine active
- [ ] F-2.25 Engine capability matrix: designer warns when a used construct is unsupported by the
  currently selected engine (e.g. `xdoxslt:` on FOP, `fox:` and various FO 1.1 objects on BIP).
  **BI Publisher does not implement the full XSL-FO 1.1 object set** — the authoritative element/
  attribute support model lives in doc 07 and is surfaced per F-1.50 and F-2.28.
- [ ] F-2.28 **Capability matrix (doc 07) is the reference model** of which XSL-FO elements/
  attributes and extension namespaces each engine (FOP, BIP) supports. The designer reads it to
  (a) badge unsupported constructs in the FO structure overlay and property panels (F-1.50),
  (b) grey/annotate insert-menu actions for the active engine, and (c) drive the validation
  warnings panel (F-11.2). Seeded from Oracle docs + known behaviour, then **refined at runtime by
  a capability probe** against the user's installed BIP version.

---

## 3. BI Publisher extensions (detail in doc 05)

- [x] F-3.1 `xdoxslt:` function library in expression editor + palette (date/number/string/aggregate/variable/sequence/barcode groups)
- [ ] F-3.2 `xdofo:` formatting constructs (page totals, contexts, dynamic data-driven formatting)
- [ ] F-3.3 Barcodes: `format_barcode`, `register_barcode_vendor`, QR code, PDF417 (render in preview via BIP engine)
- [ ] F-3.4 SVG chart/gauge helpers (`chart_svg`, `gauge_svg`) — expose, render via engine
- [ ] F-3.5 Running variables: `set_variable`/`get_variable`, sequence numbers, block counters
- [ ] F-3.6 BIP locale/timezone behavior: `xslt._XDOLOCALE`, `xslt._XDOTIMEZONE` (Java TZ IDs), Oracle date/number format masks

---

## 4. Output modes & formats

- [x] F-4.1 **Pixel-perfect mode**: paginated XSL-FO; WYSIWYG canvas mirrors the page master
- [x] F-4.2 **Web mode**: unlimited-width HTML output; canvas switches to fluid layout, no page bands
- [x] F-4.3 Per-template output-mode setting, switchable in the ruler strip; both modes from one data model where constructs overlap
- [x] F-4.4 Output formats via **FOP engine**: PDF, PostScript, PCL, AFP, PNG/TIFF, plain text, intermediate format (IF/AT)
- [x] F-4.5 Output formats via **BIP engine** (Template Viewer parity): PDF, RTF, HTML, MHTML, Excel (XLSX), PowerPoint(PPTX)*, eText, PDFZ, FO (intermediate), *if exposed by local BIP version
- [ ] F-4.6 Output format chips in status bar switch the target renderer; preview renders the real output for the chosen format
- [x] F-4.7 Export: Save-dialog export of any generated output (Template Viewer "Export" button)
- [x] F-4.8 Open generated output in the system's default application (spawn viewer)
- [ ] F-4.9 PDF flavors: PDF/A, PDF/X, PDF/UA (accessibility, `make-accessible`), encryption/permissions, digital signature fields — driven by engine config (docs 04, F-2.23)

---

## 5. Test bench — Template Viewer parity

### 5.1 Run panel (Files-tab equivalent)
- [x] F-5.1 Working-directory browser; data files (XML) pane + template files pane side by side
- [x] F-5.2 File-format filter (All, PDF Forms, RTF/eText, XSL(FO), Excel, XPT) — for viewing/conversion parity even though we author XSL only
- [x] F-5.3 Output format dropdown + **Locale** field (ll-TT), respected for number/date formats and text direction
- [ ] F-5.4 **XLIFF file** selector — apply a translation during processing
- [ ] F-5.5 **Style template file** selector (BIP style templates applied at run time)
- [x] F-5.6 Start Processing + Export buttons; progress dialog with cancel; total-time report
- [x] F-5.7 Double-click data file → open XML in viewer; double-click template → open in associated editor (for us: open in Stylus)
- [x] F-5.8 File list refresh (F5)

### 5.2 Logging & debugging
- [x] F-5.9 Log pane under run panel; levels ERROR / EXCEPTION / EVENT / PROCEDURE / STATEMENT (`xdo-debug-level`)
- [x] F-5.10 FOP logging equivalently surfaced when FOP engine active
- [x] F-5.11 **Generate XSL-FO** (run transform only, save intermediate `.fo`) — key FO debugging tool
- [x] F-5.12 **Merge multiple XSL-FO files** into one FO
- [x] F-5.13 **Inject profiling into XSL** + stopwatch functions; show per-template timing
- [ ] F-5.14 Monitor memory usage toggle
- [x] F-5.15 Conversion tools (wrap engine): Generate XSL from RTF / eText / Excel / XPT template; Generate XSL + XLIFF from RTF

### 5.3 Configuration (`xdo.cfg`) — full catalog in doc 04
- [x] F-5.16 Settings tab with property table (name/value), grouped + searchable, showing effective values
- [ ] F-5.17 **Load any configuration file** (Browse), validate it is a BIP config (`http://xmlns.oracle.com/oxp/config/`), Reload, restore Defaults
- [x] F-5.18 **Multiple named config files per project**; quick switching between them (user requirement)
- [x] F-5.19 Edit property values for the session **and** write changes back to the chosen `xdo.cfg`
- [x] F-5.20 Font section editing: font family/style/weight → TTF path mappings, `font-substitute` entries
- [x] F-5.21 **Template parameters**: `xslt.<name>` entries passed as XSLT params (incl. `_XDOLOCALE`, `_XDOTIMEZONE`, `_XDOCALENDAR`), `user-variable.` entries; parameter editor UI with per-run overrides (user requirement)
- [ ] F-5.22 FOP-side equivalent: `fop.xconf` selection + editing per project (mirrors F-5.16..20 for FOP)
- [x] F-5.23 All ~200 12c properties selectable with defaults + descriptions (doc 04); unknown/newer properties still settable as free key/value

---

## 6. Subtemplates

- [x] F-6.1 Create/manage subtemplate files (`.xsl` libraries of named templates/attribute-sets)
- [x] F-6.2 Insert `xsl:import` / `xsl:include`; resolve relative paths within project; BIP subtemplate call convention (`<?import:xdoxsl:///...?>` server syntax noted, local file resolution used at design time)
- [x] F-6.3 Call named templates from subtemplates via designer (call-template insert with param UI)
- [x] F-6.4 Preview/run resolves subtemplates; missing-reference diagnostics
- [x] F-6.5 Subtemplate browser pane listing available callable templates

---

## 7. Images & SVG

- [x] F-7.1 Insert raster images (PNG/JPEG/GIF/TIFF): file reference, embedded base64 data-URI, or XPath-driven dynamic URL
- [x] F-7.2 **SVG**: insert as `instream-foreign-object` (inline) or `external-graphic`; renders in canvas, PDF (FOP/Batik + BIP), and HTML output (user requirement)
- [ ] F-7.3 Image sizing UI (width/height/scaling/DPI), alt text (accessibility)
- [ ] F-7.4 Background images on blocks/pages (watermarks, letterhead)

---

## 8. Data handling

- [ ] F-8.1 Sample XML file per template (Template Viewer pairing: data + template in working dir)
- [ ] F-8.2 Multiple sample data files per template; quick switching to test variants
- [ ] F-8.3 Type inference (number/date/text) from sample values; manual override
- [ ] F-8.4 Optional XSD schema import for accurate structure/typing
- [ ] F-8.5 Handle BIP data-engine conventions (ROWSET/ROW, `<?xml?>` docs with attributes), namespaces
- [ ] F-8.6 Large-file safety: streaming/sampled tree building, background transforms (N10)

---

## 9. Internationalization

### 9.1 Of the application
- [ ] F-9.1 All UI strings in resource bundles; **English + Dutch** complete at v1 (user requirement)
- [x] F-9.2 Language switch in settings (default: OS locale); no restart needed or clearly prompted
- [ ] F-9.3 Locale-correct UI number/date rendering in inspectors

### 9.2 Of the templates/output
- [ ] F-9.4 Output locale selector (ll-TT) per run (F-5.3); drives `format-number`/date masks, digit substitution, text direction
- [x] F-9.5 **XLIFF workflow**: generate XLIFF from template, apply edited XLIFF at run time, preview translated output with matching locale
- [ ] F-9.6 XLIFF translation properties (`xliff-trans-*` group, doc 04)
- [ ] F-9.7 BiDi/RTL preview correctness (F-2.20)

---

## 10. Project & persistence

- [ ] F-10.1 Template files are **plain `.xsl`** — no proprietary lock-in; designer metadata in XML comments or a sidecar (decide in M2; must not break engines)
- [ ] F-10.2 Project/workspace file: working dir, template↔data pairings, config-file choices, parameters, engine + output settings
- [x] F-10.3 Import any existing XSL/XSL-FO stylesheet (incl. BIP RTF→XSL conversions): open in code view, best-effort band recognition for design view
- [ ] F-10.4 New-template wizards: pixel-perfect (page setup first) vs web; starter layouts (blank, letter, invoice-like listing with groups)

---

## 11. Quality gates & validation

- [ ] F-11.1 Continuous XSLT validation (status bar "XSLT valid")
- [ ] F-11.2 FO validation against the target engine's supported subset (FOP compliance table; BIP
  quirks — capability matrix doc 07) → warnings panel (F-2.25, F-2.28)
- [ ] F-11.3 Expression validation + evaluated preview in expression editor (F-1.32)
- [x] F-11.4 Golden-file regression harness for the code generator (model → XSL snapshot tests)
- [x] F-11.5 Rendering smoke tests: sample templates × engines × formats in CI

---

## 12. Platform & packaging

- [ ] F-12.1 Runs on macOS (arm64 + x64), Windows x64, Linux x64 from one codebase
- [ ] F-12.2 `jpackage` native installers (dmg/pkg, msi/exe, deb/rpm) with bundled runtime; plus plain runnable jar
- [x] F-12.3 BIP engine discovery UI: point at a local BI Publisher Desktop / Template Viewer install; validate jars; graceful FOP-only mode when absent
- [x] F-12.4 App works fully with FOP only (BIP features shown but disabled with explanation)
- [ ] F-12.5 Auto-detect JDK/JavaFX mismatches; single self-contained runtime preferred

---

## 13. File handling & BI Publisher server connectivity

### 13.1 Local disk (default workflow)
- [x] F-13.1 Plain open/save of `.xsl`/`.xslt`/`.fo`/XML files from disk — no server, no lock-in (user requirement)
- [ ] F-13.2 Save As, file associations, recent files (ties into F-1.43, F-10.1)

### 13.2 BI Publisher server (user requirement)
- [ ] F-13.3 Manage BIP server connections (name, URL, credentials); secure credential storage (OS keychain)
- [ ] F-13.4 Browse the BIP catalog remotely (folders, reports, data models, subtemplates, style templates)
- [ ] F-13.5 **Download/retrieve** report artifacts from server: layout templates, sample data, subtemplates — open directly in the designer
- [ ] F-13.6 **Upload/publish** templates back to a report on the server (new layout or overwrite, with confirmation)
- [ ] F-13.7 Use the official BIP web service API — SOAP `v2` services (`CatalogService`, `ReportService`, `SecurityService` at `/xmlpserver/services/v2/…`) and/or the 12c REST API where available; version-detect per server
- [ ] F-13.8 Fetch sample data from a server data model for design-time preview
- [ ] F-13.9 Fully offline-capable: server features are additive; everything works disk-only (N4)
- [ ] F-13.10 Conflict safety: warn when overwriting a server template that changed since download

## Parked (explicitly not lost — see 01 §out-of-scope)

- P-1 RTF/eText/Excel/XPT template *authoring*
- P-2 BI Beans charting UI (SVG charts cover v1)
- P-3 Bursting / delivery / scheduling
- P-4 SQL/data-model design
- P-5 PDF Forms template support beyond run/preview parity
- P-6 Additional UI languages beyond EN/NL
