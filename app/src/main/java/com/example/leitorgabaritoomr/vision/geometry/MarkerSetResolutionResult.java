package com.example.leitorgabaritoomr.vision.geometry;

public final class MarkerSetResolutionResult {

    private final boolean accepted;
    private final ResolvedMarkerSet markerSet;

    private final String reason;

    private final int evaluatedCombinations;

    private final double bestScore;
    private final double secondBestScore;

    private MarkerSetResolutionResult(
            boolean accepted,
            ResolvedMarkerSet markerSet,
            String reason,
            int evaluatedCombinations,
            double bestScore,
            double secondBestScore
    ) {

        this.accepted = accepted;
        this.markerSet = markerSet;
        this.reason = reason;
        this.evaluatedCombinations =
                evaluatedCombinations;
        this.bestScore = bestScore;
        this.secondBestScore = secondBestScore;
    }

    public static MarkerSetResolutionResult accepted(
            ResolvedMarkerSet markerSet,
            int evaluatedCombinations,
            double bestScore,
            double secondBestScore
    ) {

        return new MarkerSetResolutionResult(
                true,
                markerSet,
                "Conjunto aceito.",
                evaluatedCombinations,
                bestScore,
                secondBestScore
        );
    }

    public static MarkerSetResolutionResult rejected(
            String reason,
            int evaluatedCombinations,
            double bestScore,
            double secondBestScore
    ) {

        return new MarkerSetResolutionResult(
                false,
                null,
                reason,
                evaluatedCombinations,
                bestScore,
                secondBestScore
        );
    }

    public boolean isAccepted() {
        return accepted;
    }

    public ResolvedMarkerSet getMarkerSet() {
        return markerSet;
    }

    public String getReason() {
        return reason;
    }

    public int getEvaluatedCombinations() {
        return evaluatedCombinations;
    }

    public double getBestScore() {
        return bestScore;
    }

    public double getSecondBestScore() {
        return secondBestScore;
    }

    public double getScoreDifference() {

        if (secondBestScore < 0) {
            return bestScore;
        }

        return bestScore - secondBestScore;
    }
}