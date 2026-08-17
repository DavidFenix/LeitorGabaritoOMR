package com.example.leitorgabaritoomr.vision.detector;

import com.example.leitorgabaritoomr.vision.model.DetectedMarker;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.model.MarkerType;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugSink;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;

import org.opencv.core.CvType;
import org.opencv.core.Scalar;

import java.util.Collections;
public final class SolidSquareMarkerDetector
        implements OmrMarkerDetector {

    private final SolidSquareDetectorConfig config;

    public SolidSquareMarkerDetector() {

        this(
                SolidSquareDetectorConfig
                        .developmentDefaults()
        );
    }

    public SolidSquareMarkerDetector(
            SolidSquareDetectorConfig config
    ) {

        if (config == null) {

            throw new IllegalArgumentException(
                    "A configuração do detector é obrigatória."
            );
        }

        this.config = config;
    }

    @Override
    public String getName() {
        return "solid-square";
    }

    @Override
    public MarkerDetectionResult detect(
            Mat grayFrame
    ) {

        return detect(
                grayFrame,
                VisionDebugSink.NONE
        );
    }

    @Override
    public MarkerDetectionResult detect(
            Mat grayFrame,
            VisionDebugSink debugSink
    ) {

        if (debugSink == null) {
            debugSink = VisionDebugSink.NONE;
        }
//    @Override
//    public MarkerDetectionResult detect(
//            Mat grayFrame
//    ) {

        if (grayFrame == null || grayFrame.empty()) {

            return new MarkerDetectionResult(
                    getName(),
                    new ArrayList<>(),
                    0,
                    0
            );
        }

        long inicio = System.nanoTime();

        Mat blurred = new Mat();
        Mat binary = new Mat();
        Mat hierarchy = new Mat();

        List<MatOfPoint> contours =
                new ArrayList<>();

        List<DetectedMarker> detectedMarkers =
                new ArrayList<>();

        int rejectedCandidates = 0;

        try {

            /*
             * Reduz pequenos ruídos antes da binarização.
             */
            Imgproc.GaussianBlur(
                    grayFrame,
                    blurred,
                    new Size(5, 5),
                    0
            );

            debugSink.publish(
                    VisionStage.BLURRED,
                    blurred
            );

            /*
             * Objetos escuros se tornam brancos na imagem
             * binária. O threshold adaptativo ajuda quando
             * existe iluminação desigual.
             */
            Imgproc.adaptiveThreshold(
                    blurred,
                    binary,
                    255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    31,
                    7
            );

            /*
             * Publicamos antes do findContours porque essa operação
             * pode modificar a imagem binária.
             */
            debugSink.publish(
                    VisionStage.BINARY,
                    binary
            );

            Imgproc.findContours(
                    binary,
                    contours,
                    hierarchy,
                    Imgproc.RETR_LIST,
                    Imgproc.CHAIN_APPROX_SIMPLE
            );

            double frameArea =
                    grayFrame.cols()
                            * (double) grayFrame.rows();

            for (MatOfPoint contour : contours) {

                DetectedMarker marker =
                        analisarContorno(
                                contour,
                                grayFrame,
                                frameArea
                        );
//                DetectedMarker marker =
//                        analisarContorno(
//                                contour,
//                                binary,
//                                frameArea
//                        );

                if (marker == null) {

                    rejectedCandidates++;
                    continue;
                }

                if (isDuplicate(
                        marker,
                        detectedMarkers
                )) {

                    rejectedCandidates++;
                    continue;
                }

                detectedMarkers.add(marker);
            }

            return new MarkerDetectionResult(
                    getName(),
                    detectedMarkers,
                    rejectedCandidates,
                    System.nanoTime() - inicio
            );

        } finally {

            blurred.release();
            binary.release();
            hierarchy.release();

            liberarContornos(contours);
        }
    }

    private DetectedMarker analisarContorno(
            MatOfPoint contour,
            Mat grayFrame,
            double frameArea
    ) {
//    private DetectedMarker analisarContorno(
//            MatOfPoint contour,
//            Mat binary,
//            double frameArea
//    ) {

        double contourArea =
                Math.abs(
                        Imgproc.contourArea(contour)
                );

        if (contourArea <= 0) {
            return null;
        }

        double areaRatio =
                contourArea / frameArea;

        if (areaRatio
                < config.getMinimumAreaRatio()) {

            return null;
        }

        if (areaRatio
                > config.getMaximumAreaRatio()) {

            return null;
        }

        Rect boundingRect =
                Imgproc.boundingRect(contour);

        if (boundingRect.width
                < config.getMinimumSizePixels()
                || boundingRect.height
                < config.getMinimumSizePixels()) {

            return null;
        }

        MatOfPoint2f contour2f =
                new MatOfPoint2f(
                        contour.toArray()
                );

        MatOfPoint2f approximation =
                new MatOfPoint2f();

        MatOfPoint approximationInt = null;

        try {

            double perimeter =
                    Imgproc.arcLength(
                            contour2f,
                            true
                    );

            if (perimeter <= 0) {
                return null;
            }

            Imgproc.approxPolyDP(
                    contour2f,
                    approximation,
                    perimeter
                            * config
                            .getPolygonApproximationFactor(),
                    true
            );

            Point[] corners =
                    approximation.toArray();

            if (corners.length != 4) {
                return null;
            }

            approximationInt =
                    new MatOfPoint(corners);

            if (!Imgproc.isContourConvex(
                    approximationInt
            )) {

                return null;
            }

            RotatedRect rotatedRect =
                    Imgproc.minAreaRect(
                            approximation
                    );

            double width =
                    rotatedRect.size.width;

            double height =
                    rotatedRect.size.height;

            if (width <= 0 || height <= 0) {
                return null;
            }

            double minimumSide =
                    Math.min(width, height);

            double maximumSide =
                    Math.max(width, height);

            double sideRatio =
                    minimumSide / maximumSide;

            if (sideRatio
                    < config.getMinimumSideRatio()) {

                return null;
            }

            double darknessRatio =
                    calcularEscuridaoInterna(
                            grayFrame,
                            approximationInt,
                            boundingRect
                    );

            if (darknessRatio
                    < config.getMinimumDarknessRatio()) {

                return null;
            }
//            double fillRatio =
//                    calcularPreenchimento(
//                            binary,
//                            boundingRect
//                    );
//
//            if (fillRatio
//                    < config.getMinimumFillRatio()) {
//
//                return null;
//            }

            double rotatedArea =
                    width * height;

            double rectangularity =
                    rotatedArea <= 0
                            ? 0
                            : Math.min(
                            1.0,
                            contourArea / rotatedArea
                    );

            double confidence =
                    limitarEntreZeroEUm(
                            sideRatio * 0.35
                                    + rectangularity * 0.30
                                    + darknessRatio * 0.35
                    );
//            double confidence =
//                    limitarEntreZeroEUm(
//                            sideRatio * 0.40
//                                    + rectangularity * 0.35
//                                    + fillRatio * 0.25
//                    );

            return new DetectedMarker(
                    MarkerType.SOLID_SQUARE,
                    null,
                    corners,
                    confidence
            );

        } finally {

            contour2f.release();
            approximation.release();

            if (approximationInt != null) {
                approximationInt.release();
            }
        }
    }

    private double calcularEscuridaoInterna(
            Mat grayFrame,
            MatOfPoint polygon,
            Rect boundingRect
    ) {

        Rect safeRect =
                limitarRetangulo(
                        boundingRect,
                        grayFrame.cols(),
                        grayFrame.rows()
                );

        if (safeRect.width <= 0
                || safeRect.height <= 0) {

            return 0;
        }

        Point[] globalPoints =
                polygon.toArray();

        Point[] localPoints =
                new Point[globalPoints.length];

        for (int index = 0;
             index < globalPoints.length;
             index++) {

            localPoints[index] =
                    new Point(
                            globalPoints[index].x
                                    - safeRect.x,
                            globalPoints[index].y
                                    - safeRect.y
                    );
        }

        Mat grayRegion =
                grayFrame.submat(safeRect);

        Mat mask =
                Mat.zeros(
                        safeRect.height,
                        safeRect.width,
                        CvType.CV_8UC1
                );

        MatOfPoint localPolygon =
                new MatOfPoint(localPoints);

        try {

            Imgproc.fillPoly(
                    mask,
                    Collections.singletonList(
                            localPolygon
                    ),
                    new Scalar(255)
            );

            Scalar mean =
                    Core.mean(
                            grayRegion,
                            mask
                    );

            /*
             * Cinza 255 representa branco:
             * escuridão = 0.
             *
             * Cinza 0 representa preto:
             * escuridão = 1.
             */
            return limitarEntreZeroEUm(
                    1.0 - mean.val[0] / 255.0
            );

        } finally {

            grayRegion.release();
            mask.release();
            localPolygon.release();
        }
    }

//    private double calcularPreenchimento(
//            Mat binary,
//            Rect boundingRect
//    ) {
//
//        Rect safeRect =
//                limitarRetangulo(
//                        boundingRect,
//                        binary.cols(),
//                        binary.rows()
//                );
//
//        if (safeRect.width <= 0
//                || safeRect.height <= 0) {
//
//            return 0;
//        }
//
//        Mat region =
//                binary.submat(safeRect);
//
//        try {
//
//            double whitePixels =
//                    Core.countNonZero(region);
//
//            double regionArea =
//                    safeRect.width
//                            * (double) safeRect.height;
//
//            if (regionArea <= 0) {
//                return 0;
//            }
//
//            return whitePixels / regionArea;
//
//        } finally {
//
//            region.release();
//        }
//    }

    private Rect limitarRetangulo(
            Rect rect,
            int imageWidth,
            int imageHeight
    ) {

        int left =
                Math.max(0, rect.x);

        int top =
                Math.max(0, rect.y);

        int right =
                Math.min(
                        imageWidth,
                        rect.x + rect.width
                );

        int bottom =
                Math.min(
                        imageHeight,
                        rect.y + rect.height
                );

        return new Rect(
                left,
                top,
                Math.max(0, right - left),
                Math.max(0, bottom - top)
        );
    }

    private boolean isDuplicate(
            DetectedMarker candidate,
            List<DetectedMarker> acceptedMarkers
    ) {

        Point candidateCenter =
                candidate.getCenter();

        Point[] candidateCorners =
                candidate.getCorners();

        double candidateSize =
                averageSideLength(
                        candidateCorners
                );

        for (DetectedMarker accepted
                : acceptedMarkers) {

            Point acceptedCenter =
                    accepted.getCenter();

            double distance =
                    Math.hypot(
                            candidateCenter.x
                                    - acceptedCenter.x,
                            candidateCenter.y
                                    - acceptedCenter.y
                    );

            Point[] acceptedCorners =
                    accepted.getCorners();

            double acceptedSize =
                    averageSideLength(
                            acceptedCorners
                    );

            double referenceSize =
                    Math.min(
                            candidateSize,
                            acceptedSize
                    );

            /*
             * Contornos com praticamente o mesmo centro
             * normalmente representam a mesma figura.
             */
            if (distance
                    <= Math.max(
                    4,
                    referenceSize * 0.30
            )) {

                return true;
            }
        }

        return false;
    }

    private double averageSideLength(
            Point[] corners
    ) {

        if (corners == null
                || corners.length != 4) {

            return 0;
        }

        double total = 0;

        for (int index = 0;
             index < corners.length;
             index++) {

            Point start =
                    corners[index];

            Point end =
                    corners[
                            (index + 1)
                                    % corners.length
                            ];

            total +=
                    Math.hypot(
                            end.x - start.x,
                            end.y - start.y
                    );
        }

        return total / corners.length;
    }

    private double limitarEntreZeroEUm(
            double value
    ) {

        return Math.max(
                0,
                Math.min(1, value)
        );
    }

    private void liberarContornos(
            List<MatOfPoint> contours
    ) {

        for (MatOfPoint contour : contours) {

            if (contour != null) {
                contour.release();
            }
        }

        contours.clear();
    }
}