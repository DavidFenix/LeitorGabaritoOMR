package com.example.leitorgabaritoomr.vision.normalization;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Corrige a perspectiva da região delimitada pelos quatro marcadores.
 *
 * A ordem obrigatória dos pontos é:
 *
 * TL -> canto superior esquerdo
 * TR -> canto superior direito
 * BR -> canto inferior direito
 * BL -> canto inferior esquerdo
 */
public final class OmrRegionNormalizer {

    private final OmrRegionNormalizerConfig config;

    public OmrRegionNormalizer(
            OmrRegionNormalizerConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuração do normalizador não pode ser nula."
            );
        }

        this.config = config;
    }

    public OmrRegionNormalizationResult normalize(
            Mat sourceFrame,
            Point topLeft,
            Point topRight,
            Point bottomRight,
            Point bottomLeft
    ) {
        String validationError = validateInput(
                sourceFrame,
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
        );

        if (validationError != null) {
            return OmrRegionNormalizationResult.failure(validationError);
        }

        double topWidth = distance(topLeft, topRight);
        double bottomWidth = distance(bottomLeft, bottomRight);

        double leftHeight = distance(topLeft, bottomLeft);
        double rightHeight = distance(topRight, bottomRight);

        double measuredWidth = Math.max(topWidth, bottomWidth);
        double measuredHeight = Math.max(leftHeight, rightHeight);

        OutputSize outputSize = calculateOutputSize(
                measuredWidth,
                measuredHeight
        );

        if (outputSize == null) {
            return OmrRegionNormalizationResult.failure(
                    "Não foi possível determinar dimensões válidas para a região."
            );
        }

        MatOfPoint2f sourcePoints = new MatOfPoint2f(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
        );

        MatOfPoint2f destinationPoints = new MatOfPoint2f(
                new Point(0.0, 0.0),
                new Point(outputSize.width - 1.0, 0.0),
                new Point(
                        outputSize.width - 1.0,
                        outputSize.height - 1.0
                ),
                new Point(0.0, outputSize.height - 1.0)
        );

        Mat perspectiveTransform = new Mat();
        Mat normalizedRegion = new Mat();

        try {
            perspectiveTransform = Imgproc.getPerspectiveTransform(
                    sourcePoints,
                    destinationPoints
            );

            if (perspectiveTransform.empty()) {
                normalizedRegion.release();

                return OmrRegionNormalizationResult.failure(
                        "O OpenCV não conseguiu calcular a transformação de perspectiva."
                );
            }

            double determinant = Core.determinant(perspectiveTransform);

            if (!Double.isFinite(determinant)
                    || Math.abs(determinant) < 0.000000001) {

                normalizedRegion.release();

                return OmrRegionNormalizationResult.failure(
                        "A transformação de perspectiva ficou degenerada."
                );
            }

            Imgproc.warpPerspective(
                    sourceFrame,
                    normalizedRegion,
                    perspectiveTransform,
                    new Size(outputSize.width, outputSize.height),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    new Scalar(255, 255, 255, 255)
            );

            if (normalizedRegion.empty()) {
                normalizedRegion.release();

                return OmrRegionNormalizationResult.failure(
                        "A imagem normalizada ficou vazia."
                );
            }

            return OmrRegionNormalizationResult.success(
                    normalizedRegion,
                    outputSize.width,
                    outputSize.height
            );

        } catch (Exception exception) {
            normalizedRegion.release();

            return OmrRegionNormalizationResult.failure(
                    "Erro durante a normalização: "
                            + exception.getMessage()
            );

        } finally {
            sourcePoints.release();
            destinationPoints.release();
            perspectiveTransform.release();
        }
    }

    private String validateInput(
            Mat sourceFrame,
            Point topLeft,
            Point topRight,
            Point bottomRight,
            Point bottomLeft
    ) {
        if (sourceFrame == null || sourceFrame.empty()) {
            return "O frame de origem está nulo ou vazio.";
        }

        if (!isValidPoint(topLeft)) {
            return "O ponto TL é inválido.";
        }

        if (!isValidPoint(topRight)) {
            return "O ponto TR é inválido.";
        }

        if (!isValidPoint(bottomRight)) {
            return "O ponto BR é inválido.";
        }

        if (!isValidPoint(bottomLeft)) {
            return "O ponto BL é inválido.";
        }

        double polygonArea = Math.abs(
                polygonSignedArea(
                        topLeft,
                        topRight,
                        bottomRight,
                        bottomLeft
                )
        );

        if (!Double.isFinite(polygonArea) || polygonArea < 1.0) {
            return "Os quatro pontos formam uma região degenerada.";
        }

        return null;
    }

    private boolean isValidPoint(Point point) {
        return point != null
                && Double.isFinite(point.x)
                && Double.isFinite(point.y);
    }

    private double distance(Point first, Point second) {
        double deltaX = second.x - first.x;
        double deltaY = second.y - first.y;

        return Math.sqrt(
                deltaX * deltaX + deltaY * deltaY
        );
    }

    private double polygonSignedArea(
            Point topLeft,
            Point topRight,
            Point bottomRight,
            Point bottomLeft
    ) {
        Point[] points = {
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
        };

        double twiceArea = 0.0;

        for (int index = 0; index < points.length; index++) {
            Point current = points[index];
            Point next = points[(index + 1) % points.length];

            twiceArea += current.x * next.y;
            twiceArea -= current.y * next.x;
        }

        return twiceArea * 0.5;
    }

    private OutputSize calculateOutputSize(
            double measuredWidth,
            double measuredHeight
    ) {
        if (!Double.isFinite(measuredWidth)
                || !Double.isFinite(measuredHeight)
                || measuredWidth < 1.0
                || measuredHeight < 1.0) {

            return null;
        }

        double scale = 1.0;

        if (measuredWidth > config.getMaximumOutputWidth()) {
            scale = Math.min(
                    scale,
                    config.getMaximumOutputWidth() / measuredWidth
            );
        }

        if (measuredHeight > config.getMaximumOutputHeight()) {
            scale = Math.min(
                    scale,
                    config.getMaximumOutputHeight() / measuredHeight
            );
        }

        int outputWidth = (int) Math.round(measuredWidth * scale);
        int outputHeight = (int) Math.round(measuredHeight * scale);

        if (outputWidth < config.getMinimumOutputDimension()
                || outputHeight < config.getMinimumOutputDimension()) {

            return null;
        }

        outputWidth = Math.min(
                outputWidth,
                config.getMaximumOutputWidth()
        );

        outputHeight = Math.min(
                outputHeight,
                config.getMaximumOutputHeight()
        );

        return new OutputSize(outputWidth, outputHeight);
    }

    private static final class OutputSize {

        private final int width;
        private final int height;

        private OutputSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}