package com.example.leitorgabaritoomr.vision.layout.template;

import java.util.Locale;

/**
 * Catalogo dos modelos de cartao-resposta publicados pelo app.
 *
 * Cada quantidade recebe uma identidade propria para que QR Code,
 * gabarito oficial e leitura possam confirmar exatamente a mesma
 * geometria.
 *
 * A familia compacta validada permanece imutavel entre 1 e 10
 * questoes. As familias media e ampliada distribuem, respectivamente,
 * ate 30 e ate 90 questoes sem reduzir todas as bolhas para a mesma
 * altura do cartao compacto.
 */
public final class OmrSheetTemplateCatalog {

    public static final int MIN_QUESTION_COUNT = 1;
    public static final int MAX_QUESTION_COUNT = 90;

    public static final int COMPACT_MIN_QUESTION_COUNT = 1;
    public static final int COMPACT_MAX_QUESTION_COUNT = 10;

    public static final int MEDIUM_MIN_QUESTION_COUNT = 11;
    public static final int MEDIUM_MAX_QUESTION_COUNT = 30;

    public static final int EXTENDED_MIN_QUESTION_COUNT = 31;
    public static final int EXTENDED_MAX_QUESTION_COUNT = 90;

    public static final int STANDARD_V2_FOUR_OPTION_COUNT = 4;
    public static final int STANDARD_V2_FIVE_OPTION_COUNT = 5;

    private static final int COMPACT_TEMPLATE_VERSION = 1;
    private static final int COMPACT_CANONICAL_WIDTH = 1200;
    private static final int COMPACT_CANONICAL_HEIGHT = 500;
    private static final int COMPACT_QUESTIONS_PER_BLOCK = 5;

    private static final String[] FOUR_OPTION_LABELS = {
            "A",
            "B",
            "C",
            "D"
    };

    private static final double[] COMPACT_OPTION_LOCAL_X = {
            0.30,
            0.44,
            0.58,
            0.72
    };

    private static final double COMPACT_FIRST_ROW_Y = 0.24;
    private static final double COMPACT_ROW_SPACING_Y = 0.12;

    /*
     * No canvas 1200 x 500, a regiao mede aproximadamente
     * 36 x 36 pixels.
     */
    private static final double COMPACT_SAMPLING_RADIUS_X = 0.015;
    private static final double COMPACT_SAMPLING_RADIUS_Y = 0.036;

    private static final int MEDIUM_TEMPLATE_VERSION = 1;
    private static final int MEDIUM_CANONICAL_WIDTH = 1200;
    private static final int MEDIUM_CANONICAL_HEIGHT = 750;
    private static final int MEDIUM_QUESTIONS_PER_BLOCK = 10;

    private static final double[] MEDIUM_OPTION_LOCAL_X = {
            0.35,
            0.49,
            0.63,
            0.77
    };

    private static final double MEDIUM_FIRST_ROW_Y = 0.18;
    private static final double MEDIUM_ROW_SPACING_Y = 0.068;
    private static final double MEDIUM_SAMPLING_RADIUS_X = 0.015;
    private static final double MEDIUM_SAMPLING_RADIUS_Y = 0.024;

    private static final int EXTENDED_TEMPLATE_VERSION = 1;
    private static final int EXTENDED_CANONICAL_WIDTH = 1200;
    private static final int EXTENDED_CANONICAL_HEIGHT = 1000;
    private static final int EXTENDED_QUESTIONS_PER_BLOCK = 15;

    private static final double[] EXTENDED_OPTION_LOCAL_X = {
            0.40,
            0.55,
            0.70,
            0.85
    };

    private static final double EXTENDED_FIRST_ROW_Y = 0.165;
    private static final double EXTENDED_ROW_SPACING_Y = 0.050;
    private static final double EXTENDED_SAMPLING_RADIUS_X = 0.011;
    private static final double EXTENDED_SAMPLING_RADIUS_Y = 0.018;

    private static final int STANDARD_V2_TEMPLATE_VERSION = 2;
    private static final int STANDARD_V2_CANONICAL_WIDTH = 1200;
    private static final int STANDARD_V2_LAYOUT_COLUMN_COUNT = 5;
    private static final int STANDARD_V2_QUESTIONS_PER_COLUMN = 18;

    private static final double STANDARD_V2_FIRST_ROW = 160.0;
    private static final double STANDARD_V2_ROW_SPACING = 52.0;
    private static final double STANDARD_V2_BOTTOM_RESERVE = 100.0;
    private static final double STANDARD_V2_SAMPLING_RADIUS = 18.0;

