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
 * Constrói layouts formados por blocos regulares.
 */
public final class RegularGridLayoutFactory {

    private RegularGridLayoutFactory() {
    }

    public static OmrLayoutDefinition create(
            RegularGridLayoutConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuração da grade é obrigatória."
            );
        }

        List<OmrBlockDefinition> blocks =
                new ArrayList<>();

        for (int blockIndex = 0;
             blockIndex < config.getBlockCount();
             blockIndex++) {

            blocks.add(
                    createBlock(
                            config,
                            blockIndex
                    )
            );
        }

        return new OmrLayoutDefinition(
                config.getLayoutId(),
                config.getLayoutVersion(),
                config.getLayoutName(),
                config.getCanonicalWidth(),
                config.getCanonicalHeight(),
                blocks
        );
    }

    private static OmrBlockDefinition createBlock(
            RegularGridLayoutConfig config,
            int blockIndex
    ) {
        int humanBlockNumber =
                blockIndex + 1;

        String blockId =
                String.format(
                        Locale.US,
                        "block-%02d",
                        humanBlockNumber
                );

        String blockTitle =
                "Bloco " + humanBlockNumber;

        List<OmrQuestionDefinition> questions =
                new ArrayList<>();

        for (int rowIndex = 0;
             rowIndex
                     < config.getQuestionsPerBlock();
             rowIndex++) {

            questions.add(
                    createQuestion(
                            config,
                            blockIndex,
                            rowIndex,
                            blockId
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
            RegularGridLayoutConfig config,
            int blockIndex,
            int rowIndex,
            String blockId
    ) {
        int humanRowNumber =
                rowIndex + 1;

        String questionId =
                String.format(
                        Locale.US,
                        "%s-row-%02d",
                        blockId,
                        humanRowNumber
                );

        int questionNumber =
                calculateQuestionNumber(
                        config,
                        blockIndex,
                        rowIndex
                );

        String questionLabel =
                String.valueOf(questionNumber);

        double centerY =
                config.getFirstRowY()
                        + rowIndex
                        * config.getRowSpacingY();

        List<OmrOptionDefinition> options =
                createOptions(
                        config,
                        blockIndex,
                        questionId,
                        centerY
                );

        return new OmrQuestionDefinition(
                questionId,
                questionLabel,
                options
        );
    }

    private static int calculateQuestionNumber(
            RegularGridLayoutConfig config,
            int blockIndex,
            int rowIndex
    ) {
        if (config
                .isRestartQuestionLabelsEachBlock()) {

            return config.getFirstQuestionNumber()
                    + rowIndex;
        }

        return config.getFirstQuestionNumber()
                + blockIndex
                * config.getQuestionsPerBlock()
                + rowIndex;
    }

    private static List<OmrOptionDefinition>
    createOptions(
            RegularGridLayoutConfig config,
            int blockIndex,
            String questionId,
            double centerY
    ) {
        List<OmrOptionDefinition> options =
                new ArrayList<>();

        String[] labels =
                config.getOptionLabels();

        double[] localPositions =
                config.getOptionLocalX();

        for (int optionIndex = 0;
             optionIndex < labels.length;
             optionIndex++) {

            String optionLabel =
                    labels[optionIndex];

            double centerX =
                    calculateOptionCenterX(
                            config,
                            blockIndex,
                            localPositions[optionIndex]
                    );

            String optionId =
                    String.format(
                            Locale.US,
                            "%s-option-%02d",
                            questionId,
                            optionIndex + 1
                    );

            options.add(
                    new OmrOptionDefinition(
                            optionId,
                            optionLabel,
                            new NormalizedCoordinate(
                                    centerX,
                                    centerY
                            ),
                            config.getSamplingRadiusX(),
                            config.getSamplingRadiusY()
                    )
            );
        }

        return options;
    }

    private static double calculateOptionCenterX(
            RegularGridLayoutConfig config,
            int blockIndex,
            double localX
    ) {
        double blockWidth =
                1.0 / config.getBlockCount();

        double blockLeft =
                blockIndex * blockWidth;

        return blockLeft
                + localX * blockWidth;
    }
}