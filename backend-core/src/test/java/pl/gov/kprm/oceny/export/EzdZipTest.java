package pl.gov.kprm.oceny.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EzdZipTest {
    @Test
    void packsSheetPdfAndKriMetadata() throws Exception {
        byte[] zip = EzdZip.build(18, "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8),
                "{\"caseId\":\"MERITUM/2/2025/18\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                entries.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsOnlyKeys("arkusz-oceny-18.pdf", "metadane-ezd.json");
        assertThat(entries.get("arkusz-oceny-18.pdf")).startsWith("%PDF");
        assertThat(entries.get("metadane-ezd.json")).contains("MERITUM/2/2025/18");
    }
}
