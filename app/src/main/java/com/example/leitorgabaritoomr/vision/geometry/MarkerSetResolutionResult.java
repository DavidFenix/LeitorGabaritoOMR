package com.example.leitorgabaritoomr.vision.geometry;

/**
 * Resultado do resolvedor geometrico.
 *
 * Mantem compatibilidade com a API original e, quando disponivel,
 * conserva tambem as avaliacoes completas do primeiro e do segundo
 * colocados. Mesmo uma rejeicao por ambiguidade pode ser explicada
 * sem recalcular ou alterar a decisao tomada pelo resolvedor.
 */
public final class MarkerSetResolutionResult {

    private final boolean accepted;
    private final ResolvedMarkerSet markerSet;

    private final String reason;

    private final int evaluatedCombinations;

    private final double bestScore;
    private final double secondBestScore;

    private final MarkerSetCandidateEvaluation
            bestCandidateEvaluation;

    private final MarkerSetCandidateEvaluation
            secondBestCandidateEvaluation;

    private MarkerSetResolutionResult(
            boolean accepted,
            ResolvedMarkerSet markerSet,
            String reason,
            int evaluatedCombinations,
            double bestScore,
            double secondBestScore,
            MarkerSetCandidateEvaluation
                    bestCandidateEvaluation,
            MarkerSetCandidateEvaluation
                    secondBestCandidateEvaluation
    ) {
        this.accepted = accepted;
        this.markerSet = markerSet;
        this.reason = reason;
        this.evaluatedCombinations =
                evaluatedCombinations;

        this.bestScore = bestScore;
        this.secondBestScore = secondBestScore;

        this.bestCandidateEvaluation =
                bestCandidateEvaluation;

        this.secondBestCandidateEvaluation =
                secondBestCandidateEvaluation;
    }

    /**
     * Fabrica original preservada para compatibilidade.
     */
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
                secondBestScore,
                null,
                null
        );
    }

    /**
     * Fabrica original preservada para compatibilidade.
     */
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
                secondBestScore,
                null,
                null
        );
    }

    public static MarkerSetResolutionResult
    acceptedWithEvaluations(
            MarkerSetCandidateEvaluation best,
            MarkerSetCandidateEvaluation secondBest,
            int evaluatedCombinations
    ) {
        if (best == null) {
            throw new IllegalArgumentException(
                    "A melhor avaliacao e obrigatoria."
            );
        }

        return new MarkerSetResolutionResult(
                true,
                best.getMarkerSet(),
                "Conjunto aceito.",
                evaluatedCombinations,
                best.getTotalScore(),
                scoreOrMissing(secondBest),
                best,
                secondBest
        );
    }

    public static MarkerSetResolutionResult
    rejectedWithEvaluations(
            String reason,
            int evaluatedCombinations,
            MarkerSetCandidateEvaluation best,
            MarkerSetCandidateEvaluation secondBest
    ) {
        if (best == null) {
            throw new IllegalArgumentException(
                    "A melhor avaliacao e obrigatoria."
            );
        }

        return new MarkerSetResolutionResult(
                false,
                null,
                reason,
                evaluatedCombinations,
                best.getTotalScore(),
                scoreOrMissing(secondBest),
                best,
                secondBest
        );
    }

    private static double scoreOrMissing(
            MarkerSetCandidateEvaluation evaluation
    ) {
        return evaluation == null
                ? -1.0
                : evaluation.getTotalScore();
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

    public MarkerSetCandidateEvaluation
    getBestCandidateEvaluation() {
        return bestCandidateEvaluation;
    }

    public MarkerSetCandidateEvaluation
    getSecondBestCandidateEvaluation() {
        return secondBestCandidateEvaluation;
    }

    public boolean hasCandidateEvaluations() {
        return bestCandidateEvaluation != null;
    }
}
