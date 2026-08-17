package com.example.leitorgabaritoomr.vision.normalization;

/**
 * Configurações da normalização de perspectiva da região OMR.
 *
 * Nesta primeira versão, a largura e a altura da imagem normalizada
 * são calculadas a partir das distâncias entre os quatro marcadores.
 *
 * Portanto, não existe dependência de:
 *
 * - tamanho A4;
 * - medidas em milímetros;
 * - quantidade de questões;
 * - dimensões fixas do gabarito.
 */
public final class OmrRegionNormalizerConfig {

    private final int minimumOutputDimension;
    private final int maximumOutputWidth;
    private final int maximumOutputHeight;

    public OmrRegionNormalizerConfig(
            int minimumOutputDimension,
            int maximumOutputWidth,
            int maximumOutputHeight
    ) {
        if (minimumOutputDimension < 2) {
            throw new IllegalArgumentException(
                    "minimumOutputDimension deve ser maior ou igual a 2."
            );
        }

        if (maximumOutputWidth < minimumOutputDimension) {
            throw new IllegalArgumentException(
                    "maximumOutputWidth deve ser maior ou igual ao mínimo."
            );
        }

        if (maximumOutputHeight < minimumOutputDimension) {
            throw new IllegalArgumentException(
                    "maximumOutputHeight deve ser maior ou igual ao mínimo."
            );
        }

        this.minimumOutputDimension = minimumOutputDimension;
        this.maximumOutputWidth = maximumOutputWidth;
        this.maximumOutputHeight = maximumOutputHeight;
    }

    public static OmrRegionNormalizerConfig developmentDefaults() {
        return new OmrRegionNormalizerConfig(
                120,
                2000,
                2000
        );
    }

    public int getMinimumOutputDimension() {
        return minimumOutputDimension;
    }

    public int getMaximumOutputWidth() {
        return maximumOutputWidth;
    }

    public int getMaximumOutputHeight() {
        return maximumOutputHeight;
    }
}