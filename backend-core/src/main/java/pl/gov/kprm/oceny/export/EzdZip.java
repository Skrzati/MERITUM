package pl.gov.kprm.oceny.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Paczka do rejestracji w EZD RP: arkusz PDF/A-2b + metadane KRI (JSON) — standardowy ZIP bez dodatkowych bibliotek. */
public final class EzdZip {
    public static final String METADATA_FILE = "metadane-ezd.json";

    private EzdZip() {
    }

    public static String pdfName(long cycleId) {
        return "arkusz-oceny-" + cycleId + ".pdf";
    }

    public static byte[] build(long cycleId, byte[] pdf, byte[] metadataJson) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            add(zip, pdfName(cycleId), pdf);
            add(zip, METADATA_FILE, metadataJson);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void add(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
