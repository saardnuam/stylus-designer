# MEMORY.md — Project Progression Log

Living log of where Stylus stands. **Update this file whenever a work session changes project
state** (milestone progress, decisions, blockers). Newest session entries on top.
Feature IDs refer to [docs/02-feature-catalog.md](docs/02-feature-catalog.md); milestones to
[docs/06-roadmap.md](docs/06-roadmap.md).

## Current status

**Phase: building. M0–M6 done; M7 packaging/NL/prefs done, §13 server deferred.
76/163 catalog boxes. Verified: 67/67 tests green (18 suites incl. 7 BIP ITs on real Oracle
jars); DMG rebuilt with icon + all features; CLI E2E on both engines.**

### Next-session shortlist (in rough priority)
1. BIP server connectivity (catalog §13, optional/additive — SOAP v2/REST): needs a reachable
   BIP server + credentials from Resa; deferred deliberately rather than shipping untested code.
2. Polish leftovers: F-5.17 Defaults button, F-5.14 memory monitor, F-1.16 per-document mode
   readout, image DPI/alt (F-7.3), background images (F-7.4), F-1.45 full (hover type badge),
   F-1.47 container-distinct outlines, lists/table-borders toolbar buttons (model support).
3. Typography rest of F-2.19: padding/borders/letter-spacing/text-transform/hyphenation
   (currently via raw editor); paragraph styles combo.
4. NL descriptions for the 82 xdoxslt functions (signatures stay EN; only category labels are
   localized so far).
5. Signing/notarization for DMG distribution beyond this machine (needs Apple dev cert).

### Icon pipeline (2026-07-03)
- Sources: `icon/icons/stylus-icon{,-macos}.svg` (user-supplied; zip gitignored). Batik can't
  render `feDropShadow` — strip the filter before rasterizing (macOS draws Dock shadows).
- Regenerate: Batik PNGTranscoder → PNGs (resources `dev/stylus/app/icons/stylus-{32..512}.png`
  set as stage icons = Dock icon) + iconset → `iconutil -c icns` →
  `stylus-app/packaging/Stylus.icns` (jpackage `--icon` on macOS).

### Build environment (this machine)
- JDK 21: `~/tools/jdk-21.0.11+10/Contents/Home` (Temurin, downloaded — system java is 17)
- Gradle dist: `~/tools/gradle-8.14.3` (only needed for wrapper regeneration; use `./gradlew`)
- Build: `export JAVA_HOME="$HOME/tools/jdk-21.0.11+10/Contents/Home" && ./gradlew build`
- Golden update: `./gradlew :stylus-codegen:test -Dgolden.update=true`
- Run app: `./gradlew :stylus-app:run`

## Milestone tracker

| Milestone | Scope (short) | Status |
|---|---|---|
| M0 | Gradle multi-project, JavaFX shell, theming, EN/NL bundles, CI | ✅ done |
| M1 | Engine SPI + FOP engine, fop.xconf, CLI, Generate-FO | ✅ done |
| M2 | Test-bench UI (Template Viewer replacement), disk open/save | ✅ done |
| M3 | BIP engine adapter, xdo.cfg settings UI, conversion tools | ✅ done (F-5.14/F-5.21 parked as polish) |
| M4 | Designer canvas MVP, document model, codegen round-trip, code view | ✅ done (code view = TextArea; RichTextFX highlighting still open) |
| M5 | Properties panel, expressions, conditional formatting | ✅ done (F-1.28 data tab + F-1.29 rules landed) |
| M6 | Subtemplates, SVG, XLIFF translation workflow | ✅ done (F-6.5 browser pane + F-7.3/7.4 image polish parked) |
| M7 | BIP server connectivity, NL pass, packaging, release | 🔶 jpackage DMG ✅, NL pass ✅, prefs/i18n switch ✅; §13 server deferred (needs a live server) |

Statuses: ⬜ not started · 🔶 in progress · ✅ done · ⏸ paused

## Open decisions

