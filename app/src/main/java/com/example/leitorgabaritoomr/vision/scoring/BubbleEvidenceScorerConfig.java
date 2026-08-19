package com.example.leitorgabaritoomr.vision.scoring;

/**
 * Configura como as medições brutas são combinadas para
 * produzir uma evidência de preenchimento entre 0.0 e 1.0.
 */
public final class BubbleEvidenceScorerConfig {

    private final double localContrastSaturation;

    private final double localContrastWeight;
    private final double locallyDarkPixelRatioWeight;

    public BubbleEvidenceScorerConfig(
            double localContrastSaturation,
            double localContrastWeight,
            double locallyDarkPixelRatioWeight
    ) {
        if (!Double.isFinite(localContrastSaturation)
                || localContrastSaturation <= 0.0
                || localContrastSaturation > 1.0) {

            throw new IllegalArgumentException(
                    "localContrastSaturation deve estar"
                            + " entre 0.0 e 1.0."
            );
        }

        validateWeight(
                "localContrastWeight",
                localContrastWeight
        );

        validateWeight(
                "locallyDarkPixelRatioWeight",
                locallyDarkPixelRatioWeight
        );

        if (localContrastWeight
                + locallyDarkPixelRatioWeight
                <= 0.0) {

            throw new IllegalArgumentException(
                    "Pelo menos um peso deve ser positivo."
            );
        }

        this.localContrastSaturation =
                localContrastSaturation;

        this.localContrastWeight =
                localContrastWeight;

        this.locallyDarkPixelRatioWeight =
                locallyDarkPixelRatioWeight;
    }

    public static BubbleEvidenceScorerConfig
    developmentDefaults() {

        return new BubbleEvidenceScorerConfig(
                0.55,
                0.45,
                0.55
        );
    }

    private void validateWeight(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser finito e não negativo."
            );
        }
    }

    public double getLocalContrastSaturation() {
        return localContrastSaturation;
    }

    public double getLocalContrastWeight() {
        return localContrastWeight;
    }

    public double
    getLocallyDarkPixelRatioWeight() {

        return locallyDarkPixelRatioWeight;
    }

    public double getTotalWeight() {
        return localContrastWeight
                + locallyDarkPixelRatioWeight;
    }
}