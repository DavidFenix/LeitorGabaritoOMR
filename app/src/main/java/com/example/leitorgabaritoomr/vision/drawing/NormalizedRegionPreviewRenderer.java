package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizationResult;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Monta a visualizacao da regiao normalizada dentro de um canvas
 * com as mesmas dimensoes do frame da camera.
 *
 * Isso evita alterar as dimensoes da Mat pertencente a camera.
 * Enquanto a normalizacao ainda nao estiver disponivel, uma copia
 * limpa do frame ao vivo pode ser usada como fundo da mensagem.
 */
public final class NormalizedRegionPreviewRenderer {

    private static final Scalar BACKGROUND_COLOR =
            new Scalar(35, 35, 35, 255);

    private static final Scalar MESSAGE_BACKGROUND_COLOR =
            new Scalar(0, 0, 0, 255);

    private static final Scalar MESSAGE_COLOR =
            new Scalar(255, 255, 255, 255);

    private static final String WAITING_MESSAGE =
            "Aguardando quatro marcadores estaveis...";

    private static final int MARGIN = 30;
    private static final int MESSAGE_HORIZONTAL_PADDING = 18;
    private static final int MESSAGE_VERTICAL_PADDING = 12;

    /**
     * Mantem compatibilidade com todos os chamadores anteriores.
     * Sem um frame de espera, o comportamento antigo e preservado.
     */
    public Mat render(
            OmrRegionNormalizationResult result,
            int canvasWidth,
            int canvasHeight
    ) {
        return render(
                result,
                null,
                canvasWidth,
                canvasHeight
        );
    }

    /**
     * Renderiza a regiao normalizada ou, enquanto ela nao existir,
     * mostra o frame limpo da camera com a mensagem de espera.
     *
     * waitingFrame e somente uma fonte visual. Ele nao e modificado
     * e nunca participa de deteccao, normalizacao ou medicao.
     */
    public Mat render(
            OmrRegionNormalizationResult result,
            Mat waitingFrame,
            int canvasWidth,
            int canvasHeight
    ) {
        validateCanvasDimensions(
                canvasWidth,
                canvasHeight
        );

        Mat canvas = new Mat(
                canvasHeight,
                canvasWidth,
                CvType.CV_8UC4,
                BACKGROUND_COLOR
        );

        if (!hasNormalizedRegion(result)) {
            copyWaitingFrameIfAvailable(
                    waitingFrame,
                    canvas
            );

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

    private boolean hasNormalizedRegion(
            OmrRegionNormalizationResult result
    ) {
        return result != null
                && result.isSuccess()
                && result.getNormalizedRegion() != null
                && !result.getNormalizedRegion().empty();
    }

    private void copyWaitingFrameIfAvailable(
            Mat waitingFrame,
            Mat canvas
    ) {
        if (waitingFrame == null
                || waitingFrame.empty()) {
            return;
        }

        if (waitingFrame.cols() == canvas.cols()
                && waitingFrame.rows() == canvas.rows()) {

            copyAsRgba(
                    waitingFrame,
                    canvas
            );

            return;
        }

        Mat resizedWaitingFrame = new Mat();

        try {
            Imgproc.resize(
                    waitingFrame,
                    resizedWaitingFrame,
                    canvas.size(),
                    0.0,
                    0.0,
                    Imgproc.INTER_LINEAR
            );

            copyAsRgba(
                    resizedWaitingFrame,
                    canvas
            );
        } finally {
            resizedWaitingFrame.release();
        }
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
                    "Quantidade de canais nao suportada: "
                            + source.channels()
            );
        }
    }

    private void drawWaitingMessage(Mat canvas) {
        int fontFace =
                Imgproc.FONT_HERSHEY_SIMPLEX;

        int thickness = 2;
        double fontScale = 0.80;
        int[] baseline = new int[1];

        Size textSize = Imgproc.getTextSize(
                WAITING_MESSAGE,
                fontFace,
                fontScale,
                thickness,
                baseline
        );

        int maximumTextWidth =
                Math.max(
                        1,
                        canvas.cols()
                                - MESSAGE_HORIZONTAL_PADDING * 4
                );

        if (textSize.width > maximumTextWidth) {
            fontScale *=
                    maximumTextWidth
                            / textSize.width;

            textSize = Imgproc.getTextSize(
                    WAITING_MESSAGE,
                    fontFace,
                    fontScale,
                    thickness,
                    baseline
            );
        }

        int textWidth =
                Math.max(
                        1,
                        (int) Math.ceil(textSize.width)
                );

        int textHeight =
                Math.max(
                        1,
                        (int) Math.ceil(textSize.height)
                );

        int left = Math.max(
                0,
                (canvas.cols()
                        - textWidth
                        - MESSAGE_HORIZONTAL_PADDING * 2)
                        / 2
        );

        int right = Math.min(
                canvas.cols(),
                left
                        + textWidth
                        + MESSAGE_HORIZONTAL_PADDING * 2
        );

        int bottom = Math.max(
                textHeight
                        + MESSAGE_VERTICAL_PADDING * 2
                        + baseline[0],
                canvas.rows() - 24
        );

        bottom = Math.min(
                canvas.rows(),
                bottom
        );

        int top = Math.max(
                0,
                bottom
                        - textHeight
                        - baseline[0]
                        - MESSAGE_VERTICAL_PADDING * 2
        );

        Rect messageRect = new Rect(
                left,
                top,
                Math.max(1, right - left),
                Math.max(1, bottom - top)
        );

        darkenRegion(
                canvas,
                messageRect
        );

        double textX =
                left + MESSAGE_HORIZONTAL_PADDING;

        double textY =
                bottom
                        - MESSAGE_VERTICAL_PADDING
                        - baseline[0];

        Imgproc.putText(
                canvas,
                WAITING_MESSAGE,
                new Point(textX, textY),
                fontFace,
                fontScale,
                MESSAGE_COLOR,
                thickness
        );
    }

    private void darkenRegion(
            Mat canvas,
            Rect region
    ) {
        Mat regionRoi = null;
        Mat darkLayer = null;

        try {
            regionRoi = canvas.submat(region);

            darkLayer = new Mat(
                    region.height,
                    region.width,
                    canvas.type(),
                    MESSAGE_BACKGROUND_COLOR
            );

            Core.addWeighted(
                    regionRoi,
                    0.45,
                    darkLayer,
                    0.55,
                    0.0,
                    regionRoi
            );
        } finally {
            if (darkLayer != null) {
                darkLayer.release();
            }

            if (regionRoi != null) {
                regionRoi.release();
            }
        }
    }

    private void validateCanvasDimensions(
            int canvasWidth,
            int canvasHeight
    ) {
        if (canvasWidth <= 0
                || canvasHeight <= 0) {

            throw new IllegalArgumentException(
                    "As dimensoes do canvas devem ser positivas."
            );
        }
    }
}
