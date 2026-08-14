package com.example.leitorgabaritoomr.vision.detector;

public final class SolidSquareDetectorConfig {

    /*
     * Área mínima do candidato em relação à área total
     * do frame.
     */
    private final double minimumAreaRatio;

    /*
     * Área máxima do candidato em relação à área total
     * do frame.
     */
    private final double maximumAreaRatio;

    /*
     * Menor dimensão permitida, em pixels.
     */
    private final int minimumSizePixels;

    /*
     * Proporção mínima entre o menor e o maior lado
     * do retângulo rotacionado.
     */
    private final double minimumSideRatio;

    /*
     * Quanto do retângulo delimitador precisa estar
     * preenchido na imagem binária.
     */
    private final double minimumFillRatio;

    /*
     * Precisão usada pelo approxPolyDP.
     */
    private final double polygonApproximationFactor;

    /*
     * Configuração inicial para desenvolvimento.
     * Esses valores serão ajustados com imagens reais.
     */
    public static SolidSquareDetectorConfig developmentDefaults() {

        return new SolidSquareDetectorConfig(
                0.00002,
                0.03,
                8,
                0.50,
                0.42,
                0.04
        );
    }

    public SolidSquareDetectorConfig(
            double minimumAreaRatio,
            double maximumAreaRatio,
            int minimumSizePixels,
            double minimumSideRatio,
            double minimumFillRatio,
            double polygonApproximationFactor
    ) {

        this.minimumAreaRatio = minimumAreaRatio;
        this.maximumAreaRatio = maximumAreaRatio;
        this.minimumSizePixels = minimumSizePixels;
        this.minimumSideRatio = minimumSideRatio;
        this.minimumFillRatio = minimumFillRatio;
        this.polygonApproximationFactor =
                polygonApproximationFactor;
    }

    public double getMinimumAreaRatio() {
        return minimumAreaRatio;
    }

    public double getMaximumAreaRatio() {
        return maximumAreaRatio;
    }

    public int getMinimumSizePixels() {
        return minimumSizePixels;
    }

    public double getMinimumSideRatio() {
        return minimumSideRatio;
    }

    public double getMinimumFillRatio() {
        return minimumFillRatio;
    }

    public double getPolygonApproximationFactor() {
        return polygonApproximationFactor;
    }
}