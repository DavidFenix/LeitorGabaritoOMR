package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultXlsxExporter;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class OmrAssessmentResultXlsxExporterTest {

    @Test
    public void rejectsMissingInputs() {
        OmrAssessmentResultXlsxExporter exporter =
                new OmrAssessmentResultXlsxExporter();
        expectIllegalArgument(() -> {
            try {
                exporter.write(null, new ByteArrayOutputStream());
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
        expectIllegalArgument(() -> {
            try {
                exporter.write(OmrAssessmentResultTestData.report(), null);
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    public void writesValidZipSignature() throws Exception {
        byte[] xlsx = export();
        assertTrue(xlsx.length > 1_000);
        assertEquals('P', xlsx[0]);
        assertEquals('K', xlsx[1]);
    }

    @Test
    public void containsRequiredOoxmlParts() throws Exception {
        Map<String, String> entries = entries(export());
        assertNotNull(entries.get("[Content_Types].xml"));
        assertNotNull(entries.get("_rels/.rels"));
        assertNotNull(entries.get("xl/workbook.xml"));
        assertNotNull(entries.get("xl/_rels/workbook.xml.rels"));
        assertNotNull(entries.get("xl/styles.xml"));
        assertNotNull(entries.get("xl/worksheets/sheet1.xml"));
    }

    @Test
    public void workbookPublishesResultsSheet() throws Exception {
        String workbook = entries(export()).get("xl/workbook.xml");
        assertTrue(workbook.contains("name=\"Resultados\""));
    }

    @Test
    public void worksheetContainsMetadataAndAllStatuses() throws Exception {
        String sheet = entries(export()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("Resultados da aplicação"));
        assertTrue(sheet.contains("Avaliação &amp; revisão"));
        assertTrue(sheet.contains("Ana Souza"));
        assertTrue(sheet.contains("Corrigido - final"));
        assertTrue(sheet.contains("Pendente"));
        assertTrue(sheet.contains("Correção sem histórico disponível"));
    }

    @Test
    public void worksheetFreezesHeaderAndEnablesFilter() throws Exception {
        String sheet = entries(export()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("state=\"frozen\""));
        assertTrue(sheet.contains("<autoFilter ref=\"A15:N18\""));
        assertTrue(sheet.contains("<mergeCell ref=\"A1:N1\""));
    }

    @Test
    public void percentageIsNumericWithPercentageStyle() throws Exception {
        String sheet = entries(export()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("r=\"I16\" s=\"8\"><v>1"));
        String styles = entries(export()).get("xl/styles.xml");
        assertTrue(styles.contains("formatCode=\"0.00%\""));
    }

    private static byte[] export() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new OmrAssessmentResultXlsxExporter().write(
                OmrAssessmentResultTestData.report(), output
        );
        return output.toByteArray();
    }

    private static Map<String, String> entries(byte[] data) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(data)
        )) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                entries.put(entry.getName(), new String(
                        output.toByteArray(), StandardCharsets.UTF_8
                ));
            }
        }
        return entries;
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
