package com.example.leitorgabaritoomr.vision.processing;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;

import org.opencv.core.Mat;

public final class MarkerFrameProcessor {

    private final OmrMarkerDetector detector;
    private final MarkerOverlayRenderer renderer;
    private final VisionDebugController debugController;

    public MarkerFrameProcessor(
            OmrMarkerDetector detector,
            MarkerOverlayRenderer renderer,
            VisionDebugController debugController
    ) {

        if (detector == null) {
            throw new IllegalArgumentException(
                    "O detector é obrigatório."
            );
        }

        if (renderer == null) {
            throw new IllegalArgumentException(
                    "O renderer é obrigatório."
            );
        }

        if (debugController == null) {
            throw new IllegalArgumentException(
                    "O controlador de depuração é obrigatório."
            );
        }

        this.detector = detector;
        this.renderer = renderer;
        this.debugController = debugController;
    }

    public MarkerDetectionResult process(
            Mat grayFrame,
            Mat rgbaFrame
    ) {

        debugController.beginFrame();

        /*
         * ORIGINAL precisa ser publicado antes de desenharmos
         * qualquer contorno sobre o frame.
         */
        debugController.publish(
                VisionStage.ORIGINAL,
                rgbaFrame
        );

        debugController.publish(
                VisionStage.GRAYSCALE,
                grayFrame
        );

        MarkerDetectionResult result =
                detector.detect(
                        grayFrame,
                        debugController
                );

        /*
         * Os candidatos são desenhados no frame RGBA.
         */
        renderer.draw(
                rgbaFrame,
                result
        );

        debugController.publish(
                VisionStage.ACCEPTED_CANDIDATES,
                rgbaFrame
        );

        /*
         * Substitui o frame exibido pela etapa escolhida.
         */
        debugController.renderSelectedStage(
                rgbaFrame
        );

        return result;
    }

    public String getDetectorName() {
        return detector.getName();
    }
}

//package com.example.leitorgabaritoomr.vision.processing;
//
//import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
//import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
//
//import org.opencv.core.Mat;
//
//public final class MarkerFrameProcessor {
//
//    private final OmrMarkerDetector detector;
//    private final MarkerOverlayRenderer renderer;
//
//    public MarkerFrameProcessor(
//            OmrMarkerDetector detector,
//            MarkerOverlayRenderer renderer
//    ) {
//
//        if (detector == null) {
//            throw new IllegalArgumentException(
//                    "O detector é obrigatório."
//            );
//        }
//
//        if (renderer == null) {
//            throw new IllegalArgumentException(
//                    "O renderer é obrigatório."
//            );
//        }
//
//        this.detector = detector;
//        this.renderer = renderer;
//    }
//
//    public MarkerDetectionResult process(
//            Mat grayFrame,
//            Mat rgbaFrame
//    ) {
//
//        MarkerDetectionResult result =
//                detector.detect(grayFrame);
//
//        renderer.draw(
//                rgbaFrame,
//                result
//        );
//
//        return result;
//    }
//
//    public String getDetectorName() {
//        return detector.getName();
//    }
//}