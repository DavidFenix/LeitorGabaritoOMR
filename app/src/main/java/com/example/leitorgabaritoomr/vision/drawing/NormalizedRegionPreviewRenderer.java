package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizationResult;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Monta a visualização da região normalizada dentro de um canvas
 * com as mesmas dimensões do frame da câmera.
 *
 * Isso evita alterar as dimensões da Mat pertencente à câmera.
 */
public final class NormalizedRegionPreviewRenderer {

    private static final Scalar BACKGROUND_COLOR =
            new Scalar(35, 35, 35, 255);

    private static final Scalar MESSAGE_COLOR =
            new Scalar(255, 255, 255, 255);

    private static final int MARGIN = 30;

    public Mat render(
            OmrRegionNormalizationResult result,
            int canvasWidth,
            int canvasHeight
    ) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new IllegalArgumentException(
                    "As dimensões do canvas devem ser positivas."
            );
        }

        Mat canvas = new Mat(
                canvasHeight,
                canvasWidth,
                CvType.CV_8UC4,
                BACKGROUND_COLOR
        );

        if (result == null
                || !result.isSuccess()
                || result.getNormalizedRegion() == null
                || result.getNormalizedRegion().empty()) {

            drawWaitingMessage(canvas);

            return canvas;
        }

        Mat normalizedRegion =
                result.getNormalizedRegion();

        int availableWidth =
                Math.max(1, canvasWidth - MARGIN * 2);

        int availableHeight =
                Math.max(1, canvasHeight - MARGIN * 2);

        double horizontalScale =
                availableWidth
                        / (double) normalizedRegion.cols();

        double verticalScale =
                availableHeight
                        / (double) normalizedRegion.rows();

        double scale = Math.min(
                horizontalScale,
                verticalScale
        );

        int previewWidth = Math.max(
                1,
                (int) Math.round(
                        normalizedRegion.cols() * scale
                )
        );

        int previewHeight = Math.max(
                1,
                (int) Math.round(
                        normalizedRegion.rows() * scale
                )
        );

        int left =
                (canvasWidth - previewWidth) / 2;

        int top =
                (canvasHeight - previewHeight) / 2;

        Mat resizedRegion = new Mat();
        Mat destinationRoi = null;

        try {
            Imgproc.resize(
                    normalizedRegion,
                    resizedRegion,
                    new Size(
                            previewWidth,
                            previewHeight
                    ),
                    0.0,
                    0.0,
                    Imgproc.INTER_LINEAR
            );

            destinationRoi = canvas.submat(
                    new Rect(
                            left,
                            top,
                            previewWidth,
                            previewHeight
                    )
            );

            copyAsRgba(
                    resizedRegion,
                    destinationRoi
            );

        } finally {
            resizedRegion.release();

            if (destinationRoi != null) {
                destinationRoi.release();
            }
        }

        return canvas;
    }

    private void copyAsRgba(
            Mat source,
            Mat destination
    ) {
        if (source.channels() == 4) {
            source.copyTo(destination);

        } else if (source.channels() == 3) {
            Imgproc.cvtColor(
                    source,
                    destination,
                    Imgproc.COLOR_RGB2RGBA
            );

        } else if (source.channels() == 1) {
            Imgproc.cvtColor(
                    source,
                    destination,
                    Imgproc.COLOR_GRAY2RGBA
            );

        } else {
            throw new IllegalArgumentException(
                    "Quantidade de canais não suportada: "
                            + source.channels()
            );
        }
    }

    private void drawWaitingMessage(Mat canvas) {
        Imgproc.putText(
                canvas,
                "Aguardando quatro marcadores estaveis...",
                new Point(40, canvas.rows() / 2.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.8,
                MESSAGE_COLOR,
                2
        );
    }
}