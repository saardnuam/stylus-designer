package dev.stylus.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hard rule 3 / F-9.1: messages_en is the key-complete reference; nl must stay in exact
 * key parity. This test is the CI enforcement (roadmap standing rule 3).
 */
class BundleParityTest {

    @Test
    void englishAndDutchBundlesHaveIdenticalKeys() throws IOException {
        Set<String> en = keys("/dev/stylus/app/i18n/messages_en.properties");
        Set<String> nl = keys("/dev/stylus/app/i18n/messages_nl.properties");

        Set<String> missingInNl = new TreeSet<>(en);
        missingInNl.removeAll(nl);
        Set<String> extraInNl = new TreeSet<>(nl);
        extraInNl.removeAll(en);

        assertEquals(Set.of(), missingInNl, "keys missing in messages_nl.properties");
        assertEquals(Set.of(), extraInNl, "keys in nl that are not in the en reference");
    }

    @Test
    void noEmptyValues() throws IOException {
        for (String bundle : new String[] {
                "/dev/stylus/app/i18n/messages_en.properties",
                "/dev/stylus/app/i18n/messages_nl.properties"}) {
            Properties p = load(bundle);
            for (String key : p.stringPropertyNames()) {
                assertFalse(p.getProperty(key).isBlank(), "blank value for " + key + " in " + bundle);
            }
        }
    }

    private Set<String> keys(String resource) throws IOException {
        return load(resource).stringPropertyNames();
    }

    private Properties load(String resource) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "bundle not found: " + resource);
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }
}
