package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;

/**
 * Decorador que simula variacoes fotometricas entre frames.
 *
 * Contraste, luminosidade e desfoque sao calculados para cada indice
 * sem modificar o provedor delegado. O resultado conserva dimensoes,
 * tipo CV_8U e quatro canais RGBA. O decorador assume a propriedade do
 * provedor delegado e o fecha junto com seus proprios recursos.
 */
public final class OmrFixturePhotometricVariationProvider
        implements OmrFixtureFrameProvider {

    private static final double IDENTITY_TOLERANCE = 1.0e-12;

    private static final int DEFAULT_EXPOSURE_CYCLE_LENGTH = 10;
    private static final double DEFAULT_CONTRAST_AMPLITUDE = 0.08;
    private static final double DEFAULT_BRIGHTNESS_AMPLITUDE = 24.0;

    private static final int DEFAULT_FOCUS_CYCLE_LENGTH = 6;

    private final OmrFixtureFrameProvider delegate;
    private final IntToDoubleFunction contrastFunction;
    private final IntToDoubleFunction brightnessFunction;
    private final IntUnaryOperator blurKernelSizeFunction;

    private boolean closed;

    /**
     * Simula a compensacao automatica de exposicao da camera com
     * oscilacoes suaves e desencontradas de contraste e luminosidade.
     */
    public static OmrFixturePhotometricVariationProvider
    automaticExposure(
            OmrFixtureFrameProvider delegate
    ) {
        return new OmrFixturePhotometricVariationProvider(
                delegate,
                frameIndex -> {
                    double phase = exposurePhase(frameIndex);

                    return 1.0
                            + DEFAULT_CONTRAST_AMPLITUDE
                            * Math.sin(phase);
                },
                frameIndex -> {
                    double phase = exposurePhase(frameIndex);

                    return DEFAULT_BRIGHTNESS_AMPLITUDE
                            * Math.cos(
                            phase + Math.PI / 4.0
                    );
                },
                frameIndex -> 1
        );
    }

    /**
     * Simula pequenas tentativas periodicas de foco. O ciclo alterna
     * imagem nitida, desfoque moderado, pico de desfoque e recuperacao.
     */
    public static OmrFixturePhotometricVariationProvider
    pulsingFocus(
            OmrFixtureFrameProvider delegate
    ) {
        return new OmrFixturePhotometricVariationProvider(
                delegate,
                frameIndex -> 1.0,
                frameIndex -> 0.0,
                OmrFixturePhotometricVariationProvider
                        ::focusKernelSize
        );
    }

    public OmrFixturePhotometricVariationProvider(
            OmrFixtureFrameProvider delegate,
            IntToDoubleFunction contrastFunction,
            IntToDoubleFunction brightnessFunction,
            IntUnaryOperator blurKernelSizeFunction
    ) {
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "O provedor delegado e obrigatorio."
            );
        }

        if (contrastFunction == null) {
            throw new IllegalArgumentException(
                    "A funcao de contraste e obrigatoria."
            );
        }

        if (brightnessFunction == null) {
            throw new IllegalArgumentException(
                    "A funcao de luminosidade e obrigatoria."
            );
        }

        if (blurKernelSizeFunction == null) {
            throw new IllegalArgumentException(
                    "A funcao de desfoque e obrigatoria."
            );
        }

        this.delegate = delegate;
        this.contrastFunction = contrastFunction;
        this.brightnessFunction = brightnessFunction;
        this.blurKernelSizeFunction =
                blurKernelSizeFunction;
    }

    @Override
    public Mat createRgbaFrame(int frameIndex) {
        ensureAvailable();

        if (frameIndex < 0) {
            throw new IllegalArgumentException(
                    "frameIndex nao pode ser negativo."
            );
        }

        Mat sourceFrame =
                delegate.createRgbaFrame(frameIndex);

        try {
            validateFrame(sourceFrame);

            double contrast =
                    contrastFunction
                            .applyAsDouble(frameIndex);

            double brightness =
                    brightnessFunction
                            .applyAsDouble(frameIndex);

            int blurKernelSize =
                    blurKernelSizeFunction
                            .applyAsInt(frameIndex);

            validateContrast(contrast);
            validateBrightness(brightness);
            validateBlurKernelSize(blurKernelSize);

            boolean identityContrast =
                    Math.abs(contrast - 1.0)
                            <= IDENTITY_TOLERANCE;

            boolean identityBrightness =
                    Math.abs(brightness)
                            <= IDENTITY_TOLERANCE;

            if (identityContrast
                    && identityBrightness
                    && blurKernelSize == 1) {

                return sourceFrame;
            }

            return applyVariation(
                    sourceFrame,
                    contrast,
                    brightness,
                    blurKernelSize
            );
        } catch (RuntimeException | Error exception) {
            if (sourceFrame != null) {
                sourceFrame.release();
            }

            throw exception;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        delegate.close();
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    private Mat applyVariation(
            Mat sourceFrame,
            double contrast,
            double brightness,
            int blurKernelSize
    ) {
        Mat result = new Mat();
        Mat opaqueAlpha = null;

        try {
            sourceFrame.convertTo(
                    result,
                    -1,
                    contrast,
                    brightness
            );

            opaqueAlpha =
                    new Mat(
                            result.rows(),
                            result.cols(),
                            CvType.CV_8UC1,
                            new Scalar(255.0)
                    );

            Core.insertChannel(
                    opaqueAlpha,
                    result,
                    3
            );

            if (blurKernelSize > 1) {
                Imgproc.GaussianBlur(
                        result,
                        result,
                        new Size(
                                blurKernelSize,
                                blurKernelSize
                        ),
                        0.0
                );
            }

            sourceFrame.release();
            return result;
        } catch (RuntimeException | Error exception) {
            result.release();
            throw exception;
        } finally {
            if (opaqueAlpha != null) {
                opaqueAlpha.release();
            }
        }
    }

    private void ensureAvailable() {
        if (closed) {
            throw new IllegalStateException(
                    "O provedor fotometrico ja foi fechado."
            );
        }
    }

    private static double exposurePhase(int frameIndex) {
        return 2.0
                * Math.PI
                * Math.floorMod(
                frameIndex,
                DEFAULT_EXPOSURE_CYCLE_LENGTH
        )
                / DEFAULT_EXPOSURE_CYCLE_LENGTH;
    }

    private static int focusKernelSize(int frameIndex) {
        int cyclePosition =
                Math.floorMod(
                        frameIndex,
                        DEFAULT_FOCUS_CYCLE_LENGTH
                );

        switch (cyclePosition) {
            case 2:
            case 4:
                return 3;

            case 3:
                return 5;

            default:
                return 1;
        }
    }

    private static void validateFrame(Mat frame) {
        if (frame == null || frame.empty()) {
            throw new IllegalArgumentException(
                    "O provedor delegado retornou frame vazio."
            );
        }

        if (frame.channels() != 4) {
            throw new IllegalArgumentException(
                    "O frame delegado deve possuir quatro canais."
            );
        }

        if (frame.depth() != CvType.CV_8U) {
            throw new IllegalArgumentException(
                    "O frame delegado deve possuir profundidade CV_8U."
            );
        }
    }

    private static void validateContrast(double contrast) {
        if (!Double.isFinite(contrast)
                || contrast < 0.10
                || contrast > 3.0) {

            throw new IllegalArgumentException(
                    "O contraste deve estar entre 0.10 e 3.0."
            );
        }
    }

    private static void validateBrightness(double brightness) {
        if (!Double.isFinite(brightness)
                || brightness < -255.0
                || brightness > 255.0) {

            throw new IllegalArgumentException(
                    "A luminosidade deve estar entre -255 e 255."
            );
        }
    }

    private static void validateBlurKernelSize(
            int blurKernelSize
    ) {
        if (blurKernelSize <= 0
                || blurKernelSize > 31
                || blurKernelSize % 2 == 0) {

            throw new IllegalArgumentException(
                    "O kernel de desfoque deve ser impar entre 1 e 31."
            );
        }
    }
}
