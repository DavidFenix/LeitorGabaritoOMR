package com.example.leitorgabaritoomr.vision.layout.template;

import java.util.Locale;

/**
 * Catalogo dos modelos de cartao-resposta publicados pelo app.
 *
 * Nesta primeira versao, somente a familia compacta de 1 a 10
 * questoes esta publicada. Cada quantidade recebe uma identidade
 * propria para que QR Code, gabarito oficial e leitura possam
 * confirmar exatamente a mesma geometria.
 */
public final class OmrSheetTemplateCatalog {

    public static final int COMPACT_MIN_QUESTION_COUNT = 1;
    public static final int COMPACT_MAX_QUESTION_COUNT = 10;

    private static final int COMPACT_TEMPLATE_VERSION = 1;
    private static final int COMPACT_CANONICAL_WIDTH = 1200;
    private static final int COMPACT_CANONICAL_HEIGHT = 700;
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

    private static final double COMPACT_FIRST_ROW_Y = 0.20;
    private static final double COMPACT_ROW_SPACING_Y = 0.15;

    /*
     * No canvas 1200 x 700, a regiao mede aproximadamente
     * 36 x 36 pixels.
     */
    private static final double COMPACT_SAMPLING_RADIUS_X = 0.015;
    private static final double COMPACT_SAMPLING_RADIUS_Y = 0.026;

    private OmrSheetTemplateCatalog() {
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
}