    /*
     * As posicoes mantem tambem o fundo local de medicao dentro da
     * regiao delimitada pelos marcadores quando as cinco colunas estao
     * ocupadas. Nao basta que apenas o contorno visivel da bolha caiba.
     */
    private static final double[] STANDARD_V2_FOUR_OPTION_LOCAL_X = {
            0.300,
            0.480,
            0.660,
            0.840
    };

    private static final String[] FIVE_OPTION_LABELS = {
            "A",
            "B",
            "C",
            "D",
            "E"
    };

    private static final double[] STANDARD_V2_FIVE_OPTION_LOCAL_X = {
            0.220,
            0.375,
            0.530,
            0.685,
            0.840
    };

    private OmrSheetTemplateCatalog() {
    }

    /**
     * Resolve a familia publicada correspondente a qualquer quantidade
     * aceita pelo aplicativo.
     */
    public static OmrSheetTemplateSpec publishedFourOptions(
            int questionCount
    ) {
        validatePublishedQuestionCount(questionCount);

        if (questionCount <= COMPACT_MAX_QUESTION_COUNT) {
            return compactFourOptions(questionCount);
        }

        if (questionCount <= MEDIUM_MAX_QUESTION_COUNT) {
            return mediumFourOptions(questionCount);
        }

        return extendedFourOptions(questionCount);
    }

    public static OmrSheetTemplateSpec compactFourOptions(
            int questionCount
    ) {
        if (questionCount < COMPACT_MIN_QUESTION_COUNT
                || questionCount
                > COMPACT_MAX_QUESTION_COUNT) {

            throw new IllegalArgumentException(
                    "O modelo compacto aceita entre "
                            + COMPACT_MIN_QUESTION_COUNT
                            + " e "
                            + COMPACT_MAX_QUESTION_COUNT
                            + " questoes."
            );
        }

        String templateId = String.format(
                Locale.US,
                "omr-compact-ad-q%03d",
                questionCount
        );

        String templateName =
                "Cartao compacto - "
                        + questionCount
                        + (questionCount == 1
                        ? " questao"
                        : " questoes")
                        + " - alternativas A-D";

        return new OmrSheetTemplateSpec(
                templateId,
                COMPACT_TEMPLATE_VERSION,
                templateName,
                questionCount,
                COMPACT_CANONICAL_WIDTH,
                COMPACT_CANONICAL_HEIGHT,
                COMPACT_QUESTIONS_PER_BLOCK,
                FOUR_OPTION_LABELS,
                COMPACT_OPTION_LOCAL_X,
                COMPACT_FIRST_ROW_Y,
                COMPACT_ROW_SPACING_Y,
                COMPACT_SAMPLING_RADIUS_X,
                COMPACT_SAMPLING_RADIUS_Y,
                1
        );
    }

    public static OmrSheetTemplateSpec mediumFourOptions(
            int questionCount
    ) {
        validateFamilyQuestionCount(
                "medio",
                questionCount,
                MEDIUM_MIN_QUESTION_COUNT,
                MEDIUM_MAX_QUESTION_COUNT
        );

        return new OmrSheetTemplateSpec(
                createTemplateId("medium", questionCount),
                MEDIUM_TEMPLATE_VERSION,
                createTemplateName("medio", questionCount),
                questionCount,
                MEDIUM_CANONICAL_WIDTH,
                MEDIUM_CANONICAL_HEIGHT,
                MEDIUM_QUESTIONS_PER_BLOCK,
                FOUR_OPTION_LABELS,
                MEDIUM_OPTION_LOCAL_X,
                MEDIUM_FIRST_ROW_Y,
                MEDIUM_ROW_SPACING_Y,
                MEDIUM_SAMPLING_RADIUS_X,
                MEDIUM_SAMPLING_RADIUS_Y,
                1
        );
    }

    public static OmrSheetTemplateSpec extendedFourOptions(
            int questionCount
    ) {
        validateFamilyQuestionCount(
                "ampliado",
                questionCount,
                EXTENDED_MIN_QUESTION_COUNT,
                EXTENDED_MAX_QUESTION_COUNT
        );

        return new OmrSheetTemplateSpec(
                createTemplateId("extended", questionCount),
                EXTENDED_TEMPLATE_VERSION,
                createTemplateName("ampliado", questionCount),
                questionCount,
                EXTENDED_CANONICAL_WIDTH,
                EXTENDED_CANONICAL_HEIGHT,
                EXTENDED_QUESTIONS_PER_BLOCK,
                FOUR_OPTION_LABELS,
                EXTENDED_OPTION_LOCAL_X,
                EXTENDED_FIRST_ROW_Y,
                EXTENDED_ROW_SPACING_Y,
                EXTENDED_SAMPLING_RADIUS_X,
                EXTENDED_SAMPLING_RADIUS_Y,
                1
        );
    }

