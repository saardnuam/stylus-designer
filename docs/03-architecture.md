# 03 — Architecture & Tech Stack

## Stack decision

**Java 21+ (LTS) with JavaFX.** Rationale:

1. **The engines dictate the JVM anyway.** Apache FOP is Java; the BIP runtime (`xdocore.jar` et al.)
   is Java. Any non-Java UI (Electron, Tauri, Flutter) would still have to spawn/embed a JVM —
   two runtimes, heavier, slower. One JVM process hosts UI + engines and matches the user's
   "lightweight, maybe Java" preference.
2. **Platform independence** — one codebase for macOS/Windows/Linux, `jpackage` native installers
   with a jlink-trimmed runtime (~50–80 MB), no server component.
3. **JavaFX over Swing**: CSS-based theming maps almost 1:1 onto the design handoff's token system
   (light/dark, accent variable), proper HiDPI, animation, and a WebView for HTML preview.

Key libraries (all OSS, redistributable):

| Concern | Library |
|---|---|
| UI toolkit | JavaFX 21+ (controls, FXML optional — prefer plain Java + CSS) |
| Modern theming base | AtlantaFX (or hand-rolled CSS from the design tokens) |
| Code editor (Code view, expression editor) | RichTextFX + custom XSLT/XPath lexers |
| XSLT 1.0/2.0/3.0 for FOP pipeline + expression preview | Saxon-HE |
| XSL-FO → PDF/PS/PCL/AFP/PNG/TIFF/TXT | Apache FOP (+ Batik for SVG, XML Graphics Commons) |
| PDF preview rendering | Apache PDFBox (render pages to images in canvas/preview) |
| HTML preview | JavaFX WebView |
| BIP SOAP v2 services (catalog/report/security) | Plain JAX-WS or hand-rolled SOAP over `java.net.http` (keep it light — the v2 WSDL surface we need is small) |
| BIP REST (12c+) | `java.net.http` + Jackson |
| Credential storage | OS keychain via `java-keyring` (fallback: encrypted file) |
| i18n | `ResourceBundle` (`messages_en.properties`, `messages_nl.properties`) |
| Build | Gradle (Kotlin DSL), `jpackage` via Badass JLink/Runtime plugin |
| Tests | JUnit 5, TestFX (UI), golden-file snapshots for codegen |

## Module layout (Gradle multi-project)

```
stylus/
├─ stylus-model        # Report document model (bands, tokens, bindings, conditions) — no UI deps
├─ stylus-codegen      # Model → XSLT/XSL-FO writer + XSL → model reader (round-trip, N7)
├─ stylus-engine-api   # Engine SPI: capabilities, run(template,data,cfg,params,locale,xliff) → output/log
├─ stylus-engine-fop   # Bundled: Saxon + FOP implementation, fop.xconf handling
├─ stylus-engine-bip   # Oracle adapter: loads user-local BIP jars via isolated classloader (reflection)
├─ stylus-bipserver    # BIP web service client: catalog browse, download/upload, sample data
├─ stylus-config       # xdo.cfg + fop.xconf models, property catalog (doc 04) with metadata
├─ stylus-xliff        # XLIFF generate/apply
└─ stylus-app          # JavaFX application: shell, canvas, panels, preview, test bench, i18n
```

## Engine abstraction (the load-bearing decision)

```java
interface ReportEngine {
    EngineId id();                          // FOP | BIP
    Set<OutputFormat> supportedFormats();   // PDF, HTML, RTF, XLSX, ETEXT, ...
    CapabilityMatrix capabilities();        // FO subset, extension namespaces (F-2.25)
    RunResult run(RunRequest req);          // template, data, config, params, locale, xliff, styleTemplate
    Optional<Transformer> conversions();    // RTF→XSL, eText→XSL, FO merge, profiling injection (F-5.15)
}
```

- **FOP engine** is bundled and always available → the app is complete without any Oracle software
  (F-12.4).
- **BIP engine** discovers a local *BI Publisher Desktop / Template Viewer* installation
  (user points at it once; we validate `xdocore.jar`, `xdoparser*.jar`, …) and loads it in a
  **child-first isolated classloader**, calling the same entry points the Template Viewer uses
  (`oracle.xdo.template.FOProcessor`, `RTFProcessor`, `oracle.apps.xdo.common.config` reader, …)
  via a thin reflection facade.

### Licensing constraint (do not violate)

`lib/bip/*.jar` (salvaged from the Template Viewer 12 bundle, gitignored) are **Oracle-proprietary**.
They are reference material and a local test fixture only. **Never bundle, copy, or redistribute
them** in builds or the repo history of released artifacts. The BIP engine must always load from a
user-supplied path. Everything we ship is Apache-2.0-compatible OSS.

