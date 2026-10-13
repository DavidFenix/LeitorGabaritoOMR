package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultCsvExporter;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultExportNames;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OmrAssessmentResultCsvExporterTest {

    @Test
    public void rejectsMissingReportOrDestination() {
        OmrAssessmentResultCsvExporter exporter =
                new OmrAssessmentResultCsvExporter();
        expectIllegalArgument(() -> exporter.build(null));
        expectIllegalArgument(() -> {
            try {
                exporter.write(OmrAssessmentResultTestData.report(), null);
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    public void exportsMetadataSummaryAndHeaders() {
        String csv = new OmrAssessmentResultCsvExporter().build(
                OmrAssessmentResultTestData.report()
        );
        assertTrue(csv.contains(
                "\"Relatório\";\"Resultados da aplicação\""
        ));
        assertTrue(csv.contains("\"Prova\";\"Avaliação & revisão\""));
        assertTrue(csv.contains("\"Corrigidos\";\"1\""));
        assertTrue(csv.contains("\"Pendentes\";\"1\""));
        assertTrue(csv.contains("\"Inconsistências\";\"1\""));
        assertTrue(csv.contains(
                "\"Posição\";\"Matrícula\";\"Aluno\""
        ));
    }

    @Test
    public void exportsEveryOperationalStatus() {
        String csv = new OmrAssessmentResultCsvExporter().build(
                OmrAssessmentResultTestData.report()
        );
        assertTrue(csv.contains("Corrigido - final"));
        assertTrue(csv.contains("Pendente"));
        assertTrue(csv.contains("Correção sem histórico disponível"));
        assertTrue(csv.contains("\"2\";\"1,00\";\"1,00\";\"100,00\""));
    }

    @Test
    public void writesUtf8BomAndCrLf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new OmrAssessmentResultCsvExporter().write(
                OmrAssessmentResultTestData.report(), output
        );
        byte[] bytes = output.toByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String text = new String(
                bytes, 3, bytes.length - 3, StandardCharsets.UTF_8
        );
        assertTrue(text.contains("\r\n"));
        assertTrue(text.contains("Avaliação"));
    }

    @Test
    public void protectsCellsFromSpreadsheetFormulaInjection() {
        OmrAssessmentResultReport original =
                OmrAssessmentResultTestData.report();
        OmrAssessmentResultReport.Row unsafe =
                new OmrAssessmentResultReport.Row(
                        OmrAssessmentResultTestData.participant(
                                "student-danger", "=1+1",
                                "@SUM(A1:A2)", 4
                        ),
                        null,
                        null
                );
        java.util.List<OmrAssessmentResultReport.Row> rows =
                new java.util.ArrayList<>(original.getRows());
        rows.add(unsafe);
        OmrAssessmentResultReport report =
                new OmrAssessmentResultReport(
                        original.getApplication(),
                        original.getAssessment(),
                        original.getFrozenClassName(),
                        original.getGeneratedAtEpochMillis(),
                        rows
                );
        String csv = new OmrAssessmentResultCsvExporter().build(report);
        assertTrue(csv.contains("\"'=1+1\""));
        assertTrue(csv.contains("\"'@SUM(A1:A2)\""));
    }

    @Test
    public void suggestedNamesArePortableAndTyped() {
        OmrAssessmentResultReport report = OmrAssessmentResultTestData.report();
        assertEquals(
                "avaliacao_revisao_9_a_resultados.csv",
                OmrAssessmentResultExportNames.csv(report)
        );
        assertEquals(
                "avaliacao_revisao_9_a_resultados.xlsx",
                OmrAssessmentResultExportNames.xlsx(report)
        );
        assertEquals(
                "avaliacao_revisao_9_a_resultados.pdf",
                OmrAssessmentResultExportNames.pdf(report)
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}
