package com.example.leitorgabaritoomr.vision.layout.factory;

import com.example.leitorgabaritoomr.vision.layout.NormalizedCoordinate;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converte uma especificacao de cartao-resposta em um layout OMR.
 *
 * Diferentemente da grade regular legada, esta fabrica conhece a
 * quantidade total de questoes e nao completa a ultima coluna com
 * linhas inexistentes.
 */
public final class OmrDynamicLayoutFactory {

    private OmrDynamicLayoutFactory() {
    }

    public static OmrLayoutDefinition create(
            OmrSheetTemplateSpec spec
    ) {
        if (spec == null) {
            throw new IllegalArgumentException(
                    "A especificacao do modelo e obrigatoria."
            );
        }

        List<OmrBlockDefinition> blocks =
                new ArrayList<>(spec.getBlockCount());

        for (int blockIndex = 0;
             blockIndex < spec.getBlockCount();
             blockIndex++) {

            blocks.add(
                    createBlock(spec, blockIndex)
            );
        }

        OmrLayoutDefinition layout =
                new OmrLayoutDefinition(
                        spec.getTemplateId(),
                        spec.getTemplateVersion(),
                        spec.getTemplateName(),
                        spec.getCanonicalWidth(),
                        spec.getCanonicalHeight(),
                        blocks
                );

        validateGeneratedLayout(spec, layout);

        return layout;
    }

    private static OmrBlockDefinition createBlock(
            OmrSheetTemplateSpec spec,
            int blockIndex
    ) {
        int humanBlockNumber = blockIndex + 1;

        String blockId = String.format(
                Locale.US,
                "block-%02d",
                humanBlockNumber
        );

        String blockTitle =
                "Bloco " + humanBlockNumber;

        int questionCount =
                spec.getQuestionCountForBlock(blockIndex);

        List<OmrQuestionDefinition> questions =
                new ArrayList<>(questionCount);

        for (int rowIndex = 0;
             rowIndex < questionCount;
             rowIndex++) {

            int globalQuestionIndex =
                    blockIndex
                            * spec.getQuestionsPerBlock()
                            + rowIndex;

            questions.add(
                    createQuestion(
                            spec,
                            blockIndex,
                            rowIndex,
                            globalQuestionIndex
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
            OmrSheetTemplateSpec spec,
            int blockIndex,
            int rowIndex,
            int globalQuestionIndex
    ) {
        int questionNumber =
                spec.getFirstQuestionNumber()
                        + globalQuestionIndex;

        String questionId = String.format(
                Locale.US,
                "question-%03d",
                questionNumber
        );

        String questionLabel =
                String.valueOf(questionNumber);

        double centerY =
                spec.getFirstRowY()
                        + rowIndex
                        * spec.getRowSpacingY();

        List<OmrOptionDefinition> options =
                createOptions(
                        spec,
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

    private static List<OmrOptionDefinition> createOptions(
            OmrSheetTemplateSpec spec,
            int blockIndex,
            String questionId,
            double centerY
    ) {
        String[] labels = spec.getOptionLabels();
        double[] localPositions = spec.getOptionLocalX();

        List<OmrOptionDefinition> options =
                new ArrayList<>(labels.length);

        for (int optionIndex = 0;
             optionIndex < labels.length;
             optionIndex++) {

            double centerX = calculateOptionCenterX(
                    spec,
                    blockIndex,
                    localPositions[optionIndex]
            );

            String optionId = String.format(
                    Locale.US,
                    "%s-option-%02d",
                    questionId,
                    optionIndex + 1
            );

            options.add(
                    new OmrOptionDefinition(
                            optionId,
                            labels[optionIndex],
                            new NormalizedCoordinate(
                                    centerX,
                                    centerY
                            ),
                            spec.getSamplingRadiusX(),
                            spec.getSamplingRadiusY()
                    )
            );
        }

        return options;
    }

    private static double calculateOptionCenterX(
            OmrSheetTemplateSpec spec,
            int blockIndex,
            double localX
    ) {
        double blockWidth =
                1.0 / spec.getBlockCount();

        double blockLeft =
                blockIndex * blockWidth;

        return blockLeft
                + localX * blockWidth;
    }

    private static void validateGeneratedLayout(
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout
    ) {
        if (layout.getBlockCount()
                != spec.getBlockCount()) {

            throw new IllegalStateException(
                    "A fabrica gerou uma quantidade"
                            + " inesperada de blocos."
            );
        }

        if (layout.getQuestionCount()
                != spec.getQuestionCount()) {

            throw new IllegalStateException(
                    "A fabrica gerou uma quantidade"
                            + " inesperada de questoes."
            );
        }

        if (layout.getOptionCount()
                != spec.getTotalOptionCount()) {

            throw new IllegalStateException(
                    "A fabrica gerou uma quantidade"
                            + " inesperada de alternativas."
            );
        }
    }
}
