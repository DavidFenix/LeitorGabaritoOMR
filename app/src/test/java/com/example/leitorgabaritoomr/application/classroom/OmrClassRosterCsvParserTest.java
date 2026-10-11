package com.example.leitorgabaritoomr.application.classroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class OmrClassRosterCsvParserTest {

    private final OmrClassRosterCsvParser parser =
            new OmrClassRosterCsvParser();

    @Test
    public void parsesCommaCsvAndPreservesLeadingZeros() {
        OmrClassRosterCsvParser.Preview preview = parse(
                row(" 7º   A ", " Fundamental  II ",
                        "2026", "000017", " Ana   Maria ", "3")
        );
        OmrClassRosterCsvParser.Row row = preview.getRows().get(0);

        assertEquals(',', preview.getDelimiter());
        assertEquals("7º A", row.getClassName());
        assertEquals("Fundamental II", row.getGradeLevel());
        assertEquals("000017", row.getRegistration());
        assertEquals("Ana Maria", row.getStudentName());
        assertEquals(3, row.getRosterPosition());
    }

    @Test
    public void acceptsSemicolonReorderedSynonymsAndExtraColumn() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                "extra;Número da chamada;Aluno;Número de matrícula;"
                        + "Ano;Série;Nome da turma\n"
                        + "ok;12;Bruno;0012;2026;8º ano;8º B"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        assertEquals("8º B", preview.getRows().get(0).getClassName());
    }

    @Test
    public void quotedCellsSupportDelimiterQuotesAndNewline() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + "\"Turma, Integral\",Médio,2026,001,"
                        + "\"Ana \"\"Bia\"\"\nSilva\",1"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals("Turma, Integral", preview.getRows().get(0).getClassName());
        assertEquals(
                "Ana \"Bia\" Silva",
                preview.getRows().get(0).getStudentName()
        );
    }

    @Test
    public void ignoresLeadingTextBom() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                "\uFEFF" + header() + "\n"
                        + row("A", "Etapa", "2026", "001", "Ana", "1")
        );
        assertTrue(preview.isReadyToImport());
    }

    @Test
    public void decodesUtf8Bom() {
        byte[] content = csv().getBytes(StandardCharsets.UTF_8);
        OmrClassRosterCsvParser.Preview preview = parser.parse(withPrefix(
                new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                content
        ));

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-8 BOM", preview.getEncoding());
    }

    @Test
    public void decodesUtf16LittleEndian() {
        byte[] content = csv().getBytes(StandardCharsets.UTF_16LE);
        OmrClassRosterCsvParser.Preview preview = parser.parse(withPrefix(
                new byte[]{(byte) 0xFF, (byte) 0xFE},
                content
        ));

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-16LE", preview.getEncoding());
    }

    @Test
    public void decodesUtf16BigEndian() {
        byte[] content = csv().getBytes(StandardCharsets.UTF_16BE);
        OmrClassRosterCsvParser.Preview preview = parser.parse(withPrefix(
                new byte[]{(byte) 0xFE, (byte) 0xFF},
                content
        ));

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-16BE", preview.getEncoding());
    }

    @Test
    public void fallsBackToWindows1252() {
        byte[] content = (header() + "\n"
                + row("A", "Educação", "2026", "001", "João", "1"))
                .getBytes(Charset.forName("windows-1252"));
        OmrClassRosterCsvParser.Preview preview = parser.parse(content);

        assertTrue(preview.isReadyToImport());
        assertEquals("Windows-1252", preview.getEncoding());
        assertEquals("João", preview.getRows().get(0).getStudentName());
    }

    @Test
    public void rejectsNullEmptyAndHeaderOnlyFiles() {
        assertFalse(parser.parse((String) null).isReadyToImport());
        assertFalse(parser.parse(new byte[0]).isReadyToImport());
        assertFalse(parser.parse(header()).isReadyToImport());
    }

    @Test
    public void reportsMissingHeaders() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                "turma,nome\nA,Ana"
        );

        assertFalse(preview.isReadyToImport());
        assertIssue(preview, "ano_letivo");
        assertIssue(preview, "matricula");
        assertIssue(preview, "posicao");
    }

    @Test
    public void rejectsDuplicateRequiredHeader() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + ",classe\n"
                        + row("A", "Etapa", "2026", "001", "Ana", "1")
                        + ",A"
        );
        assertIssue(preview, "mais de uma coluna de turma");
    }

    @Test
    public void reportsAllEmptyRequiredFieldsOnTheirLine() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n,,,,,"
        );

        assertEquals(6, preview.getIssueCount());
        for (OmrClassRosterCsvParser.Issue issue : preview.getIssues()) {
            assertEquals(2, issue.getLineNumber());
        }
    }

    @Test
    public void yearAndPositionMustBePositiveIntegers() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("A", "Etapa", "20x6", "001", "Ana", "0")
        );

        assertIssue(preview, "ano letivo");
        assertIssue(preview, "posição na chamada");
    }

    @Test
    public void rejectsRepeatedStudentInClass() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("A", "Etapa", "2026", "001", "Ana", "1")
                        + "\n" + row(
                        "A", "Etapa", "2026", "001", "Ana", "2"
                )
        );
        assertIssue(preview, "repetida na mesma turma");
    }

    @Test
    public void rejectsRepeatedPositionInClass() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("A", "Etapa", "2026", "001", "Ana", "1")
                        + "\n" + row(
                        "A", "Etapa", "2026", "002", "Bruno", "1"
                )
        );
        assertIssue(preview, "posição 1 está repetida");
    }

    @Test
    public void normalizedClassCannotHaveDifferentGrades() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("Sétimo A", "Etapa 1", "2026", "001", "Ana", "1")
                        + "\n" + row(
                        " setimo   a ", "Etapa 2", "2026", "002", "Bruno", "2"
                )
        );
        assertIssue(preview, "etapas diferentes");
    }

    @Test
    public void registrationCannotHaveDifferentNames() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("A", "Etapa", "2025", "001", "Ana", "1")
                        + "\n" + row(
                        "B", "Etapa", "2026", "001", "Beatriz", "1"
                )
        );
        assertIssue(preview, "nomes diferentes");
    }

    @Test
    public void studentCannotAppearInTwoClassesSameYear() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("A", "Etapa", "2026", "001", "Ana", "1")
                        + "\n" + row(
                        "B", "Etapa", "2026", "001", "Ana", "1"
                )
        );
        assertIssue(preview, "mais de uma turma no ano letivo");
    }

    @Test
    public void studentMayAppearInDifferentYears() {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n"
                        + row("A", "Etapa 1", "2025", "001", "Ana", "1")
                        + "\n" + row(
                        "B", "Etapa 2", "2026", "001", "Ana", "2"
                )
        );
        assertTrue(preview.isReadyToImport());
        assertEquals(2, preview.getValidRowCount());
    }

    @Test
    public void rejectsMalformedQuotesAndKeepsCollectionsImmutable() {
        OmrClassRosterCsvParser.Preview invalid = parser.parse(
                header() + "\n\"A,Etapa,2026,001,Ana,1"
        );
        assertIssue(invalid, "não foi encerrada");

        OmrClassRosterCsvParser.Preview valid = parse(
                row("A", "Etapa", "2026", "001", "Ana", "1")
        );
        expectUnsupported(() -> valid.getRows().clear());
        expectUnsupported(() -> valid.getIssues().clear());
    }

    private OmrClassRosterCsvParser.Preview parse(String body) {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n" + body
        );
        if (!preview.isReadyToImport()) {
            fail("Prévia inválida: " + preview.getIssues());
        }
        return preview;
    }

    private static String csv() {
        return header() + "\n"
                + row("A", "Educação", "2026", "001", "João", "1");
    }

    private static String header() {
        return "turma,etapa,ano_letivo,matricula,nome,posicao";
    }

    private static String row(
            String className,
            String grade,
            String year,
            String registration,
            String name,
            String position
    ) {
        return className + "," + grade + "," + year + ","
                + registration + "," + name + "," + position;
    }

    private static byte[] withPrefix(byte[] prefix, byte[] content) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(prefix, 0, prefix.length);
        output.write(content, 0, content.length);
        return output.toByteArray();
    }

    private static void assertIssue(
            OmrClassRosterCsvParser.Preview preview,
            String fragment
    ) {
        for (OmrClassRosterCsvParser.Issue issue : preview.getIssues()) {
            if (issue.getMessage().contains(fragment)) {
                return;
            }
        }
        fail("Problema ausente: " + fragment + " em " + preview.getIssues());
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
