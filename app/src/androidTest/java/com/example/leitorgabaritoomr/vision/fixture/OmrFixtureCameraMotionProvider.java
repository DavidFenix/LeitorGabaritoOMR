package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Gera uma sequencia deterministica de movimento leve de camera.
 *
 * A imagem original e clonada no construtor. Cada frame combina
 * translacao suave e pequena variacao de perspectiva, conservando
 * a mesma resolucao durante toda a sequencia.
 */
public final class OmrFixtureCameraMotionProvider
        implements OmrFixtureFrameProvider {

    private static final Scalar DEFAULT_BACKGROUND_RGBA =
            new Scalar(174.0, 174.0, 174.0, 255.0);

    private static final double DEFAULT_HORIZONTAL_MARGIN =
            0.030;

    private static final double DEFAULT_VERTICAL_MARGIN =
            0.050;

    private static final double DEFAULT_TRANSLATION_AMPLITUDE =
            0.004;

    private static final double DEFAULT_PERSPECTIVE_AMPLITUDE =
            0.003;

    private static final int DEFAULT_CYCLE_LENGTH = 12;

    private final Mat sourceRgba;
    private final Scalar backgroundRgba;

    private final double horizontalMarginRatio;
    private final double verticalMarginRatio;
    private final double translationAmplitudeRatio;
    private final double perspectiveAmplitudeRatio;
    private final int cycleLength;

    private boolean released;

    /**
     * Configuracao conservadora para simular uma folha segurada
     * por uma pessoa com leve movimento das maos.
     */
    public static OmrFixtureCameraMotionProvider gentle(
            Mat sourceRgba
    ) {
        return new OmrFixtureCameraMotionProvider(
                sourceRgba,
                DEFAULT_BACKGROUND_RGBA,
                DEFAULT_HORIZONTAL_MARGIN,
                DEFAULT_VERTICAL_MARGIN,
                DEFAULT_TRANSLATION_AMPLITUDE,
                DEFAULT_PERSPECTIVE_AMPLITUDE,
                DEFAULT_CYCLE_LENGTH
        );
    }

    public OmrFixtureCameraMotionProvider(
            Mat sourceRgba,
            Scalar backgroundRgba,
            double horizontalMarginRatio,
            double verticalMarginRatio,
            double translationAmplitudeRatio,
            double perspectiveAmplitudeRatio,
            int cycleLength
    ) {
        validateSource(sourceRgba);
        validateBackground(backgroundRgba);

        validateRatio(
                "horizontalMarginRatio",
                horizontalMarginRatio,
                false
        );

        validateRatio(
                "verticalMarginRatio",
                verticalMarginRatio,
                false
        );

        validateRatio(
                "translationAmplitudeRatio",
                translationAmplitudeRatio,
                true
        );

        validateRatio(
                "perspectiveAmplitudeRatio",
                perspectiveAmplitudeRatio,
                true
        );

        if (translationAmplitudeRatio
                + perspectiveAmplitudeRatio
                >= Math.min(
                horizontalMarginRatio,
                verticalMarginRatio
        )) {

            throw new IllegalArgumentException(
                    "As amplitudes devem caber dentro"
                            + " das margens da sequencia."
            );
        }

        if (cycleLength < 4) {
            throw new IllegalArgumentException(
                    "cycleLength deve ser pelo menos 4."
            );
        }

        this.sourceRgba = sourceRgba.clone();

        this.backgroundRgba =
                new Scalar(
                        backgroundRgba.val[0],
                        backgroundRgba.val[1],
                        backgroundRgba.val[2],
                        backgroundRgba.val[3]
                );

        this.horizontalMarginRatio =
                horizontalMarginRatio;

        this.verticalMarginRatio =
                verticalMarginRatio;

        this.translationAmplitudeRatio =
                translationAmplitudeRatio;

        this.perspectiveAmplitudeRatio =
                perspectiveAmplitudeRatio;

        this.cycleLength = cycleLength;
    }

    @Override
    public Mat createRgbaFrame(int frameIndex) {
        ensureAvailable();

        if (frameIndex < 0) {
            throw new IllegalArgumentException(
                    "frameIndex nao pode ser negativo."
            );
        }

        double width = sourceRgba.cols();
        double height = sourceRgba.rows();

        double phase =
                2.0
                        * Math.PI
                        * Math.floorMod(
                        frameIndex,
                        cycleLength
                )
                        / cycleLength;

        double translationX =
                width
                        * translationAmplitudeRatio
                        * Math.sin(phase);

        double translationY =
                height
                        * translationAmplitudeRatio
                        * Math.cos(phase);

        double perspectiveX =
                width
                        * perspectiveAmplitudeRatio
                        * Math.sin(
                        phase * 2.0
                                + Math.PI / 6.0
                );

        double perspectiveY =
                height
                        * perspectiveAmplitudeRatio
                        * Math.cos(
                        phase * 2.0
                                - Math.PI / 5.0
                );

        double left =
                width * horizontalMarginRatio;

        double right =
                width - 1.0 - left;

        double top =
                height * verticalMarginRatio;

        double bottom =
                height - 1.0 - top;

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

        MatOfPoint2f destinationCorners =
                new MatOfPoint2f(
                        new Point(
                                left
                                        + translationX
                                        - perspectiveX,
                                top
                                        + translationY
                                        + perspectiveY
                        ),
                        new Point(
                                right
                                        + translationX
                                        + perspectiveX,
                                top
                                        + translationY
                                        - perspectiveY
                        ),
                        new Point(
                                right
                                        + translationX
                                        - perspectiveX,
                                bottom
                                        + translationY
                                        + perspectiveY
                        ),
                        new Point(
                                left
                                        + translationX
                                        + perspectiveX,
                                bottom
                                        + translationY
                                        - perspectiveY
                        )
                );

        Mat transform = null;
        Mat result = new Mat();

        try {
            transform =
                    Imgproc.getPerspectiveTransform(
                            sourceCorners,
                            destinationCorners
                    );

            Imgproc.warpPerspective(
                    sourceRgba,
                    result,
                    transform,
                    new Size(width, height),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    backgroundRgba
            );

            return result;
        } catch (RuntimeException exception) {
            result.release();
            throw exception;
        } finally {
            if (transform != null) {
                transform.release();
            }

            sourceCorners.release();
            destinationCorners.release();
        }
    }

    public boolean isReleased() {
        return released;
    }

    @Override
    public void close() {
        if (released) {
            return;
        }

        sourceRgba.release();
        released = true;
    }

    private void ensureAvailable() {
        if (released) {
            throw new IllegalStateException(
                    "O provedor de movimento ja foi fechado."
            );
        }
    }

    private static void validateSource(Mat sourceRgba) {
        if (sourceRgba == null || sourceRgba.empty()) {
            throw new IllegalArgumentException(
                    "A imagem RGBA de origem e obrigatoria."
            );
        }

        if (sourceRgba.channels() != 4) {
            throw new IllegalArgumentException(
                    "A imagem de origem deve possuir quatro canais."
            );
        }
    }

    private static void validateBackground(
            Scalar backgroundRgba
    ) {
        if (backgroundRgba == null
                || backgroundRgba.val == null
                || backgroundRgba.val.length < 4) {

            throw new IllegalArgumentException(
                    "A cor RGBA de fundo e obrigatoria."
            );
        }
    }

    private static void validateRatio(
            String name,
            double value,
            boolean zeroAllowed
    ) {
        boolean invalidMinimum =
                zeroAllowed
                        ? value < 0.0
                        : value <= 0.0;

        if (!Double.isFinite(value)
                || invalidMinimum
                || value >= 0.25) {

            throw new IllegalArgumentException(
                    name
                            + " deve estar no intervalo valido."
            );
        }
    }
}
