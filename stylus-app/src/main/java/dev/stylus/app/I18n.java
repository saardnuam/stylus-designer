package dev.stylus.app;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * All user-visible strings go through here (hard rule 3, F-9.1): {@code messages_en.properties}
 * is the key-complete reference, {@code nl} stays in parity (CI-checked).
 */
public final class I18n {

    public static final String BUNDLE = "dev.stylus.app.i18n.messages";

    private static volatile ResourceBundle bundle = load(Locale.getDefault());

    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String t(String key, Object... args) {
        return MessageFormat.format(t(key), args);
    }

    /** Switch UI language at runtime (F-9.2); callers rebuild the scene afterwards. */
    public static void setLocale(Locale locale) {
        bundle = load(locale);
    }

    public static Locale currentLocale() {
        return bundle.getLocale().getLanguage().isEmpty() ? Locale.ENGLISH : bundle.getLocale();
    }

    private static ResourceBundle load(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale);
    }

    private I18n() { }
}
