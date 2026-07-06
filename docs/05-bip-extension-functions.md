# 05 — BI Publisher Extension Functions & Constructs

Catalog for the expression editor's function palette and the engine capability matrix (F-2.24,
F-3.x). Names verified against `oracle/xdo/template/rtf/XSLTFunctions.class` in `xdocore.jar`
(12c); semantics per the *Report Designer's Guide* "Extended Function Support" appendix.
Namespace: `xmlns:xdoxslt="http://www.oracle.com/XSL/Transform/java/oracle.apps.xdo.template.rtf.XSLTFunctions"`.

Palette metadata to build per function: signature, description (EN+NL), category, insert snippet,
example, min-engine (BIP only vs both).

## `xdoxslt:` functions by category

### Date & time
| Function | Purpose |
|---|---|
| `current_date(loc,tz)` / `current_time(loc,tz)` | Current date/time for locale+timezone |
| `sysdate()` / `sysdate_as_xsdformat()` | System date, raw / XSD format |
| `format_date` / `format_date_tz` | Format XSD date with Oracle mask |
| `ora_format_date` / `ora_format_date_offset` / `ora_format_date_tz` | Oracle-style date formatting/offsets |
| `xdo_format_date` (+ `_c`, `_c_tz`, `_l`, `_nt`, `_tz` variants), `xdo_format_sysdate` | Locale/calendar-aware date masks |
| `ms_format_date` / `ms_format_date_tz` / `ms_sysdate` | Microsoft-mask date formatting (RTF heritage) |
| `date_diff(fmt,d1,d2,loc,tz)` / `sec_diff` | Difference in given unit / seconds |
| `get_day` / `get_month` / `get_year` / `month_name(n,abbr,loc)` | Date parts |
| `increase_date(d,n)` / `decrease_date(d,n)` | Add/subtract days |
| `maximum_date` / `minimum_date` | Aggregate over node-set |
| `date_to_millis` / `millis_to_date` / `time_in_millis` | Epoch conversions |
| `convertMSDateFormat`, `parsePLSQLDateFormat` | Mask conversions (internal but present) |

### Number
| Function | Purpose |
|---|---|
| `format_number(v,mask,loc)` / `xdo_format_number` (+ `_l`, `_lc`) | Locale-aware number masks |
| `pat_format_number` / `format_bigdecimal_number` / `ms_format_number` | Pattern / BigDecimal / MS-mask variants |
| `xdo_format_currency` / currency masks via `currency-format.` config | Currency formatting |
| `to_number` / `to_char` | Oracle-style conversions |
| `abs`, `round`, `is_numeric` | Numeric helpers |
| `convert_base` (+ `_nl`, `_s`) | Base conversions |
| `integer_part` / `decimal_part` / `power_part` | Number decomposition (eText) |
| `german_eft_string`, amount-to-words helpers (`toWordsAmt`, crores/lakhs) | EFT/words output (eText heritage) |

### String
| Function | Purpose |
|---|---|
| `convert_case`, `init_cap` | Case conversion, initial caps |
| `lpad` / `rpad` / `lpadb` / `rpadb` (+ `lpadext`/`rpadext`) | Padding (byte-aware variants) |
| `instr`, `index_of` | Substring position |
| `left`, `right`, `substringb`, `truncate`, `replicate` | Substring/repeat (byte-aware) |
| `mtranslate`, `ora_translate` | translate() variants |
| `normalize_string`, `chr` | Normalization, char from code |
| `str_to_link_id`, `url-encode` helpers | Link/encoding utilities |
| `is_bidi_locale`, BiDi string helpers | Bidirectional text support |

### Aggregates, variables & flow
| Function | Purpose |
|---|---|
| `minimum(ns)` / `maximum(ns)` | Min/max over node-set |
| `ifelse(cond,a,b)` | Inline conditional |
| `set_variable(ctx,name,val)` / `get_variable(ctx,name)` | Mutable running variables (running totals) |
| `distinct_values(ns)` | Distinct node values |
| `string_to_nodelist`, `intersect_nodelists`, `contains_node`, `wrap_node`, `next_element` / `prev_element` | Node-set utilities |
| `create_groups` / `get_groups` / group-array functions | Runtime regrouping |
| `sequence_number` / `next_sequence_number` / `reset_sequence_number` / `create_sequence_number` | Sequence counters |
| `create_block_counter` / `increment_block_counter` / `fill_block` | eText block counters |
| `foreach_number(ctx,from,to,step)` | Numeric loop source |
| `get_xdo_property` / XDO property access, `getContextLocale` | Runtime context |

### Barcodes, charts & images
| Function | Purpose |
|---|---|
| `format_barcode(data,vendorClass,method)` | Barcode via registered encoder |
| `register_barcode_vendor(class,id)` | Register barcode encoder class |
| QR / PDF417 generation (`generateQRCodeAsBase64`, `generatePDF417AsBase64`, `qrcode`, `pdf417`) | 2-D barcodes |
| `chart_svg`, `gauge_svg` (`generateChartAsBase64`, `generateGaugeAsBase64`) | Data-driven SVG charts/gauges |
| `image_extension`, `renderExtensionImage` | Dynamic image handling |

### Diagnostics
`stopwatch_start` / `stopwatch_stop` / `stopwatch_print` (pairs with "Inject Profiling into XSL",
F-5.13) · `xdo-debug-level` interplay · `clean_cache`

## `xdofo:` constructs (FO-namespace extensions)

- `<xdofo:pagetotal name="…">` + brought-forward/carried-forward page totals
- `xdofo:ctx="…"` context attributes (e.g. `incontext`, section/table contexts) controlling where
  generated FO lands
- Dynamic data-driven formatting attributes (row/cell formatting from data)
- Last-page-only content (`xdofo:` + page-master tricks), total-pages handling
- `<?...?>` BIP field syntax appears only in RTF templates — **not** our concern except when
  importing RTF→XSL conversions, where it is already compiled away.

## Related BIP namespaces to recognize/preserve on import

- `xmlns:xdofo="http://xmlns.oracle.com/oxp/fo/extensions"`
- `xmlns:xdoxslt="…XSLTFunctions"` (above)
- `xmlns:xdoxliff` / XLIFF-related attributes
- Barcode util: `oracle.xdo.template.rtf.util.XDOBarcodeUtil`

## Engine matrix note (F-2.25)

All `xdoxslt:`/`xdofo:` items above: **BIP engine only** → the designer must flag them when the
FOP engine is selected. Standard XPath/XSLT/FO and SVG work on both. FOP `fox:` extensions are
FOP-only and flagged inversely.
