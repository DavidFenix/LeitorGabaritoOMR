package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.Locale;

/**
 * Armazena medições brutas de uma alternativa.
 *
 * Não realiza classificação.
 */
public final class BubbleMeasurement {

    private final OmrOptionDefinition option;

    private final int regionLeft;
    private final int regionTop;
    private final int regionWidth;
    private final int regionHeight;

    private final double regionMeanIntensity;
    private final double coreMeanIntensity;
    private final double borderMeanIntensity;

    /*
     * Média do fundo ao redor da alternativa, excluindo
     * toda a região da bolha.
     */
    private final double localBackgroundMeanIntensity;

    private final double regionDarkPixelRatio;
    private final double coreDarkPixelRatio;
    private final double borderDarkPixelRatio;

    /*
     * Proporção do núcleo que ficou significativamente mais
     * escura que o fundo local.
     */
    private final double coreLocallyDarkPixelRatio;

    private final double coreBorderDarknessContrast;

    /*
     * Contraste normalizado entre fundo local e núcleo:
     *
     * (fundo - núcleo) / fundo
     */
    private final double localContrastScore;

    public BubbleMeasurement(
            OmrOptionDefinition option,
            int regionLeft,
            int regionTop,
            int regionWidth,
            int regionHeight,
            double regionMeanIntensity,
            double coreMeanIntensity,
            double borderMeanIntensity,
            double localBackgroundMeanIntensity,
            double regionDarkPixelRatio,
            double coreDarkPixelRatio,
            double borderDarkPixelRatio,
            double coreLocallyDarkPixelRatio
    ) {
        if (option == null) {
            throw new IllegalArgumentException(
                    "A alternativa medida é obrigatória."
            );
        }

        if (regionLeft < 0
                || regionTop < 0
                || regionWidth <= 0
                || regionHeight <= 0) {

            throw new IllegalArgumentException(
                    "A região medida é inválida."
            );
        }

        validateIntensity(
                "regionMeanIntensity",
                regionMeanIntensity
        );

        validateIntensity(
                "coreMeanIntensity",
                coreMeanIntensity
        );

        validateIntensity(
                "borderMeanIntensity",
                borderMeanIntensity
        );

        validateIntensity(
                "localBackgroundMeanIntensity",
                localBackgroundMeanIntensity
        );

        validateRatio(
                "regionDarkPixelRatio",
                regionDarkPixelRatio
        );

        validateRatio(
                "coreDarkPixelRatio",
                coreDarkPixelRatio
        );

        validateRatio(
                "borderDarkPixelRatio",
                borderDarkPixelRatio
        );

        validateRatio(
                "coreLocallyDarkPixelRatio",
                coreLocallyDarkPixelRatio
        );

        this.option = option;

        this.regionLeft = regionLeft;
        this.regionTop = regionTop;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;

        this.regionMeanIntensity =
                regionMeanIntensity;

        this.coreMeanIntensity =
                coreMeanIntensity;

        this.borderMeanIntensity =
                borderMeanIntensity;

        this.localBackgroundMeanIntensity =
                localBackgroundMeanIntensity;

        this.regionDarkPixelRatio =
                regionDarkPixelRatio;

        this.coreDarkPixelRatio =
                coreDarkPixelRatio;

        this.borderDarkPixelRatio =
                borderDarkPixelRatio;

        this.coreLocallyDarkPixelRatio =
                coreLocallyDarkPixelRatio;

        this.coreBorderDarknessContrast =
                coreDarkPixelRatio
                        - borderDarkPixelRatio;

        this.localContrastScore =
                calculateLocalContrast(
                        localBackgroundMeanIntensity,
                        coreMeanIntensity
                );
    }

    private double calculateLocalContrast(
            double backgroundMean,
            double coreMean
    ) {
        if (backgroundMean <= 1.0) {
            return 0.0;
        }

        double contrast =
                (backgroundMean - coreMean)
                        / backgroundMean;

        return Math.max(
                -1.0,
                Math.min(1.0, contrast)
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

    private void validateRatio(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve estar entre 0.0 e 1.0."
            );
        }
    }

    public OmrOptionDefinition getOption() {
        return option;
    }

    public int getRegionLeft() {
        return regionLeft;
    }

    public int getRegionTop() {
        return regionTop;
    }

    public int getRegionWidth() {
        return regionWidth;
    }

    public int getRegionHeight() {
        return regionHeight;
    }

    public double getRegionMeanIntensity() {
        return regionMeanIntensity;
    }

    public double getCoreMeanIntensity() {
        return coreMeanIntensity;
    }

    public double getBorderMeanIntensity() {
        return borderMeanIntensity;
    }

    public double getLocalBackgroundMeanIntensity() {
        return localBackgroundMeanIntensity;
    }

    public double getRegionDarkPixelRatio() {
        return regionDarkPixelRatio;
    }

    public double getCoreDarkPixelRatio() {
        return coreDarkPixelRatio;
    }

    public double getBorderDarkPixelRatio() {
        return borderDarkPixelRatio;
    }

    public double getCoreLocallyDarkPixelRatio() {
        return coreLocallyDarkPixelRatio;
    }

    public double getCoreBorderDarknessContrast() {
        return coreBorderDarknessContrast;
    }

    public double getLocalContrastScore() {
        return localContrastScore;
    }

    public double getCoreNormalizedDarkness() {
        return 1.0
                - coreMeanIntensity / 255.0;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s core=%.1f fundo=%.1f contrasteLocal=%.3f escuroLocal=%.3f",
                option.getId(),
                coreMeanIntensity,
                localBackgroundMeanIntensity,
                localContrastScore,
                coreLocallyDarkPixelRatio
        );
    }
}

