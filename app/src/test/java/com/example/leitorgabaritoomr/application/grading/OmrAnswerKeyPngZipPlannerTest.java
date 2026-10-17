package com.example.leitorgabaritoomr.application.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.infrastructure.export.OmrAnswerKeyCsvExporter;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;

public final class OmrAnswerKeyPngZipPlannerTest {

    private final OmrAnswerKeyCsvParser parser =
            new OmrAnswerKeyCsvParser();
    private final OmrAnswerKeyPngZipPlanner planner =
            new OmrAnswerKeyPngZipPlanner();

    @Test
    public void downloadableTemplateCreatesTwoPortablePngEntries() {
        OmrAnswerKeyPngZipPlan plan = planner.plan(parser.parse(
                new OmrAnswerKeyCsvExporter().buildTemplate()
        ));

        assertTrue(plan.isReadyToExport());
        assertEquals(2, plan.getCardCount());
        assertEquals(
                "mat-a-v1.png",
                plan.getCards().get(0).getOutputFileName()
        );
        assertEquals(
                "nat-b-v1.png",
                plan.getCards().get(1).getOutputFileName()
        );
        assertEquals(
                "MATEMATICA - MODELO A",
                plan.getCards().get(0).getHeaderOverride()
        );
        assertEquals(
                "",
                plan.getCards().get(1).getFooterOverride()
        );
    }

    @Test
    public void blankTextUsesDefaultAndMarkerOmitsText() {
        String row = "\r\nTXT;1;Textos;4;4;auto;A|B|C|D;1;"
                + ";[VAZIO];;texto;;;;";

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(header() + row)
        );

        assertTrue(plan.isReadyToExport());
        OmrAnswerKeyPngZipPlan.Card card = plan.getCards().get(0);
        assertNull(card.getHeaderOverride());
        assertEquals("", card.getInstructionOverride());
        assertNull(card.getFooterOverride());
    }

    @Test
    public void missingFileNameGetsDeterministicNameAndVersion() {
        String row = "\r\nCiências 2026;3;Natureza;4;4;auto;"
                + "A|B|C|D;1;;;;;;;;";

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(header() + row)
        );

        assertTrue(plan.isReadyToExport());
        assertEquals(
                "ciencias-2026-v3.png",
                plan.getCards().get(0).getOutputFileName()
        );
    }

    @Test
    public void duplicateFileNamesAreRejectedIgnoringCaseAndExtension() {
        String rows = "\r\nA;1;Primeiro;4;4;auto;A|B|C|D;1;;;;Cartao;;;;"
                + "\r\nB;1;Segundo;4;4;auto;D|C|B|A;1;;;;CARTAO.PNG;;;;";

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(header() + rows)
        );

        assertFalse(plan.isReadyToExport());
        assertEquals(1, plan.getIssues().size());
        assertTrue(plan.getIssues().get(0).getMessage()
                .contains("repetido"));
    }

    @Test
    public void reservedPortableFileNameIsRejected() {
        String row = "\r\nA;1;Teste;4;4;auto;A|B|C|D;1;;;;CON.txt;;;;";

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(header() + row)
        );

        assertFalse(plan.isReadyToExport());
        assertTrue(plan.getIssues().get(0).getMessage()
                .contains("reservado"));
    }

    @Test
    public void legacyDevelopmentLayoutCannotBeExportedAsCurrentCard() {
        String row = "\r\nLEGADO;1;Legado;52;4;13;"
                + answers(52)
                + ";1;;;;legado;legacy-key;avalie-ce-development;1";

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(header() + row)
        );

        assertFalse(plan.isReadyToExport());
        assertTrue(plan.getIssues().get(0).getMessage()
                .contains("modelo de cartão publicado"));
    }

    @Test
    public void renderedTextLongerThanEightyCharactersIsRejected() {
        String longHeader = repeated('A', 81);
        String row = "\r\nA;1;Teste;4;4;auto;A|B|C|D;1;"
                + longHeader
                + ";;;teste;;;;";

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(header() + row)
        );

        assertFalse(plan.isReadyToExport());
        assertTrue(plan.getIssues().get(0).getMessage()
                .contains("80 caracteres"));
    }

    @Test
    public void batchAboveSafeLimitIsRejectedBeforeRendering() {
        StringBuilder csv = new StringBuilder(header());
        for (int index = 1;
             index <= OmrAnswerKeyPngZipPlanner.MAX_CARD_COUNT + 1;
             index++) {
            csv.append("\r\nK")
                    .append(index)
                    .append(";1;Gabarito ")
                    .append(index)
                    .append(";1;4;auto;A;1;;;;arquivo-")
                    .append(index)
                    .append(";;;;");
        }

        OmrAnswerKeyPngZipPlan plan = planner.plan(
                parser.parse(csv.toString())
        );

        assertFalse(plan.isReadyToExport());
        assertEquals(1, plan.getIssues().size());
        assertTrue(plan.getIssues().get(0).getMessage()
                .contains(String.valueOf(
                        OmrAnswerKeyPngZipPlanner.MAX_CARD_COUNT
                )));
    }

    @Test
    public void validPlanSurvivesActivityStateSerialization()
            throws Exception {
        OmrAnswerKeyPngZipPlan original = planner.plan(parser.parse(
                new OmrAnswerKeyCsvExporter().buildTemplate()
        ));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        OmrAnswerKeyPngZipPlan restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            restored = (OmrAnswerKeyPngZipPlan) input.readObject();
        }

        assertTrue(restored.isReadyToExport());
        assertEquals(original.getCardCount(), restored.getCardCount());
        assertEquals(
                original.getCards().get(1).getOutputFileName(),
                restored.getCards().get(1).getOutputFileName()
        );
        assertEquals(
                original.getCards().get(1)
                        .getAnswerKeyDefinition().getLayoutId(),
                restored.getCards().get(1)
                        .getAnswerKeyDefinition().getLayoutId()
        );
    }

    private static String header() {
        return "codigo_gabarito;versao;nome;questoes;alternativas;"
                + "itens_por_coluna;respostas;pesos;cabecalho;instrucoes;"
                + "rodape;nome_arquivo;id_gabarito;layout_id;layout_versao";
    }

    private static String answers(int count) {
        StringBuilder result = new StringBuilder(count * 2);
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                result.append('|');
            }
            result.append((char) ('A' + index % 4));
        }
        return result.toString();
    }

    private static String repeated(char value, int count) {
        return String.format(
                Locale.ROOT,
                "%" + count + "s",
                ""
        ).replace(' ', value);
    }
}
