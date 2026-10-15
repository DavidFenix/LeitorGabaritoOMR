package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class OmrAssessmentStructureCsvParserTest {

    private final OmrAssessmentStructureCsvParser parser =
            new OmrAssessmentStructureCsvParser();

    @Test
    public void parsesCanonicalSemicolonCsv() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\n"
                        + row(';', "NAT-1", 1, 1, "QUI",
                        "Ciências da Natureza", "QUI", "Química", 15)
                        + "\n"
                        + row(';', "NAT-1", 1, 2, "FIS",
                        "Ciências da Natureza", "FIS", "Física", 15)
                        + "\n"
                        + row(';', "NAT-1", 1, 3, "BIO",
                        "Ciências da Natureza", "BIO", "Biologia", 15)
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(';', preview.getDelimiter());
        assertEquals(3, preview.getRows().size());
        assertEquals("Química", preview.getRows().get(0).getComponentName());
        assertEquals(15, preview.getRows().get(2).getQuestionCount());
    }

    @Test
    public void detectsCommaDelimiter() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(',') + "\n"
                        + row(',', "MAT", 2, 1, "MAT",
                        "Matemática", "MAT", "Matemática", 20)
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(',', preview.getDelimiter());
    }

    @Test
    public void acceptsTabAndHeaderAliases() {
        String csv = "prova\tversão\tordem\tbloco\tárea\t"
                + "componente_codigo\tdisciplina\tquestões\n"
                + "HUM\t1\t1\tHIS\tHumanas\tHIS\tHistória\t12";

        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals('\t', preview.getDelimiter());
        assertEquals("História", preview.getRows().get(0).getComponentName());
    }

    @Test
    public void decodesUtf8Bytes() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                (header(';') + "\n"
                        + row(';', "NAT", 1, 1, "QUI", "Natureza",
                        "QUI", "Química", 10))
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertTrue(preview.isReadyToImport());
        assertEquals("UTF-8", preview.getEncoding());
        assertEquals("Química", preview.getRows().get(0).getComponentName());
    }

    @Test
    public void fallsBackToWindows1252() {
        byte[] bytes = (header(';') + "\n"
                + row(';', "NAT", 1, 1, "QUI", "Natureza",
                "QUI", "Química", 10)).getBytes(
                Charset.forName("windows-1252")
        );

        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(bytes);

        assertTrue(preview.isReadyToImport());
        assertEquals("Windows-1252", preview.getEncoding());
        assertEquals("Química", preview.getRows().get(0).getComponentName());
    }

    @Test
    public void removesUtf8Bom() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                "\ufeff" + header(';') + "\n"
                        + row(';', "P1", 1, 1, "B1", null,
                        null, "Geral", 5)
        );

        assertTrue(preview.isReadyToImport());
    }

    @Test
    public void parsesQuotedDelimiterAndEscapedQuote() {
        String csv = header(';') + "\n"
                + "P1;1;1;B1;\"Natureza; Tecnologia\";NAT;"
                + "\"Química \"\"Aplicada\"\"\";10";

        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(
                "Natureza; Tecnologia",
                preview.getRows().get(0).getAreaName()
        );
        assertEquals(
                "Química \"Aplicada\"",
                preview.getRows().get(0).getComponentName()
        );
    }

    @Test
    public void normalizesWhitespaceIncludingQuotedLineBreak() {
        String csv = header(';') + "\n"
                + "P1;1;1;;;COD;\"  Projeto\n Integrador  \";8";

        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals(
                "Projeto Integrador",
                preview.getRows().get(0).getComponentName()
        );
        assertEquals(2, preview.getRows().get(0).getLineNumber());
    }

    @Test
    public void rejectsMissingRequiredHeader() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                "codigo_prova;versao_prova;componente\nP1;1;Química"
        );

        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getIssues().size() >= 2);
    }

    @Test
    public void rejectsDuplicatedConceptualHeader() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                "codigo_prova;prova;versao_prova;ordem_bloco;"
                        + "componente;itens\nP1;P1;1;1;Química;10"
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "mais de uma vez");
    }

    @Test
    public void rejectsEmptyInput() {
        assertFalse(parser.parse("  ").isReadyToImport());
        assertFalse(parser.parse(new byte[0]).isReadyToImport());
    }

    @Test
    public void reportsAllInvalidRequiredValues() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\n;;;;;;;0"
        );

        assertFalse(preview.isReadyToImport());
        assertEquals(5, preview.getIssues().size());
    }

    @Test
    public void rejectsDuplicateOrderWithinAssessment() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\n"
                        + row(';', "P1", 1, 1, "A", null,
                        null, "A", 5) + "\n"
                        + row(';', "P1", 1, 1, "B", null,
                        null, "B", 5)
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "ordem 1");
    }

    @Test
    public void rejectsNonContiguousOrders() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\n"
                        + row(';', "P1", 1, 1, "A", null,
                        null, "A", 5) + "\n"
                        + row(';', "P1", 1, 3, "B", null,
                        null, "B", 5)
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "sequencia");
    }

    @Test
    public void rejectsDuplicateBlockCodeIgnoringCaseAndAccents() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\n"
                        + row(';', "P1", 1, 1, "FÍS", null,
                        null, "Física I", 5) + "\n"
                        + row(';', "P1", 1, 2, "fis", null,
                        null, "Física II", 5)
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "repetido");
    }

    @Test
    public void allowsSameOrderInDifferentAssessmentsOrVersions() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\n"
                        + row(';', "P1", 1, 1, "A", null,
                        null, "A", 5) + "\n"
                        + row(';', "P1", 2, 1, "A", null,
                        null, "A", 5) + "\n"
                        + row(';', "P2", 1, 1, "A", null,
                        null, "A", 5)
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(3, preview.getRows().size());
    }

    @Test
    public void optionalColumnsMayBeEmpty() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\nP1;1;1;;;;Geral;7"
        );

        assertTrue(preview.isReadyToImport());
        assertNull(preview.getRows().get(0).getBlockCode());
        assertNull(preview.getRows().get(0).getAreaName());
        assertNull(preview.getRows().get(0).getComponentCode());
    }

    @Test
    public void ignoresTrailingBlankRecords() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\nP1;1;1;;;;Geral;7\n\n ; ; ; ; ; ; ; \n"
        );

        assertTrue(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
    }

    @Test
    public void reportsUnclosedQuotedField() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\nP1;1;1;;;COD;\"Química;10"
        );

        assertFalse(preview.isReadyToImport());
        assertContains(preview, "nao foi encerrado");
    }

    @Test
    public void previewCollectionsAreImmutable() {
        OmrAssessmentStructureCsvParser.Preview preview = parser.parse(
                header(';') + "\nP1;1;1;;;;Geral;7"
        );

        try {
            preview.getRows().clear();
            fail("A lista deveria ser imutavel.");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }

        try {
            preview.getIssues().add(null);
            fail("A lista deveria ser imutavel.");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    private static String header(char delimiter) {
        return String.join(
                Character.toString(delimiter),
                "codigo_prova",
                "versao_prova",
                "ordem_bloco",
                "codigo_bloco",
                "area",
                "codigo_componente",
                "componente",
                "itens"
        );
    }

    private static String row(
            char delimiter,
            String assessment,
            int version,
            int order,
            String block,
            String area,
            String componentCode,
            String component,
            int items
    ) {
        String separator = Character.toString(delimiter);
        return String.join(
                separator,
                value(assessment),
                Integer.toString(version),
                Integer.toString(order),
                value(block),
                value(area),
                value(componentCode),
                value(component),
                Integer.toString(items)
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static void assertContains(
            OmrAssessmentStructureCsvParser.Preview preview,
            String expected
    ) {
        for (OmrAssessmentStructureCsvParser.Issue issue
                : preview.getIssues()) {
            if (issue.getMessage().contains(expected)) {
                return;
            }
        }
        fail("Mensagem nao encontrada: " + expected);
    }
}
