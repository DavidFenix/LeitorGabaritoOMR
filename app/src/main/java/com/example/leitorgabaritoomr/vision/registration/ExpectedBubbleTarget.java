package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.Locale;

/**
 * Representa uma bolha que o layout espera encontrar na
 * imagem normalizada.
 *
 * A posição prevista é apenas o ponto de partida do registro.
 * Ela não é tratada como a posição definitiva de medição.
 */
public final class ExpectedBubbleTarget {

    private final int blockIndex;
    private final int questionIndex;
    private final int optionIndex;

    private final String blockId;
    private final String questionId;

    private final OmrOptionDefinition option;

    private final PixelRectangle expectedBounds;

    private final double expectedCenterX;
    private final double expectedCenterY;

    public ExpectedBubbleTarget(
            int blockIndex,
            int questionIndex,
            int optionIndex,
            String blockId,
            String questionId,
            OmrOptionDefinition option,
            PixelRectangle expectedBounds,
            double expectedCenterX,
            double expectedCenterY
    ) {
        validateIndex(
                "blockIndex",
                blockIndex
        );

        validateIndex(
                "questionIndex",
                questionIndex
        );

        validateIndex(
                "optionIndex",
                optionIndex
        );

        this.blockId =
                requireText(
                        "blockId",
                        blockId
                );

        this.questionId =
                requireText(
                        "questionId",
                        questionId
                );

        if (option == null) {
            throw new IllegalArgumentException(
                    "A alternativa é obrigatória."
            );
        }

        if (expectedBounds == null) {
            throw new IllegalArgumentException(
                    "Os limites esperados são obrigatórios."
            );
        }

        validateFinite(
                "expectedCenterX",
                expectedCenterX
        );

        validateFinite(
                "expectedCenterY",
                expectedCenterY
        );

        this.blockIndex = blockIndex;
        this.questionIndex = questionIndex;
        this.optionIndex = optionIndex;

        this.option = option;
        this.expectedBounds = expectedBounds;

        this.expectedCenterX = expectedCenterX;
        this.expectedCenterY = expectedCenterY;
    }

    private void validateIndex(
            String fieldName,
            int value
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName
                            + " não pode ser negativo."
            );
        }
    }

    private String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser finito."
            );
        }
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public String getBlockId() {
        return blockId;
    }

    public String getQuestionId() {
        return questionId;
    }

    public OmrOptionDefinition getOption() {
        return option;
    }

    public String getOptionId() {
        return option.getId();
    }

    public PixelRectangle getExpectedBounds() {
        return expectedBounds;
    }

    public double getExpectedCenterX() {
        return expectedCenterX;
    }

    public double getExpectedCenterY() {
        return expectedCenterY;
    }

    public double getExpectedWidth() {
        return expectedBounds.getWidth();
    }

    public double getExpectedHeight() {
        return expectedBounds.getHeight();
    }

    public double getExpectedDiagonal() {
        return Math.hypot(
                getExpectedWidth(),
                getExpectedHeight()
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s expected=(%.2f, %.2f) size=%dx%d",
                getOptionId(),
                expectedCenterX,
                expectedCenterY,
                expectedBounds.getWidth(),
                expectedBounds.getHeight()
        );
    }
}
