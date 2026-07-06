package dev.stylus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StylusCliTest {

    @TempDir
    Path tmp;

    @Test
    void rendersPdfEndToEnd() throws Exception {
        Path template = Path.of(getClass().getResource("/hello-fo.xsl").toURI());
        Path data = Path.of(getClass().getResource("/hello.xml").toURI());
        Path out = tmp.resolve("hello.pdf");

        int code = StylusCli.execute(new String[] {
                "render",
                "--template", template.toString(),
                "--data", data.toString(),
                "--format", "pdf",
                "--output", out.toString(),
                "--verbose", "error",
        });

        assertEquals(0, code);
        assertTrue(Files.size(out) > 500, "no real PDF produced");
    }

    @Test
    void listsEnginesAndFormats() {
        assertEquals(0, StylusCli.execute(new String[] {"engines"}));
        assertEquals(0, StylusCli.execute(new String[] {"formats", "--engine", "fop"}));
    }

    @Test
    void rejectsBadArguments() {
        assertEquals(2, StylusCli.execute(new String[] {"render", "--nope"}));
        assertEquals(2, StylusCli.execute(new String[] {}));
        assertEquals(2, StylusCli.execute(new String[] {"frobnicate"}));
    }
}
