package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converte as posições normalizadas do layout em alvos
 * esperados na resolução real da região normalizada.
 *
 * Usa as mesmas regras de floor/ceil empregadas pela medição
 * das bolhas. Assim, o Laboratório e o cálculo partem da mesma
 * geometria em pixels.
 */
public final class ExpectedBubbleTargetFactory {

    public List<ExpectedBubbleTarget> create(
            OmrLayoutDefinition layout,
            int imageWidth,
            int imageHeight
    ) {
        validateInput(
                layout,
                imageWidth,
                imageHeight
        );

        List<ExpectedBubbleTarget> targets =
                new ArrayList<>();

        int blockIndex = 0;

        for (OmrBlockDefinition block
                : layout.getBlocks()) {

            int questionIndex = 0;

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                int optionIndex = 0;

                for (OmrOptionDefinition option
                        : question.getOptions()) {

                    PixelRectangle bounds =
                            createExpectedBounds(
                                    option,
                                    imageWidth,
                                    imageHeight
                            );

                    double centerX =
                            bounds.getLeft()
                                    + (
                                    bounds.getWidth() - 1
                            ) / 2.0;

                    double centerY =
                            bounds.getTop()
                                    + (
                                    bounds.getHeight() - 1
                            ) / 2.0;

                    targets.add(
                            new ExpectedBubbleTarget(
                                    blockIndex,
                                    questionIndex,
                                    optionIndex,
                                    block.getId(),
                                    question.getId(),
                                    option,
                                    bounds,
                                    centerX,
                                    centerY
                            )
                    );

                    optionIndex++;
                }

                questionIndex++;
            }

            blockIndex++;
        }

        if (targets.size()
                != layout.getOptionCount()) {

            throw new IllegalStateException(
                    "A quantidade de alvos gerados não coincide"
                            + " com o total de alternativas do layout."
            );
        }

        return Collections.unmodifiableList(
                targets
        );
    }

    private PixelRectangle createExpectedBounds(
            OmrOptionDefinition option,
            int imageWidth,
            int imageHeight
    ) {
        int left = clamp(
                (int) Math.floor(
                        option.getLeft()
                                * imageWidth
                ),
                0,
                imageWidth - 1
        );

        int top = clamp(
                (int) Math.floor(
                        option.getTop()
                                * imageHeight
                ),
                0,
                imageHeight - 1
        );

        int rightExclusive = clamp(
                (int) Math.ceil(
                        option.getRight()
                                * imageWidth
                ),
                left + 1,
                imageWidth
        );

        int bottomExclusive = clamp(
                (int) Math.ceil(
                        option.getBottom()
                                * imageHeight
                ),
                top + 1,
                imageHeight
        );

        return new PixelRectangle(
                left,
                top,
                rightExclusive - left,
                bottomExclusive - top
        );
    }

    private void validateInput(
            OmrLayoutDefinition layout,
            int imageWidth,
            int imageHeight
    ) {
        if (layout == null) {
            throw new IllegalArgumentException(
                    "O layout é obrigatório."
            );
        }

        if (imageWidth <= 0
                || imageHeight <= 0) {

            throw new IllegalArgumentException(
                    "As dimensões da imagem devem ser positivas."
            );
        }

        if (layout.getOptionCount() <= 0) {
            throw new IllegalArgumentException(
                    "O layout não possui alternativas."
            );
        }
    }

    private int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(value, maximum)
        );
    }
}
