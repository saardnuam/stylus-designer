# Oracle BI Publisher 12c runtime jars (local reference/test fixture)

Salvaged from `Template Viewer 12.app/Contents` (BIP Desktop 12c, build 2018) before the bundle
was removed. The jar set is exactly the Template Viewer's manifest `Class-Path`
(Main-Class `oracle.xdo.runner.XDORunner`), plus `tmplviewer.jar` itself as the feature-parity
reference.

| Role | Jars |
|---|---|
| BIP core engine (FOProcessor, RTFProcessor, config, XLIFF) | `xdocore.jar` |
| XML/XSLT parsers (Oracle XDK) | `xmlparserv2.jar`, `xdoparser12c.jar`, `xdoparser11g.jar`, `xdoparser.jar` |
| i18n / locale / collation | `i18nAPI_v3.jar`, `orai18n-api.jar`, `orai18n-collation-api.jar`, `orai18n-mapping-api.jar` |
| Charts (BI Beans) | `dvt-jclient.jar`, `dvt-utils.jar` |
| PDF security / signing (OSDT) | `osdt_core.jar`, `osdt_cert.jar`, `osdt_cms.jar`, `osdt_smime.jar` |
| Logging / shared Oracle libs | `ojdl.jar`, `core.jar`, `share.jar`, `jewt-core-jewt4.jar`, `javase.jar` |
| Email delivery | `mail.jar` |
| Template Viewer app (parity reference, decompilation source) | `tmplviewer.jar` |

## ⚠️ License

**Oracle-proprietary. Never commit to a public repo, bundle into builds, or redistribute**
(CLAUDE.md hard rule #1). The shipped product loads BIP jars from a user-supplied installation;
this copy exists only for local development of the `stylus-engine-bip` adapter and for reference.
`lib/bip/*.jar` is gitignored.
