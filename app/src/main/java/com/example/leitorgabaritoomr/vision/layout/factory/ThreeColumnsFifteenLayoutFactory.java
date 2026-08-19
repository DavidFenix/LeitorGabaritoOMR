package com.example.leitorgabaritoomr.vision.layout.factory;

import com.example.leitorgabaritoomr.vision.layout.NormalizedCoordinate;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gera o primeiro layout de desenvolvimento:
 *
 * - três colunas;
 * - quinze questões por coluna;
 * - cinco alternativas por questão;
 * - numeração sequencial de 1 a 45.
 *
 * As posições são normalizadas e poderão ser ajustadas depois
 * da validação visual no Laboratório OMR.
 */
public final class ThreeColumnsFifteenLayoutFactory {

    private static final String LAYOUT_ID =
            "three-columns-fifteen";

    private static final int LAYOUT_VERSION = 1;

    /*
     * Canvas digital de referência.
     *
     * Não representa milímetros nem tamanho físico do papel.
     */
    private static final int CANONICAL_WIDTH = 1500;
    private static final int CANONICAL_HEIGHT = 700;

    private static final int COLUMN_COUNT = 3;
    private static final int QUESTIONS_PER_COLUMN = 15;

    /*
     * Área vertical ocupada pelas quinze linhas.
     */
    private static final double FIRST_ROW_Y = 0.08;
    private static final double ROW_SPACING_Y = 0.06;

    /*
     * Posições das alternativas dentro de cada coluna.
     *
     * Os valores são relativos à largura da própria coluna:
     *
     * 0.35 = 35% da largura da coluna
     * 0.46 = 46%
     * ...
     */
    private static final double[] OPTION_LOCAL_X = {
            0.35,
            0.46,
            0.57,
            0.68,
            0.79
    };

    private static final String[] OPTION_LABELS = {
            "A",
            "B",
            "C",
            "D",
            "E"
    };

    /*
     * Região inicial de amostragem ao redor de cada bolha.
     *
     * Os raios X e Y são diferentes porque o canvas é largo.
     * Em pixels, a região resultante fica aproximadamente:
     *
     * largura:  36 pixels
     * altura:   25 pixels
     */
    private static final double SAMPLING_RADIUS_X = 0.012;
    private static final double SAMPLING_RADIUS_Y = 0.018;

    private ThreeColumnsFifteenLayoutFactory() {
        /*
         * Fábrica utilitária: não precisa ser instanciada.
         */
    }

    public static OmrLayoutDefinition create() {
        List<OmrBlockDefinition> blocks =
                new ArrayList<>();

        for (int columnIndex = 0;
             columnIndex < COLUMN_COUNT;
             columnIndex++) {

            blocks.add(
                    createColumnBlock(columnIndex)
            );
        }

        OmrLayoutDefinition layout =
                new OmrLayoutDefinition(
                        LAYOUT_ID,
                        LAYOUT_VERSION,
                        "Tres colunas, quinze itens e cinco alternativas",
                        CANONICAL_WIDTH,
                        CANONICAL_HEIGHT,
                        blocks
                );

        validateGeneratedLayout(layout);

        return layout;
    }

    private static OmrBlockDefinition createColumnBlock(
            int columnIndex
    ) {
        int humanColumnNumber =
                columnIndex + 1;

        String blockId =
                "column-" + humanColumnNumber;

        String blockTitle =
                "Coluna " + humanColumnNumber;

        List<OmrQuestionDefinition> questions =
                new ArrayList<>();

        for (int rowIndex = 0;
             rowIndex < QUESTIONS_PER_COLUMN;
             rowIndex++) {

            int questionNumber =
                    columnIndex
                            * QUESTIONS_PER_COLUMN
                            + rowIndex
                            + 1;

            questions.add(
                    createQuestion(
                            blockId,
                            columnIndex,
                            rowIndex,
                            questionNumber
                    )
            );
        }

        return new OmrBlockDefinition(
                blockId,
                blockTitle,
                questions
        );
    }

    private static OmrQuestionDefinition createQuestion(
            String blockId,
            int columnIndex,
            int rowIndex,
            int questionNumber
    ) {
        String questionNumberText =
                String.format(
                        Locale.US,
                        "%02d",
                        questionNumber
                );

        String questionId =
                blockId
                        + "-question-"
                        + questionNumberText;

        String questionLabel =
                String.valueOf(questionNumber);

        double centerY =
                FIRST_ROW_Y
                        + rowIndex
                        * ROW_SPACING_Y;

        List<OmrOptionDefinition> options =
                new ArrayList<>();

        for (int optionIndex = 0;
             optionIndex < OPTION_LABELS.length;
             optionIndex++) {

            String optionLabel =
                    OPTION_LABELS[optionIndex];

            double centerX =
                    calculateOptionCenterX(
                            columnIndex,
                            optionIndex
                    );

            String optionId =
                    questionId
                            + "-option-"
                            + optionLabel.toLowerCase(
                            Locale.US
                    );

            options.add(
                    new OmrOptionDefinition(
                            optionId,
                            optionLabel,
                            new NormalizedCoordinate(
                                    centerX,
                                    centerY
                            ),
                            SAMPLING_RADIUS_X,
                            SAMPLING_RADIUS_Y
                    )
            );
        }

        return new OmrQuestionDefinition(
                questionId,
                questionLabel,
                options
        );
    }

    private static double calculateOptionCenterX(
            int columnIndex,
            int optionIndex
    ) {
        double columnWidth =
                1.0 / COLUMN_COUNT;

        double columnLeft =
                columnIndex * columnWidth;

        double localX =
                OPTION_LOCAL_X[optionIndex];

        return columnLeft
                + localX * columnWidth;
    }

    private static void validateGeneratedLayout(
            OmrLayoutDefinition layout
    ) {
        int expectedBlocks = 3;
        int expectedQuestions = 45;
        int expectedOptions = 225;

        if (layout.getBlockCount()
                != expectedBlocks) {

            throw new IllegalStateException(
                    "A fábrica deveria gerar "
                            + expectedBlocks
                            + " blocos, mas gerou "
                            + layout.getBlockCount()
                            + "."
            );
        }

        if (layout.getQuestionCount()
                != expectedQuestions) {

            throw new IllegalStateException(
                    "A fábrica deveria gerar "
                            + expectedQuestions
                            + " questões, mas gerou "
                            + layout.getQuestionCount()
                            + "."
            );
        }

        if (layout.getOptionCount()
                != expectedOptions) {

            throw new IllegalStateException(
                    "A fábrica deveria gerar "
                            + expectedOptions
                            + " alternativas, mas gerou "
                            + layout.getOptionCount()
                            + "."
            );
        }
    }
}