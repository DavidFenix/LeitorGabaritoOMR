package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Exibe exatamente as regiões e os pixels usados na medição.
 *
 * Ciano:
 * limite externo da região usada para estimar o fundo local.
 *
 * Amarelo:
 * região completa da bolha.
 *
 * Branco:
 * núcleo usado na análise de preenchimento.
 *
 * Vermelho:
 * pixels do núcleo efetivamente classificados como
 * localmente escuros pelo BubbleMeasurer.
 *
 * Nenhuma coordenada ou máscara é recalculada aqui.
 */
public final class BubbleMeasurementOverlayRenderer {

    private static final Scalar BACKGROUND_REGION_COLOR =
            new Scalar(
                    0,
                    220,
                    255,
                    255
            );

    private static final Scalar BUBBLE_REGION_COLOR =
            new Scalar(
                    255,
                    255,
                    0,
                    255
            );

    private static final Scalar CORE_REGION_COLOR =
            new Scalar(
                    255,
                    255,
                    255,
                    255
            );

    private static final Scalar LOCALLY_DARK_PIXEL_COLOR =
            new Scalar(
                    255,
                    0,
                    0,
                    255
            );

    /*
     * Quanto da imagem original permanece visível nos pixels
     * classificados como escuros.
     */
    private static final double ORIGINAL_IMAGE_WEIGHT =
            0.35;

    private static final double MASK_COLOR_WEIGHT =
            0.65;

    public BubbleMeasurementOverlayRenderer() {
    }

    public void draw(
            Mat normalizedRegion,
            OmrSheetMeasurementResult result
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || result == null) {

            return;
        }

        Mat combinedDarkMask =
                createCombinedDarkMask(
                        normalizedRegion,
                        result
                );

