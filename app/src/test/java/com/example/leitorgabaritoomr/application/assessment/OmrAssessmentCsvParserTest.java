package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class OmrAssessmentCsvParserTest {

    private final OmrAssessmentCsvParser parser =
            new OmrAssessmentCsvParser();

    @Test
    public void parsesCanonicalCommaSeparatedRows() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("MAT-9-B2", 1, "Prova bimestral", "Gabarito 9A", 1)
                        + "\n"
                        + row("MAT-9-B3", 2, "Revisão", "key-002", 3)
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(',', preview.getDelimiter());
        assertEquals("Texto", preview.getEncoding());
        assertEquals(2, preview.getRows().size());

        OmrAssessmentCsvParser.Row first = preview.getRows().get(0);
        assertEquals(2, first.getLineNumber());
        assertEquals("MAT-9-B2", first.getAssessmentCode());
        assertEquals(1, first.getAssessmentVersion());
        assertEquals("Prova bimestral", first.getTitle());
        assertEquals("Matemática", first.getSubject());
        assertEquals("9º ano", first.getGradeLevel());
        assertEquals("2º bimestre", first.getAcademicPeriod());
        assertEquals(2026, first.getSchoolYear());
        assertEquals("Gabarito 9A", first.getAnswerKeyReference());
        assertEquals(1, first.getAnswerKeyVersion());
    }

    @Test
    public void acceptsSemicolonAndPortugueseHeaderAliases() {
        String csv = "Código;Versão;Nome da prova;Matéria;Série;"
                + "Bimestre;Ano letivo;Nome do gabarito;Versão do gabarito\n"
                + " prova  01 ;1; Avaliação   mensal ; Matemática ;"
                + " 9º ano ; 3º bimestre ;2026; Gabarito mensal ;2";

        OmrAssessmentCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        assertEquals("PROVA 01", preview.getRows().get(0).getAssessmentCode());
        assertEquals(
                "Avaliação mensal",
                preview.getRows().get(0).getTitle()
        );
    }

    @Test
    public void acceptsEnglishHeaderAliasesAndIgnoresExtraColumns() {
        String csv = "assessment_code,assessment_version,assessment_title,"
                + "subject,grade_level,academic_period,school_year,"
                + "answer_key,answer_key_version,observacao\n"
                + "A1,1,Test,Math,Grade 9,Term 1,2026,key-1,1,ok";

        OmrAssessmentCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
    }

    @Test
    public void acceptsOptionalInternalAssessmentIdForBackupRestore() {
        String csv = header() + ",id_interno_prova\n"
                + row("CODIGO VISIVEL", 2, "Prova", "key-1", 1)
                + ",assessment-manual-123";

        OmrAssessmentCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(
                "assessment-manual-123",
                preview.getRows().get(0).getAssessmentInternalId()
        );
    }

    @Test
    public void internalIdParticipatesInDuplicateIdentity() {
        String csv = header() + ",assessment_id\n"
                + row("CODIGO A", 1, "Primeira", "key-1", 1)
                + ",assessment-shared\n"
                + row("CODIGO B", 1, "Segunda", "key-1", 1)
                + ",assessment-shared";

        OmrAssessmentCsvParser.Preview preview = parser.parse(csv);

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getRows().isEmpty());
        assertIssue(preview, "repetidos");
    }

    @Test
    public void parsesQuotedDelimiterQuotesAndLineBreak() {
        String csv = header() + "\n"
                + "P1,1,\"Prova, diagnóstica\",Matemática,9º ano,"
                + "\"1º\nbimestre\",2026,\"Gabarito \"\"A\"\"\",1";

        OmrAssessmentCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        OmrAssessmentCsvParser.Row row = preview.getRows().get(0);
        assertEquals("Prova, diagnóstica", row.getTitle());
        assertEquals("1º bimestre", row.getAcademicPeriod());
        assertEquals("Gabarito \"A\"", row.getAnswerKeyReference());
    }

    @Test
    public void detectsUtf8Bom() {
        byte[] body = validCsv().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);

        OmrAssessmentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-8 BOM", preview.getEncoding());
    }

    @Test
    public void detectsUtf16LittleEndian() {
        byte[] body = validCsv().getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);

        OmrAssessmentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-16LE", preview.getEncoding());
    }

    @Test
    public void detectsUtf16BigEndian() {
        byte[] body = validCsv().getBytes(StandardCharsets.UTF_16BE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFE;
        bytes[1] = (byte) 0xFF;
        System.arraycopy(body, 0, bytes, 2, body.length);

        OmrAssessmentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-16BE", preview.getEncoding());
    }

    @Test
    public void fallsBackToWindows1252() {
        byte[] bytes = validCsv().getBytes(
                Charset.forName("windows-1252")
        );

        OmrAssessmentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("Windows-1252", preview.getEncoding());
        assertEquals(
                "Matemática",
                preview.getRows().get(0).getSubject()
        );
    }

    @Test
    public void rejectsEmptyContentAndHeaderOnly() {
        assertFalse(parser.parse((String) null).isReadyToImport());
        assertIssue(parser.parse("  "), "vazio");
        assertIssue(parser.parse(header()), "não possui linhas");
    }

    @Test
    public void reportsEveryMissingRequiredHeader() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                "codigo_prova,titulo\nP1,Prova"
        );

        assertFalse(preview.isReadyToImport());
        assertIssue(preview, "versao_prova");
        assertIssue(preview, "disciplina");
        assertIssue(preview, "etapa");
        assertIssue(preview, "periodo");
        assertIssue(preview, "ano_letivo");
        assertIssue(preview, "gabarito");
        assertIssue(preview, "versao_gabarito");
    }

    @Test
    public void duplicateRecognizedHeaderIsRejected() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                header() + ",codigo\n"
                        + row("P1", 1, "Prova", "Key", 1) + ",P2"
        );

        assertFalse(preview.isReadyToImport());
        assertIssue(preview, "mais de uma coluna de código");
    }

    @Test
    public void reportsBlankTextAndInvalidNumbers() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + ",zero,,,,,abc,,menos"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getIssues().size() >= 9);
        assertIssue(preview, "código");
        assertIssue(preview, "versão da prova");
        assertIssue(preview, "título");
        assertIssue(preview, "disciplina");
        assertIssue(preview, "etapa");
        assertIssue(preview, "período");
        assertIssue(preview, "ano letivo");
        assertIssue(preview, "referência do gabarito");
        assertIssue(preview, "versão do gabarito");
    }

    @Test
    public void rejectsDuplicateCodeAndVersionIgnoringCaseAndSpaces() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("prova 01", 1, "Primeira", "Key", 1)
                        + "\n"
                        + row(" PROVA   01 ", 1, "Segunda", "Key", 1)
        );

        assertFalse(preview.isReadyToImport());
        assertEquals(0, preview.getRows().size());
        assertEquals(2, preview.getIssues().size());
        assertIssue(preview, "repetidos");
    }

    @Test
    public void sameCodeWithDifferentVersionsIsAllowed() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("P1", 1, "Versão um", "Key", 1)
                        + "\n"
                        + row("P1", 2, "Versão dois", "Key", 2)
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(2, preview.getRows().size());
    }

    @Test
    public void ignoresCompletelyBlankRecords() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                "\n" + header() + "\n\n"
                        + row("P1", 1, "Prova", "Key", 1)
                        + "\n,,,,,,,,\n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
    }

    @Test
    public void malformedQuotedCellProducesIssueWithoutPartialRow() {
        OmrAssessmentCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + "P1,1,\"Título sem fim,Matemática,9º ano,"
                        + "Bimestre,2026,Key,1"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getRows().isEmpty());
        assertIssue(preview, "não foi encerrada");
    }

    @Test
    public void previewCollectionsAreImmutable() {
        OmrAssessmentCsvParser.Preview valid = parser.parse(validCsv());
        OmrAssessmentCsvParser.Preview invalid = parser.parse(" ");

        expectUnsupported(() -> valid.getRows().clear());
        expectUnsupported(() -> invalid.getIssues().clear());
    }

    private static String validCsv() {
        return header() + "\n"
                + row("P1", 1, "Prova", "Gabarito", 1);
    }

    private static String header() {
        return "codigo_prova,versao_prova,titulo,disciplina,etapa,"
                + "periodo,ano_letivo,gabarito,versao_gabarito";
    }

    private static String row(
            String code,
            int version,
            String title,
            String answerKey,
            int answerKeyVersion
    ) {
        return code + "," + version + "," + title
                + ",Matemática,9º ano,2º bimestre,2026,"
                + answerKey + "," + answerKeyVersion;
    }

    private static void assertIssue(
            OmrAssessmentCsvParser.Preview preview,
            String fragment
    ) {
        for (OmrAssessmentCsvParser.Issue issue
                : preview.getIssues()) {
            if (issue.getMessage().toLowerCase()
                    .contains(fragment.toLowerCase())) {
                return;
            }
        }

        fail("Não foi encontrada inconsistência contendo: " + fragment);
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
