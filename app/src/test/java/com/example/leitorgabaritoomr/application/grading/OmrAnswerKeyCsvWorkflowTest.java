package com.example.leitorgabaritoomr.application.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAnswerKeyCsvExporter;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OmrAnswerKeyCsvWorkflowTest {

    private final OmrAnswerKeyCsvParser parser =
            new OmrAnswerKeyCsvParser();
    private final OmrAnswerKeyCsvExporter exporter =
            new OmrAnswerKeyCsvExporter();

    @Test
    public void downloadableTemplateIsValidAndUsesV4() {
        OmrAnswerKeyCsvParser.Preview preview = parser.parse(
                exporter.buildTemplate()
        );
        assertTrue(preview.isReadyToImport());
        assertEquals(2, preview.getRows().size());
        assertEquals(
                4,
                preview.getRows().get(0)
                        .getAnswerKeyDefinition()
                        .getLayoutVersion()
        );
        assertEquals(
                "gabarito-csv:mat-a",
                preview.getRows().get(0)
                        .getAnswerKeyDefinition().getId()
        );
    }

    @Test
    public void acceptsMultipleAnswersAndDifferentWeights() {
        OmrAnswerKeyCsvParser.Row row = parser.parse(
                exporter.buildTemplate()
        ).getRows().get(1);
        assertEquals(2, row.getAnswerKeyDefinition().getEntries()
                .get(0).getAcceptedOptionCount());
        assertEquals(2.0, row.getAnswerKeyDefinition().getEntries()
                .get(2).getWeight(), 0.000001);
        assertEquals(5, row.getQuestionsPerColumn());
        assertEquals("NATUREZA - MODELO B", row.getHeaderText());
        assertEquals("", row.getFooterText());
    }

    @Test
    public void textOmissionMarkerIsDifferentFromBlankCell() {
        String csv = header()
                + "\r\nTXT;1;Textos;4;4;auto;A|B|C|D;1;"
                + ";[vazio];;texto;;;;";

        OmrAnswerKeyCsvParser.Row row = parser.parse(csv)
                .getRows().get(0);

        assertNull(row.getHeaderText());
        assertEquals("", row.getInstructionsText());
        assertNull(row.getFooterText());
    }

    @Test
    public void exportedDefinitionsCanBeImportedWithoutLosingIdentity() {
        OmrAnswerKeyCsvParser.Preview source = parser.parse(
                exporter.buildTemplate()
        );
        List<OmrAnswerKeyDefinition> definitions = definitions(source);
        OmrAnswerKeyCsvParser.Preview restored = parser.parse(
                exporter.build(definitions)
        );
        assertTrue(restored.isReadyToImport());
        assertEquals(2, restored.getRows().size());
        assertEquals(
                definitions.get(1).getId(),
                restored.getRows().get(1)
                        .getAnswerKeyDefinition().getId()
        );
        assertEquals(
                definitions.get(1).getLayoutId(),
                restored.getRows().get(1)
                        .getAnswerKeyDefinition().getLayoutId()
        );
    }

    @Test
    public void writesUtf8BomForExcel() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exporter.writeTemplate(output);
        byte[] bytes = output.toByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        assertTrue(new String(
                bytes, 3, bytes.length - 3, StandardCharsets.UTF_8
        ).contains("Matematica"));
    }

    @Test
    public void readsWindows1252WithAccents() {
        String csv = header()
                + "\r\nNAT;1;Ciências;4;4;automatico;A|B|C|D;1;;;;nat;;;;";
        OmrAnswerKeyCsvParser.Preview preview = parser.parse(
                csv.getBytes(Charset.forName("windows-1252"))
        );
        assertTrue(preview.isReadyToImport());
        assertEquals("Windows-1252", preview.getEncoding());
        assertEquals("Ciências", preview.getRows().get(0)
                .getAnswerKeyDefinition().getName());
    }

    @Test
    public void rejectsMissingRequiredHeader() {
        OmrAnswerKeyCsvParser.Preview preview = parser.parse(
                "codigo_gabarito;versao\r\nA;1"
        );
        assertFalse(preview.isReadyToImport());
        assertTrue(preview.getIssues().size() >= 5);
    }

    @Test
    public void rejectsWrongAnswerCountAndInvalidAlternative() {
        OmrAnswerKeyCsvParser.Preview shortAnswers = parser.parse(
                header() + "\r\nA;1;Teste;4;4;auto;A|B|C;1;;;;;;;;"
        );
        assertFalse(shortAnswers.isReadyToImport());

        OmrAnswerKeyCsvParser.Preview invalidOption = parser.parse(
                header() + "\r\nA;1;Teste;4;4;auto;A|B|C|E;1;;;;;;;;"
        );
        assertFalse(invalidOption.isReadyToImport());
    }

    @Test
    public void rejectsDuplicateIdentityEvenWhenCodesDiffer() {
        String rows = "\r\nA;1;Primeiro;4;4;auto;A|B|C|D;1;;;;a;exact-id;;;"
                + "\r\nB;1;Segundo;4;4;auto;D|C|B|A;1;;;;b;exact-id;;;";
        OmrAnswerKeyCsvParser.Preview preview = parser.parse(header() + rows);
        assertFalse(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
    }

    @Test
    public void rejectsTechnicalLayoutThatDisagreesWithVisibleColumns() {
        String row = "\r\nA;1;Teste;4;5;auto;A|B|C|D;1;;;;a;id;"
                + "omr-flex-ad-q004-r004;4";
        OmrAnswerKeyCsvParser.Preview preview = parser.parse(header() + row);
        assertFalse(preview.isReadyToImport());
    }

    @Test
    public void protectsFormulasAndRestoresOriginalTextOnReimport() {
        OmrAnswerKeyDefinition original = parser.parse(
                header() + "\r\nA;1;=2+2;4;4;auto;A|B|C|D;1;;;;;;;;"
        ).getRows().get(0).getAnswerKeyDefinition();
        String csv = exporter.build(java.util.Collections.singletonList(
                original
        ));
        assertTrue(csv.contains("\"'=2+2\""));
        OmrAnswerKeyCsvParser.Preview restored = parser.parse(csv);
        assertTrue(restored.isReadyToImport());
        assertEquals("=2+2", restored.getRows().get(0)
                .getAnswerKeyDefinition().getName());
    }

    @Test
    public void registrationReturnsImportedAndUnchangedCounts() {
        OmrAnswerKeyCsvParser.Preview preview = parser.parse(
                exporter.buildTemplate()
        );
        RecordingBatchRepository repository =
                new RecordingBatchRepository(1);
        OmrAnswerKeyCsvRegistrationService.ImportSummary summary =
                new OmrAnswerKeyCsvRegistrationService(repository)
                        .importPreview(preview);
        assertEquals(2, summary.getProcessedCount());
        assertEquals(1, summary.getImportedCount());
        assertEquals(1, summary.getUnchangedCount());
        assertEquals(2, repository.received.size());
    }

    @Test
    public void registrationRejectsInvalidPreviewBeforeRepositoryCall() {
        RecordingBatchRepository repository =
                new RecordingBatchRepository(0);
        try {
            new OmrAnswerKeyCsvRegistrationService(repository)
                    .importPreview(parser.parse(""));
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
        assertTrue(repository.received.isEmpty());
    }

    private static List<OmrAnswerKeyDefinition> definitions(
            OmrAnswerKeyCsvParser.Preview preview
    ) {
        List<OmrAnswerKeyDefinition> result = new ArrayList<>();
        for (OmrAnswerKeyCsvParser.Row row : preview.getRows()) {
            result.add(row.getAnswerKeyDefinition());
        }
        return result;
    }

    private static String header() {
        return "codigo_gabarito;versao;nome;questoes;alternativas;"
                + "itens_por_coluna;respostas;pesos;cabecalho;instrucoes;"
                + "rodape;nome_arquivo;id_gabarito;layout_id;layout_versao";
    }

    private static final class RecordingBatchRepository
            implements OmrAnswerKeyBatchRepository {
        private final int imported;
        private final List<OmrAnswerKeyDefinition> received =
                new ArrayList<>();

        private RecordingBatchRepository(int imported) {
            this.imported = imported;
        }

        @Override
        public int saveAllCompatible(
                List<OmrAnswerKeyDefinition> answerKeys
        ) {
            received.addAll(answerKeys);
            return imported;
        }
    }
}
