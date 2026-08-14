package com.example.leitorgabaritoomr.vision.detector;

import com.example.leitorgabaritoomr.vision.model.DetectedMarker;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.model.MarkerType;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

import java.util.ArrayList;
import java.util.List;

public final class ArucoMarkerDetector
        implements OmrMarkerDetector {

    private final ArucoDetector detector;

    public ArucoMarkerDetector() {

        Dictionary dictionary =
                Objdetect.getPredefinedDictionary(
                        Objdetect.DICT_4X4_50
                );

        DetectorParameters parameters =
                new DetectorParameters();

        detector = new ArucoDetector(
                dictionary,
                parameters
        );
    }

    @Override
    public String getName() {
        return "aruco-4x4-50";
    }

    @Override
    public MarkerDetectionResult detect(Mat grayFrame) {

        if (grayFrame == null || grayFrame.empty()) {

            return new MarkerDetectionResult(
                    getName(),
                    new ArrayList<>(),
                    0,
                    0
            );
        }

        long inicio = System.nanoTime();

        List<Mat> corners = new ArrayList<>();
        List<Mat> rejectedCandidates = new ArrayList<>();
        List<DetectedMarker> detectedMarkers =
                new ArrayList<>();

        Mat ids = new Mat();

        try {

            detector.detectMarkers(
                    grayFrame,
                    corners,
                    ids,
                    rejectedCandidates
            );

            int markerCount =
                    Math.min(
                            corners.size(),
                            ids.rows()
                    );

            for (int index = 0;
                 index < markerCount;
                 index++) {

                MatOfPoint2f markerCornersMat =
                        new MatOfPoint2f(
                                corners.get(index)
                        );

                Point[] markerCorners;

                try {

                    markerCorners =
                            markerCornersMat.toArray();

                } finally {

                    markerCornersMat.release();
                }

                if (markerCorners.length != 4) {
                    continue;
                }

                int markerId =
                        (int) ids.get(index, 0)[0];

                detectedMarkers.add(
                        new DetectedMarker(
                                MarkerType.ARUCO,
                                markerId,
                                markerCorners,
                                1.0
                        )
                );
            }

            return new MarkerDetectionResult(
                    getName(),
                    detectedMarkers,
                    rejectedCandidates.size(),
                    System.nanoTime() - inicio
            );

        } finally {

            ids.release();
            liberarMats(corners);
            liberarMats(rejectedCandidates);
        }
    }

    private void liberarMats(List<Mat> mats) {

        for (Mat mat : mats) {

            if (mat != null) {
                mat.release();
            }
        }

        mats.clear();
    }
}