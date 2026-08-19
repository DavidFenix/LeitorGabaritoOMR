package com.example.leitorgabaritoomr.vision.scoring;

import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;

/**
 * Transforma as medições de uma alternativa em uma evidência
 * de preenchimento entre 0.0 e 1.0.
 *
 * Esta classe não compara alternativas e não classifica questões.
 */
public final class BubbleEvidenceScorer {

    private final BubbleEvidenceScorerConfig config;

    public BubbleEvidenceScorer(
            BubbleEvidenceScorerConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuração de pontuação é obrigatória."
            );
        }

        this.config = config;
    }

    public double score(
            BubbleMeasurement measurement
    ) {
        if (measurement == null) {
            throw new IllegalArgumentException(
                    "A medição é obrigatória."
            );
        }

        double positiveLocalContrast =
                Math.max(
                        0.0,
                        measurement
                                .getLocalContrastScore()
                );

        double normalizedContrast =
                clamp01(
                        positiveLocalContrast
                                / config
                                .getLocalContrastSaturation()
                );

        double locallyDarkRatio =
                clamp01(
                        measurement
                                .getCoreLocallyDarkPixelRatio()
                );

        double weightedSum =
                normalizedContrast
                        * config
                        .getLocalContrastWeight()
                        + locallyDarkRatio
                        * config
                        .getLocallyDarkPixelRatioWeight();

        return clamp01(
                weightedSum
                        / config.getTotalWeight()
        );
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}