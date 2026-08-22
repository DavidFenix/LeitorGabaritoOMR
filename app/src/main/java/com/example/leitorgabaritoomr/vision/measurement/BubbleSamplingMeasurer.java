package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.image.GrayImageBuffer;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegion;

/**
 * Mede uma alternativa usando exatamente a geometria exibida no
 * Laboratorio OMR.
 *
 * Esta classe nao depende de Android nem de OpenCV. Ela recebe uma
 * fotografia imutavel da imagem cinza e a mesma instancia de
 * BubbleSamplingGeometry entregue ao renderizador de diagnostico.
 * Portanto, nenhum retangulo, nucleo ou fundo e recalculado durante
 * a medicao.
 */
public final class BubbleSamplingMeasurer {

    /**
     * Produz as medicoes brutas de uma alternativa.
     *
     * As zonas sao mutuamente exclusivas e vem exclusivamente de
     * BubbleSamplingGeometry.classifyPixel(...):
     *
     * - CORE: nucleo da bolha;
     * - BORDER: restante da bolha;
     * - LOCAL_BACKGROUND: fundo local externo a bolha.
     */
    public BubbleMeasurement measure(
            GrayImageBuffer grayImage,
            BubbleSamplingGeometry geometry
    ) {
        validateInputs(
                grayImage,
                geometry
        );

        BubbleMeasurementConfig config =
                geometry.getConfig();

        ZoneStatistics statistics =
                collectZoneStatistics(
                        grayImage,
                        geometry,
                        config.getDarkPixelThreshold()
                );

        statistics.validate(
                geometry.getOptionId()
        );

        double coreMean =
                divide(
                        statistics.coreIntensitySum,
                        statistics.corePixelCount
                );

        double borderMean =
                divide(
                        statistics.borderIntensitySum,
                        statistics.borderPixelCount
                );

        double localBackgroundMean =
                divide(
                        statistics.backgroundIntensitySum,
                        statistics.backgroundPixelCount
                );

        long regionPixelCount =
                statistics.corePixelCount
                        + statistics.borderPixelCount;

        long regionIntensitySum =
                statistics.coreIntensitySum
                        + statistics.borderIntensitySum;

        long regionDarkPixelCount =
                statistics.coreDarkPixelCount
                        + statistics.borderDarkPixelCount;

        double localDarknessThreshold =
                clampIntensity(
                        localBackgroundMean
                                - config.getLocalDarknessDelta()
                );

        PixelRectangle bubbleBounds =
                geometry
                        .getBubblePolygon()
                        .getPixelBounds();

        PixelRectangle coreBounds =
                geometry
                        .getCorePolygon()
                        .getPixelBounds();

        PixelRectangle backgroundBounds =
                geometry.getSamplingBounds();

        /*
         * Este objeto guarda os mesmos limites usados na varredura.
         * A decisao de pertencimento de cada pixel continua sendo
         * feita exclusivamente por geometry.classifyPixel(...).
         */
        BubbleMeasurementGeometry measurementGeometry =
                new BubbleMeasurementGeometry(
                        geometry.getOption(),
                        bubbleBounds,
                        coreBounds,
                        backgroundBounds
                );

        byte[] locallyDarkCoreMask =
                createLocallyDarkCoreMask(
                        grayImage,
                        geometry,
                        coreBounds,
                        localDarknessThreshold
                );

        long locallyDarkCorePixelCount =
                countMarkedPixels(
                        locallyDarkCoreMask
                );

        return new BubbleMeasurement(
                measurementGeometry,

                clampIntensity(
                        divide(
                                regionIntensitySum,
                                regionPixelCount
                        )
                ),

                clampIntensity(coreMean),
                clampIntensity(borderMean),
                clampIntensity(localBackgroundMean),
                localDarknessThreshold,

                clampRatio(
                        divide(
                                regionDarkPixelCount,
                                regionPixelCount
                        )
                ),

                clampRatio(
                        divide(
                                statistics.coreDarkPixelCount,
                                statistics.corePixelCount
                        )
                ),

                clampRatio(
                        divide(
                                statistics.borderDarkPixelCount,
                                statistics.borderPixelCount
                        )
                ),

                clampRatio(
                        divide(
                                locallyDarkCorePixelCount,
                                statistics.corePixelCount
                        )
                ),

                locallyDarkCoreMask
        );
    }

    private void validateInputs(
            GrayImageBuffer grayImage,
            BubbleSamplingGeometry geometry
    ) {
        if (grayImage == null) {
            throw new IllegalArgumentException(
                    "A imagem cinza e obrigatoria."
            );
        }

        if (geometry == null) {
            throw new IllegalArgumentException(
                    "A geometria de amostragem e obrigatoria."
            );
        }

        RegisteredBubbleRegion registeredRegion =
                geometry.getRegisteredRegion();

        if (!grayImage.hasDimensions(
                registeredRegion.getImageWidth(),
                registeredRegion.getImageHeight()
        )) {
            throw new IllegalArgumentException(
                    "A imagem cinza "
                            + grayImage.getWidth()
                            + "x"
                            + grayImage.getHeight()
                            + " nao corresponde a imagem registrada "
                            + registeredRegion.getImageWidth()
                            + "x"
                            + registeredRegion.getImageHeight()
                            + " para "
                            + geometry.getOptionId()
                            + "."
            );
        }

        /*
         * Um fundo cortado pela borda da imagem produz uma media
         * local assimetrica e pode favorecer ou prejudicar a bolha.
         * O conjunto so deve ser medido depois que o registro deixar
         * todas as areas de fundo completas.
         */
        if (geometry.isBackgroundClippedByImage()) {
            throw new IllegalArgumentException(
                    "O fundo local foi cortado pela imagem para "
                            + geometry.getOptionId()
                            + "."
            );
        }
    }