| ID | Decision | Due | Status |
|---|---|---|---|
| D-1 | Sidecar metadata format (`*.stylus.json`) vs embedded comments (F-10.1) | M2 checkpoint | open — sidecar tentatively chosen (docs/03) |
| D-2 | XSLT 2.0 default vs 1.0-compat default for new BIP templates (F-2.1/2.2) | M4 | open |
| D-3 | AtlantaFX base vs fully hand-rolled CSS theme | M0 | open |

## Constraints to never forget

- Oracle jars in `lib/bip/` are proprietary: gitignored, local-only, never bundled (CLAUDE.md rule 1).
- Round-trip safety N7: unknown XSLT re-emitted byte-identical.
- `en`/`nl` resource-bundle parity from M0 onward.

## Session log

### 2026-07-04 — typography, xsl:text+Ω, page masters, §1.8, F-5.21 & gap sweep
- **Typography (F-2.19 subset)**: StyleProps + font-family/background-color/underline/strike/
  line-height, emitted AFTER the M4 attrs (old goldens byte-stable); reader strict
  (text-decoration only in writer forms); Style tab second row; toolbar U/S/highlight/family
  live; rules preserve the new fields.
- **xsl:text + special characters (user request)**: `XslTextInline` first-class (whitespace-
  exact, F-2.3); writer's `ncrEscape` renders invisibles as `&#x…;` (deterministic → fixed
  point); toolbar „a“ insert with Ω picker (45 chars + free hex) + live code preview; canvas
  dotted-underline runs; XLIFF-translatable.
- **Conditional page masters (F-2.26/27)**: PageMaster/MasterSelector model; writer emits
  masters + page-sequence-master "stylus-pages" with first-match-wins conditional refs; reader
  strict; ▤ page-setup dialog (single-geometry editing = first F-2.7 UI, or master list +
  selector rows). Golden masters-model.xsl + FOP render test.
- **§1.8**: fo:/xsl: mono chip in props header (opaque nodes show their real root tag),
  clickable ancestor breadcrumb (ModelEdits.ancestorsOf), engine-support badges from the
  registry matrices via DesignerState.targetCapabilities (F-1.50), raw XSLT/FO editor on
  opaque nodes, hover hairlines.
- **Gap sweep**: bench Parameters… (F-5.21 — FOP: XSLT params; BIP: merged temp xdo.cfg
  xslt.* channel), variable + engine-aware date inserts (all toolbar stubs now live),
  subtemplate browser dialog (F-6.5, Subtemplates utility shared with call-template insert),
  page header/footer canvas editing (ghost add-bands, field drops, TextRun now selectable/
  editable everywhere), xdoxslt palette categories localized.
- **67/67 tests** (18 suites), catalog 76/163, DMG rebuilt.

### 2026-07-03 (later) — Preferences (⌘,), fo: inserts, M6 finish, M7 packaging
- **⌘, Preferences dialog** (user request): language EN/NL switched **live** — StylusApp owns
  a shared DesignerState, `state.detachUi()` + scene-root rebuild keeps document/sample/undo
  across the language change (F-9.2); theme choice persisted; **BIP home picker with live
  discovery feedback** (F-12.3). Locale/theme in java.util.prefs, applied at startup.
- **fo: element menu** in the insert toolbar (user request): fo:block, fo:inline,
  block-container, table, list-block, leader, basic-link, footnote, page-number(-citation) —
  mapped kinds insert as editable bands/tokens, the rest as preserved skeletons; inline
  inserts target the selected static band. Number insert wired.
- **M6 finished**: engine-fop now uses the aggregate `fop` artifact → **Batik renders
  instream SVG** (pixel-verified test); SVG insert asks embed-vs-link; image src documents
  `{XPath}` AVTs (dynamic URLs); **call-template dialog** discovers named templates + params
  from the document's imports (F-6.3); File → New Subtemplate… creates a callable library
  skeleton (F-6.1); run/editor already surface missing-import diagnostics (F-6.4).
