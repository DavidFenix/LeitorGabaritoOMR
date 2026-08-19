package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
import com.example.leitorgabaritoomr.vision.measurement.QuestionMeasurement;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * Exibe a comparação entre as alternativas de cada questão.
 *
 * Verde:
 * alternativa comum.
 *
 * Ciano:
 * segunda maior evidência.
 *
 * Amarelo, laranja ou vermelho:
 * alternativa com maior evidência.
 *
 * A intensidade da cor da primeira colocada representa sua
 * vantagem sobre a segunda, combinada com sua força absoluta.
 */
public final class QuestionComparisonOverlayRenderer {

    private static final Scalar COMMON_OPTION_COLOR =
            new Scalar(0, 210, 0, 255);

    private static final Scalar SECOND_OPTION_COLOR =
            new Scalar(0, 220, 255, 255);

    public void draw(
            Mat normalizedRegion,
            List<QuestionMeasurement> questions
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || questions == null) {

            return;
        }

        for (QuestionMeasurement question
                : questions) {

            drawQuestion(
                    normalizedRegion,
                    question
            );
        }
    }

    private void drawQuestion(
            Mat image,
            QuestionMeasurement question
    ) {
        /*
         * Primeiro desenhamos as alternativas comuns.
         */
        for (BubbleMeasurement measurement
                : question.getMeasurements()) {

            String optionId =
                    measurement
                            .getOption()
                            .getId();

            if (!question.isBestOption(optionId)
                    && !question
                    .isSecondBestOption(optionId)) {

                drawRegion(
                        image,
                        measurement,
                        COMMON_OPTION_COLOR,
                        1,
                        false
                );
            }
        }

        /*
         * Depois desenhamos a segunda colocada.
         */
        drawRegion(
                image,
                question.getSecondBestMeasurement(),
                SECOND_OPTION_COLOR,
                2,
                false
        );

        /*
         * Por último, a primeira colocada.
         */
        double comparisonStrength =
                calculateComparisonStrength(
                        question
                );

        Scalar bestColor =
                bestOptionColor(
                        comparisonStrength
                );

        drawRegion(
                image,
                question.getBestMeasurement(),
                bestColor,
                3,
                true
        );
    }

    /**
     * Combina:
     *
     * - diferença absoluta entre primeira e segunda;
     * - diferença proporcional;
     * - força absoluta da primeira.
     *
     * É somente uma pontuação visual.
     */
    private double calculateComparisonStrength(
            QuestionMeasurement question
    ) {
        double best =
                question.getBestEvidence();

        double gap =
                question.getEvidenceGap();

        double proportionalGap;

        if (best <= 0.000001) {
            proportionalGap = 0.0;

        } else {
            proportionalGap =
                    clamp01(gap / best);
        }

        /*
         * Uma evidência absoluta de 0.35 já é considerada
         * forte para esta visualização experimental.
         */
        double absoluteStrength =
                clamp01(best / 0.35);

        /*
         * A separação relativa recebe maior peso.
         */
        double strength =
                0.65 * proportionalGap
                        + 0.35 * absoluteStrength;

        return clamp01(strength);
    }

    private Scalar bestOptionColor(
            double strength
    ) {
        /*
         * 0.0 até 0.5:
         *
         * amarelo -> laranja
         */
        if (strength <= 0.5) {
            double progress =
                    strength / 0.5;

            double green =
                    255.0
                            - 90.0 * progress;

            return new Scalar(
                    255.0,
                    green,
                    0.0,
                    255.0
            );
        }

        /*
         * 0.5 até 1.0:
         *
         * laranja -> vermelho
         */
        double progress =
                (strength - 0.5) / 0.5;

        double green =
                165.0
                        * (1.0 - progress);

        return new Scalar(
                255.0,
                green,
                0.0,
                255.0
        );
    }

    private void drawRegion(
            Mat image,
            BubbleMeasurement measurement,
            Scalar color,
            int thickness,
            boolean drawCenter
    ) {
        int left =
                measurement.getRegionLeft();

        int top =
                measurement.getRegionTop();

        int right =
                left
                        + measurement.getRegionWidth()
                        - 1;

        int bottom =
                top
                        + measurement.getRegionHeight()
                        - 1;

        Imgproc.rectangle(
                image,
                new Point(left, top),
                new Point(right, bottom),
                color,
                thickness
        );

        if (drawCenter) {
            int centerX =
                    left
                            + measurement.getRegionWidth()
                            / 2;

            int centerY =
                    top
                            + measurement.getRegionHeight()
                            / 2;

            Imgproc.circle(
                    image,
                    new Point(centerX, centerY),
                    4,
                    color,
                    -1
            );
        }
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}