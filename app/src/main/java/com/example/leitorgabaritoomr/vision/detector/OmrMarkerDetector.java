package com.example.leitorgabaritoomr.vision.detector;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugSink;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;

import org.opencv.core.Mat;

public interface OmrMarkerDetector {

    String getName();

    MarkerDetectionResult detect(
            Mat grayFrame
    );

    /*
     * Detectores que possuem etapas intermediárias podem
     * sobrescrever este método.
     *
     * O ArUco continua funcionando sem nenhuma alteração.
     */
    default MarkerDetectionResult detect(
            Mat grayFrame,
            VisionDebugSink debugSink
    ) {

        return detect(grayFrame);
    }
}

//package com.example.leitorgabaritoomr.vision.detector;
//
//import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
//
//import org.opencv.core.Mat;
//
//public interface OmrMarkerDetector {
//
//    String getName();
//
//    MarkerDetectionResult detect(Mat grayFrame);
//}