        try {
            applyDarkPixelTint(
                    normalizedRegion,
                    combinedDarkMask
            );

            /*
             * Os retângulos são desenhados depois da máscara
             * para permanecerem visíveis.
             */
            for (BubbleMeasurement measurement
                    : result.getMeasurements()) {

                drawMeasurementRegions(
                        normalizedRegion,
                        measurement
                );
            }

        } finally {
            combinedDarkMask.release();
        }
    }

    /**
     * Monta uma máscara única do tamanho da folha normalizada.
     *
     * Os dados copiados vêm diretamente das máscaras armazenadas
     * nos BubbleMeasurement. Não existe nova limiarização aqui.
     */
    private Mat createCombinedDarkMask(
            Mat image,
            OmrSheetMeasurementResult result
    ) {
        Mat combinedMask =
                Mat.zeros(
                        image.rows(),
                        image.cols(),
                        CvType.CV_8UC1
                );

        for (BubbleMeasurement measurement
                : result.getMeasurements()) {

            copyMeasurementMask(
                    combinedMask,
                    measurement
            );
        }

        return combinedMask;
    }

    private void copyMeasurementMask(
            Mat combinedMask,
            BubbleMeasurement measurement
    ) {
        PixelRectangle core =
                measurement.getCoreRegion();

        if (!isInsideImage(
                core,
                combinedMask.cols(),
                combinedMask.rows()
        )) {
            return;
        }

        byte[] localMask =
                measurement
                        .copyLocallyDarkCoreMask();

        Mat targetRegion = null;

        try {
            targetRegion =
                    combinedMask.submat(
                            new Rect(
                                    core.getLeft(),
                                    core.getTop(),
                                    core.getWidth(),
                                    core.getHeight()
                            )
                    );

            int copiedValues =
                    targetRegion.put(
                            0,
                            0,
                            localMask
                    );

            if (copiedValues <= 0) {
                throw new IllegalStateException(
                        "Não foi possível desenhar a máscara de "
                                + measurement
                                .getOption()
                                .getId()
                                + "."
                );
            }

        } finally {
            if (targetRegion != null) {
                targetRegion.release();
            }
        }
    }

    /**
     * Colore somente os pixels que já haviam sido classificados
     * como escuros durante a medição.
     */
    private void applyDarkPixelTint(
            Mat image,
            Mat combinedMask
    ) {
        if (Core.countNonZero(combinedMask)
                <= 0) {

            return;
        }

        Mat colorLayer = null;
        Mat blendedImage = new Mat();

        try {
            colorLayer =
                    new Mat(
                            image.size(),
                            image.type(),
                            LOCALLY_DARK_PIXEL_COLOR
                    );

            Core.addWeighted(
                    image,
                    ORIGINAL_IMAGE_WEIGHT,
                    colorLayer,
                    MASK_COLOR_WEIGHT,
                    0.0,
                    blendedImage
            );

            blendedImage.copyTo(
                    image,
                    combinedMask
            );

        } finally {
            blendedImage.release();

            if (colorLayer != null) {
                colorLayer.release();
            }
        }
    }

    private void drawMeasurementRegions(
            Mat image,
            BubbleMeasurement measurement
    ) {
        /*
         * Região mais externa: fundo local.
         */
        drawRectangle(
                image,
                measurement.getBackgroundRegion(),
                BACKGROUND_REGION_COLOR,
                1
        );

        /*
         * Região completa da bolha.
         *
         * Este é o retângulo que observaremos para verificar
         * a tangência com a bolha impressa.
         */
        drawRectangle(
                image,
                measurement.getBubbleRegion(),
                BUBBLE_REGION_COLOR,
                2
        );

        /*
         * Núcleo que participa da análise principal.
         */
        drawRectangle(
                image,
                measurement.getCoreRegion(),
                CORE_REGION_COLOR,
                1
        );
    }

    private void drawRectangle(
            Mat image,
            PixelRectangle rectangle,
            Scalar color,
            int thickness
    ) {
        if (!isInsideImage(
                rectangle,
                image.cols(),
                image.rows()
        )) {
            return;
        }

        Imgproc.rectangle(
                image,

                new Point(
                        rectangle.getLeft(),
                        rectangle.getTop()
                ),

                new Point(
                        rectangle.getRightInclusive(),
                        rectangle.getBottomInclusive()
                ),

                color,
                thickness
        );
    }

    private boolean isInsideImage(
            PixelRectangle rectangle,
            int imageWidth,
            int imageHeight
    ) {
        return rectangle != null
                && rectangle.getLeft() >= 0
                && rectangle.getTop() >= 0
                && rectangle.getRightExclusive()
                <= imageWidth
                && rectangle.getBottomExclusive()
                <= imageHeight;
    }
}
//package com.example.leitorgabaritoomr.vision.drawing;
//
//import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
//import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;
//import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorer;
//import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorerConfig;
//
//import org.opencv.core.Mat;
//import org.opencv.core.Point;
//import org.opencv.core.Scalar;
//import org.opencv.imgproc.Imgproc;
//
///**
// * Representa visualmente a evidência absoluta de preenchimento.
// *
// * Verde    = pouca evidência
// * Amarelo  = evidência intermediária
// * Vermelho = evidência forte
// */
//public final class BubbleMeasurementOverlayRenderer {
//
//    private final BubbleEvidenceScorer evidenceScorer;
//
//    /**
//     * Mantém compatibilidade com a construção atual feita
//     * pelo MarkerFrameProcessor.
//     */
//    public BubbleMeasurementOverlayRenderer() {
//        this(
//                new BubbleEvidenceScorer(
//                        BubbleEvidenceScorerConfig
//                                .developmentDefaults()
//                )
//        );
//    }
//
//    public BubbleMeasurementOverlayRenderer(
//            BubbleEvidenceScorer evidenceScorer
//    ) {
//        if (evidenceScorer == null) {
//            throw new IllegalArgumentException(
//                    "BubbleEvidenceScorer é obrigatório."
//            );
//        }
//
//        this.evidenceScorer = evidenceScorer;
//    }
//
//    public void draw(
//            Mat normalizedRegion,
//            OmrSheetMeasurementResult result
//    ) {
//        if (normalizedRegion == null
//                || normalizedRegion.empty()
//                || result == null) {
//
//            return;
//        }
//
//        for (BubbleMeasurement measurement
//                : result.getMeasurements()) {
//
//            drawMeasurement(
//                    normalizedRegion,
//                    measurement
//            );
//        }
//    }
//
//    private void drawMeasurement(
//            Mat image,
//            BubbleMeasurement measurement
//    ) {
//        double evidenceScore =
//                evidenceScorer.score(
//                        measurement
//                );
//
//        Scalar color =
//                heatColor(evidenceScore);
//
//        int left =
//                measurement.getRegionLeft();
//
//        int top =
//                measurement.getRegionTop();
//
//        int right =
//                left
//                        + measurement.getRegionWidth()
//                        - 1;
//
//        int bottom =
//                top
//                        + measurement.getRegionHeight()
//                        - 1;
//
//        Imgproc.rectangle(
//                image,
//                new Point(left, top),
//                new Point(right, bottom),
//                color,
//                2
//        );
//
//        int centerX =
//                left
//                        + measurement.getRegionWidth()
//                        / 2;
//
//        int centerY =
//                top
//                        + measurement.getRegionHeight()
//                        / 2;
//
//        Imgproc.circle(
//                image,
//                new Point(centerX, centerY),
//                3,
//                color,
//                -1
//        );
//    }
//
//    private Scalar heatColor(double value) {
//        if (value <= 0.5) {
//            double progress =
//                    value / 0.5;
//
//            return new Scalar(
//                    255.0 * progress,
//                    255.0,
//                    0.0,
//                    255.0
//            );
//        }
//
//        double progress =
//                (value - 0.5) / 0.5;
//
//        return new Scalar(
//                255.0,
//                255.0 * (1.0 - progress),
//                0.0,
//                255.0
//        );
//    }
//}
////package com.example.leitorgabaritoomr.vision.drawing;
////
////import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
////import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;
////
////import org.opencv.core.Mat;
////import org.opencv.core.Point;
////import org.opencv.core.Scalar;
////import org.opencv.imgproc.Imgproc;
////
/////**
//// * Representa visualmente a evidência local de preenchimento.
//// *
//// * A iluminação de cada alternativa é comparada com sua própria
//// * vizinhança.
//// *
//// * Verde    = pouca evidência
//// * Amarelo  = evidência intermediária
//// * Vermelho = forte evidência
//// *
//// * Ainda não representa classificação definitiva.
//// */
////public final class BubbleMeasurementOverlayRenderer {
////
////    public void draw(
////            Mat normalizedRegion,
////            OmrSheetMeasurementResult result
////    ) {
////        if (normalizedRegion == null
////                || normalizedRegion.empty()
////                || result == null) {
////
////            return;
////        }
////
////        for (BubbleMeasurement measurement
////                : result.getMeasurements()) {
////
////            drawMeasurement(
////                    normalizedRegion,
////                    measurement
////            );
////        }
////    }
////
////    private void drawMeasurement(
////            Mat image,
////            BubbleMeasurement measurement
////    ) {
////        double evidenceScore =
////                calculateEvidenceScore(
////                        measurement
////                );
////
////        Scalar color =
////                heatColor(evidenceScore);
////
////        int left =
////                measurement.getRegionLeft();
////
////        int top =
////                measurement.getRegionTop();
////
////        int right =
////                left
////                        + measurement.getRegionWidth()
////                        - 1;
////
////        int bottom =
////                top
////                        + measurement.getRegionHeight()
////                        - 1;
////
////        Imgproc.rectangle(
////                image,
////                new Point(left, top),
////                new Point(right, bottom),
////                color,
////                2
////        );
////
////        int centerX =
////                left
////                        + measurement.getRegionWidth()
////                        / 2;
////
////        int centerY =
////                top
////                        + measurement.getRegionHeight()
////                        / 2;
////
////        Imgproc.circle(
////                image,
////                new Point(centerX, centerY),
////                3,
////                color,
////                -1
////        );
////    }
////
////    /**
////     * Combina duas medições independentes:
////     *
////     * 1. contraste médio entre o núcleo e o fundo local;
////     * 2. quantidade de pixels individualmente mais escuros
////     *    que esse fundo.
////     */
////    private double calculateEvidenceScore(
////            BubbleMeasurement measurement
////    ) {
////        double positiveLocalContrast =
////                Math.max(
////                        0.0,
////                        measurement
////                                .getLocalContrastScore()
////                );
////
////        /*
////         * Um contraste local próximo de 0.55 já representa
////         * evidência muito forte nesta visualização.
////         */
////        double normalizedContrast =
////                clamp01(
////                        positiveLocalContrast / 0.55
////                );
////
////        double locallyDarkRatio =
////                clamp01(
////                        measurement
////                                .getCoreLocallyDarkPixelRatio()
////                );
////
////        /*
////         * A razão de pixels recebe peso um pouco maior por
////         * ser menos sensível a um pequeno ponto muito escuro.
////         */
////        double score =
////                0.45 * normalizedContrast
////                        + 0.55 * locallyDarkRatio;
////
////        return clamp01(score);
////    }
////
////    private Scalar heatColor(double value) {
////        /*
////         * 0.0 até 0.5:
////         *
////         * verde -> amarelo
////         */
////        if (value <= 0.5) {
////            double progress =
////                    value / 0.5;
////
////            return new Scalar(
////                    255.0 * progress,
////                    255.0,
////                    0.0,
////                    255.0
////            );
////        }
////
////        /*
////         * 0.5 até 1.0:
////         *
////         * amarelo -> vermelho
////         */
////        double progress =
////                (value - 0.5) / 0.5;
////
////        return new Scalar(
////                255.0,
////                255.0 * (1.0 - progress),
////                0.0,
////                255.0
////        );
////    }
////
////    private double clamp01(double value) {
////        return Math.max(
////                0.0,
////                Math.min(1.0, value)
////        );
////    }
////}
////package com.example.leitorgabaritoomr.vision.drawing;
////
////import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
////import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;
////
////import org.opencv.core.Mat;
////import org.opencv.core.Point;
////import org.opencv.core.Scalar;
////import org.opencv.imgproc.Imgproc;
////
/////**
//// * Representa visualmente a proporção de pixels escuros
//// * encontrada no núcleo de cada alternativa.
//// *
//// * Verde   = núcleo claro
//// * Amarelo = escuridão intermediária
//// * Vermelho = núcleo muito escuro
//// *
//// * As cores representam medição, não classificação definitiva.
//// */
////public final class BubbleMeasurementOverlayRenderer {
////
////    public void draw(
////            Mat normalizedRegion,
////            OmrSheetMeasurementResult result
////    ) {
////        if (normalizedRegion == null
////                || normalizedRegion.empty()
////                || result == null) {
////
////            return;
////        }
////
////        for (BubbleMeasurement measurement
////                : result.getMeasurements()) {
////
////            drawMeasurement(
////                    normalizedRegion,
////                    measurement
////            );
////        }
////    }
////
////    private void drawMeasurement(
////            Mat image,
////            BubbleMeasurement measurement
////    ) {
////        double darkness =
////                clamp01(
////                        measurement
////                                .getCoreDarkPixelRatio()
////                );
////
////        Scalar color =
////                heatColor(darkness);
////
////        int left =
////                measurement.getRegionLeft();
////
////        int top =
////                measurement.getRegionTop();
////
////        int right =
////                left
////                        + measurement.getRegionWidth()
////                        - 1;
////
////        int bottom =
////                top
////                        + measurement.getRegionHeight()
////                        - 1;
////
////        Imgproc.rectangle(
////                image,
////                new Point(left, top),
////                new Point(right, bottom),
////                color,
////                2
////        );
////
////        int centerX =
////                left
////                        + measurement.getRegionWidth()
////                        / 2;
////
////        int centerY =
////                top
////                        + measurement.getRegionHeight()
////                        / 2;
////
////        Imgproc.circle(
////                image,
////                new Point(centerX, centerY),
////                3,
////                color,
////                -1
////        );
////    }
////
////    private Scalar heatColor(double value) {
////        /*
////         * Primeira metade:
////         *
////         * verde -> amarelo
////         */
////        if (value <= 0.5) {
////            double progress =
////                    value / 0.5;
////
////            double red =
////                    255.0 * progress;
////
////            return new Scalar(
////                    red,
////                    255.0,
////                    0.0,
////                    255.0
////            );
////        }
////
////        /*
////         * Segunda metade:
////         *
////         * amarelo -> vermelho
////         */
////        double progress =
////                (value - 0.5) / 0.5;
////
////        double green =
////                255.0 * (1.0 - progress);
////
////        return new Scalar(
////                255.0,
////                green,
////                0.0,
////                255.0
////        );
////    }
////
////    private double clamp01(double value) {
////        return Math.max(
////                0.0,
////                Math.min(1.0, value)
////        );
////    }
////}