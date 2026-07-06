# Stylus — XSLT/XSL-FO Report Designer

WYSIWYG banded report designer for XSLT/XSL-FO templates targeting **Apache FOP** and
**Oracle BI Publisher**, plus a full BIP Template Viewer replacement (run bench, xdo.cfg editing,
XLIFF). Desktop app: **Java 21+, JavaFX**, Gradle multi-project (see `docs/03-architecture.md`).

## Read before working

- `MEMORY.md` (project root) — progression log: milestone status, open decisions, session
  history. **Update it at the end of any session that changes project state.**
- `docs/02-feature-catalog.md` — **master feature checklist with stable IDs (F-x.y)**. Every
  feature traces here; check boxes when features land; never delete entries (move to "parked").
- `design_handoff_xslt_report_designer/README.md` — source of truth for all visuals (design
  tokens, layout, components). The HTML prototype there is reference only, not production code.
- `docs/03-architecture.md` — module layout, engine SPI, round-trip strategy.

## Hard rules

1. **Never bundle/copy Oracle jars** (`lib/bip/*.jar` — gitignored) into builds or the repo —
   they are proprietary reference material. The BIP engine loads them at runtime from a
   user-supplied local installation via an isolated classloader; `lib/bip/` is only the local
   dev fixture for that adapter.
2. **Round-trip safety (N7)**: opening + saving a template must never alter hand-written XSLT the
   designer doesn't understand — unknown constructs become opaque nodes, re-emitted byte-identical.
3. **i18n**: no hardcoded UI strings; `messages_en.properties` is key-complete reference, `nl`
   must stay in parity.
4. Codegen changes ship with their golden-file test updates in the same commit.

## Key context

- Two output modes per template: pixel-perfect paginated (XSL-FO→PDF) and unlimited-width web (HTML).
- Oracle's Template Viewer 12 is the parity reference; its help doc + decompiled strings were
  mined into the feature catalog before the app bundle was deleted. Its runtime jars survive in
  `lib/bip/` (`tmplviewer.jar` included for further decompilation). The 200 xdo.cfg properties
  (doc 04) and xdoxslt function catalog (doc 05) were extracted from `xdocore.jar` — treat those
  docs as the authoritative lists.
- **XSL-FO is a tree of formatting objects** (`fo:block` ≠ `fo:inline` ≠ `fo:block-container` …).
  The author must always know which element they're editing: the canvas draws a thin selectable
  outline per FO element, selection is element-level, and it drives the properties panel
  (catalog §1.8, design handoff §9). `fo:layout-master-set` incl. conditional page masters is
  editable (F-2.26/F-2.27). **BIP implements only a subset of XSL-FO 1.1** — the FOP-vs-BIP
  support model is doc 07 (a seeded reference refined by a runtime probe, *not* a jar extraction).
- BIP server connectivity (SOAP v2 / REST) is optional and additive; disk-only must always work.
- UI languages v1: English + Dutch.
