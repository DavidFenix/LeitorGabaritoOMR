package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.aggregation.OptionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Desenha o consenso temporal usando a geometria registrada do
 * frame atual.
 *
 * O SheetEvidenceAggregate fornece exclusivamente o resultado
 * logico acumulado: alternativas comuns, segunda colocada e
 * vencedora temporal.
 *
 * O OmrSheetMeasurementResult fornece exclusivamente a geometria
 * visual. Cada alternativa e localizada por optionId e desenhada
 * com os mesmos limites de BubbleMeasurement utilizados na
 * medicao precisa das etapas 15 e 16.
 *
 * Nenhuma coordenada do layout normalizado e convertida novamente
 * neste renderizador.
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

    private static final Scalar FAILURE_FOREGROUND =
            new Scalar(255, 80, 80, 255);

    /**
     * Assinatura correta para o Laboratorio OMR.
     */
    public void draw(
            Mat normalizedRegion,
            SheetEvidenceAggregate sheetAggregate,
            OmrSheetMeasurementResult measurementResult
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || sheetAggregate == null) {

            return;
        }

        String validationError =
                validateMeasurementGeometry(
                        sheetAggregate,
                        measurementResult
                );

        if (validationError != null) {
            drawFailure(
                    normalizedRegion,
                    validationError
            );

            return;
        }

        for (QuestionEvidenceAggregate question
                : sheetAggregate
                .getQuestionAggregates()) {

            drawQuestion(
                    normalizedRegion,
                    question,
                    sheetAggregate.isReady(),
                    measurementResult
            );
        }

        drawProgress(
                normalizedRegion,
                sheetAggregate
        );
    }

    /**
     * Mantem compatibilidade de compilacao com consumidores antigos.
     *
     * Sem o resultado de medicao nao existe uma geometria registrada
     * legitima para desenhar. Portanto, este overload nao retorna ao
     * desenho aproximado baseado no layout.
     */
    @Deprecated
    public void draw(
            Mat normalizedRegion,
            SheetEvidenceAggregate sheetAggregate
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || sheetAggregate == null) {

            return;
        }

        drawFailure(
                normalizedRegion,
                "CONSENSO SEM GEOMETRIA REGISTRADA"
        );
    }

    private String validateMeasurementGeometry(
            SheetEvidenceAggregate sheetAggregate,
            OmrSheetMeasurementResult measurementResult
    ) {
        if (measurementResult == null) {
            return "MEDICOES DO FRAME INDISPONIVEIS";
        }

        if (!measurementResult.isComplete()) {
            return "MEDICOES DO FRAME INCOMPLETAS";
        }

        int expectedOptionCount =
                sheetAggregate
                .getLayout()
                .getOptionCount();

        if (measurementResult.getMeasuredOptionCount()
                != expectedOptionCount) {

            return "CONSENSO E MEDICAO POSSUEM TAMANHOS DIFERENTES";
        }

        for (QuestionEvidenceAggregate question
                : sheetAggregate.getQuestionAggregates()) {

            for (OptionEvidenceAggregate option
                    : question.getOptionAggregates()) {

                String optionId =
                        option
                        .getOption()
                        .getId();

                if (measurementResult.findByOptionId(
                        optionId
                ) == null) {

                    return "GEOMETRIA AUSENTE PARA "
                            + optionId;
                }
            }
        }

        return null;
    }

    private void drawQuestion(
            Mat image,
            QuestionEvidenceAggregate question,
            boolean sheetReady,
            OmrSheetMeasurementResult measurementResult
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
                        measurementResult.findByOptionId(
                                optionId
                        ),
                        COMMON_COLOR,
                        1,
                        false
                );
            }
        }

        drawOption(
                image,
                measurementResult.findByOptionId(
                        question
                        .getRunnerUp()
                        .getOption()
                        .getId()
                ),
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
                measurementResult.findByOptionId(
                        question
                        .getWinner()
                        .getOption()
                        .getId()
                ),
                winnerColor,
                3,
                true
        );
    }

    /**
     * Combina estabilidade das vitorias com a diferenca entre a
     * primeira e a segunda colocadas no consenso.
     */
    private double calculateWinnerStrength(
            QuestionEvidenceAggregate question
    ) {
        double voteRatio =
                clamp01(
                        question
                        .getWinnerVoteRatio()
                );

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

    /**
     * Usa diretamente os limites guardados em BubbleMeasurement.
     * Esses limites pertencem a BubbleMeasurementGeometry e foram
     * usados nos calculos do frame atual.
     */
    private void drawOption(
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

        drawStatusBox(
                image,
                text,
                STATUS_FOREGROUND
        );
    }

    private void drawFailure(
            Mat image,
            String message
    ) {
        drawStatusBox(
                image,
                message,
                FAILURE_FOREGROUND
        );
    }

    private void drawStatusBox(
            Mat image,
            String text,
            Scalar foreground
    ) {
        int left = 12;

        int top =
                Math.max(
                        0,
                        image.rows() - 50
                );

        int right =
                Math.min(
                        image.cols() - 1,
                        540
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
                foreground,
                2
        );
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}