- **M7 packaging**: `./gradlew :stylus-app:jpackage` → `stylus-app/build/dist/Stylus-1.0.0.dmg`
  (~131 MB, bundled JRE; macOS needs app-version ≥ 1). **Mounted the DMG and launched the
  packaged app — runs.** No Oracle jars inside (hard rule 1). NL bundle reviewed — clean.
- **§13 BIP server deferred**: no live server to verify SOAP/REST against; shipping untested
  client code would be worse than the honest gap.
- Verification: **63/63 tests, 0 skipped** (18 suites). Catalog 69/163.

### 2026-07-03 — code editor, reader strictness, canvas polish, M6 core
- **RichTextFX code editor** (F-1.35/37): xsl:/fo:-aware highlighting (themed tokens), line
  numbers, debounced SAX well-formedness (precise line marker) + Saxon compile errors in a
  validation strip; ⌘Z routes to editor history in code view.
- **Element-rooted templates** (`match="/Root"`) recognized: rootMatch on ReportDocument,
  contextChain prepends it (fixes data tab/drops/ƒx previews). **N7 strictness pass**: attr
  guards on every recognized element; fancy static-content → wholesale opaque region;
  PageSetup gained region geometry; samples/invoice-fo.xsl now opens with its group live.
- **Canvas polish**: live FormatToolbar (B/I/size/color/align on selection), Cut/Copy/Paste
  (deep-copy paste, focus-aware forwarding), drop-position caret top-level + group bodies
  (F-1.21), ⧉ FO-structure outline toggle (F-1.48).
- **M6 core**: ImageBand (F-7.1: insert/canvas thumbnail/props editor; data: URIs; SVG files
  via external-graphic), PageCountToken ("Page X of Y" via plain citation + stylus-last anchor
  — works on BIP too, doc 07), xsl:import subtemplates (F-6.2 menu insert), raw-XSLT insert
  dialog, page-break insert. **XLIFF workflow (F-9.5)**: stylus-xliff module (generate with
  stable position-path ids, readTargets, apply), Tools menu generator, bench pre-translates
  templates for FOP runs (BIP keeps native setXLIFF). New golden m6-model.xsl; FOP render test
  proves "Page 1 of 1" + data-URI image. Left open: instream SVG, dynamic image URL (F-7.1),
  call-template param UI (F-6.3), subtemplate browser (F-6.5), date tokens (F-1.22).
- **Verification**: 62/62 tests, 0 skipped (17 suites; 7 BIP ITs on real jars). CLI E2E: the
  refreshed samples/invoice-designed.xsl (now with F-1.29 rules) → FOP PDF 6.9 KB + BIP PDF
  1.8 KB, both text-verified. BIP still shows `—` as `?` (default-font mapping, use Fonts…)
  and formats numbers with comma decimals (server-locale default) — both noted, not bugs.

### 2026-07-03 — M3 complete: conversions, fonts UI, BIP log capture
- **Conversions (F-5.12/13/15)**: `EngineConversions` grew excel/etext/xpt; `BipConversions`
  reflects the exact TV-12 call sequences (mined from tmplviewer.jar bytecode): RTFProcessor
  (→XSL, +setXLIFFOutput), Excel2XSLParser.setXSLOutput, EFTProcessor with **setXSL = XSL
  output path** (needs sample data; report output discarded), OnlineReportProcessor
  createReportContext("ctxName")→createReportEngine→convertXmlDesignToXslfo→print,
  FOUtility.mergeFOs(String[]), XSLTProfiler.**addProfile4Layout(content)→content**
  (main() writes to a "-prf" file — call the method, not main). 4 new ITs green on real jars
  (RTF fixture must be WordPad-shaped; too-minimal RTF → "mHeaderrElement is null").
- **Tools menu** (Shell): the 5 generators + Merge FO + Inject profiling; background thread;
  XSL results open straight in the designer; disabled hint without BIP.
