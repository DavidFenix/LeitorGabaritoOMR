package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.aggregation.OptionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Desenha o consenso temporal das respostas.
 *
 * Verde:
 * alternativas comuns.
 *
 * Ciano:
 * segunda colocada temporal.
 *
 * Amarelo, laranja ou vermelho:
 * vencedora temporal.
 */
public final class TemporalConsensusOverlayRenderer {

    private static final Scalar COMMON_COLOR =
            new Scalar(0, 190, 0, 255);

    private static final Scalar RUNNER_UP_COLOR =
            new Scalar(0, 220, 255, 255);

    private static final Scalar STATUS_BACKGROUND =
            new Scalar(0, 0, 0, 220);

    private static final Scalar STATUS_FOREGROUND =
            new Scalar(255, 255, 255, 255);

    public void draw(
            Mat normalizedRegion,
            SheetEvidenceAggregate sheetAggregate
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || sheetAggregate == null) {

            return;
        }

        for (QuestionEvidenceAggregate question
                : sheetAggregate
                .getQuestionAggregates()) {

            drawQuestion(
                    normalizedRegion,
                    question,
                    sheetAggregate.isReady()
            );
        }

        drawProgress(
                normalizedRegion,
                sheetAggregate
        );
    }

    private void drawQuestion(
            Mat image,
            QuestionEvidenceAggregate question,
            boolean sheetReady
    ) {
        for (OptionEvidenceAggregate option
                : question.getOptionAggregates()) {

            String optionId =
                    option
                            .getOption()
                            .getId();

            if (!question.isWinner(optionId)
                    && !question.isRunnerUp(optionId)) {

                drawOption(
                        image,
                        option.getOption(),
                        COMMON_COLOR,
                        1,
                        false
                );
            }
        }

        drawOption(
                image,
                question
                        .getRunnerUp()
                        .getOption(),
                RUNNER_UP_COLOR,
                2,
                false
        );

        double winnerStrength =
                calculateWinnerStrength(
                        question
                );

        Scalar winnerColor =
                winnerColor(
                        winnerStrength,
                        sheetReady
                );

        drawOption(
                image,
                question
                        .getWinner()
                        .getOption(),
                winnerColor,
                3,
                true
        );
    }

    /**
     * Combina estabilidade das vitórias com a diferença
     * entre primeira e segunda colocadas no consenso.
     */
    private double calculateWinnerStrength(
            QuestionEvidenceAggregate question
    ) {
        double voteRatio =
                clamp01(
                        question
                                .getWinnerVoteRatio()
                );

        /*
         * Uma diferença temporal de 0.25 já é visualmente
         * considerada forte.
         */
        double normalizedGap =
                clamp01(
                        question.getConsensusGap()
                                / 0.25
                );

        return clamp01(
                0.70 * voteRatio
                        + 0.30 * normalizedGap
        );
    }

    private Scalar winnerColor(
            double strength,
            boolean sheetReady
    ) {
        /*
         * Enquanto acumula:
         *
         * amarelo -> laranja
         */
        if (!sheetReady) {
            double green =
                    255.0
                            - 110.0 * strength;

            return new Scalar(
                    255.0,
                    green,
                    0.0,
                    255.0
            );
        }

        /*
         * Consenso pronto:
         *
         * laranja -> vermelho
         */
        double green =
                150.0
                        * (1.0 - strength);

        return new Scalar(
                255.0,
                green,
                0.0,
                255.0
        );
    }

    private void drawOption(
            Mat image,
            OmrOptionDefinition option,
            Scalar color,
            int thickness,
            boolean drawCenter
    ) {
        int left =
                normalizedXToPixel(
                        option.getLeft(),
                        image.cols()
                );

        int top =
                normalizedYToPixel(
                        option.getTop(),
                        image.rows()
                );

        int right =
                normalizedXToPixel(
                        option.getRight(),
                        image.cols()
                );

        int bottom =
                normalizedYToPixel(
                        option.getBottom(),
                        image.rows()
                );

        Imgproc.rectangle(
                image,
                new Point(left, top),
                new Point(right, bottom),
                color,
                thickness
        );

        if (drawCenter) {
            int centerX =
                    normalizedXToPixel(
                            option
                                    .getCenter()
                                    .getX(),
                            image.cols()
                    );

            int centerY =
                    normalizedYToPixel(
                            option
                                    .getCenter()
                                    .getY(),
                            image.rows()
                    );

            Imgproc.circle(
                    image,
                    new Point(centerX, centerY),
                    4,
                    color,
                    -1
            );
        }
    }

    private void drawProgress(
            Mat image,
            SheetEvidenceAggregate sheet
    ) {
        String text;

        if (sheet.isReady()) {
            text =
                    "CONSENSO PRONTO "
                            + sheet.getAccumulatedFrames()
                            + "/"
                            + sheet.getRequiredFrames();

        } else {
            text =
                    "ACUMULANDO "
                            + sheet.getAccumulatedFrames()
                            + "/"
                            + sheet.getRequiredFrames();
        }

        int left = 12;

        int top =
                Math.max(
                        0,
                        image.rows() - 50
                );

        int right =
                Math.min(
                        image.cols() - 1,
                        390
                );

        int bottom =
                image.rows() - 8;

        Imgproc.rectangle(
                image,
                new Point(left, top),
                new Point(right, bottom),
                STATUS_BACKGROUND,
                -1
        );

        Imgproc.putText(
                image,
                text,
                new Point(
                        left + 12,
                        bottom - 12
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.65,
                STATUS_FOREGROUND,
                2
        );
    }

    private int normalizedXToPixel(
            double normalizedX,
            int width
    ) {
        return clamp(
                (int) Math.round(
                        normalizedX
                                * (width - 1.0)
                ),
                0,
                width - 1
        );
    }

    private int normalizedYToPixel(
            double normalizedY,
            int height
    ) {
        return clamp(
                (int) Math.round(
                        normalizedY
                                * (height - 1.0)
                ),
                0,
                height - 1
        );
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

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}