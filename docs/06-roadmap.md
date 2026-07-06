# 06 — Roadmap

Milestones ordered so every stage yields something runnable. Feature IDs refer to
[02-feature-catalog.md](02-feature-catalog.md).

## M0 — Foundation (repo & walking skeleton)
- Gradle multi-project per architecture doc; Java 21 + JavaFX; CI (build, test, jlink smoke)
- Empty 3-pane shell with theming from design tokens (light+dark, accent) — F-1.1, F-1.7, F-1.8
- i18n plumbing with EN+NL bundles from day one (retrofitting is what gets forgotten) — F-9.1
- Golden-file test harness skeleton — F-11.4

## M1 — Engine core (headless value first)
- `stylus-engine-api` SPI + FOP engine: XML+XSL → PDF/HTML/… (Saxon + FOP) — F-4.4
- `fop.xconf` handling; log capture; background runs with cancel — F-5.6, F-5.10, F-5.22
- Generate-XSL-FO intermediate output — F-5.11
- CLI entry point (also enables CI rendering tests) — F-11.5

## M2 — Test bench UI (Template Viewer replacement usable here)
- Run panel: working dir, data/template panes, output format, locale, start/export/open — F-5.1…F-5.8
- Log pane with levels — F-5.9
- Plain disk open/save — F-13.1, F-13.2
- **Decision checkpoint: sidecar metadata format (F-10.1)**

## M3 — BIP engine + configuration
- BIP discovery + isolated classloader adapter; FOP-only graceful mode — F-12.3, F-12.4
- All BIP output formats — F-4.5
- Settings UI: xdo.cfg load/edit/save, multiple configs, fonts, full property catalog (doc 04),
  template parameters — F-5.16…F-5.23
- Conversion & debug tools: RTF/eText/Excel/XPT→XSL, FO merge, profiling injection — F-5.13…F-5.15

## M4 — Designer canvas MVP (the product moment)
- Document model + codegen with opaque-node round-trip — F-10.x, F-1.36
- Data tree with drag & drop; bands: static text, field, for-each (nested), detail table — F-1.10…F-1.24
- Code view (RichTextFX) with two-way sync + validation — F-1.35…F-1.38
- Undo/redo, selection, clipboard — F-1.39…F-1.41
- **FO structure overlay**: hairline element outlines, element-level selection, type badges,
  ancestor breadcrumb, structure toggle — F-1.45…F-1.51
- Page setup (pixel-perfect) + web mode toggle — F-2.7, F-4.1…F-4.3
- Layout-master-set + conditional-page-master editor — F-2.26, F-2.27

## M5 — Expressions, conditions & properties
- Properties panel: binding, format masks, style tab, data tab — F-1.25…F-1.28
- Conditional bands (if/choose) + conditional formatting rules — F-1.18, F-1.29
- Expression editor: highlighting, palette (std + xdoxslt per engine), live validate/preview — F-1.30…F-1.34
- FO element property editing (mapped set + raw-attributes editor, FO type header) — F-1.46, F-1.50
- Engine capability warnings + capability matrix (doc 07) badges/probe — F-2.25, F-2.28
- Sort, xsl:number, page-number tokens, keeps/breaks UI — F-2.13, F-2.14

## M6 — Subtemplates, SVG, translation
- Subtemplate management, import/include, call-template UI, subtemplate browser — F-6.x
- Images + SVG insertion, sizing, backgrounds/watermarks — F-7.x
- XLIFF generate/apply + locale preview; xliff-trans-* properties — F-9.4…F-9.7
- Markers/running headers, footnotes, leaders, links/bookmarks via insert menu — F-2.15…F-2.18

## M7 — BI server, polish & release
- BIP server connections, catalog browse, download/upload with conflict check — F-13.3…F-13.10
- Full NL translation pass, accessibility pass, keyboard map — F-9.x, N6
- jpackage installers for macOS/Windows/Linux; onboarding wizards — F-12.x, F-10.4
- PDF/A / PDF/UA / signature configuration surfaced — F-4.9

## Standing rules
1. Every merged feature checks its box in doc 02 (traceability).
2. No Oracle jar ever enters the repo or a build artifact.
3. `en` bundle is the string source of truth; `nl` parity enforced in CI.
4. Codegen changes require golden-file updates in the same commit.