- **BIP log capture (F-5.10)**: XDOLogImpl.setDestination(XdoLogForwarder)+Logger.setLog per
  run at EVENT level, `[ts][module][LEVEL]`-parsed into run log; restore previous log after.
  Verified: CLI BIP run now shows Oracle-internal "FOProcessor.generate: XML-data-size=…".
- **Fonts… dialog** in ConfigPane (F-5.20): editable table → xdo.cfg `<fonts>` on save;
  `font-substitute.` stays available via free property keys.

### 2026-07-03 — M5 complete: F-1.29 conditional format + F-1.28 data tab
- **StyleRule model** on StaticBand + TableColumn; codegen emits rules as leading
  `xsl:if`+`xsl:attribute` setters (one line, deterministic attr order; HTML mode re-emits the
  whole merged `style` attr since XSLT attribute re-addition replaces). Reader recognizes
  exactly that leading shape back (non-leading rule-ifs stay opaque inlines); goldens updated,
  render test proves FOP accepts the conditional attrs. 48/48 tests.
- **PropertiesPane tabs now real**: Properties (binding/format/sort/condition + rules badge),
  Style (static style controls + F-1.29 rule builder rows: test+ƒx+B/I/color+✕, Add rule),
  Data (F-1.28: row count, first 10 rows via `ExpressionValidator.probe` walking the context
  chain, ✓-highlight per rule match, match summary). Canvas shows a "◈ n" rules chip.
- Rule tests evaluate in the band's group/row context; xdoxslt-based rules can't be probed
  locally (matched=false silently) — BIP-only semantics stay in the expr editor.

### 2026-07-03 — F-3.1 xdoxslt library + full test pass (commit 3be466b)
- **All 82 `xdoxslt:` functions** (doc 05) in `XdoFunctionCatalog` (stylus-config TSV) → engine-
  aware palette in the expression editor (category+search chips, signature tooltips, snippets
  with `$_XDOLOCALE/$_XDOTIMEZONE/$_XDOCTX`); shows only when bench targets BIP.
- Validator declares the xdoxslt ns; xdoxslt exprs → "✓ Valid (BI Publisher)", no local preview,
  warning when FOP targeted. Writer emits `xmlns:xdoxslt` exactly when used (round-trip tested).
- CLI run task workingDir = repo root (relative paths from `./stylus cli` now work).
- **Test pass: 47/47 green, 0 skipped** (13 classes, incl. 3 BIP ITs on real Oracle jars).
  E2E CLI: designed template → FOP PDF + BIP PDF (text verified), BIP RTF, FOP HTML.
- Note: BIP default-font PDF shows `—` oddly in extraction — revisit with xdo.cfg font mappings.
- NL descriptions for the 82 functions: deferred (signatures are EN; palette metadata i18n TODO).

### 2026-07-03 — Config editor + undo/redo (commits 1306aed, 5f00dc8)
- **xdo.cfg editor** in the bench (Run | Configuration tabs): ns-validated load/reload/save/
  save-as, full 194-property catalog table (search, only-set filter, groups, free custom keys),
  8-slot recent-config switcher, fop.xconf path field; **runs pass the active config per engine**.
- **Undo/redo** (F-1.39): snapshot-based in DesignerState (immutable bands → shallow-copy
  snapshots, structural sharing), bounded 100, Edit menu ⌘Z/⇧⌘Z, covers all edit paths.
- App relaunched & verified after each chunk (bench tabs render, no exceptions).

### 2026-07-03 — M5 core (commit 685850d)
- `ModelEdits` (replace/remove by identity with spine rebuild; contextChain) + tests.
- PropertiesPane edits bindings/masks/sort/condition/static-style + delete; ƒx buttons open
  the **ExpressionEditorOverlay** (dark code card, palette chips, live Saxon validate+preview
  against the sample in group context — `ExpressionValidator`, doc cached per file).
- InsertToolbar live: Field/For-each/if/Choose/Sort/XPath (into selected group); group-band
  bodies accept tree drops with path relativization (`DropFactory`); Saxon added to app deps.
