package com.example.leitorgabaritoomr.application.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class OmrStudentCsvParserTest {

    private final OmrStudentCsvParser parser =
            new OmrStudentCsvParser();

    @Test
    public void parsesCommaSeparatedStudentsAndPreservesLeadingZeros() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n"
                        + "000123,  Ana   Beatriz  \n"
                        + "AB-09,Bruno Lima"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(',', preview.getDelimiter());
        assertEquals(2, preview.getRows().size());
        assertEquals(
                "000123",
                preview.getRows().get(0).getRegistration()
        );
        assertEquals(
                "Ana Beatriz",
                preview.getRows().get(0).getName()
        );
        assertEquals(
                "AB-09",
                preview.getRows().get(1).getRegistration()
        );
    }

    @Test
    public void detectsExcelSemicolonDelimiter() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula;nome\n000123;Ana Beatriz"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        assertEquals(1, preview.getRows().size());
    }

    @Test
    public void delimiterDetectionSkipsLeadingBlankLines() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "\r\n\nmatricula;nome\r\n000123;Ana"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        assertEquals(4, preview.getRows().get(0).getLineNumber());
    }

    @Test
    public void decodesUtf8BomAndAccentedHeader() {
        byte[] content = "Matrícula;Nome\n000123;João"
                .getBytes(StandardCharsets.UTF_8);
        byte[] bytes = withPrefix(
                new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                content
        );

        OmrStudentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-8 BOM", preview.getEncoding());
        assertEquals("João", preview.getRows().get(0).getName());
    }

    @Test
    public void fallsBackToWindows1252WithoutMojibake() {
        byte[] bytes = "matricula;nome\n000123;João"
                .getBytes(Charset.forName("windows-1252"));

        OmrStudentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("Windows-1252", preview.getEncoding());
        assertEquals("João", preview.getRows().get(0).getName());
    }

    @Test
    public void decodesUtf16LittleEndianBom() {
        byte[] content = "matricula,nome\n000123,José"
                .getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = withPrefix(
                new byte[]{(byte) 0xFF, (byte) 0xFE},
                content
        );

        OmrStudentCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-16LE", preview.getEncoding());
        assertEquals("José", preview.getRows().get(0).getName());
    }

    @Test
    public void parsesQuotedDelimiterAndEscapedQuotes() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n"
                        + "000123,\"Ana, a \"\"Bia\"\"\""
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(
                "Ana, a \"Bia\"",
                preview.getRows().get(0).getName()
        );
    }

    @Test
    public void parsesQuotedMultilineNameAndTracksStartingLine() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n"
                        + "000123,\"Ana\nBeatriz\""
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(2, preview.getRows().get(0).getLineNumber());
        assertEquals(
                "Ana Beatriz",
                preview.getRows().get(0).getName()
        );
    }

    @Test
    public void acceptsSwappedRequiredColumnsAndIgnoresExtras() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "turma,nome,matricula,email\n"
                        + "9A,Ana,000123,a@example.com"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals("000123", preview.getRows().get(0).getRegistration());
        assertEquals("Ana", preview.getRows().get(0).getName());
    }

    @Test
    public void acceptsDocumentedEnglishHeaderAliases() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "registration,name\n000123,Ana"
        );

        assertTrue(preview.isReadyToImport());
    }

    @Test
    public void ignoresBlankLinesBetweenStudents() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n\n000123,Ana\n   \n000124,Bruno\n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(2, preview.getRows().size());
    }

    @Test
    public void nullAndEmptySourcesProducePreviewIssue() {
        assertFalse(parser.parse((String) null).isReadyToImport());
        assertEquals(1, parser.parse("  ").getIssues().size());
        assertFalse(parser.parse((byte[]) null).isReadyToImport());
        assertEquals(1, parser.parse(new byte[0]).getIssues().size());
    }

    @Test
    public void reportsMissingRegistrationHeader() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "codigo,nome\n000123,Ana"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getRows().isEmpty());
        assertContains(preview, "coluna matricula");
    }

    @Test
    public void reportsMissingNameHeader() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,estudante_completo\n000123,Ana"
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "coluna nome");
    }

    @Test
    public void reportsEmptyRequiredCellsWithTheirLines() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n,Ana\n000124,"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getRows().isEmpty());
        assertEquals(2, preview.getIssues().size());
        assertEquals(2, preview.getIssues().get(0).getLineNumber());
        assertEquals(3, preview.getIssues().get(1).getLineNumber());
    }

    @Test
    public void rowContainingOnlyDelimiterIsReportedInsteadOfIgnored() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n,"
        );

        assertFalse(preview.isReadyToImport());
        assertEquals(2, preview.getIssues().size());
        assertEquals(2, preview.getIssues().get(0).getLineNumber());
        assertEquals(2, preview.getIssues().get(1).getLineNumber());
    }

    @Test
    public void duplicateRegistrationInvalidatesEveryOccurrence() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\nabc-1,Ana\nABC-1,Outra Ana"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getRows().isEmpty());
        assertEquals(2, preview.getIssues().size());
        assertContains(preview, "Matrícula repetida");
    }

    @Test
    public void reportsUnterminatedQuotedCell() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n000123,\"Ana"
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "não foi encerrada");
    }

    @Test
    public void headerWithoutStudentsCannotBeImported() {
        OmrStudentCsvParser.Preview preview = parser.parse(
                "matricula,nome\n"
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "não possui linhas de alunos");
    }

    @Test
    public void previewCollectionsAreImmutable() {
        OmrStudentCsvParser.Preview valid = parser.parse(
                "matricula,nome\n000123,Ana"
        );
        OmrStudentCsvParser.Preview invalid = parser.parse("");

        expectUnsupportedOperation(() -> valid.getRows().clear());
        expectUnsupportedOperation(() -> invalid.getIssues().clear());
    }

    private static byte[] withPrefix(
            byte[] prefix,
            byte[] content
    ) {
        byte[] result = Arrays.copyOf(
                prefix,
                prefix.length + content.length
        );
        System.arraycopy(
                content,
                0,
                result,
                prefix.length,
                content.length
        );
        return result;
    }

    private static void assertContains(
            OmrStudentCsvParser.Preview preview,
            String expectedText
    ) {
        for (OmrStudentCsvParser.Issue issue : preview.getIssues()) {
            if (issue.getMessage().contains(expectedText)) {
                return;
            }
        }

        fail("Texto não encontrado nos erros: " + expectedText);
    }

    private static void expectUnsupportedOperation(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
