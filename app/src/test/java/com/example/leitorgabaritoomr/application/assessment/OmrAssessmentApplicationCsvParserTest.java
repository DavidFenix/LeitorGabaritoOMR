package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class OmrAssessmentApplicationCsvParserTest {

    private final OmrAssessmentApplicationCsvParser parser =
            new OmrAssessmentApplicationCsvParser();

    @Test
    public void parsesSemicolonSeparatedRows() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "MAT-9A-B1;PROVA MATEMÁTICA;2;9º A\n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        assertEquals(1, preview.getRows().size());
        assertEquals(
                "MAT-9A-B1",
                preview.getRows().get(0).getApplicationCode()
        );
        assertEquals(2, preview.getRows().get(0)
                .getAssessmentVersion());
    }

    @Test
    public void parsesCommaAndQuotedContent() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao,prova,versao_prova,turma\n"
                        + "APP-1,\"Matemática, revisão\",1,\"9º A\"\n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(',', preview.getDelimiter());
        assertEquals(
                "Matemática, revisão",
                preview.getRows().get(0).getAssessmentReference()
        );
    }

    @Test
    public void acceptsUtf8Bom() {
        byte[] bytes = ("\uFEFFcodigo_aplicacao;prova;versao_prova;turma\n"
                + "APLICAÇÃO-1;Avaliação;1;3ª Série A\n")
                .getBytes(StandardCharsets.UTF_8);

        OmrAssessmentApplicationCsvParser.Preview preview =
                parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-8", preview.getEncoding());
        assertEquals(
                "APLICAÇÃO-1",
                preview.getRows().get(0).getApplicationCode()
        );
    }

    @Test
    public void fallsBackToWindows1252() {
        byte[] bytes = (
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APLICAÇÃO-2;Ciências;1;3ª Série B\n"
        ).getBytes(Charset.forName("windows-1252"));

        OmrAssessmentApplicationCsvParser.Preview preview =
                parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("Windows-1252", preview.getEncoding());
        assertEquals(
                "Ciências",
                preview.getRows().get(0).getAssessmentReference()
        );
    }

    @Test
    public void acceptsDocumentedHeaderAliases() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "id_aplicacao,id_prova,versao,id_turma\n"
                        + "APP-3,assessment-1,4,class-1\n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals("class-1", preview.getRows().get(0)
                .getClassReference());
    }

    @Test
    public void reportsEveryMissingHeader() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova\nAPP;PROVA\n"
        );

        assertFalse(preview.isReadyToImport());
        assertEquals(2, preview.getIssues().size());
        assertTrue(preview.getIssues().get(0).getMessage()
                .contains("turma"));
        assertTrue(preview.getIssues().get(1).getMessage()
                .contains("versao_prova"));
    }

    @Test
    public void rejectsDuplicateSemanticHeader() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;id_aplicacao;prova;versao_prova;turma\n"
                        + "A;B;P;1;T\n"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getIssues().get(0).getMessage()
                .contains("mais de uma coluna"));
    }

    @Test
    public void reportsInvalidFieldsWithoutImportingBadRows() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + ";;zero;\n"
                        + "APP-OK;PROVA;1;TURMA\n"
        );

        assertFalse(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
        assertEquals(4, preview.getIssues().size());
    }

    @Test
    public void rejectsDuplicateCodesIgnoringCaseAndAccents() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "Aplicação 1;P1;1;T1\n"
                        + "aplicacao   1;P2;1;T2\n"
        );

        assertFalse(preview.isReadyToImport());
        assertEquals(0, preview.getRows().size());
        assertEquals(2, preview.getIssues().size());
    }

    @Test
    public void normalizesWhitespaceIncludingQuotedLineBreaks() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APP  1;\"Prova\nFinal\";1;  9º   A  \n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(
                "Prova Final",
                preview.getRows().get(0).getAssessmentReference()
        );
        assertEquals(
                "9º A",
                preview.getRows().get(0).getClassReference()
        );
    }

    @Test
    public void reportsUnclosedQuotes() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APP;\"Prova sem fim;1;Turma\n"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getIssues().stream().anyMatch(
                issue -> issue.getMessage().contains("não encerradas")
        ));
    }

    @Test
    public void emptyCsvProducesOneIssue() {
        OmrAssessmentApplicationCsvParser.Preview preview =
                parser.parse("   ");

        assertFalse(preview.isReadyToImport());
        assertEquals(1, preview.getIssues().size());
        assertTrue(preview.getRows().isEmpty());
    }

    @Test
    public void previewCollectionsAreImmutable() {
        OmrAssessmentApplicationCsvParser.Preview preview = parser.parse(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APP;PROVA;1;TURMA\n"
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> preview.getRows().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> preview.getIssues().add(null)
        );
    }
}
