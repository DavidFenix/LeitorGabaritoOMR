package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.HashSet;
import java.util.Set;

/**
 * Exibe a interpretacao semantica final da folha.
 *
 * Verde   = alternativa comum nao marcada.
 * Vermelho = marcacao unica confirmada.
 * Cinza   = questao classificada como vazia.
 * Magenta = alternativas de uma marcacao multipla.
 * Laranja = alternativas envolvidas em uma ambiguidade.
 * Ciano   = consenso ainda nao pronto.
 *
 * Todas as regioes desenhadas sao obtidas diretamente de
 * BubbleMeasurement. O renderizador nao utiliza coordenadas
 * normalizadas do layout e nao recalcula nenhuma geometria.
 */
public final class SheetInterpretationOverlayRenderer {

    private static final Scalar COMMON_COLOR =
            new Scalar(0, 190, 0, 255);

    private static final Scalar SINGLE_MARK_COLOR =
            new Scalar(255, 0, 0, 255);

    private static final Scalar BLANK_COLOR =
            new Scalar(125, 125, 125, 255);

    private static final Scalar MULTIPLE_MARK_COLOR =
            new Scalar(255, 0, 255, 255);

    private static final Scalar AMBIGUOUS_COLOR =
            new Scalar(255, 165, 0, 255);

    private static final Scalar NOT_READY_COLOR =
            new Scalar(0, 220, 255, 255);

    private static final Scalar STATUS_BACKGROUND =
            new Scalar(0, 0, 0, 220);

    private static final Scalar STATUS_READY =
            new Scalar(0, 255, 0, 255);

    private static final Scalar STATUS_REVIEW =
            new Scalar(255, 165, 0, 255);

    private static final Scalar STATUS_WAITING =
            new Scalar(0, 220, 255, 255);

    private static final Scalar STATUS_FAILURE =
            new Scalar(255, 80, 80, 255);

    public void draw(
            Mat normalizedRegion,
            SheetInterpretationResult interpretationResult,
            OmrSheetMeasurementResult measurementResult
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()) {

            return;
        }

        String validationError =
                validateInputs(
                        interpretationResult,
                        measurementResult
                );

        if (validationError != null) {
            drawStatus(
                    normalizedRegion,
                    validationError,
                    STATUS_FAILURE
            );

            return;
        }

        for (QuestionInterpretation interpretation
                : interpretationResult
                .getQuestionInterpretations()) {

            drawQuestion(
                    normalizedRegion,
                    interpretation,
                    measurementResult
            );
        }

        drawSummary(
                normalizedRegion,
                interpretationResult
        );
    }

    private String validateInputs(
            SheetInterpretationResult interpretationResult,
            OmrSheetMeasurementResult measurementResult
    ) {
        if (interpretationResult == null) {
            return "INTERPRETACAO INDISPONIVEL";
        }

        if (measurementResult == null) {
            return "MEDICOES DO FRAME INDISPONIVEIS";
        }

        if (!measurementResult.isComplete()) {
            return "MEDICOES DO FRAME INCOMPLETAS";
        }

        if (interpretationResult.getQuestionCount()
                != measurementResult
                .getLayout()
                .getQuestionCount()) {

            return "INTERPRETACAO E MEDICAO DIVERGENTES";
        }

        for (QuestionInterpretation interpretation
                : interpretationResult
                .getQuestionInterpretations()) {

            for (OmrOptionDefinition option
                    : interpretation
                    .getQuestion()
                    .getOptions()) {

                if (measurementResult.findByOptionId(
                        option.getId()
                ) == null) {

                    return "GEOMETRIA AUSENTE PARA "
                            + option.getId();
                }
            }
        }

        return null;
    }

    private void drawQuestion(
            Mat image,
            QuestionInterpretation interpretation,
            OmrSheetMeasurementResult measurementResult
    ) {
        QuestionMarkState state =
                interpretation.getState();

        if (state == QuestionMarkState.BLANK) {
            drawAllOptions(
                    image,
                    interpretation,
                    measurementResult,
                    BLANK_COLOR,
                    2
            );

            return;
        }

        if (state == QuestionMarkState.NOT_READY) {
            drawAllOptions(
                    image,
                    interpretation,
                    measurementResult,
                    NOT_READY_COLOR,
                    1
            );

            return;
        }

        drawAllOptions(
                image,
                interpretation,
                measurementResult,
                COMMON_COLOR,
                1
        );

        if (state == QuestionMarkState.SINGLE_MARK) {
            drawOption(
                    image,
                    measurementResult.findByOptionId(
                            interpretation
                            .getSelectedOption()
                            .getId()
                    ),
                    SINGLE_MARK_COLOR,
                    3,
                    true
            );

            return;
        }

        Scalar relevantColor =
                state == QuestionMarkState.MULTIPLE_MARKS
                        ? MULTIPLE_MARK_COLOR
                        : AMBIGUOUS_COLOR;

        Set<String> relevantOptionIds =
                createRelevantOptionIds(
                        interpretation
                );

        for (String optionId
                : relevantOptionIds) {

            drawOption(
                    image,
                    measurementResult.findByOptionId(
                            optionId
                    ),
                    relevantColor,
                    3,
                    true
            );
        }
    }

    private void drawAllOptions(
            Mat image,
            QuestionInterpretation interpretation,
            OmrSheetMeasurementResult measurementResult,
            Scalar color,
            int thickness
    ) {
        for (OmrOptionDefinition option
                : interpretation
                .getQuestion()
                .getOptions()) {

            drawOption(
                    image,
                    measurementResult.findByOptionId(
                            option.getId()
                    ),
                    color,
                    thickness,
                    false
            );
        }
    }

    private Set<String> createRelevantOptionIds(
            QuestionInterpretation interpretation
    ) {
        Set<String> result =
                new HashSet<>();

        for (OmrOptionDefinition option
                : interpretation.getRelevantOptions()) {

            result.add(option.getId());
        }

        return result;
    }

    private void drawOption(
            Mat image,
            BubbleMeasurement measurement,
            Scalar color,
            int thickness,
            boolean drawCenter
    ) {
        int left = measurement.getRegionLeft();
        int top = measurement.getRegionTop();

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

    private void drawSummary(
            Mat image,
            SheetInterpretationResult result
    ) {
        String text =
                "FINAL"
                        + " | unica="
                        + result.getSingleMarkCount()
                        + " | branca="
                        + result.getBlankCount()
                        + " | multipla="
                        + result.getMultipleMarkCount()
                        + " | ambigua="
                        + result.getAmbiguousCount()
                        + " | aguardando="
                        + result.getNotReadyCount();

        Scalar color;

        if (!result.isComplete()) {
            color = STATUS_WAITING;

        } else if (result.requiresReview()) {
            color = STATUS_REVIEW;

        } else {
            color = STATUS_READY;
        }

        drawStatus(
                image,
                text,
                color
        );
    }

    private void drawStatus(
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
                        720
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
                0.52,
                foreground,
                2
        );
    }
}
