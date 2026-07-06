# 01 — Vision & Requirements

## Vision

A single, platform‑independent desktop application in which a report author can **design, test,
translate, and export** XSLT/XSL‑FO report templates for both **Apache FOP** and
**Oracle BI Publisher** — combining a modern WYSIWYG banded designer (see the design handoff) with
the full test‑bench capabilities of Oracle's BI Publisher Template Viewer.

## Primary users

1. **Report designers** — build templates visually, drag fields, set formats and conditions.
2. **XSLT developers** — work in the code view, write XPath/XSLT directly, debug FO output.
3. **Translators / functional staff** — preview templates per locale, work with XLIFF files.

## Functional scope (summary — full list in 02-feature-catalog.md)

1. **WYSIWYG banded designer** for XSLT + XSL‑FO templates (3‑pane IDE per design handoff).
2. **Two output modes** per template:
   - **Pixel‑perfect document** — paginated XSL‑FO (A4, Letter, custom page masters) → PDF etc.
   - **Web page, unlimited width** — HTML output, no pagination, fluid width.
3. **Every capability of XSLT 1.0/2.0 and XSL‑FO 1.1** must be reachable — visually where it maps
   to a designer concept, and always via the code view (the designer must never block a construct
   it cannot visualize; unknown constructs round‑trip untouched).
4. **BI Publisher extended functions** (`xdoxslt:`, `xdofo:`) available in the expression editor
   and function palette when the BIP engine is targeted.
5. **Template Viewer parity**: run templates against XML data, all BIP output formats, locale
   selection, `xdo.cfg` selection + property editing, parameter passing, log levels, XLIFF
   generate/apply, FO debugging tools, export.
6. **Subtemplates**: create, manage, import (`xsl:import`/`xsl:include`, BIP subtemplate call
   conventions), and test templates that reference them.
7. **SVG images**: insert and render SVG graphics in both engines (FOP via Batik;
   BIP native SVG support) and both output modes.
8. **Multilingual UI**: English and Dutch in v1; architecture ready for more locales.
9. **Plain disk workflow**: open/save templates and data as ordinary files — no lock-in.
10. **BI Publisher server connectivity**: retrieve and upload report templates (and fetch sample
    data) from/to a BI Publisher server via its official web service API; strictly optional —
    the app is fully functional offline.

## Non‑functional requirements

| # | Requirement | Notes |
|---|---|---|
| N1 | Platform independent | macOS (incl. Apple Silicon), Windows, Linux |
| N2 | Lightweight | Desktop app, fast startup (< 3 s warm), installer ≲ 100 MB, no server component |
| N3 | Java‑based | User preference; also required by FOP + BIP runtimes → single JVM process |
| N4 | Offline | No network required for any core function |
| N5 | License‑safe | Ship only OSS; Oracle jars are discovered on the user's machine, never bundled |
| N6 | Accessible | Keyboard navigable UI; generated output supports PDF/UA + accessibility properties |
| N7 | Robust round‑trip | Opening + saving a template must never destroy hand‑written XSLT |
| N8 | Localized | UI strings fully externalized; EN + NL shipped; locale‑aware output preview |
| N9 | Theming | Light + dark theme, themeable accent color (design tokens in handoff README) |
| N10 | Large data | Preview must handle multi‑MB sample XML without freezing the UI (background rendering) |

## Explicit user requirements (traceability)

| Req | Source | Covered in |
|---|---|---|
| XSLT report designer for Apache FOP **and** BI Publisher | user | catalog §2, §3, architecture §engines |
| All XSLT / XSL‑FO features | user | catalog §2 |
| BI Publisher extended functions | user | catalog §3, doc 05 |
| Pixel‑perfect document output | user | catalog §4 |
| Web page with unlimited width | user | catalog §4 |
| All Template Viewer configuration features | user + Template Viewer 12 (jars in `lib/bip/`) | catalog §5, doc 04 |
| Select different `xdo.cfg` files + edit parameters | user | catalog §5.3 |
| Subtemplates | user | catalog §6 |
| SVG images | user | catalog §7 |
| English + Dutch UI | user | catalog §9 |
| Platform independent, lightweight, "maybe Java" | user | architecture doc |
| Open/save files from disk | user | catalog §13.1 |
| Retrieve/upload reports from a BI server (BIP API) | user | catalog §13.2 |

## Out of scope for v1 (parked, do not lose)

- RTF / eText / Excel / XPT **template authoring** (BIP's other template types). We *consume* the
  Template Viewer's conversions (RTF→XSL etc.) but author only XSL templates. Parity tools that
  operate on those files (generate XSL from RTF/eText/Excel/XPT) are kept — they wrap engine calls.
- BIP charts via BI Beans (`dvt-jclient`); v1 covers charts through SVG instead.
- Bursting, delivery channels (email/FTP/print/fax/WebDAV), scheduling — server‑side BIP concerns.
- Data source design (SQL queries etc.) — Stylus consumes XML sample files, like Template Viewer.
- Collaborative/multi‑user editing.
