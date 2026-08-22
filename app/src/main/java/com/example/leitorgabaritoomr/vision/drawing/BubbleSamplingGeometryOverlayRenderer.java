package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.geometry.PixelQuadrilateral;
import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.measurement.BubbleSamplingGeometry;
import com.example.leitorgabaritoomr.vision.measurement.BubbleSamplingGeometrySet;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

/**
 * Mostra no Laboratorio OMR as areas exatas de amostragem.
 *
 * Este renderizador nao reconstrói regioes a partir do layout e
 * nao usa retangulos visuais aproximados. Para formar as mascaras,
 * ele percorre os pixels das BubbleSamplingGeometry e chama a
 * mesma funcao classifyPixel() que sera usada pelo medidor.
 *
 * Cores:
 *
 * - azul: fundo local;
 * - amarelo: borda da bolha;
 * - magenta: nucleo da bolha.
 *
 * As cores recebem transparencia para preservar a imagem real da
 * folha abaixo delas. Os contornos usam os mesmos quatro cantos
 * armazenados nos poligonos de amostragem.
 */
public final class
BubbleSamplingGeometryOverlayRenderer {

    private static final Scalar BACKGROUND_COLOR =
            new Scalar(
                    0.0,
                    128.0,
                    255.0,
                    255.0
            );

    private static final Scalar BORDER_COLOR =
            new Scalar(
                    255.0,
                    255.0,
                    0.0,
                    255.0
            );

    private static final Scalar CORE_COLOR =
            new Scalar(
                    255.0,
                    0.0,
                    255.0,
                    255.0
            );

    private static final Scalar SUMMARY_COLOR =
            new Scalar(
                    0.0,
                    255.0,
                    0.0,
                    255.0
            );

    private static final Scalar FAILURE_COLOR =
            new Scalar(
                    255.0,
                    0.0,
                    0.0,
                    255.0
            );

    private static final double BACKGROUND_ALPHA =
            0.12;

    private static final double BORDER_ALPHA =
            0.24;

    private static final double CORE_ALPHA =
            0.20;

    public void draw(
            Mat normalizedRegion,
            BubbleSamplingGeometrySet geometrySet
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()) {

            return;
        }

        String validationError =
                validateInput(
                        normalizedRegion,
                        geometrySet
                );

        if (validationError != null) {
            drawFailure(
                    normalizedRegion,
                    validationError
            );

            return;
        }

        ZoneMasks masks = null;

        try {
            masks = createZoneMasks(
                    normalizedRegion,
                    geometrySet
            );

            applyTint(
                    normalizedRegion,
                    masks.backgroundMask,
                    BACKGROUND_COLOR,
                    BACKGROUND_ALPHA
            );

            applyTint(
                    normalizedRegion,
                    masks.borderMask,
                    BORDER_COLOR,
                    BORDER_ALPHA
            );

            applyTint(
                    normalizedRegion,
                    masks.coreMask,
                    CORE_COLOR,
                    CORE_ALPHA
            );

            drawExactOutlines(
                    normalizedRegion,
                    geometrySet
            );

            drawLegend(normalizedRegion);

            drawSummary(
                    normalizedRegion,
                    geometrySet,
                    masks
            );
        } finally {
            if (masks != null) {
                masks.release();
            }
        }
    }

    private String validateInput(
            Mat image,
            BubbleSamplingGeometrySet geometrySet
    ) {
        if (geometrySet == null) {
            return "geometrias de amostragem indisponiveis";
        }

        if (!geometrySet.isComplete()) {
            return "conjunto de amostragem incompleto";
        }

        if (image.cols() != geometrySet.getImageWidth()
                || image.rows()
                != geometrySet.getImageHeight()) {

            return "imagem e amostragem possuem tamanhos diferentes";
        }

        return null;
    }

    /**
     * Constrói as tres mascaras diretamente pela classificacao de
     * cada pixel. Nenhuma coordenada visual paralela e criada.
     */
    private ZoneMasks createZoneMasks(
            Mat image,
            BubbleSamplingGeometrySet geometrySet
    ) {
        int width = image.cols();
        int height = image.rows();

        long longPixelCount =
                (long) width * (long) height;

        if (longPixelCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "A imagem e grande demais para as mascaras"
                            + " do Laboratorio OMR."
            );
        }

        int pixelCount = (int) longPixelCount;

        byte[] backgroundPixels =
                new byte[pixelCount];

        byte[] borderPixels =
                new byte[pixelCount];

        byte[] corePixels =
                new byte[pixelCount];

        for (BubbleSamplingGeometry geometry
                : geometrySet.getGeometries()) {

            PixelRectangle bounds =
                    geometry.getSamplingBounds();

            int bottomExclusive =
                    bounds.getTop()
                            + bounds.getHeight();

            int rightExclusive =
                    bounds.getLeft()
                            + bounds.getWidth();

            for (int pixelY = bounds.getTop();
                 pixelY < bottomExclusive;
                 pixelY++) {

                int rowOffset = pixelY * width;

                for (int pixelX = bounds.getLeft();
                     pixelX < rightExclusive;
                     pixelX++) {

                    int pixelIndex =
                            rowOffset + pixelX;

                    BubbleSamplingGeometry.Zone zone =
                            geometry.classifyPixel(
                                    pixelX,
                                    pixelY
                            );

                    switch (zone) {
                        case LOCAL_BACKGROUND:
                            backgroundPixels[pixelIndex] =
                                    (byte) 0xFF;
                            break;

                        case BORDER:
                            borderPixels[pixelIndex] =
                                    (byte) 0xFF;
                            break;

                        case CORE:
                            corePixels[pixelIndex] =
                                    (byte) 0xFF;
                            break;

                        case OUTSIDE:
                        default:
                            break;
                    }
                }
            }
        }

        int backgroundPixelCount = 0;
        int borderPixelCount = 0;
        int corePixelCount = 0;

        /*
         * Em uma eventual sobreposicao entre alternativas, a zona
         * mais interna tem prioridade apenas na visualizacao final.
         * A classificacao original de cada geometria nao e alterada.
         */
        for (int pixelIndex = 0;
             pixelIndex < pixelCount;
             pixelIndex++) {

            if (corePixels[pixelIndex] != 0) {
                borderPixels[pixelIndex] = 0;
                backgroundPixels[pixelIndex] = 0;
                corePixelCount++;

                continue;
            }

            if (borderPixels[pixelIndex] != 0) {
                backgroundPixels[pixelIndex] = 0;
                borderPixelCount++;

                continue;
            }

            if (backgroundPixels[pixelIndex] != 0) {
                backgroundPixelCount++;
            }
        }

        Mat backgroundMask = null;
        Mat borderMask = null;
        Mat coreMask = null;

        try {
            backgroundMask = createMask(
                    height,
                    width,
                    backgroundPixels
            );

            borderMask = createMask(
                    height,
                    width,
                    borderPixels
            );

            coreMask = createMask(
                    height,
                    width,
                    corePixels
            );

            return new ZoneMasks(
                    backgroundMask,
                    borderMask,
                    coreMask,
                    backgroundPixelCount,
                    borderPixelCount,
                    corePixelCount
            );
        } catch (RuntimeException exception) {
            release(backgroundMask);
            release(borderMask);
            release(coreMask);

            throw exception;
        }
    }

    private Mat createMask(
            int rows,
            int columns,
            byte[] pixels
    ) {
        Mat mask = new Mat(
                rows,
                columns,
                CvType.CV_8UC1
        );

        int written = mask.put(
                0,
                0,
                pixels
        );

        if (written != pixels.length) {
            mask.release();

            throw new IllegalStateException(
                    "A mascara recebeu "
                            + written
                            + " de "
                            + pixels.length
                            + " pixels."
            );
        }

        return mask;
    }

    private void applyTint(
            Mat image,
            Mat mask,
            Scalar color,
            double alpha
    ) {
        Mat colorLayer = new Mat(
                image.size(),
                image.type(),
                color
        );

        Mat blended = new Mat();

        try {
            Core.addWeighted(
                    image,
                    1.0 - alpha,
                    colorLayer,
                    alpha,
                    0.0,
                    blended
            );

            blended.copyTo(image, mask);
        } finally {
            colorLayer.release();
            blended.release();
        }
    }

    private void drawExactOutlines(
            Mat image,
            BubbleSamplingGeometrySet geometrySet
    ) {
        for (BubbleSamplingGeometry geometry
                : geometrySet.getGeometries()) {

            drawPolygon(
                    image,
                    geometry.getBackgroundPolygon(),
                    BACKGROUND_COLOR,
                    1
            );

            drawPolygon(
                    image,
                    geometry.getBubblePolygon(),
                    BORDER_COLOR,
                    1
            );

            drawPolygon(
                    image,
                    geometry.getCorePolygon(),
                    CORE_COLOR,
                    1
            );
        }
    }

    private void drawPolygon(
            Mat image,
            PixelQuadrilateral polygon,
            Scalar color,
            int thickness
    ) {
        for (int cornerIndex = 0;
             cornerIndex < polygon.getCornerCount();
             cornerIndex++) {

            int nextCornerIndex =
                    (cornerIndex + 1)
                            % polygon.getCornerCount();

            Imgproc.line(
                    image,
                    pointAt(polygon, cornerIndex),
                    pointAt(polygon, nextCornerIndex),
                    color,
                    thickness
            );
        }
    }

    private Point pointAt(
            PixelQuadrilateral polygon,
            int cornerIndex
    ) {
        return new Point(
                polygon.getCornerX(cornerIndex),
                polygon.getCornerY(cornerIndex)
        );
    }

    private void drawLegend(Mat image) {
        Imgproc.putText(
                image,
                "azul=fundo | amarelo=borda | magenta=nucleo",
                new Point(12, 20),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.45,
                SUMMARY_COLOR,
                1
        );
    }

    private void drawSummary(
            Mat image,
            BubbleSamplingGeometrySet geometrySet,
            ZoneMasks masks
    ) {
        String text = String.format(
                Locale.US,
                "amostragem=%d | fundoPx=%d"
                        + " | bordaPx=%d | nucleoPx=%d"
                        + " | fundos cortados=%d",
                geometrySet.getGeometryCount(),
                masks.backgroundPixelCount,
                masks.borderPixelCount,
                masks.corePixelCount,
                geometrySet.getClippedBackgroundCount()
        );

        Imgproc.putText(
                image,
                text,
                new Point(
                        12,
                        Math.max(
                                24,
                                image.rows() - 16
                        )
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.45,
                SUMMARY_COLOR,
                1
        );
    }

    private void drawFailure(
            Mat image,
            String message
    ) {
        String safeMessage =
                message == null
                        || message.trim().isEmpty()
                        ? "amostragem indisponivel"
                        : message.trim();

        Imgproc.putText(
                image,
                "AMOSTRAGEM: " + safeMessage,
                new Point(12, 28),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.55,
                FAILURE_COLOR,
                2
        );
    }

    private void release(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }

    private static final class ZoneMasks {

        private final Mat backgroundMask;
        private final Mat borderMask;
        private final Mat coreMask;

        private final int backgroundPixelCount;
        private final int borderPixelCount;
        private final int corePixelCount;

        private ZoneMasks(
                Mat backgroundMask,
                Mat borderMask,
                Mat coreMask,
                int backgroundPixelCount,
                int borderPixelCount,
                int corePixelCount
        ) {
            this.backgroundMask = backgroundMask;
            this.borderMask = borderMask;
            this.coreMask = coreMask;
            this.backgroundPixelCount =
                    backgroundPixelCount;
            this.borderPixelCount =
                    borderPixelCount;
            this.corePixelCount =
                    corePixelCount;
        }

        private void release() {
            backgroundMask.release();
            borderMask.release();
            coreMask.release();
        }
    }
}
