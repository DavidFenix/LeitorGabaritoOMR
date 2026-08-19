package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado da medição de todas as regiões de resposta
 * de uma folha normalizada.
 */
public final class OmrSheetMeasurementResult {

    private final OmrLayoutDefinition layout;

    private final List<BubbleMeasurement> measurements;
    private final List<String> errors;

    private final Map<String, BubbleMeasurement>
            measurementsByOptionId;

    private final double minimumCoreDarkRatio;
    private final double maximumCoreDarkRatio;
    private final double averageCoreDarkRatio;

    private final double minimumCoreIntensity;
    private final double maximumCoreIntensity;
    private final double averageCoreIntensity;

    public OmrSheetMeasurementResult(
            OmrLayoutDefinition layout,
            List<BubbleMeasurement> measurements,
            List<String> errors
    ) {
        if (layout == null) {
            throw new IllegalArgumentException(
                    "O layout é obrigatório."
            );
        }

        if (measurements == null || errors == null) {
            throw new IllegalArgumentException(
                    "As listas de medições e erros são obrigatórias."
            );
        }

        this.layout = layout;

        this.measurements =
                Collections.unmodifiableList(
                        new ArrayList<>(measurements)
                );

        this.errors =
                Collections.unmodifiableList(
                        new ArrayList<>(errors)
                );

        Map<String, BubbleMeasurement> index =
                new HashMap<>();

        for (BubbleMeasurement measurement
                : this.measurements) {

            if (measurement == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter medições nulas."
                );
            }

            String optionId =
                    measurement
                            .getOption()
                            .getId();

            if (index.put(optionId, measurement)
                    != null) {

                throw new IllegalArgumentException(
                        "Medição repetida para a alternativa: "
                                + optionId
                );
            }
        }

        this.measurementsByOptionId =
                Collections.unmodifiableMap(index);

        Statistics statistics =
                calculateStatistics(
                        this.measurements
                );

        this.minimumCoreDarkRatio =
                statistics.minimumCoreDarkRatio;

        this.maximumCoreDarkRatio =
                statistics.maximumCoreDarkRatio;

        this.averageCoreDarkRatio =
                statistics.averageCoreDarkRatio;

        this.minimumCoreIntensity =
                statistics.minimumCoreIntensity;

        this.maximumCoreIntensity =
                statistics.maximumCoreIntensity;

        this.averageCoreIntensity =
                statistics.averageCoreIntensity;
    }

    private Statistics calculateStatistics(
            List<BubbleMeasurement> measurements
    ) {
        if (measurements.isEmpty()) {
            return new Statistics(
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );
        }

        double minimumDarkRatio =
                Double.POSITIVE_INFINITY;

        double maximumDarkRatio =
                Double.NEGATIVE_INFINITY;

        double darkRatioSum = 0.0;

        double minimumIntensity =
                Double.POSITIVE_INFINITY;

        double maximumIntensity =
                Double.NEGATIVE_INFINITY;

        double intensitySum = 0.0;

        for (BubbleMeasurement measurement
                : measurements) {

            double darkRatio =
                    measurement
                            .getCoreDarkPixelRatio();

            double intensity =
                    measurement
                            .getCoreMeanIntensity();

            minimumDarkRatio =
                    Math.min(
                            minimumDarkRatio,
                            darkRatio
                    );

            maximumDarkRatio =
                    Math.max(
                            maximumDarkRatio,
                            darkRatio
                    );

            darkRatioSum += darkRatio;

            minimumIntensity =
                    Math.min(
                            minimumIntensity,
                            intensity
                    );

            maximumIntensity =
                    Math.max(
                            maximumIntensity,
                            intensity
                    );

            intensitySum += intensity;
        }

        return new Statistics(
                minimumDarkRatio,
                maximumDarkRatio,
                darkRatioSum / measurements.size(),
                minimumIntensity,
                maximumIntensity,
                intensitySum / measurements.size()
        );
    }

    public OmrLayoutDefinition getLayout() {
        return layout;
    }

    public List<BubbleMeasurement>
    getMeasurements() {

        return measurements;
    }

    public List<String> getErrors() {
        return errors;
    }

    public BubbleMeasurement findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return measurementsByOptionId.get(optionId);
    }

    public int getMeasuredOptionCount() {
        return measurements.size();
    }

    public int getExpectedOptionCount() {
        return layout.getOptionCount();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public boolean isComplete() {
        return errors.isEmpty()
                && getMeasuredOptionCount()
                == getExpectedOptionCount();
    }

    public double getCompletionRatio() {
        if (getExpectedOptionCount() <= 0) {
            return 0.0;
        }

        return getMeasuredOptionCount()
                / (double) getExpectedOptionCount();
    }

    public double getMinimumCoreDarkRatio() {
        return minimumCoreDarkRatio;
    }

    public double getMaximumCoreDarkRatio() {
        return maximumCoreDarkRatio;
    }

    public double getAverageCoreDarkRatio() {
        return averageCoreDarkRatio;
    }

    public double getMinimumCoreIntensity() {
        return minimumCoreIntensity;
    }

    public double getMaximumCoreIntensity() {
        return maximumCoreIntensity;
    }

    public double getAverageCoreIntensity() {
        return averageCoreIntensity;
    }

    private static final class Statistics {

        private final double minimumCoreDarkRatio;
        private final double maximumCoreDarkRatio;
        private final double averageCoreDarkRatio;

        private final double minimumCoreIntensity;
        private final double maximumCoreIntensity;
        private final double averageCoreIntensity;

        private Statistics(
                double minimumCoreDarkRatio,
                double maximumCoreDarkRatio,
                double averageCoreDarkRatio,
                double minimumCoreIntensity,
                double maximumCoreIntensity,
                double averageCoreIntensity
        ) {
            this.minimumCoreDarkRatio =
                    minimumCoreDarkRatio;

            this.maximumCoreDarkRatio =
                    maximumCoreDarkRatio;

            this.averageCoreDarkRatio =
                    averageCoreDarkRatio;

            this.minimumCoreIntensity =
                    minimumCoreIntensity;

            this.maximumCoreIntensity =
                    maximumCoreIntensity;

            this.averageCoreIntensity =
                    averageCoreIntensity;
        }
    }
}