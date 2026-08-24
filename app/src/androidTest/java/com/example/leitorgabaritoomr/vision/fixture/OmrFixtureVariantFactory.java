package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gera variacoes deterministicas de uma fixture OMR em memoria.
 *
 * A imagem original nunca e alterada. Todas as variantes conservam
 * as mesmas dimensoes e podem atravessar o mesmo pipeline usado pela
 * Activity, sem camera e sem arquivos PNG adicionais.
 */
public final class OmrFixtureVariantFactory {

    private static final Scalar PAPER_BACKGROUND_RGBA =
            new Scalar(174.0, 174.0, 174.0, 255.0);

    private OmrFixtureVariantFactory() {
        throw new AssertionError(
                "Esta classe nao deve ser instanciada."
        );
    }

    /**
     * Suite inicial conservadora. As transformacoes sao fortes o
     * bastante para revelar acoplamentos, mas ainda representam uma
     * folha perfeitamente fotografavel.
     */
    public static List<OmrFixtureVariant>
    createStandardSuite(Mat originalRgba) {
        validateOriginal(originalRgba);

        List<OmrFixtureVariant> variants =
                new ArrayList<>();

        try {
            variants.add(
                    variant(
                            "original",
                            "Imagem de referencia sem alteracao",
                            originalRgba.clone()
                    )
            );

            variants.add(
                    variant(
                            "brightness-darker",
                            "Luminosidade reduzida de forma uniforme",
                            adjustIntensity(
                                    originalRgba,
                                    0.84,
                                    -8.0
                            )
                    )
            );

            variants.add(
                    variant(
                            "brightness-brighter",
                            "Luminosidade aumentada de forma uniforme",
                            adjustIntensity(
                                    originalRgba,
                                    1.08,
                                    10.0
                            )
                    )
            );

            variants.add(
                    variant(
                            "blur-light",
                            "Desfoque gaussiano leve de camera",
                            lightBlur(originalRgba)
                    )
            );

            variants.add(
                    variant(
                            "resolution-75-percent",
                            "Reducao e recomposicao de resolucao",
                            reducedResolution(
                                    originalRgba,
                                    0.75
                            )
                    )
            );

            variants.add(
                    variant(
                            "scale-90-percent",
                            "Folha centralizada ocupando 90 por cento do frame",
                            centeredScale(
                                    originalRgba,
                                    0.90
                            )
                    )
            );

            variants.add(
                    variant(
                            "perspective-left",
                            "Perspectiva leve com lado esquerdo inclinado",
                            perspective(
                                    originalRgba,
                                    true
                            )
                    )
            );

            variants.add(
                    variant(
                            "perspective-right",
                            "Perspectiva leve com lado direito inclinado",
                            perspective(
                                    originalRgba,
                                    false
                            )
                    )
            );

            return Collections.unmodifiableList(
                    variants
            );
        } catch (RuntimeException exception) {
            releaseAll(variants);
            throw exception;
        }
    }

    public static void releaseAll(
            List<OmrFixtureVariant> variants
    ) {
        if (variants == null) {
            return;
        }

        for (OmrFixtureVariant variant : variants) {
            if (variant != null) {
                variant.release();
            }
        }
    }

    private static OmrFixtureVariant variant(
            String id,
            String description,
            Mat rgba
    ) {
        return new OmrFixtureVariant(
                id,
                description,
                rgba
        );
    }

    private static Mat adjustIntensity(
            Mat source,
            double alpha,
            double beta
    ) {
        Mat result = new Mat();

        source.convertTo(
                result,
                source.type(),
                alpha,
                beta
        );

        return result;
    }

    private static Mat lightBlur(Mat source) {
        Mat result = new Mat();

        Imgproc.GaussianBlur(
                source,
                result,
                new Size(3.0, 3.0),
                0.75,
                0.75,
                Core.BORDER_REPLICATE
        );

        return result;
    }

