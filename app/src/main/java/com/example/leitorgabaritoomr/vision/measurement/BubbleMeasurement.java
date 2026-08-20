package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.Locale;

/**
 * Armazena as medições brutas de uma alternativa.
 *
 * Também conserva:
 *
 * - a geometria exata usada pelo cálculo;
 * - o limiar local calculado;
 * - a máscara exata dos pixels do núcleo considerados escuros.
 *
 * Não realiza classificação.
 */
public final class BubbleMeasurement {

    private final BubbleMeasurementGeometry geometry;

    private final double regionMeanIntensity;
    private final double coreMeanIntensity;
    private final double borderMeanIntensity;

    /*
     * Média do fundo ao redor da alternativa,
     * excluindo toda a região da bolha.
     */
    private final double localBackgroundMeanIntensity;

    /*
     * Limiar efetivamente utilizado para classificar cada
     * pixel do núcleo:
     *
     * pixel < localDarkThreshold
     */
    private final double localDarkThreshold;

    private final double regionDarkPixelRatio;
    private final double coreDarkPixelRatio;
    private final double borderDarkPixelRatio;

    /*
     * Proporção do núcleo significativamente mais escura
     * que o fundo local.
     */
    private final double coreLocallyDarkPixelRatio;

    /*
     * Máscara exata produzida pelo Core.compare().
     *
     * Cada posição corresponde a um pixel de coreRegion:
     *
     * 0   = não considerado localmente escuro;
     * 255 = considerado localmente escuro.
     */
    private final byte[] locallyDarkCoreMask;

    private final double coreBorderDarknessContrast;
    private final double localContrastScore;

