package com.example.leitorgabaritoomr.vision.geometry;

import java.util.Locale;

/**
 * Diagnostico imutavel de uma combinacao de quatro marcadores.
 *
 * Conserva exatamente as parcelas usadas pelo MarkerSetResolver.
 * Nenhum valor e recalculado por overlays, testes ou loggers.
 */
public final class MarkerSetCandidateEvaluation {

    private final ResolvedMarkerSet markerSet;

    private final double totalScore;
    private final double regionAreaRatio;
    private final double cloudCoverage;
    private final double containmentRatio;
    private final double sizeSimilarity;
    private final double sideCoherence;
    private final double averageMarkerConfidence;

    MarkerSetCandidateEvaluation(
            ResolvedMarkerSet markerSet,
            double totalScore,
            double regionAreaRatio,
            double cloudCoverage,
            double containmentRatio,
            double sizeSimilarity,
            double sideCoherence,
            double averageMarkerConfidence
    ) {
        if (markerSet == null) {
            throw new IllegalArgumentException(
                    "O conjunto avaliado e obrigatorio."
            );
        }

        validateUnitValue("totalScore", totalScore);

        validateUnitValue(
                "regionAreaRatio",
                regionAreaRatio
        );

        validateUnitValue(
                "cloudCoverage",
                cloudCoverage
        );

        validateUnitValue(
                "containmentRatio",
                containmentRatio
        );

        validateUnitValue(
                "sizeSimilarity",
                sizeSimilarity
        );

        validateUnitValue(
                "sideCoherence",
                sideCoherence
        );

        validateUnitValue(
                "averageMarkerConfidence",
                averageMarkerConfidence
        );

        this.markerSet = markerSet;
        this.totalScore = totalScore;
        this.regionAreaRatio = regionAreaRatio;
        this.cloudCoverage = cloudCoverage;
        this.containmentRatio = containmentRatio;
        this.sizeSimilarity = sizeSimilarity;
        this.sideCoherence = sideCoherence;
        this.averageMarkerConfidence =
                averageMarkerConfidence;
    }

    public ResolvedMarkerSet getMarkerSet() {
        return markerSet;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public double getRegionAreaRatio() {
        return regionAreaRatio;
    }

    public double getCloudCoverage() {
        return cloudCoverage;
    }

    public double getContainmentRatio() {
        return containmentRatio;
    }

    public double getSizeSimilarity() {
        return sizeSimilarity;
    }

    public double getSideCoherence() {
        return sideCoherence;
    }

    public double getAverageMarkerConfidence() {
        return averageMarkerConfidence;
    }

    private void validateUnitValue(
            String name,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    name
                            + " deve estar entre 0.0 e 1.0."
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "score=%.6f area=%.6f cloud=%.6f"
                        + " inside=%.6f size=%.6f"
                        + " sides=%.6f confidence=%.6f",
                totalScore,
                regionAreaRatio,
                cloudCoverage,
                containmentRatio,
                sizeSimilarity,
                sideCoherence,
                averageMarkerConfidence
        );
    }
}
