# Stylus — XSLT / XSL‑FO Report Designer

Stylus is a banded, WYSIWYG report designer for building **XSLT / XSL‑FO templates**, targeting
**Apache FOP** and **Oracle BI Publisher** as rendering engines. Report authors drag fields from an
XML data source onto a paginated canvas, nest repeating groups, add conditional logic, and edit
XPath expressions — producing either a **pixel‑perfect document** (A4/Letter via XSL‑FO) or a
**web page with unlimited width** (HTML).

It also replaces the Oracle **BI Publisher Template Viewer** as a desktop test bench: run any
template against sample XML, switch output formats, load and edit `xdo.cfg` configuration files,
pass parameters, apply XLIFF translations, and inspect engine logs.

## Running

```sh
./stylus            # start the designer (bench opens your last working dir)
./stylus samples    # start with the bundled samples/ (invoice data + templates)
./stylus <dir>      # start with a specific working directory
./stylus cli …      # headless CLI: render / engines / formats
```

Or double-click **`Stylus.command`** in Finder. The launcher finds a JDK 21+ by itself
(`$STYLUS_JAVA_HOME` → `$JAVA_HOME` → `~/tools/jdk-21*` → `java_home`); the first run downloads
dependencies via the Gradle wrapper.

Quick tour with the samples: double-click `invoice-designed.xsl` in the test bench (recognized
onto the canvas), **⌘D** to load `invoice.xml` into the data tree, drag fields onto the paper,
**ƒx** edits expressions with live preview, **Run & Preview** renders the real PDF.

## Status

**In active development** — M0–M4 complete, M3/M5 largely complete (see the tracker in
[MEMORY.md](MEMORY.md)). Working today: the 3-pane designer shell (light/dark), banded canvas
with drag & drop and round-trip-safe codegen (N7), Template-Viewer-style test bench with the
`xdo.cfg` editor, FOP engine (bundled), BI Publisher engine via a user-supplied local
installation, EN/NL UI. The complete specification lives in [docs/](docs/):

| Doc | Contents |
|---|---|
| [01-vision-and-requirements.md](docs/01-vision-and-requirements.md) | Goals, scope, non‑functional requirements |
| [02-feature-catalog.md](docs/02-feature-catalog.md) | **The master feature checklist** — every feature, nothing forgotten |
| [03-architecture.md](docs/03-architecture.md) | Tech stack decision, module layout, engine abstraction, licensing |
| [04-bip-configuration-properties.md](docs/04-bip-configuration-properties.md) | Full `xdo.cfg` property catalog (extracted from `xdocore.jar` 12c) |
| [05-bip-extension-functions.md](docs/05-bip-extension-functions.md) | `xdoxslt:` / `xdofo:` extension function & construct catalog |
| [06-roadmap.md](docs/06-roadmap.md) | Milestones M0–M7 |

## Source material

- [design_handoff_xslt_report_designer/](design_handoff_xslt_report_designer/) — high‑fidelity UI design
  (light + dark theme), with a self‑sufficient spec in its README and an interactive HTML prototype.
  This is the **source of truth for visuals**.
- [lib/bip/](lib/bip/) — the Oracle BI Publisher 12c runtime jars + `tmplviewer.jar`, salvaged
  from the (since deleted) Template Viewer 12 app bundle. Oracle‑proprietary, gitignored,
  **never redistributable** — see [lib/bip/README.md](lib/bip/README.md) and the architecture doc.
- [MEMORY.md](MEMORY.md) — progression log: milestone tracker, open decisions, session history.

## Key decisions (summary)

- **Platform**: Java 21+, JavaFX UI — one codebase for macOS / Windows / Linux, lightweight
  (~60 MB installer via `jpackage`), and the same JVM that Apache FOP and the BIP runtime need anyway.
- **Engines are pluggable**: Apache FOP is bundled; the Oracle BIP engine is loaded at runtime from a
  user‑supplied BI Publisher Desktop / Template Viewer installation (license‑safe).
- **UI languages**: English + Dutch in v1.
