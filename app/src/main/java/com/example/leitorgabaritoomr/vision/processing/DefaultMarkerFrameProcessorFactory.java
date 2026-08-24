package com.example.leitorgabaritoomr.vision.processing;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.detector.ArucoMarkerDetector;
import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
import com.example.leitorgabaritoomr.vision.detector.SolidSquareMarkerDetector;
import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.ResolvedMarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.StableMarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolver;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.stability.MarkerSetStabilizer;

/**
 * Ponto unico de composicao do pipeline padrao do aplicativo.
 *
 * A Activity, os testes instrumentados e futuras fontes de imagem
 * devem solicitar o processador a esta fabrica. Assim todos usam os
 * mesmos detectores, resolvedor, estabilizador e configuracoes
 * internas criadas pelo construtor curto de MarkerFrameProcessor.
 */
public final class DefaultMarkerFrameProcessorFactory {

    private DefaultMarkerFrameProcessorFactory() {
        throw new AssertionError(
                "Esta classe nao deve ser instanciada."
        );
    }

    public static MarkerFrameProcessor create(
            MarkerDetectorMode detectorMode,
            VisionDebugController debugController
    ) {
        if (detectorMode == null) {
            throw new IllegalArgumentException(
                    "O modo do detector e obrigatorio."
            );
        }

        if (debugController == null) {
            throw new IllegalArgumentException(
                    "VisionDebugController e obrigatorio."
            );
        }

        return new MarkerFrameProcessor(
                createDetector(detectorMode),
                new MarkerOverlayRenderer(),
                new MarkerSetResolver(),
                new ResolvedMarkerOverlayRenderer(),
                new MarkerSetStabilizer(),
                new StableMarkerOverlayRenderer(),
                debugController
        );
    }

    private static OmrMarkerDetector createDetector(
            MarkerDetectorMode detectorMode
    ) {
        switch (detectorMode) {

            case ARUCO:
                return new ArucoMarkerDetector();

            case SOLID_SQUARE:
                return new SolidSquareMarkerDetector();

            default:
                throw new IllegalStateException(
                        "Modo de detector nao suportado: "
                                + detectorMode
                );
        }
    }
}