    /**
     * Modelo fisico padronizado de segunda geracao. Ele ainda nao substitui
     * automaticamente os modelos publicados v1: deve ser selecionado de
     * forma explicita ate concluir a validacao visual e de leitura.
     */
    public static OmrSheetTemplateSpec standardFourOptionsV2(
            int questionCount
    ) {
        return createStandardV2(
                questionCount,
                "ad",
                "alternativas A-D",
                FOUR_OPTION_LABELS,
                STANDARD_V2_FOUR_OPTION_LOCAL_X
        );
    }

    /**
     * Usa a mesma malha fisica para a modalidade A-E, validada pelo fluxo
     * completo de criacao, persistencia, leitura e correcao.
     */
    public static OmrSheetTemplateSpec standardFiveOptionsV2(
            int questionCount
    ) {
        return createStandardV2(
                questionCount,
                "ae",
                "alternativas A-E",
                FIVE_OPTION_LABELS,
                STANDARD_V2_FIVE_OPTION_LOCAL_X
        );
    }

    /**
     * Seleciona explicitamente uma das duas modalidades padronizadas v2.
     * Os metodos especificos A-D e A-E permanecem disponiveis para deixar
     * claro o contrato de chamadas que ja conhecem previamente o modelo.
     */
    public static OmrSheetTemplateSpec standardOptionsV2(
            int questionCount,
            int optionCount
    ) {
        if (optionCount == STANDARD_V2_FOUR_OPTION_COUNT) {
            return standardFourOptionsV2(questionCount);
        }

        if (optionCount == STANDARD_V2_FIVE_OPTION_COUNT) {
            return standardFiveOptionsV2(questionCount);
        }

        throw new IllegalArgumentException(
                "O modelo padronizado v2 aceita 4 ou 5 alternativas."
        );
    }

    private static OmrSheetTemplateSpec createStandardV2(
            int questionCount,
            String optionCode,
            String optionDescription,
            String[] optionLabels,
            double[] optionLocalX
    ) {
        validatePublishedQuestionCount(questionCount);

        int actualRowCount = Math.min(
                questionCount,
                STANDARD_V2_QUESTIONS_PER_COLUMN
        );

        int canonicalHeight = (int) Math.round(
                STANDARD_V2_FIRST_ROW
                        + (actualRowCount - 1)
                        * STANDARD_V2_ROW_SPACING
                        + STANDARD_V2_BOTTOM_RESERVE
        );

        return new OmrSheetTemplateSpec(
                createStandardV2TemplateId(
                        optionCode,
                        questionCount
                ),
                STANDARD_V2_TEMPLATE_VERSION,
                "Cartao padronizado v2 - "
                        + questionCount
                        + (questionCount == 1
                        ? " questao - "
                        : " questoes - ")
                        + optionDescription,
                questionCount,
                STANDARD_V2_CANONICAL_WIDTH,
                canonicalHeight,
                STANDARD_V2_QUESTIONS_PER_COLUMN,
                STANDARD_V2_LAYOUT_COLUMN_COUNT,
                optionLabels,
                optionLocalX,
                STANDARD_V2_FIRST_ROW / canonicalHeight,
                STANDARD_V2_ROW_SPACING / canonicalHeight,
                STANDARD_V2_SAMPLING_RADIUS
                        / STANDARD_V2_CANONICAL_WIDTH,
                STANDARD_V2_SAMPLING_RADIUS
                        / canonicalHeight,
                1
        );
    }

    private static String createStandardV2TemplateId(
            String optionCode,
            int questionCount
    ) {
        return String.format(
                Locale.US,
                "omr-standard-%s-q%03d",
                optionCode,
                questionCount
        );
    }

    private static String createTemplateId(
            String family,
            int questionCount
    ) {
        return String.format(
                Locale.US,
                "omr-%s-ad-q%03d",
                family,
                questionCount
        );
    }

    private static String createTemplateName(
            String family,
            int questionCount
    ) {
        return "Cartao "
                + family
                + " - "
                + questionCount
                + " questoes - alternativas A-D";
    }

    private static void validatePublishedQuestionCount(
            int questionCount
    ) {
        validateFamilyQuestionCount(
                "publicado",
                questionCount,
                MIN_QUESTION_COUNT,
                MAX_QUESTION_COUNT
        );
    }

    private static void validateFamilyQuestionCount(
            String family,
            int questionCount,
            int minimum,
            int maximum
    ) {
        if (questionCount < minimum
                || questionCount > maximum) {

            throw new IllegalArgumentException(
                    "O modelo "
                            + family
                            + " aceita entre "
                            + minimum
                            + " e "
                            + maximum
                            + " questoes."
            );
        }
    }
}