- Still M6+: Variable/raw-XSLT/Number/Date/Page-break inserts, F-1.29 rule builder, F-1.28
  data tab, RichTextFX code editor, engine-capability badges, undo/redo (F-1.39!).

### 2026-07-03 — M4 designer core (commits 999f90a, 57e2731)
- **M4a (the N7 wall)**: full band model (Static/Group/Table/Conditional/Opaque + inline
  Text/Field/PageNumber/OpaqueInline, StyleProps subset, SortKey, FieldFormat) — immutable
  records; ReportDocument tracks originalSource+modified. XslWriter emits deterministic
  FO/HTML stylesheets; XslReader strictly recognizes exactly those shapes, everything else →
  opaque (serialized). **Guarantees tested (13 tests)**: unmodified docs re-save byte-identical;
  hand-written/non-XML files always byte-identical; opaque fragments survive edits verbatim;
  regeneration is a fixed point; generated template renders via FOP (PDFBox-verified — caught
  a real bug: top-level group paths must be document-rooted).
- **M4b**: XmlSampleTree (kind inference, sibling-based group detection); DataSourcePane real
  tree with glyphs/GROUP badges/search/tabs/drag; BandRenderer (handoff-styled group cards,
  detail grid, amber conditional, opaque cards, field chips, selection halo); CanvasPane
  renders model + drop-appends fields/groups; PropertiesPane read-only summary; Shell wires
  New/Open/OpenSample/Save/SaveAs + **code↔design sync on view switch**; unsaved dot.
- samples/invoice-designed.xsl = generated file that opens fully recognized (demo).
- Known M5 refinements: recognize `match="/Root"` element-rooted templates (now → fully-opaque);
  drop-position caret (drops append at end); enable toolbar insert actions; field chips render
  para style/color on canvas; RichTextFX code editor + validation markers (F-1.35/37).

### 2026-07-03 — M2 + M3 core (commits b620366, 777129c)
- **M2**: TestBenchPane (working dir, data/template panes, filter, engine+format+locale+xliff+
  style-template controls, Start/Cancel/progress/total-time, F5, double-click viewers), log pane
  with TV levels; RunService (background+cancel); PreviewPane (PDF→PDFBox images, HTML→WebView,
  FO/TXT→text, export/open); Shell menu (Open/Save As), bench drawer toggle; `samples/` dir.
  App verified on macOS. `-Dstylus.workingDir=` hook for dev runs.
- **M3 core**: `XdoConfig` (ns-validated load/save, fonts, xslt.* param extraction) +
  `PropertyCatalog` (194 props doc 04 + 6 prefixes); `BipInstallation.discover`;
  **ChildFirstClassLoader with platform parent** (full isolation); `BipEngine` reflection facade
  over FOProcessor with **FORMAT_\* probe** → local 12c exposes PDF/HTML/RTF/XLSX/PPTX/MHTML/
  PDFZ/FO (no ETEXT constant found — check name later); BipEngineIT renders a real PDF through
  Oracle runtime (PDFBox-verified). CLI `--bip-home`; registry auto-detects dev fixture lib/bip.
