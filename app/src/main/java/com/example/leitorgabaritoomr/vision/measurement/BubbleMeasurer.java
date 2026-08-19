package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

/**
 * Mede núcleo, borda e fundo local de uma alternativa.
 *
 * A imagem recebida deve estar em escala de cinza.
 */
public final class BubbleMeasurer {

    private final BubbleMeasurementConfig config;

    public BubbleMeasurer(
            BubbleMeasurementConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuração de medição é obrigatória."
            );
        }

        this.config = config;
    }

    public BubbleMeasurement measure(
            Mat normalizedGray,
            OmrOptionDefinition option
    ) {
        validateInput(
                normalizedGray,
                option
        );

        Rect regionRect =
                calculateRegionRect(
                        normalizedGray,
                        option
                );

        validateRegionSize(regionRect);

        Rect coreRect =
                calculateCoreRect(regionRect);

        Rect backgroundRect =
                calculateBackgroundRect(
                        normalizedGray,
                        regionRect
                );

        Mat region = null;
        Mat core = null;
        Mat backgroundArea = null;

        Mat regionDarkMask = new Mat();
        Mat coreDarkMask = new Mat();
        Mat locallyDarkCoreMask = new Mat();

        try {
            region =
                    normalizedGray.submat(regionRect);

            core =
                    normalizedGray.submat(coreRect);

            backgroundArea =
                    normalizedGray.submat(
                            backgroundRect
                    );

            double regionMean =
                    Core.mean(region).val[0];

            double coreMean =
                    Core.mean(core).val[0];

            double backgroundAreaMean =
                    Core.mean(backgroundArea).val[0];

            int regionPixelCount =
                    region.rows() * region.cols();

            int corePixelCount =
                    core.rows() * core.cols();

            int borderPixelCount =
                    regionPixelCount
                            - corePixelCount;

            int backgroundAreaPixelCount =
                    backgroundArea.rows()
                            * backgroundArea.cols();

            int localBackgroundPixelCount =
                    backgroundAreaPixelCount
                            - regionPixelCount;

            double borderMean =
                    calculateExcludedMean(
                            regionMean,
                            regionPixelCount,
                            coreMean,
                            corePixelCount,
                            borderPixelCount,
                            regionMean
                    );

            double localBackgroundMean =
                    calculateExcludedMean(
                            backgroundAreaMean,
                            backgroundAreaPixelCount,
                            regionMean,
                            regionPixelCount,
                            localBackgroundPixelCount,
                            backgroundAreaMean
                    );

            Core.compare(
                    region,
                    new Scalar(
                            config.getDarkPixelThreshold()
                    ),
                    regionDarkMask,
                    Core.CMP_LT
            );

            Core.compare(
                    core,
                    new Scalar(
                            config.getDarkPixelThreshold()
                    ),
                    coreDarkMask,
                    Core.CMP_LT
            );

            double localThreshold =
                    clampIntensity(
                            localBackgroundMean
                                    - config
                                    .getLocalDarknessDelta()
                    );

            Core.compare(
                    core,
                    new Scalar(localThreshold),
                    locallyDarkCoreMask,
                    Core.CMP_LT
            );

            int regionDarkPixels =
                    Core.countNonZero(
                            regionDarkMask
                    );

            int coreDarkPixels =
                    Core.countNonZero(
                            coreDarkMask
                    );

            int borderDarkPixels =
                    Math.max(
                            0,
                            regionDarkPixels
                                    - coreDarkPixels
                    );

            int locallyDarkCorePixels =
                    Core.countNonZero(
                            locallyDarkCoreMask
                    );

            return new BubbleMeasurement(
                    option,
                    regionRect.x,
                    regionRect.y,
                    regionRect.width,
                    regionRect.height,

                    clampIntensity(regionMean),
                    clampIntensity(coreMean),
                    clampIntensity(borderMean),
                    clampIntensity(
                            localBackgroundMean
                    ),

                    clampRatio(
                            divide(
                                    regionDarkPixels,
                                    regionPixelCount
                            )
                    ),

                    clampRatio(
                            divide(
                                    coreDarkPixels,
                                    corePixelCount
                            )
                    ),

                    clampRatio(
                            divide(
                                    borderDarkPixels,
                                    borderPixelCount
                            )
                    ),

                    clampRatio(
                            divide(
                                    locallyDarkCorePixels,
                                    corePixelCount
                            )
                    )
            );

        } finally {
            if (backgroundArea != null) {
                backgroundArea.release();
            }

            if (core != null) {
                core.release();
            }

            if (region != null) {
                region.release();
            }

            regionDarkMask.release();
            coreDarkMask.release();
            locallyDarkCoreMask.release();
        }
    }

    private void validateInput(
            Mat normalizedGray,
            OmrOptionDefinition option
    ) {
        if (normalizedGray == null
                || normalizedGray.empty()) {

            throw new IllegalArgumentException(
                    "A imagem normalizada está vazia."
            );
        }

        if (normalizedGray.channels() != 1) {
            throw new IllegalArgumentException(
                    "BubbleMeasurer exige uma imagem"
                            + " em escala de cinza."
            );
        }

        if (option == null) {
            throw new IllegalArgumentException(
                    "A alternativa é obrigatória."
            );
        }
    }

    private Rect calculateRegionRect(
            Mat image,
            OmrOptionDefinition option
    ) {
        int left = clamp(
                (int) Math.floor(
                        option.getLeft()
                                * image.cols()
                ),
                0,
                image.cols() - 1
        );

        int top = clamp(
                (int) Math.floor(
                        option.getTop()
                                * image.rows()
                ),
                0,
                image.rows() - 1
        );

        int rightExclusive = clamp(
                (int) Math.ceil(
                        option.getRight()
                                * image.cols()
                ),
                left + 1,
                image.cols()
        );

        int bottomExclusive = clamp(
                (int) Math.ceil(
                        option.getBottom()
                                * image.rows()
                ),
                top + 1,
                image.rows()
        );

        return new Rect(
                left,
                top,
                rightExclusive - left,
                bottomExclusive - top
        );
    }

    private Rect calculateCoreRect(
            Rect region
    ) {
        int coreWidth = Math.max(
                1,
                (int) Math.round(
                        region.width
                                * config.getCoreWidthScale()
                )
        );

        int coreHeight = Math.max(
                1,
                (int) Math.round(
                        region.height
                                * config.getCoreHeightScale()
                )
        );

        if (region.width >= 3) {
            coreWidth = Math.min(
                    coreWidth,
                    region.width - 2
            );
        }

        if (region.height >= 3) {
            coreHeight = Math.min(
                    coreHeight,
                    region.height - 2
            );
        }

        return new Rect(
                region.x
                        + (region.width - coreWidth) / 2,
                region.y
                        + (region.height - coreHeight) / 2,
                coreWidth,
                coreHeight
        );
    }

    private Rect calculateBackgroundRect(
            Mat image,
            Rect region
    ) {
        int targetWidth = Math.max(
                region.width + 2,
                (int) Math.round(
                        region.width
                                * config
                                .getBackgroundWidthScale()
                )
        );

        int targetHeight = Math.max(
                region.height + 2,
                (int) Math.round(
                        region.height
                                * config
                                .getBackgroundHeightScale()
                )
        );

        int extraX =
                (targetWidth - region.width + 1) / 2;

        int extraY =
                (targetHeight - region.height + 1) / 2;

        int left = Math.max(
                0,
                region.x - extraX
        );

        int top = Math.max(
                0,
                region.y - extraY
        );

        int right = Math.min(
                image.cols(),
                region.x + region.width + extraX
        );

        int bottom = Math.min(
                image.rows(),
                region.y + region.height + extraY
        );

        return new Rect(
                left,
                top,
                right - left,
                bottom - top
        );
    }

    private void validateRegionSize(Rect region) {
        if (region.width
                < config.getMinimumRegionWidth()
                || region.height
                < config.getMinimumRegionHeight()) {

            throw new IllegalArgumentException(
                    "A região ficou pequena demais: "
                            + region.width
                            + "x"
                            + region.height
            );
        }
    }

    private double calculateExcludedMean(
            double completeMean,
            int completePixelCount,
            double excludedMean,
            int excludedPixelCount,
            int remainingPixelCount,
            double fallback
    ) {
        if (remainingPixelCount <= 0) {
            return fallback;
        }

        return (
                completeMean * completePixelCount
                        - excludedMean
                        * excludedPixelCount
        ) / remainingPixelCount;
    }

    private double divide(
            int numerator,
            int denominator
    ) {
        if (denominator <= 0) {
            return 0.0;
        }

        return numerator
                / (double) denominator;
    }

    private double clampIntensity(double value) {
        return Math.max(
                0.0,
                Math.min(255.0, value)
        );
    }

    private double clampRatio(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(value, maximum)
        );
    }
}
//package com.example.leitorgabaritoomr.vision.measurement;
//
//import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
//
//import org.opencv.core.Core;
//import org.opencv.core.Mat;
//import org.opencv.core.Rect;
//import org.opencv.core.Scalar;
//
///**
// * Mede uma alternativa em uma imagem normalizada em escala
// * de cinza.
// *
// * A Mat recebida deve possuir exatamente um canal.
// */
//public final class BubbleMeasurer {
//
//    private final BubbleMeasurementConfig config;
//
//    public BubbleMeasurer(
//            BubbleMeasurementConfig config
//    ) {
//        if (config == null) {
//            throw new IllegalArgumentException(
//                    "A configuração de medição é obrigatória."
//            );
//        }
//
//        this.config = config;
//    }
//
//    public BubbleMeasurement measure(
//            Mat normalizedGray,
//            OmrOptionDefinition option
//    ) {
//        validateInput(
//                normalizedGray,
//                option
//        );
//
//        Rect regionRect =
//                calculateRegionRect(
//                        normalizedGray,
//                        option
//                );
//
//        validateRegionSize(regionRect);
//
//        Rect coreRect =
//                calculateCoreRect(regionRect);
//
//        Mat region = null;
//        Mat core = null;
//
//        Mat regionDarkMask = new Mat();
//        Mat coreDarkMask = new Mat();
//
//        try {
//            region =
//                    normalizedGray.submat(regionRect);
//
//            Rect relativeCoreRect =
//                    new Rect(
//                            coreRect.x - regionRect.x,
//                            coreRect.y - regionRect.y,
//                            coreRect.width,
//                            coreRect.height
//                    );
//
//            core =
//                    region.submat(relativeCoreRect);
//
//            double regionMean =
//                    Core.mean(region).val[0];
//
//            double coreMean =
//                    Core.mean(core).val[0];
//
//            Core.compare(
//                    region,
//                    new Scalar(
//                            config.getDarkPixelThreshold()
//                    ),
//                    regionDarkMask,
//                    Core.CMP_LT
//            );
//
//            Core.compare(
//                    core,
//                    new Scalar(
//                            config.getDarkPixelThreshold()
//                    ),
//                    coreDarkMask,
//                    Core.CMP_LT
//            );
//
//            int regionPixelCount =
//                    region.rows() * region.cols();
//
//            int corePixelCount =
//                    core.rows() * core.cols();
//
//            int borderPixelCount =
//                    regionPixelCount - corePixelCount;
//
//            int regionDarkPixels =
//                    Core.countNonZero(regionDarkMask);
//
//            int coreDarkPixels =
//                    Core.countNonZero(coreDarkMask);
//
//            int borderDarkPixels =
//                    Math.max(
//                            0,
//                            regionDarkPixels
//                                    - coreDarkPixels
//                    );
//
//            double regionDarkRatio =
//                    divide(
//                            regionDarkPixels,
//                            regionPixelCount
//                    );
//
//            double coreDarkRatio =
//                    divide(
//                            coreDarkPixels,
//                            corePixelCount
//                    );
//
//            double borderDarkRatio =
//                    divide(
//                            borderDarkPixels,
//                            borderPixelCount
//                    );
//
//            double borderMean =
//                    calculateBorderMean(
//                            regionMean,
//                            regionPixelCount,
//                            coreMean,
//                            corePixelCount,
//                            borderPixelCount
//                    );
//
//            return new BubbleMeasurement(
//                    option,
//                    regionRect.x,
//                    regionRect.y,
//                    regionRect.width,
//                    regionRect.height,
//                    clampIntensity(regionMean),
//                    clampIntensity(coreMean),
//                    clampIntensity(borderMean),
//                    clampRatio(regionDarkRatio),
//                    clampRatio(coreDarkRatio),
//                    clampRatio(borderDarkRatio)
//            );
//
//        } finally {
//            if (core != null) {
//                core.release();
//            }
//
//            if (region != null) {
//                region.release();
//            }
//
//            regionDarkMask.release();
//            coreDarkMask.release();
//        }
//    }
//
//    private void validateInput(
//            Mat normalizedGray,
//            OmrOptionDefinition option
//    ) {
//        if (normalizedGray == null
//                || normalizedGray.empty()) {
//
//            throw new IllegalArgumentException(
//                    "A imagem normalizada está vazia."
//            );
//        }
//
//        if (normalizedGray.channels() != 1) {
//            throw new IllegalArgumentException(
//                    "BubbleMeasurer exige uma imagem"
//                            + " em escala de cinza."
//            );
//        }
//
//        if (option == null) {
//            throw new IllegalArgumentException(
//                    "A alternativa é obrigatória."
//            );
//        }
//    }
//
//    private Rect calculateRegionRect(
//            Mat image,
//            OmrOptionDefinition option
//    ) {
//        int left = clamp(
//                (int) Math.floor(
//                        option.getLeft()
//                                * image.cols()
//                ),
//                0,
//                image.cols() - 1
//        );
//
//        int top = clamp(
//                (int) Math.floor(
//                        option.getTop()
//                                * image.rows()
//                ),
//                0,
//                image.rows() - 1
//        );
//
//        int rightExclusive = clamp(
//                (int) Math.ceil(
//                        option.getRight()
//                                * image.cols()
//                ),
//                left + 1,
//                image.cols()
//        );
//
//        int bottomExclusive = clamp(
//                (int) Math.ceil(
//                        option.getBottom()
//                                * image.rows()
//                ),
//                top + 1,
//                image.rows()
//        );
//
//        return new Rect(
//                left,
//                top,
//                rightExclusive - left,
//                bottomExclusive - top
//        );
//    }
//
//    private Rect calculateCoreRect(
//            Rect region
//    ) {
//        int coreWidth = Math.max(
//                1,
//                (int) Math.round(
//                        region.width
//                                * config.getCoreWidthScale()
//                )
//        );
//
//        int coreHeight = Math.max(
//                1,
//                (int) Math.round(
//                        region.height
//                                * config.getCoreHeightScale()
//                )
//        );
//
//        /*
//         * Mantém pelo menos um pixel de borda quando a região
//         * possui tamanho suficiente.
//         */
//        if (region.width >= 3) {
//            coreWidth = Math.min(
//                    coreWidth,
//                    region.width - 2
//            );
//        }
//
//        if (region.height >= 3) {
//            coreHeight = Math.min(
//                    coreHeight,
//                    region.height - 2
//            );
//        }
//
//        int coreLeft =
//                region.x
//                        + (region.width - coreWidth) / 2;
//
//        int coreTop =
//                region.y
//                        + (region.height - coreHeight) / 2;
//
//        return new Rect(
//                coreLeft,
//                coreTop,
//                coreWidth,
//                coreHeight
//        );
//    }
//
//    private void validateRegionSize(Rect region) {
//        if (region.width
//                < config.getMinimumRegionWidth()
//                || region.height
//                < config.getMinimumRegionHeight()) {
//
//            throw new IllegalArgumentException(
//                    "A região da alternativa ficou pequena demais: "
//                            + region.width
//                            + "x"
//                            + region.height
//            );
//        }
//    }
//
//    private double calculateBorderMean(
//            double regionMean,
//            int regionPixelCount,
//            double coreMean,
//            int corePixelCount,
//            int borderPixelCount
//    ) {
//        if (borderPixelCount <= 0) {
//            return regionMean;
//        }
//
//        double regionIntensitySum =
//                regionMean * regionPixelCount;
//
//        double coreIntensitySum =
//                coreMean * corePixelCount;
//
//        return (
//                regionIntensitySum
//                        - coreIntensitySum
//        ) / borderPixelCount;
//    }
//
//    private double divide(
//            int numerator,
//            int denominator
//    ) {
//        if (denominator <= 0) {
//            return 0.0;
//        }
//
//        return numerator
//                / (double) denominator;
//    }
//
//    private double clampIntensity(double value) {
//        return Math.max(
//                0.0,
//                Math.min(255.0, value)
//        );
//    }
//
//    private double clampRatio(double value) {
//        return Math.max(
//                0.0,
//                Math.min(1.0, value)
//        );
//    }
//
//    private int clamp(
//            int value,
//            int minimum,
//            int maximum
//    ) {
//        return Math.max(
//                minimum,
//                Math.min(value, maximum)
//        );
//    }
//}