## Document model & round-trip (N7 — second load-bearing decision)

- The **model is the source of truth in Design view**; `stylus-codegen` emits clean, deterministic,
  commented XSLT+FO (golden-file tested, F-11.4).
- **Recognition on load**: the reader maps known patterns (for-each bands, conditional blocks,
  field tokens, page masters) back into the model. Anything unrecognized becomes an **opaque node**
  that renders as a generic "XSLT block" band and is re-emitted byte-identical.
- Designer metadata (band names, sample pairings, UI hints) lives in a **sidecar file**
  (`Invoice.xsl` + `Invoice.stylus.json`) — templates stay 100% engine-clean. (Decision F-10.1:
  sidecar over embedded comments; revisit in M2 if sync proves brittle.)
- Code view edits re-parse into the model on switch; parse errors keep you in code view with
  markers rather than corrupting the model.

## FO structure model & element selection (make the FO tree editable)

A design fundamental: the author must always know **which XSL-FO formatting object** they are
editing (`fo:block` ≠ `fo:inline` ≠ `fo:block-container` ≠ a table cell). The model reflects this —
each recognized FO element is a **model node** with its own property set — and the Design-view
canvas mirrors it:

- Every model node projects to a canvas node carrying a **1px hairline outline** (near-invisible at
  rest, hover-brightened, accent-halo when selected) so block/inline/container structure is
  visible, not just content (F-1.45..F-1.48). Opaque round-trip nodes (N7) still get a generic
  outline + type label so unmapped hand-written FO stays selectable.
- **Selection is element-level.** Clicking an outline (or an FO ancestor breadcrumb crumb, F-1.49)
  sets `selectedElementId` to that FO node and binds the Properties/Style panel to *that element's*
  properties — the mapped set via the Style UI (F-2.19) plus a raw-attributes editor for the rest.
  The panel header shows the FO type chip (`fo:block`, …).
- **Page geometry** (`fo:layout-master-set`) is a first-class part of the model: multiple
  `simple-page-master`s, `page-sequence-master`s, and the full **conditional-page-master-reference**
  matrix (first/last/rest/only × odd/even × blank/not-blank) are editable objects, not codegen-only
  (F-2.7, F-2.26, F-2.27), and round-trip byte-safe.
- The **engine capability matrix** (`stylus-config`, doc 07) annotates each node/attribute with
  per-engine support. Because **BIP implements only a subset of XSL-FO 1.1**, nodes unsupported by
  the active engine get an inline support badge (F-1.50) and feed the validation warnings panel
  (F-11.2). The matrix is a static reference model refined by a runtime capability probe against the
  user's installed BIP jars.

## Preview pipeline

```
sample.xml ─┐
            ├─ Engine.run(...) ──► PDF ──► PDFBox ──► page images (pixel-perfect preview)
template ───┤                 └──► HTML ──► WebView  (web / unlimited-width preview)
xdo.cfg /   │                 └──► RTF/XLSX/eText… ──► export / open externally
params ─────┘
```

- All runs on background threads with progress + cancel (Template Viewer parity, N10).
- Design-view canvas is a *live approximation* (JavaFX nodes); Preview is *engine truth*.
- Expression editor preview: Saxon evaluates the XPath against the sample XML in the current
  group context (F-1.32).

## BIP server connectivity (`stylus-bipserver`)

- SOAP v2 endpoints (`/xmlpserver/services/v2/CatalogService|ReportService|SecurityService`) for
  11g/12c compatibility; REST where the server offers it. Version-detect and hide what a server
  can't do.
- Catalog tree browser → download layout templates/subtemplates/sample data into the local
  project; upload publishes a template to a report with an overwrite confirmation and a
  changed-on-server conflict check (F-13.10).
- Credentials in OS keychain; connections are optional and never required for any local feature.

## i18n

- Every user-visible string through `ResourceBundle`; `en` is the key-complete reference, `nl`
  ships complete at v1 (F-9.1). CI check: bundle key parity.
- Locale switch in settings; output-locale (report) is independent of UI locale.

## Testing strategy

1. `stylus-codegen` golden files: model fixtures → expected XSL (and reverse).
2. Engine integration: sample templates × {FOP} rendered in CI (PDF checksum/text extraction);
   BIP engine tested locally only (jars can't be in CI).
3. TestFX smoke tests for the shell; screenshot diffs for canvas rendering.
4. XLIFF + config round-trip tests.
