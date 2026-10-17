package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCsvParser;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentStructureCsvParser;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public final class OmrAssessmentCsvExportersTest {

    @Test
    public void assessmentExportRoundTripsManualIdentityAndMetadata() {
        OmrAssessmentDefinition assessment = new OmrAssessmentDefinition(
                "assessment-manual-123",
                2,
                "Caderno de Ciências",
                "Natureza",
                "1ª série",
                "1º período",
                2026,
                "answer-key-45",
                3
        );

        String csv = new OmrAssessmentCsvExporter().build(
                Collections.singletonList(assessment)
        );
        OmrAssessmentCsvParser.Preview preview =
                new OmrAssessmentCsvParser().parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        OmrAssessmentCsvParser.Row row = preview.getRows().get(0);
        assertEquals("ASSESSMENT-MANUAL-123", row.getAssessmentCode());
        assertEquals("assessment-manual-123", row.getAssessmentInternalId());
        assertEquals(2, row.getAssessmentVersion());
        assertEquals("Caderno de Ciências", row.getTitle());
        assertEquals("Natureza", row.getSubject());
        assertEquals("1ª série", row.getGradeLevel());
        assertEquals("1º período", row.getAcademicPeriod());
        assertEquals(2026, row.getSchoolYear());
        assertEquals("answer-key-45", row.getAnswerKeyReference());
        assertEquals(3, row.getAnswerKeyVersion());
    }

    @Test
    public void assessmentTemplateIsImmediatelyParseable() {
        String csv = new OmrAssessmentCsvExporter().buildTemplate();

        OmrAssessmentCsvParser.Preview preview =
                new OmrAssessmentCsvParser().parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
        assertEquals("NAT-1", preview.getRows().get(0).getAssessmentCode());
        assertNull(preview.getRows().get(0).getAssessmentInternalId());
    }

    @Test
    public void assessmentWriteUsesUtf8BomAndCrLf() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new OmrAssessmentCsvExporter().write(
                Collections.singletonList(assessment("assessment-1", 1)),
                output
        );

        byte[] bytes = output.toByteArray();
        assertEquals(0xEF, bytes[0] & 0xFF);
        assertEquals(0xBB, bytes[1] & 0xFF);
        assertEquals(0xBF, bytes[2] & 0xFF);
        String text = new String(
                bytes,
                3,
                bytes.length - 3,
                StandardCharsets.UTF_8
        );
        assertTrue(text.contains("\r\n"));
        assertFalse(text.replace("\r\n", "").contains("\n"));
    }

    @Test
    public void assessmentExportRejectsDuplicateVersion() {
        OmrAssessmentDefinition assessment = assessment("assessment-1", 1);

        expectIllegalArgument(() -> new OmrAssessmentCsvExporter().build(
                Arrays.asList(assessment, assessment)
        ));
    }

    @Test
    public void structureExportRoundTripsRowsAndStableBlockCodes() {
        OmrAssessmentStructure structure = structure();

        String csv = new OmrAssessmentStructureCsvExporter().build(
                Collections.singletonList(structure)
        );
        OmrAssessmentStructureCsvParser.Preview preview =
                new OmrAssessmentStructureCsvParser().parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(2, preview.getRows().size());
        OmrAssessmentStructureCsvParser.Row first =
                preview.getRows().get(0);
        assertEquals("assessment-manual-123", first.getAssessmentReference());
        assertEquals(2, first.getAssessmentVersion());
        assertEquals(1, first.getBlockOrder());
        assertEquals("qui", first.getBlockCode());
        assertEquals("Ciências da Natureza", first.getAreaName());
        assertEquals("QUI", first.getComponentCode());
        assertEquals("Química", first.getComponentName());
        assertEquals(15, first.getQuestionCount());

        assertEquals("fisica", preview.getRows().get(1).getBlockCode());
        assertEquals(30, preview.getRows().get(1).getQuestionCount());
    }

    @Test
    public void structureTemplateIsImmediatelyParseable() {
        OmrAssessmentStructureCsvParser.Preview preview =
                new OmrAssessmentStructureCsvParser().parse(
                        new OmrAssessmentStructureCsvExporter().buildTemplate()
                );

        assertTrue(preview.isReadyToImport());
        assertEquals(3, preview.getRows().size());
        assertEquals(45, preview.getRows().stream()
                .mapToInt(
                        OmrAssessmentStructureCsvParser.Row::getQuestionCount
                )
                .sum());
    }

    @Test
    public void structureExportRejectsDuplicateStructure() {
        OmrAssessmentStructure structure = structure();

        expectIllegalArgument(() ->
                new OmrAssessmentStructureCsvExporter().build(
                        Arrays.asList(structure, structure)
                )
        );
    }

    @Test
    public void spreadsheetFormulaPrefixesAreNeutralized() {
        OmrAssessmentDefinition assessment = new OmrAssessmentDefinition(
                "assessment-formula",
                1,
                "=WEBSERVICE(\"https://example.invalid\")",
                "+Natureza",
                "-1ª série",
                "@período",
                2026,
                "answer-key-1",
                1
        );

        String csv = new OmrAssessmentCsvExporter().build(
                Collections.singletonList(assessment)
        );

        assertTrue(csv.contains("\"'=WEBSERVICE"));
        assertTrue(csv.contains("\"'+Natureza\""));
        assertTrue(csv.contains("\"'-1ª série\""));
        assertTrue(csv.contains("\"'@período\""));
    }

    private static OmrAssessmentDefinition assessment(
            String assessmentId,
            int version
    ) {
        return new OmrAssessmentDefinition(
                assessmentId,
                version,
                "Prova",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-1",
                1
        );
    }

    private static OmrAssessmentStructure structure() {
        return new OmrAssessmentStructure(
                "assessment-manual-123",
                2,
                45,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                "bloco:qui",
                                1,
                                "Ciências da Natureza",
                                "QUI",
                                "Química",
                                1,
                                15,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        ),
                        new OmrAssessmentBlock(
                                "bloco:fisica",
                                2,
                                "Ciências da Natureza",
                                "FIS",
                                "Física",
                                16,
                                30,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        )
                )
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