//package com.example.leitorgabaritoomr.vision.measurement;
//
//import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
//
//import java.util.Locale;
//
///**
// * Armazena as medições brutas de uma região de resposta.
// *
// * Nenhuma decisão MARKED ou EMPTY é tomada nesta classe.
// */
//public final class BubbleMeasurement {
//
//    private final OmrOptionDefinition option;
//
//    private final int regionLeft;
//    private final int regionTop;
//    private final int regionWidth;
//    private final int regionHeight;
//
//    private final double regionMeanIntensity;
//    private final double coreMeanIntensity;
//    private final double borderMeanIntensity;
//
//    private final double regionDarkPixelRatio;
//    private final double coreDarkPixelRatio;
//    private final double borderDarkPixelRatio;
//
//    /*
//     * Diferença entre a escuridão do núcleo e a da borda.
//     *
//     * Valores maiores tendem a indicar preenchimento interno.
//     */
//    private final double coreBorderDarknessContrast;
//
//    public BubbleMeasurement(
//            OmrOptionDefinition option,
//            int regionLeft,
//            int regionTop,
//            int regionWidth,
//            int regionHeight,
//            double regionMeanIntensity,
//            double coreMeanIntensity,
//            double borderMeanIntensity,
//            double regionDarkPixelRatio,
//            double coreDarkPixelRatio,
//            double borderDarkPixelRatio
//    ) {
//        if (option == null) {
//            throw new IllegalArgumentException(
//                    "A alternativa medida é obrigatória."
//            );
//        }
//
//        if (regionLeft < 0
//                || regionTop < 0
//                || regionWidth <= 0
//                || regionHeight <= 0) {
//
//            throw new IllegalArgumentException(
//                    "A região medida é inválida."
//            );
//        }
//
//        validateIntensity(
//                "regionMeanIntensity",
//                regionMeanIntensity
//        );
//
//        validateIntensity(
//                "coreMeanIntensity",
//                coreMeanIntensity
//        );
//
//        validateIntensity(
//                "borderMeanIntensity",
//                borderMeanIntensity
//        );
//
//        validateRatio(
//                "regionDarkPixelRatio",
//                regionDarkPixelRatio
//        );
//
//        validateRatio(
//                "coreDarkPixelRatio",
//                coreDarkPixelRatio
//        );
//
//        validateRatio(
//                "borderDarkPixelRatio",
//                borderDarkPixelRatio
//        );
//
//        this.option = option;
//        this.regionLeft = regionLeft;
//        this.regionTop = regionTop;
//        this.regionWidth = regionWidth;
//        this.regionHeight = regionHeight;
//
//        this.regionMeanIntensity =
//                regionMeanIntensity;
//
//        this.coreMeanIntensity =
//                coreMeanIntensity;
//
//        this.borderMeanIntensity =
//                borderMeanIntensity;
//
//        this.regionDarkPixelRatio =
//                regionDarkPixelRatio;
//
//        this.coreDarkPixelRatio =
//                coreDarkPixelRatio;
//
//        this.borderDarkPixelRatio =
//                borderDarkPixelRatio;
//
//        this.coreBorderDarknessContrast =
//                coreDarkPixelRatio
//                        - borderDarkPixelRatio;
//    }
//
//    private void validateIntensity(
//            String fieldName,
//            double value
//    ) {
//        if (!Double.isFinite(value)
//                || value < 0.0
//                || value > 255.0) {
//
//            throw new IllegalArgumentException(
//                    fieldName
//                            + " deve estar entre 0 e 255."
//            );
//        }
//    }
//
//    private void validateRatio(
//            String fieldName,
//            double value
//    ) {
//        if (!Double.isFinite(value)
//                || value < 0.0
//                || value > 1.0) {
//
//            throw new IllegalArgumentException(
//                    fieldName
//                            + " deve estar entre 0.0 e 1.0."
//            );
//        }
//    }
//
//    public OmrOptionDefinition getOption() {
//        return option;
//    }
//
//    public int getRegionLeft() {
//        return regionLeft;
//    }
//
//    public int getRegionTop() {
//        return regionTop;
//    }
//
//    public int getRegionWidth() {
//        return regionWidth;
//    }
//
//    public int getRegionHeight() {
//        return regionHeight;
//    }
//
//    public double getRegionMeanIntensity() {
//        return regionMeanIntensity;
//    }
//
//    public double getCoreMeanIntensity() {
//        return coreMeanIntensity;
//    }
//
//    public double getBorderMeanIntensity() {
//        return borderMeanIntensity;
//    }
//
//    public double getRegionDarkPixelRatio() {
//        return regionDarkPixelRatio;
//    }
//
//    public double getCoreDarkPixelRatio() {
//        return coreDarkPixelRatio;
//    }
//
//    public double getBorderDarkPixelRatio() {
//        return borderDarkPixelRatio;
//    }
//
//    public double getCoreBorderDarknessContrast() {
//        return coreBorderDarknessContrast;
//    }
//
//    public double getCoreNormalizedDarkness() {
//        return 1.0
//                - coreMeanIntensity / 255.0;
//    }
//
//    @Override
//    public String toString() {
//        return String.format(
//                Locale.US,
//                "%s coreMean=%.1f coreDark=%.3f borderDark=%.3f contraste=%.3f",
//                option.getId(),
//                coreMeanIntensity,
//                coreDarkPixelRatio,
//                borderDarkPixelRatio,
//                coreBorderDarknessContrast
//        );
//    }
//}