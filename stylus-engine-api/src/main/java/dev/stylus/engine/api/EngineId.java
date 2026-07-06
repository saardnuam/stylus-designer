package dev.stylus.engine.api;

/** Identifies a report engine implementation (docs/03 §engine abstraction). */
public enum EngineId {
    /** Apache FOP + Saxon-HE — bundled, always available (F-12.4). */
    FOP,
    /** Oracle BI Publisher — user-supplied local installation, loaded via isolated classloader. */
    BIP
}
