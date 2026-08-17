package com.example.leitorgabaritoomr.vision.geometry;

public final class MarkerSetResolverConfig {

    /*
     * Quantos candidatos extremos serão mantidos
     * para cada papel geométrico.
     */
    private final int candidatesPerCorner;

    /*
     * Área mínima da região em relação ao frame.
     * O valor é propositalmente baixo para permitir
     * gabaritos compactos dentro de páginas maiores.
     */
    private final double minimumRegionAreaRatio;

    /*
     * Semelhança mínima entre o menor e o maior
     * marcador do conjunto.
     */
    private final double minimumSizeSimilarity;

    /*
     * Nota mínima para aceitar o conjunto.
     */
    private final double minimumAcceptedScore;

    /*
     * Diferença mínima entre o melhor e o segundo
     * melhor conjunto.
     */
    private final double minimumScoreDifference;

    public static MarkerSetResolverConfig developmentDefaults() {

        return new MarkerSetResolverConfig(
                4,
                0.01,
                0.40,
                0.82,
                //0.60,
                0.02
        );
    }

    public MarkerSetResolverConfig(
            int candidatesPerCorner,
            double minimumRegionAreaRatio,
            double minimumSizeSimilarity,
            double minimumAcceptedScore,
            double minimumScoreDifference
    ) {

        this.candidatesPerCorner =
                candidatesPerCorner;

        this.minimumRegionAreaRatio =
                minimumRegionAreaRatio;

        this.minimumSizeSimilarity =
                minimumSizeSimilarity;

        this.minimumAcceptedScore =
                minimumAcceptedScore;

        this.minimumScoreDifference =
                minimumScoreDifference;
    }

    public int getCandidatesPerCorner() {
        return candidatesPerCorner;
    }

    public double getMinimumRegionAreaRatio() {
        return minimumRegionAreaRatio;
    }

    public double getMinimumSizeSimilarity() {
        return minimumSizeSimilarity;
    }

    public double getMinimumAcceptedScore() {
        return minimumAcceptedScore;
    }

    public double getMinimumScoreDifference() {
        return minimumScoreDifference;
    }
}