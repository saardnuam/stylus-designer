# 07 — Engine Capability Matrix (XSL-FO subset per engine)

The reference model behind **F-2.25 / F-2.28** (engine warnings), **F-1.50** (per-element support
badges in the FO structure overlay), and **F-11.2** (validation warnings panel). It answers one
question the designer asks constantly: *"is this XSL-FO element / attribute / extension supported by
the engine I'm targeting?"*

**Design fundamental captured here:** _Apache FOP and Oracle BI Publisher do **not** implement the
same XSL-FO surface._ FOP tracks XSL-FO 1.1 closely; **BIP implements a subset** (its FO processor
descends from an older Oracle implementation) and adds its own `xdofo:`/`xdoxslt:` extensions, while
FOP adds `fox:`. The designer must not silently emit something the target engine drops.

> **Provenance / accuracy.** Unlike docs 04 (xdo.cfg properties) and 05 (`xdoxslt:` functions),
> which were *extracted* from `xdocore.jar`, this matrix is **seeded** from Oracle's *BI Publisher
> Report Designer's Guide* + FOP's compliance page + known field behaviour. Treat the BIP column as
> a **reference default, not gospel** — exact support varies by BIP version. The app **refines it at
> runtime** with a capability probe against the user's installed BIP jars (see below), and every
> warning is advisory: the code view never blocks emitting any construct (N7).

Legend: ✅ supported · ⚠️ partial / version-dependent / quirks · ❌ not supported · — not applicable.

## XSL-FO 1.1 formatting objects

| Object group | Elements | FOP | BIP | Designer treatment |
|---|---|---|---|---|
| **Layout** | `layout-master-set`, `simple-page-master`, `region-*` | ✅ | ✅ | Page-master editor (F-2.7, F-2.26) |
| **Conditional pages** | `page-sequence-master`, `single-`/`repeatable-`/`conditional-page-master-reference` | ✅ | ✅ | Conditional-page matrix editor (F-2.27) |
| **Multi-column body** | `region-body/@column-count`,`@column-gap` | ✅ | ⚠️ | Expose; badge ⚠️ on BIP |
| **Flow / sequence** | `page-sequence`, `flow`, `static-content`, `title` | ✅ | ✅ | Header/footer + body bands |
| **Blocks** | `block`, `block-container` (incl. absolute position) | ✅ | ✅ | Core; hairline outline + Style tab (F-1.45) |
| **Inlines** | `inline`, `inline-container`, `wrapper`, `character` | ✅ | ⚠️ | `inline-container` badge ⚠️ on BIP |
| **Tables** | `table`, `table-column`, header/footer/body, row, cell, spans | ✅ | ✅ | Table editor (F-2.10) |
| **Table markers (1.1)** | `retrieve-table-marker` | ✅ | ❌ | Insert only under FOP; badge ❌ on BIP |
| **Lists** | `list-block`, `list-item`, `list-item-label`/`-body` | ✅ | ✅ | List UI (F-2.11) |
| **Graphics** | `external-graphic`, `instream-foreign-object` (SVG) | ✅ | ✅ | Image/SVG insert (F-7.x) — both render SVG |
| **Page numbering** | `page-number`, `page-number-citation(-last)` | ✅ | ✅ | Tokens (F-2.13) |
| **Leaders** | `leader` (rules, dots, TOC fills) | ✅ | ✅ | Leader insert (F-2.17) |
| **Markers** | `marker`, `retrieve-marker` (running heads) | ✅ | ⚠️ | Expose; verify per BIP version |
| **Footnotes** | `footnote`, `footnote-body` | ✅ | ⚠️ | Insert; badge ⚠️ on BIP |
| **Floats** | `float` (before), side floats | ⚠️ | ❌ | Side floats badge ❌ on BIP |
| **Links / nav** | `basic-link` | ✅ | ✅ | Link UI (F-2.18) |
| **Bookmarks (1.1)** | `bookmark-tree`, `bookmark`, `bookmark-title` | ✅ | ⚠️ | BIP often via `fox:`/native path — verify |
| **Index (1.1)** | `index-page-citation-list`, `index-key-reference`, `index-*` | ⚠️ | ❌ | Code-view only; badge ❌ on BIP |
| **Change bars (1.1)** | `change-bar-begin`, `change-bar-end` | ⚠️ | ❌ | Code-view only; badge ❌ on BIP |
| **Writing mode / BiDi** | `writing-mode`, `bidi-override`, RTL | ✅ | ⚠️ | RTL preview (F-2.20); verify masks per BIP |
| **Absolute positioning** | `block-container` absolute/fixed | ✅ | ✅ | Pixel-perfect forms (F-2.21) |

## Extension namespaces

| Namespace | Purpose | FOP | BIP | Designer treatment |
|---|---|---|---|---|
| `fox:` (Apache) | bookmarks, `external-document`, transparency, embedded files (F-2.22) | ✅ | ❌ | Insert/palette only when FOP active; badge ❌ on BIP |
| `xdofo:` (Oracle) | page totals, contexts, data-driven formatting (F-3.2) | ❌ | ✅ | Insert/palette only when BIP active; badge ❌ on FOP |
| `xdoxslt:` (Oracle) | extension function library (doc 05, F-3.1) | ❌ | ✅ | Function palette gated to BIP (F-1.31) |

## Attribute-level notes (common gotchas)

- **Property masks**: BIP resolves Oracle-style number/date masks (doc 05) that FOP does not; FOP
  relies on `format-number()` / XSLT date functions. The mask editor is engine-aware.
- **Fonts**: FOP resolves fonts via `fop.xconf` (F-2.23); BIP via `xdo.cfg` font section (F-5.20).
  A font referenced but not registered for the active engine is a ⚠️ warning, not an FO error.
- **PDF flavors** (PDF/A, PDF/X, PDF/UA, encryption, signatures) are engine-config driven, not FO
  elements — see doc 04 (BIP) and F-2.23 (FOP), tracked under F-4.9.

## Runtime capability probe (refines this table)

- On BIP discovery (F-12.3), `stylus-config` records the detected BIP version and, where feasible,
  probes the loaded processor for the objects/extensions above; results **override** the seeded BIP
  column for that installation so warnings match reality, not this document's defaults.
- FOP's column is fixed to the bundled FOP version (we ship it), so it needs no probe.
- The resolved matrix is what F-1.50, F-2.25/F-2.28, and F-11.2 consult; when the two engines
  disagree on an element, switching the active engine re-badges the canvas live.

## How a warning surfaces (recap)

1. **In the FO structure overlay** — an unsupported element's type badge gets an amber (⚠️) or red
   (❌) dot with a hover explanation + fallback (F-1.50; design handoff §9).
2. **In insert menus / function palette** — actions for the *inactive*-engine namespace are greyed
   or annotated; `xdoxslt:` chips hide under FOP, `fox:` inserts hide under BIP.
3. **In the warnings panel** — F-11.2 lists every used-but-unsupported construct for the target
   engine before a run, with jump-to-node.