    public BubbleMeasurement(
            BubbleMeasurementGeometry geometry,
            double regionMeanIntensity,
            double coreMeanIntensity,
            double borderMeanIntensity,
            double localBackgroundMeanIntensity,
            double localDarkThreshold,
            double regionDarkPixelRatio,
            double coreDarkPixelRatio,
            double borderDarkPixelRatio,
            double coreLocallyDarkPixelRatio,
            byte[] locallyDarkCoreMask
    ) {
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "A geometria da medição é obrigatória."
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

        validateIntensity(
                "localDarkThreshold",
                localDarkThreshold
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

        PixelRectangle coreRegion =
                geometry.getCoreRegion();

        int expectedMaskLength =
                coreRegion.getArea();

        if (locallyDarkCoreMask == null
                || locallyDarkCoreMask.length
                != expectedMaskLength) {

            throw new IllegalArgumentException(
                    "A máscara local deve possuir exatamente "
                            + expectedMaskLength
                            + " pixels."
            );
        }

        this.geometry = geometry;

        this.regionMeanIntensity =
                regionMeanIntensity;

        this.coreMeanIntensity =
                coreMeanIntensity;

        this.borderMeanIntensity =
                borderMeanIntensity;

        this.localBackgroundMeanIntensity =
                localBackgroundMeanIntensity;

        this.localDarkThreshold =
                localDarkThreshold;

        this.regionDarkPixelRatio =
                regionDarkPixelRatio;

        this.coreDarkPixelRatio =
                coreDarkPixelRatio;

        this.borderDarkPixelRatio =
                borderDarkPixelRatio;

        this.coreLocallyDarkPixelRatio =
                coreLocallyDarkPixelRatio;

        /*
         * Cópia defensiva: ninguém poderá alterar posteriormente
         * os pixels que produziram a medição.
         */
        this.locallyDarkCoreMask =
                locallyDarkCoreMask.clone();

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

    public BubbleMeasurementGeometry getGeometry() {
        return geometry;
    }

    public OmrOptionDefinition getOption() {
        return geometry.getOption();
    }

    public PixelRectangle getBubbleRegion() {
        return geometry.getBubbleRegion();
    }

    public PixelRectangle getCoreRegion() {
        return geometry.getCoreRegion();
    }

    public PixelRectangle getBackgroundRegion() {
        return geometry.getBackgroundRegion();
    }

    /*
     * Getters mantidos para compatibilidade com os renderizadores
     * e demais componentes existentes.
     */

    public int getRegionLeft() {
        return getBubbleRegion().getLeft();
    }

    public int getRegionTop() {
        return getBubbleRegion().getTop();
    }

    public int getRegionWidth() {
        return getBubbleRegion().getWidth();
    }

    public int getRegionHeight() {
        return getBubbleRegion().getHeight();
    }

    public int getCoreLeft() {
        return getCoreRegion().getLeft();
    }

    public int getCoreTop() {
        return getCoreRegion().getTop();
    }

    public int getCoreWidth() {
        return getCoreRegion().getWidth();
    }

    public int getCoreHeight() {
        return getCoreRegion().getHeight();
    }

    public int getBackgroundLeft() {
        return getBackgroundRegion().getLeft();
    }

    public int getBackgroundTop() {
        return getBackgroundRegion().getTop();
    }

    public int getBackgroundWidth() {
        return getBackgroundRegion().getWidth();
    }

    public int getBackgroundHeight() {
        return getBackgroundRegion().getHeight();
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

    public double getLocalDarkThreshold() {
        return localDarkThreshold;
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

    public byte[] copyLocallyDarkCoreMask() {
        return locallyDarkCoreMask.clone();
    }

    /**
     * Consulta exatamente a máscara usada na contagem.
     *
     * As coordenadas são locais ao núcleo:
     *
     * localX: 0 até coreWidth - 1
     * localY: 0 até coreHeight - 1
     */
    public boolean isCorePixelLocallyDark(
            int localX,
            int localY
    ) {
        PixelRectangle core =
                getCoreRegion();

        if (localX < 0
                || localX >= core.getWidth()
                || localY < 0
                || localY >= core.getHeight()) {

            throw new IllegalArgumentException(
                    "Coordenada fora do núcleo."
            );
        }

        int index =
                localY * core.getWidth()
                        + localX;

        return locallyDarkCoreMask[index] != 0;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s core=%.1f fundo=%.1f limiar=%.1f"
                        + " contrasteLocal=%.3f escuroLocal=%.3f",
                getOption().getId(),
                coreMeanIntensity,
                localBackgroundMeanIntensity,
                localDarkThreshold,
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
// * Armazena medições brutas de uma alternativa.
// *
// * Não realiza classificação.
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
//    /*
//     * Média do fundo ao redor da alternativa, excluindo
//     * toda a região da bolha.
//     */
//    private final double localBackgroundMeanIntensity;
//
//    private final double regionDarkPixelRatio;
//    private final double coreDarkPixelRatio;
//    private final double borderDarkPixelRatio;
//
//    /*
//     * Proporção do núcleo que ficou significativamente mais
//     * escura que o fundo local.
//     */
//    private final double coreLocallyDarkPixelRatio;
//
//    private final double coreBorderDarknessContrast;
//
//    /*
//     * Contraste normalizado entre fundo local e núcleo:
//     *
//     * (fundo - núcleo) / fundo
//     */
//    private final double localContrastScore;
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
//            double localBackgroundMeanIntensity,
//            double regionDarkPixelRatio,
//            double coreDarkPixelRatio,
//            double borderDarkPixelRatio,
//            double coreLocallyDarkPixelRatio
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
//        validateIntensity(
//                "localBackgroundMeanIntensity",
//                localBackgroundMeanIntensity
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
//        validateRatio(
//                "coreLocallyDarkPixelRatio",
//                coreLocallyDarkPixelRatio
//        );
//
//        this.option = option;
//
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
//        this.localBackgroundMeanIntensity =
//                localBackgroundMeanIntensity;
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
//        this.coreLocallyDarkPixelRatio =
//                coreLocallyDarkPixelRatio;
//
//        this.coreBorderDarknessContrast =
//                coreDarkPixelRatio
//                        - borderDarkPixelRatio;
//
//        this.localContrastScore =
//                calculateLocalContrast(
//                        localBackgroundMeanIntensity,
//                        coreMeanIntensity
//                );
//    }
//
//    private double calculateLocalContrast(
//            double backgroundMean,
//            double coreMean
//    ) {
//        if (backgroundMean <= 1.0) {
//            return 0.0;
//        }
//
//        double contrast =
//                (backgroundMean - coreMean)
//                        / backgroundMean;
//
//        return Math.max(
//                -1.0,
//                Math.min(1.0, contrast)
//        );
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
//    public double getLocalBackgroundMeanIntensity() {
//        return localBackgroundMeanIntensity;
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
//    public double getCoreLocallyDarkPixelRatio() {
//        return coreLocallyDarkPixelRatio;
//    }
//
//    public double getCoreBorderDarknessContrast() {
//        return coreBorderDarknessContrast;
//    }
//
//    public double getLocalContrastScore() {
//        return localContrastScore;
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
//                "%s core=%.1f fundo=%.1f contrasteLocal=%.3f escuroLocal=%.3f",
//                option.getId(),
//                coreMeanIntensity,
//                localBackgroundMeanIntensity,
//                localContrastScore,
//                coreLocallyDarkPixelRatio
//        );
//    }
//}
