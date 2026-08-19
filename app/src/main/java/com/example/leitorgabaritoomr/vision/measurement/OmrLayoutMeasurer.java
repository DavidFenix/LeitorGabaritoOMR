package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Mede todas as alternativas descritas por um layout.
 */
public final class OmrLayoutMeasurer {

    private final BubbleMeasurer bubbleMeasurer;

    public OmrLayoutMeasurer(
            BubbleMeasurer bubbleMeasurer
    ) {
        if (bubbleMeasurer == null) {
            throw new IllegalArgumentException(
                    "BubbleMeasurer é obrigatório."
            );
        }

        this.bubbleMeasurer = bubbleMeasurer;
    }

    public OmrSheetMeasurementResult measure(
            Mat normalizedRegion,
            OmrLayoutDefinition layout
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()) {

            throw new IllegalArgumentException(
                    "A região normalizada está vazia."
            );
        }

        if (layout == null) {
            throw new IllegalArgumentException(
                    "O layout é obrigatório."
            );
        }

        Mat normalizedGray =
                convertToGray(normalizedRegion);

        List<BubbleMeasurement> measurements =
                new ArrayList<>();

        List<String> errors =
                new ArrayList<>();

        try {
            for (OmrBlockDefinition block
                    : layout.getBlocks()) {

                measureBlock(
                        normalizedGray,
                        block,
                        measurements,
                        errors
                );
            }

            return new OmrSheetMeasurementResult(
                    layout,
                    measurements,
                    errors
            );

        } finally {
            normalizedGray.release();
        }
    }

    private void measureBlock(
            Mat normalizedGray,
            OmrBlockDefinition block,
            List<BubbleMeasurement> measurements,
            List<String> errors
    ) {
        for (OmrQuestionDefinition question
                : block.getQuestions()) {

            for (OmrOptionDefinition option
                    : question.getOptions()) {

                try {
                    BubbleMeasurement measurement =
                            bubbleMeasurer.measure(
                                    normalizedGray,
                                    option
                            );

                    measurements.add(measurement);

                } catch (RuntimeException exception) {
                    errors.add(
                            option.getId()
                                    + ": "
                                    + safeMessage(exception)
                    );
                }
            }
        }
    }

    private Mat convertToGray(Mat source) {
        Mat gray = new Mat();

        if (source.channels() == 1) {
            source.copyTo(gray);

        } else if (source.channels() == 3) {
            Imgproc.cvtColor(
                    source,
                    gray,
                    Imgproc.COLOR_RGB2GRAY
            );

        } else if (source.channels() == 4) {
            Imgproc.cvtColor(
                    source,
                    gray,
                    Imgproc.COLOR_RGBA2GRAY
            );

        } else {
            gray.release();

            throw new IllegalArgumentException(
                    "Quantidade de canais não suportada: "
                            + source.channels()
            );
        }

        return gray;
    }

    private String safeMessage(
            RuntimeException exception
    ) {
        String message =
                exception.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }
}