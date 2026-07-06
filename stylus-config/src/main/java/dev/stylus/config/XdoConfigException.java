package dev.stylus.config;

/** Invalid or non-BIP configuration file (Template Viewer parity message, F-5.17). */
public class XdoConfigException extends RuntimeException {

    public XdoConfigException(String message) {
        super(message);
    }

    public XdoConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
