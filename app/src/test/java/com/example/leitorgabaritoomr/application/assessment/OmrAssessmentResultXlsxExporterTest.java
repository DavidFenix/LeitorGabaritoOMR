package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
        assertNull(entries.get("xl/worksheets/sheet2.xml"));
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
        assertTrue(sheet.contains(
                "<pane ySplit=\"1\" topLeftCell=\"A2\""
        ));
        assertTrue(sheet.contains("<autoFilter ref=\"A1:N4\""));
        assertTrue(sheet.contains("<mergeCells count=\"3\">"));
        assertTrue(sheet.contains("<mergeCell ref=\"A6:N6\""));
        assertTrue(sheet.contains("<mergeCell ref=\"A8:N8\""));
    }

    @Test
    public void percentageIsNumericWithPercentageStyle() throws Exception {
        String sheet = entries(export()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("r=\"I2\" s=\"8\"><v>1"));
        String styles = entries(export()).get("xl/styles.xml");
        assertTrue(styles.contains("formatCode=\"0.00%\""));
    }

    @Test
    public void structuredWorkbookPublishesComponentPairsAndAuditSheet()
            throws Exception {
        Map<String, String> entries = entries(export(
                OmrAssessmentResultTestData.structuredReportWithPending()
        ));
        String workbook = entries.get("xl/workbook.xml");
        String results = entries.get("xl/worksheets/sheet1.xml");
        String components = entries.get("xl/worksheets/sheet2.xml");

        assertNotNull(components);
        assertTrue(workbook.contains("name=\"Componentes\""));
        assertTrue(results.contains(
                "Ciências da Natureza | QUI - Química | ACERTOS"
        ));
        assertTrue(results.contains(
                "Ciências da Natureza | FIS - Física | NOTA"
        ));
        assertTrue(results.contains("<autoFilter ref=\"A1:R3\""));
        assertTrue(results.contains("<mergeCell ref=\"A5:R5\""));
        assertTrue(results.contains("r=\"O2\" s=\"2\"><v>1</v>"));
        assertTrue(results.contains("r=\"P2\" s=\"4\"><v>10</v>"));
        assertTrue(results.contains("r=\"Q2\" s=\"2\"><v>0</v>"));
        assertTrue(results.contains("r=\"R2\" s=\"4\"><v>0</v>"));
        assertTrue(results.contains(
                "r=\"O3\" s=\"2\" t=\"inlineStr\"><is>"
                        + "<t xml:space=\"preserve\"></t>"
        ));
        assertTrue(components.contains("Ciências da Natureza"));
        assertTrue(components.contains("Química"));
        assertTrue(components.contains(
                "proportional-correct-questions@v1"
        ));
    }

    @Test
    public void structuredPackageLinksSecondWorksheetAndStyles()
            throws Exception {
        Map<String, String> entries = entries(export(
                OmrAssessmentResultTestData.structuredReport()
        ));
        assertTrue(entries.get("[Content_Types].xml").contains(
                "/xl/worksheets/sheet2.xml"
        ));
        String relationships = entries.get(
                "xl/_rels/workbook.xml.rels"
        );
        assertTrue(relationships.contains(
                "Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/"
                        + "officeDocument/2006/relationships/worksheet\""
        ));
        assertTrue(relationships.contains(
                "Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/"
                        + "officeDocument/2006/relationships/styles\""
        ));
    }

    @Test
    public void inconsistentRowUsesAlertStyleAndBlankComponentCells()
            throws Exception {
        Map<String, String> entries = entries(export(
                OmrAssessmentResultTestData
                        .structuredInconsistentReport()
        ));
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains(
                "r=\"E2\" s=\"10\" t=\"inlineStr\""
        ));
        assertTrue(sheet.contains(
                "Inconsistência entre correção e estrutura"
        ));
        assertTrue(sheet.contains(
                "r=\"O2\" s=\"2\" t=\"inlineStr\"><is>"
                        + "<t xml:space=\"preserve\"></t>"
        ));
        assertTrue(entries.get("xl/styles.xml").contains(
                "<cellXfs count=\"11\">"
        ));
    }

    private static byte[] export() throws Exception {
        return export(OmrAssessmentResultTestData.report());
    }

    private static byte[] export(
            OmrAssessmentResultReport report
    ) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new OmrAssessmentResultXlsxExporter().write(
                report, output
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
