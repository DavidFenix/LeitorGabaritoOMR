package com.example.leitorgabaritoomr.vision.measurement;

/**
 * Configura as regiões e critérios usados para medir
 * cada alternativa.
 */
public final class BubbleMeasurementConfig {

    /*
     * Mantido para diagnóstico da escuridão absoluta.
     */
    private final double darkPixelThreshold;

    private final double coreWidthScale;
    private final double coreHeightScale;

    /*
     * Ampliação da região usada para estimar o fundo local.
     *
     * Deve ser maior que 1.0.
     */
    private final double backgroundWidthScale;
    private final double backgroundHeightScale;

    /*
     * Um pixel do núcleo será considerado localmente escuro
     * quando estiver pelo menos esta quantidade abaixo da
     * intensidade média do fundo local.
     */
    private final double localDarknessDelta;

    private final int minimumRegionWidth;
    private final int minimumRegionHeight;

    public BubbleMeasurementConfig(
            double darkPixelThreshold,
            double coreWidthScale,
            double coreHeightScale,
            double backgroundWidthScale,
            double backgroundHeightScale,
            double localDarknessDelta,
            int minimumRegionWidth,
            int minimumRegionHeight
    ) {
        validateIntensity(
                "darkPixelThreshold",
                darkPixelThreshold
        );

        validateCoreScale(
                "coreWidthScale",
                coreWidthScale
        );

        validateCoreScale(
                "coreHeightScale",
                coreHeightScale
        );

        validateBackgroundScale(
                "backgroundWidthScale",
                backgroundWidthScale
        );

        validateBackgroundScale(
                "backgroundHeightScale",
                backgroundHeightScale
        );

        if (!Double.isFinite(localDarknessDelta)
                || localDarknessDelta < 0.0
                || localDarknessDelta > 255.0) {

            throw new IllegalArgumentException(
                    "localDarknessDelta deve estar entre 0 e 255."
            );
        }

        if (minimumRegionWidth < 1
                || minimumRegionHeight < 1) {

            throw new IllegalArgumentException(
                    "As dimensões mínimas devem ser positivas."
            );
        }

        this.darkPixelThreshold =
                darkPixelThreshold;

        this.coreWidthScale =
                coreWidthScale;

        this.coreHeightScale =
                coreHeightScale;

        this.backgroundWidthScale =
                backgroundWidthScale;

        this.backgroundHeightScale =
                backgroundHeightScale;

        this.localDarknessDelta =
                localDarknessDelta;

        this.minimumRegionWidth =
                minimumRegionWidth;

        this.minimumRegionHeight =
                minimumRegionHeight;
    }

    public static BubbleMeasurementConfig
    developmentDefaults() {

        return new BubbleMeasurementConfig(
                160.0,

                /*
                 * Núcleo central.
                 */
                0.55,
                0.55,

                /*
                 * Região externa usada para estimar iluminação.
                 */
                1.60,
                1.60,

                /*
                 * Diferença mínima em níveis de cinza.
                 */
                25.0,

                3,
                3
        );
    }

    private void validateIntensity(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 255.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve estar entre 0 e 255."
            );
        }
    }

    private void validateCoreScale(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser maior que 0.0"
                            + " e menor ou igual a 1.0."
            );
        }
    }

    private void validateBackgroundScale(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 1.0
                || value > 4.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser maior que 1.0"
                            + " e menor ou igual a 4.0."
            );
        }
    }

    public double getDarkPixelThreshold() {
        return darkPixelThreshold;
    }

    public double getCoreWidthScale() {
        return coreWidthScale;
    }

    public double getCoreHeightScale() {
        return coreHeightScale;
    }

    public double getBackgroundWidthScale() {
        return backgroundWidthScale;
    }

    public double getBackgroundHeightScale() {
        return backgroundHeightScale;
    }

    public double getLocalDarknessDelta() {
        return localDarknessDelta;
    }

    public int getMinimumRegionWidth() {
        return minimumRegionWidth;
    }

    public int getMinimumRegionHeight() {
        return minimumRegionHeight;
    }
}