- **Empirical doc-07 correction: BIP 12c rejects `fo:page-number-citation-last`** ("not supported
  yet"). Matrix + doc 07 updated; portable sample uses plain page-number. "Page X of Y" on BIP
  will need `xdofo:` page-total — codegen must emit per engine (M4/M5 concern!).
- BIP version string: manifest carries none → shows "unknown version"; try
  `oracle.xdo.common.MetaInfo` fields later.
- Open M3 leftovers: settings UI for xdo.cfg/fop.xconf (F-5.16..22), multiple configs (F-5.18),
  conversions RTF→XSL etc. + FO merge + profiling (F-5.12..15), BIP log capture into log pane.

### 2026-07-03 — M0 + M1 built and green (commit d17f029)
- Env: installed Temurin 21 + Gradle 8.14.3 under `~/tools` (no admin), generated wrapper.
- `git init` (branch main); spec baseline committed first (7178df3); Oracle jars stay ignored.
- **M0**: 10-module Gradle build (`stylus-model/codegen/engine-api/engine-fop/engine-bip/
  bipserver/config/xliff/cli/app`), Kotlin DSL + version catalog + foojay toolchain; JavaFX shell
  per handoff (app bar/toolbars/3 panes/status bar, view switch swaps canvas↔code↔preview);
  design-token CSS (base + light/dark looked-up colors, runtime toggle); **bundled OFL fonts**
  Hanken Grotesk + JetBrains Mono in `stylus-app/.../fonts`; EN+NL bundles with parity test;
  golden-file harness with `-Dgolden.update=true` workflow; GitHub Actions CI.
- **M1**: engine SPI (`ReportEngine`, `RunRequest` builder, `CapabilityMatrix`, TV log levels);
  FOP engine — Saxon-HE s9api → FO string → FOP (PDF/PS/PCL/AFP/PNG/TIFF/TXT/IF), HTML direct
  transform, FO intermediate, `.fo` passthrough, fop.xconf; CLI (`render|engines|formats`).
  16 tests green incl. PDFBox text-extraction assertions.
- Versions: Saxon-HE 12.5, FOP 2.10, PDFBox 3.0.4, JavaFX 21.0.6, RichTextFX 0.11.5 (unused yet).
- Decision **D-3 resolved: hand-rolled CSS** from design tokens (no AtlantaFX) — exact handoff match.
- Deviation from docs/03 module list: added `stylus-cli` as 10th module (roadmap M1 needs a CLI;
  keeps engine-fop UI-free). Docs update pending.
- App launches on macOS (smoke-tested); UI toolbars are disabled chrome until M4/M5 wiring.

### 2026-07-02 — FO-structure design fundamental + capability matrix
- Captured a design fundamental across the docs: **XSL-FO is a tree of formatting objects** and the
  author must always know which one they're editing. Added catalog §1.8 (F-1.45…F-1.51): thin
  selectable hairline outline per FO element, element-level selection → properties panel, FO type
  chip, structure toggle, ancestor breadcrumb, raw-attributes editor, engine-support badge.
- Added `fo:layout-master-set`/conditional-page editing as first-class features — F-2.26 (layout
  master editor), F-2.27 (conditional-page-master matrix: first/last/rest/only × odd/even × blank).
- Wrote **doc 07 — engine capability matrix**: FOP vs BIP XSL-FO support (BIP = subset of FO 1.1) +
  `fox:`/`xdofo:`/`xdoxslt:` namespaces. Seeded from Oracle/FOP docs, refined by a runtime probe —
  **not** a jar extraction (unlike docs 04/05). Wired via F-2.25/F-2.28, F-1.50, F-11.2.
- Updated architecture doc 03 (FO structure model & selection), design handoff README §9 (outline
  visual spec + tokens, ruler `⧉ FO structure` toggle, `foStructureVisible` state), roadmap (M4/M5),
  CLAUDE.md key context.

### 2026-07-02 — Project preparation
- Mined design handoff, Template Viewer 12 help, and `xdocore.jar` (200 config properties,
  `xdoxslt:` function catalog) → wrote full spec `docs/01…06`, `README.md`, `CLAUDE.md`.
- Decisions taken: Java 21+ / JavaFX; pluggable engine SPI (FOP bundled, BIP user-supplied);
  EN+NL i18n; BIP server API (SOAP v2/REST) as optional catalog §13.
- Salvaged the 21 manifest-classpath Oracle jars + `tmplviewer.jar` into `lib/bip/`
  (28 MB, see `lib/bip/README.md`), deleted the rest of `resource/` (Template Viewer app bundle,
  help files — content fully captured in docs beforehand). Added `.gitignore` guarding the jars.
- Next: `git init` + M0 scaffold.
