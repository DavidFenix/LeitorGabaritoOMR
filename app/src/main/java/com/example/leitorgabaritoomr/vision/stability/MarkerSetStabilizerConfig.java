package com.example.leitorgabaritoomr.vision.stability;

public final class MarkerSetStabilizerConfig {

    /*
     * Quantos resultados compatíveis são necessários
     * para declarar estabilidade.
     */
    private final int requiredConsistentFrames;

    /*
     * Quantos frames rejeitados podem ocorrer antes
     * de perder completamente o conjunto estável.
     */
    private final int maximumMissedFrames;

    /*
     * Diferença máxima permitida entre os formatos
     * normalizados dos quadriláteros.
     */
    private final double maximumNormalizedShapeDistance;

    /*
     * Variação relativa máxima da área da região.
     */
    private final double maximumRegionAreaChangeRatio;

    public static MarkerSetStabilizerConfig
    developmentDefaults() {

        return new MarkerSetStabilizerConfig(
                3,
                2,
                0.12,
                0.25
        );
    }

    public MarkerSetStabilizerConfig(
            int requiredConsistentFrames,
            int maximumMissedFrames,
            double maximumNormalizedShapeDistance,
            double maximumRegionAreaChangeRatio
    ) {

        this.requiredConsistentFrames =
                requiredConsistentFrames;

        this.maximumMissedFrames =
                maximumMissedFrames;

        this.maximumNormalizedShapeDistance =
                maximumNormalizedShapeDistance;

        this.maximumRegionAreaChangeRatio =
                maximumRegionAreaChangeRatio;
    }

    public int getRequiredConsistentFrames() {
        return requiredConsistentFrames;
    }

    public int getMaximumMissedFrames() {
        return maximumMissedFrames;
    }

    public double
    getMaximumNormalizedShapeDistance() {

        return maximumNormalizedShapeDistance;
    }

    public double
    getMaximumRegionAreaChangeRatio() {

        return maximumRegionAreaChangeRatio;
    }
}