    private static Mat reducedResolution(
            Mat source,
            double scale
    ) {
        int reducedWidth = Math.max(
                1,
                (int) Math.round(
                        source.cols() * scale
                )
        );

        int reducedHeight = Math.max(
                1,
                (int) Math.round(
                        source.rows() * scale
                )
        );

        Mat reduced = new Mat();
        Mat restored = new Mat();

        try {
            Imgproc.resize(
                    source,
                    reduced,
                    new Size(
                            reducedWidth,
                            reducedHeight
                    ),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA
            );

            Imgproc.resize(
                    reduced,
                    restored,
                    source.size(),
                    0.0,
                    0.0,
                    Imgproc.INTER_LINEAR
            );

            return restored;
        } catch (RuntimeException exception) {
            restored.release();
            throw exception;
        } finally {
            reduced.release();
        }
    }

    private static Mat centeredScale(
            Mat source,
            double scale
    ) {
        int scaledWidth = Math.max(
                1,
                (int) Math.round(
                        source.cols() * scale
                )
        );

        int scaledHeight = Math.max(
                1,
                (int) Math.round(
                        source.rows() * scale
                )
        );

        int left =
                (source.cols() - scaledWidth) / 2;

        int top =
                (source.rows() - scaledHeight) / 2;

        Mat scaled = new Mat();

        Mat result = new Mat(
                source.rows(),
                source.cols(),
                source.type(),
                PAPER_BACKGROUND_RGBA
        );

        Mat destinationRegion = null;

        try {
            Imgproc.resize(
                    source,
                    scaled,
                    new Size(
                            scaledWidth,
                            scaledHeight
                    ),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA
            );

            destinationRegion =
                    result.submat(
                            new Rect(
                                    left,
                                    top,
                                    scaledWidth,
                                    scaledHeight
                            )
                    );

            scaled.copyTo(destinationRegion);

            return result;
        } catch (RuntimeException exception) {
            result.release();
            throw exception;
        } finally {
            if (destinationRegion != null) {
                destinationRegion.release();
            }

            scaled.release();
        }
    }

    private static Mat perspective(
            Mat source,
            boolean leanLeft
    ) {
        double width = source.cols();
        double height = source.rows();

        MatOfPoint2f sourceCorners =
                new MatOfPoint2f(
                        new Point(0.0, 0.0),
                        new Point(width - 1.0, 0.0),
                        new Point(
                                width - 1.0,
                                height - 1.0
                        ),
                        new Point(0.0, height - 1.0)
                );

        MatOfPoint2f destinationCorners;

        if (leanLeft) {
            destinationCorners =
                    new MatOfPoint2f(
                            point(width, height, 0.035, 0.035),
                            point(width, height, 0.955, 0.015),
                            point(width, height, 0.985, 0.970),
                            point(width, height, 0.015, 0.985)
                    );
        } else {
            destinationCorners =
                    new MatOfPoint2f(
                            point(width, height, 0.015, 0.015),
                            point(width, height, 0.965, 0.035),
                            point(width, height, 0.985, 0.985),
                            point(width, height, 0.040, 0.970)
                    );
        }

        Mat transform = null;
        Mat result = new Mat();

        try {
            transform =
                    Imgproc.getPerspectiveTransform(
                            sourceCorners,
                            destinationCorners
                    );

            Imgproc.warpPerspective(
                    source,
                    result,
                    transform,
                    source.size(),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    PAPER_BACKGROUND_RGBA
            );

            return result;
        } catch (RuntimeException exception) {
            result.release();
            throw exception;
        } finally {
            if (transform != null) {
                transform.release();
            }

            destinationCorners.release();
            sourceCorners.release();
        }
    }

    private static Point point(
            double width,
            double height,
            double normalizedX,
            double normalizedY
    ) {
        return new Point(
                (width - 1.0) * normalizedX,
                (height - 1.0) * normalizedY
        );
    }

    private static void validateOriginal(Mat originalRgba) {
        if (originalRgba == null
                || originalRgba.empty()) {

            throw new IllegalArgumentException(
                    "A fixture RGBA original e obrigatoria."
            );
        }

        if (originalRgba.channels() != 4) {
            throw new IllegalArgumentException(
                    "A fixture deve possuir quatro canais RGBA."
            );
        }

        if (originalRgba.cols() < 100
                || originalRgba.rows() < 100) {

            throw new IllegalArgumentException(
                    "A fixture e pequena demais para as variacoes."
            );
        }
    }
}