    private ZoneStatistics collectZoneStatistics(
            GrayImageBuffer grayImage,
            BubbleSamplingGeometry geometry,
            double absoluteDarknessThreshold
    ) {
        ZoneStatistics statistics =
                new ZoneStatistics();

        PixelRectangle bounds =
                geometry.getSamplingBounds();

        int rightExclusive =
                bounds.getLeft()
                        + bounds.getWidth();

        int bottomExclusive =
                bounds.getTop()
                        + bounds.getHeight();

        for (int pixelY = bounds.getTop();
             pixelY < bottomExclusive;
             pixelY++) {

            int rowOffset =
                    grayImage.getRowOffset(pixelY);

            for (int pixelX = bounds.getLeft();
                 pixelX < rightExclusive;
                 pixelX++) {

                BubbleSamplingGeometry.Zone zone =
                        geometry.classifyPixel(
                                pixelX,
                                pixelY
                        );

                if (zone
                        == BubbleSamplingGeometry.Zone.OUTSIDE) {
                    continue;
                }

                int intensity =
                        grayImage.getIntensityAtIndex(
                                rowOffset + pixelX
                        );

                statistics.add(
                        zone,
                        intensity,
                        intensity
                                < absoluteDarknessThreshold
                );
            }
        }

        return statistics;
    }

    /**
     * Constroi a mascara com as dimensoes do retangulo envolvente
     * do nucleo, como BubbleMeasurement exige.
     *
     * Somente pixels classificados como CORE podem receber 255.
     * Os cantos do retangulo que estejam fora do poligono real do
     * nucleo permanecem com zero.
     */
    private byte[] createLocallyDarkCoreMask(
            GrayImageBuffer grayImage,
            BubbleSamplingGeometry geometry,
            PixelRectangle coreBounds,
            double localDarknessThreshold
    ) {
        byte[] mask =
                new byte[coreBounds.getArea()];

        int rightExclusive =
                coreBounds.getLeft()
                        + coreBounds.getWidth();

        int bottomExclusive =
                coreBounds.getTop()
                        + coreBounds.getHeight();

        for (int pixelY = coreBounds.getTop();
             pixelY < bottomExclusive;
             pixelY++) {

            int rowOffset =
                    grayImage.getRowOffset(pixelY);

            int localY =
                    pixelY - coreBounds.getTop();

            for (int pixelX = coreBounds.getLeft();
                 pixelX < rightExclusive;
                 pixelX++) {

                if (geometry.classifyPixel(
                        pixelX,
                        pixelY
                ) != BubbleSamplingGeometry.Zone.CORE) {
                    continue;
                }

                int intensity =
                        grayImage.getIntensityAtIndex(
                                rowOffset + pixelX
                        );

                if (intensity
                        >= localDarknessThreshold) {
                    continue;
                }

                int localX =
                        pixelX - coreBounds.getLeft();

                int maskIndex =
                        localY
                                * coreBounds.getWidth()
                                + localX;

                mask[maskIndex] = (byte) 255;
            }
        }

        return mask;
    }

    private long countMarkedPixels(
            byte[] mask
    ) {
        long count = 0L;

        for (byte value : mask) {
            if (value != 0) {
                count++;
            }
        }

        return count;
    }

    private double divide(
            long numerator,
            long denominator
    ) {
        if (denominator <= 0L) {
            return 0.0;
        }

        return numerator / (double) denominator;
    }

    private double clampIntensity(double value) {
        return clamp(
                value,
                0.0,
                255.0
        );
    }

    private double clampRatio(double value) {
        return clamp(
                value,
                0.0,
                1.0
        );
    }

    private double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        if (!Double.isFinite(value)) {
            return minimum;
        }

        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    private static final class ZoneStatistics {

        private long coreIntensitySum;
        private long borderIntensitySum;
        private long backgroundIntensitySum;

        private long corePixelCount;
        private long borderPixelCount;
        private long backgroundPixelCount;

        private long coreDarkPixelCount;
        private long borderDarkPixelCount;

        private void add(
                BubbleSamplingGeometry.Zone zone,
                int intensity,
                boolean isAbsolutelyDark
        ) {
            switch (zone) {
                case CORE:
                    coreIntensitySum += intensity;
                    corePixelCount++;

                    if (isAbsolutelyDark) {
                        coreDarkPixelCount++;
                    }
                    break;

                case BORDER:
                    borderIntensitySum += intensity;
                    borderPixelCount++;

                    if (isAbsolutelyDark) {
                        borderDarkPixelCount++;
                    }
                    break;

                case LOCAL_BACKGROUND:
                    backgroundIntensitySum += intensity;
                    backgroundPixelCount++;
                    break;

                case OUTSIDE:
                default:
                    break;
            }
        }

        private void validate(String optionId) {
            if (corePixelCount <= 0L) {
                throw new IllegalArgumentException(
                        "O nucleo nao possui pixels para "
                                + optionId
                                + "."
                );
            }

            if (borderPixelCount <= 0L) {
                throw new IllegalArgumentException(
                        "A borda nao possui pixels para "
                                + optionId
                                + "."
                );
            }

            if (backgroundPixelCount <= 0L) {
                throw new IllegalArgumentException(
                        "O fundo local nao possui pixels para "
                                + optionId
                                + "."
                );
            }
        }
    }
}
