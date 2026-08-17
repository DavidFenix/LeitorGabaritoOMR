package com.example.leitorgabaritoomr.vision.processing;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.NormalizedRegionPreviewRenderer;
import com.example.leitorgabaritoomr.vision.drawing.ResolvedMarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.StableMarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.geometry.CornerRole;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolver;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizationResult;
import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizer;
import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizerConfig;
import com.example.leitorgabaritoomr.vision.stability.MarkerSetStabilizer;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;

import org.opencv.core.Mat;
import org.opencv.core.Point;

public final class MarkerFrameProcessor {

    /*
     * Impede que o aplicativo pause novamente a cada frame
     * enquanto o mesmo conjunto permanece estável.
     */
    private boolean autoFreezeConsumedForCurrentStableSet =
            false;

    private final OmrMarkerDetector detector;

    private final MarkerOverlayRenderer markerRenderer;

    private final MarkerSetResolver markerSetResolver;

    private final ResolvedMarkerOverlayRenderer
            resolvedMarkerRenderer;

    private final MarkerSetStabilizer markerSetStabilizer;

    private final StableMarkerOverlayRenderer
            stableMarkerRenderer;

    private final VisionDebugController debugController;

    private final OmrRegionNormalizer regionNormalizer;

    private final NormalizedRegionPreviewRenderer
            normalizedRegionPreviewRenderer;

    private volatile MarkerDetectionResult
            lastDetectionResult;

    private volatile MarkerSetResolutionResult
            lastResolutionResult;

    private volatile MarkerStabilityResult
            lastStabilityResult;

    public MarkerFrameProcessor(
            OmrMarkerDetector detector,
            MarkerOverlayRenderer markerRenderer,
            MarkerSetResolver markerSetResolver,
            ResolvedMarkerOverlayRenderer resolvedMarkerRenderer,
            MarkerSetStabilizer markerSetStabilizer,
            StableMarkerOverlayRenderer stableMarkerRenderer,
            VisionDebugController debugController
    ) {
        this(
                detector,
                markerRenderer,
                markerSetResolver,
                resolvedMarkerRenderer,
                markerSetStabilizer,
                stableMarkerRenderer,
                debugController,
                new OmrRegionNormalizer(
                        OmrRegionNormalizerConfig
                                .developmentDefaults()
                ),
                new NormalizedRegionPreviewRenderer()
        );
    }

    /**
     * Construtor completo para permitir testes e configurações
     * diferentes no futuro.
     *
     * O construtor anterior continua existindo, portanto não é
     * necessário alterar a MainActivity agora.
     */
    public MarkerFrameProcessor(
            OmrMarkerDetector detector,
            MarkerOverlayRenderer markerRenderer,
            MarkerSetResolver markerSetResolver,
            ResolvedMarkerOverlayRenderer resolvedMarkerRenderer,
            MarkerSetStabilizer markerSetStabilizer,
            StableMarkerOverlayRenderer stableMarkerRenderer,
            VisionDebugController debugController,
            OmrRegionNormalizer regionNormalizer,
            NormalizedRegionPreviewRenderer normalizedRegionPreviewRenderer
    ) {
        if (detector == null
                || markerRenderer == null
                || markerSetResolver == null
                || resolvedMarkerRenderer == null
                || markerSetStabilizer == null
                || stableMarkerRenderer == null
                || debugController == null
                || regionNormalizer == null
                || normalizedRegionPreviewRenderer == null) {

            throw new IllegalArgumentException(
                    "Todos os componentes do processamento são obrigatórios."
            );
        }

        this.detector = detector;
        this.markerRenderer = markerRenderer;
        this.markerSetResolver = markerSetResolver;

        this.resolvedMarkerRenderer =
                resolvedMarkerRenderer;

        this.markerSetStabilizer =
                markerSetStabilizer;

        this.stableMarkerRenderer =
                stableMarkerRenderer;

        this.debugController =
                debugController;

        this.regionNormalizer =
                regionNormalizer;

        this.normalizedRegionPreviewRenderer =
                normalizedRegionPreviewRenderer;
    }

    private void applyAutomaticFreezeIfEligible() {
        if (lastStabilityResult == null) {
            return;
        }

        MarkerStabilityState state =
                lastStabilityResult.getState();

        /*
         * Quando a estabilidade foi realmente perdida,
         * uma futura sequência estável poderá disparar
         * uma nova pausa automática.
         */
        if (state == MarkerStabilityState.SEARCHING
                || state == MarkerStabilityState.LOST) {

            autoFreezeConsumedForCurrentStableSet =
                    false;

            return;
        }

        /*
         * HELD_STABLE conserva a última solução,
         * mas não inicia uma nova pausa.
         */
        if (state != MarkerStabilityState.STABLE) {
            return;
        }

        if (!debugController
                .isAutoFreezeOnStableEnabled()) {

            return;
        }

        VisionStage selectedStage =
                debugController.getSelectedStage();

        /*
         * A pausa automática é útil nas duas etapas
         * que dependem da estabilidade temporal.
         */
        if (selectedStage
                != VisionStage.STABLE_MARKERS
                && selectedStage
                != VisionStage.NORMALIZED_REGION) {

            return;
        }

        if (autoFreezeConsumedForCurrentStableSet) {
            return;
        }

        boolean frozen =
                debugController.freeze();

        if (frozen) {
            autoFreezeConsumedForCurrentStableSet =
                    true;
        }
    }

    public MarkerDetectionResult process(
            Mat grayFrame,
            Mat rgbaFrame
    ) {
        if (debugController.isFrozen()
                && lastDetectionResult != null) {

            debugController.renderSelectedStage(
                    rgbaFrame
            );

            return lastDetectionResult;
        }

        debugController.beginFrame();

        debugController.publish(
                VisionStage.ORIGINAL,
                rgbaFrame
        );

        debugController.publish(
                VisionStage.GRAYSCALE,
                grayFrame
        );

        /*
         * Criamos cópias limpas somente quando a respectiva
         * etapa está selecionada.
         */
        Mat cleanStabilityFrame = null;
        Mat normalizedPreviewFrame = null;

        OmrRegionNormalizationResult
                normalizationResult = null;

        if (debugController.getSelectedStage()
                == VisionStage.STABLE_MARKERS) {

            cleanStabilityFrame =
                    rgbaFrame.clone();
        }

        try {
            lastDetectionResult =
                    detector.detect(
                            grayFrame,
                            debugController
                    );

            markerRenderer.draw(
                    rgbaFrame,
                    lastDetectionResult
            );

            debugController.publish(
                    VisionStage.ACCEPTED_CANDIDATES,
                    rgbaFrame
            );

            lastResolutionResult =
                    markerSetResolver.resolve(
                            lastDetectionResult.getMarkers(),
                            rgbaFrame.cols(),
                            rgbaFrame.rows()
                    );

            resolvedMarkerRenderer.draw(
                    rgbaFrame,
                    lastResolutionResult
            );

            debugController.publish(
                    VisionStage.RESOLVED_MARKERS,
                    rgbaFrame
            );

            lastStabilityResult =
                    markerSetStabilizer.update(
                            lastResolutionResult
                    );

            if (cleanStabilityFrame != null) {
                stableMarkerRenderer.draw(
                        cleanStabilityFrame,
                        lastStabilityResult
                );

                debugController.publish(
                        VisionStage.STABLE_MARKERS,
                        cleanStabilityFrame
                );
            }

            if (debugController.getSelectedStage()
                    == VisionStage.NORMALIZED_REGION) {

                normalizationResult =
                        normalizeStableRegion(
                                rgbaFrame
                        );

                normalizedPreviewFrame =
                        normalizedRegionPreviewRenderer
                                .render(
                                        normalizationResult,
                                        rgbaFrame.cols(),
                                        rgbaFrame.rows()
                                );

                debugController.publish(
                        VisionStage.NORMALIZED_REGION,
                        normalizedPreviewFrame
                );
            }

            /*
             * A pausa automática ocorre somente depois que a
             * imagem da etapa selecionada foi publicada.
             */
            applyAutomaticFreezeIfEligible();

            debugController.renderSelectedStage(
                    rgbaFrame
            );

            return lastDetectionResult;

        } finally {
            if (normalizationResult != null) {
                normalizationResult.release();
            }

            if (normalizedPreviewFrame != null) {
                normalizedPreviewFrame.release();
            }

            if (cleanStabilityFrame != null) {
                cleanStabilityFrame.release();
            }
        }
    }

    private OmrRegionNormalizationResult
    normalizeStableRegion(Mat sourceFrame) {

        if (lastStabilityResult == null
                || !lastStabilityResult.isStable()
                || lastStabilityResult.getMarkerSet()
                == null) {

            return OmrRegionNormalizationResult.failure(
                    "Ainda não existe um conjunto estável."
            );
        }

        ResolvedMarkerSet markerSet =
                lastStabilityResult.getMarkerSet();

        Point topLeft =
                markerSet.get(CornerRole.TOP_LEFT)
                        .getCenter();

        Point topRight =
                markerSet.get(CornerRole.TOP_RIGHT)
                        .getCenter();

        Point bottomRight =
                markerSet.get(CornerRole.BOTTOM_RIGHT)
                        .getCenter();

        Point bottomLeft =
                markerSet.get(CornerRole.BOTTOM_LEFT)
                        .getCenter();

        return regionNormalizer.normalize(
                sourceFrame,
                topLeft,
                topRight,
                bottomRight,
                bottomLeft
        );
    }

    public String getDetectorName() {
        return detector.getName();
    }

    public MarkerSetResolutionResult
    getLastResolutionResult() {
        return lastResolutionResult;
    }

    public MarkerStabilityResult
    getLastStabilityResult() {
        return lastStabilityResult;
    }

    public void resetStability() {
        markerSetStabilizer.reset();

        lastStabilityResult = null;

        autoFreezeConsumedForCurrentStableSet =
                false;
    }
}
//package com.example.leitorgabaritoomr.vision.processing;
//
//import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
//import com.example.leitorgabaritoomr.vision.debug.VisionStage;
//import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
//import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.drawing.ResolvedMarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.drawing.StableMarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
//import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolver;
//import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
//import com.example.leitorgabaritoomr.vision.stability.MarkerSetStabilizer;
//import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;
//
//import org.opencv.core.Mat;
//
//import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;
//
//public final class MarkerFrameProcessor {
//
//    /*
//     * Impede que o aplicativo pause novamente a cada frame
//     * enquanto o mesmo conjunto permanece estável.
//     */
//    private boolean autoFreezeConsumedForCurrentStableSet =
//            false;
//    private final OmrMarkerDetector detector;
//    private final MarkerOverlayRenderer markerRenderer;
//    private final MarkerSetResolver markerSetResolver;
//
//    private final ResolvedMarkerOverlayRenderer
//            resolvedMarkerRenderer;
//
//    private final MarkerSetStabilizer markerSetStabilizer;
//
//    private final StableMarkerOverlayRenderer
//            stableMarkerRenderer;
//
//    private final VisionDebugController debugController;
//
//    private volatile MarkerDetectionResult
//            lastDetectionResult;
//
//    private volatile MarkerSetResolutionResult
//            lastResolutionResult;
//
//    private volatile MarkerStabilityResult
//            lastStabilityResult;
//
//    public MarkerFrameProcessor(
//            OmrMarkerDetector detector,
//            MarkerOverlayRenderer markerRenderer,
//            MarkerSetResolver markerSetResolver,
//            ResolvedMarkerOverlayRenderer resolvedMarkerRenderer,
//            MarkerSetStabilizer markerSetStabilizer,
//            StableMarkerOverlayRenderer stableMarkerRenderer,
//            VisionDebugController debugController
//    ) {
//
//        if (detector == null
//                || markerRenderer == null
//                || markerSetResolver == null
//                || resolvedMarkerRenderer == null
//                || markerSetStabilizer == null
//                || stableMarkerRenderer == null
//                || debugController == null) {
//
//            throw new IllegalArgumentException(
//                    "Todos os componentes do processamento são obrigatórios."
//            );
//        }
//
//        this.detector = detector;
//        this.markerRenderer = markerRenderer;
//        this.markerSetResolver = markerSetResolver;
//        this.resolvedMarkerRenderer =
//                resolvedMarkerRenderer;
//        this.markerSetStabilizer =
//                markerSetStabilizer;
//        this.stableMarkerRenderer =
//                stableMarkerRenderer;
//        this.debugController = debugController;
//    }
//
//    private void applyAutomaticFreezeIfEligible() {
//
//        if (lastStabilityResult == null) {
//            return;
//        }
//
//        MarkerStabilityState state =
//                lastStabilityResult.getState();
//
//        /*
//         * Quando a estabilidade foi realmente perdida,
//         * uma futura sequência estável poderá disparar
//         * uma nova pausa automática.
//         */
//        if (state == MarkerStabilityState.SEARCHING
//                || state == MarkerStabilityState.LOST) {
//
//            autoFreezeConsumedForCurrentStableSet = false;
//
//            return;
//        }
//
//        /*
//         * HELD_STABLE não inicia pausa automática.
//         * Precisamos de uma confirmação STABLE real.
//         */
//        if (state != MarkerStabilityState.STABLE) {
//            return;
//        }
//
//        if (!debugController
//                .isAutoFreezeOnStableEnabled()) {
//
//            return;
//        }
//
//        if (debugController.getSelectedStage()
//                != VisionStage.STABLE_MARKERS) {
//
//            return;
//        }
//
//        if (autoFreezeConsumedForCurrentStableSet) {
//            return;
//        }
//
//        boolean frozen =
//                debugController.freeze();
//
//        if (frozen) {
//
//            autoFreezeConsumedForCurrentStableSet =
//                    true;
//        }
//    }
//
//    public MarkerDetectionResult process(
//            Mat grayFrame,
//            Mat rgbaFrame
//    ) {
//
//        if (debugController.isFrozen()
//                && lastDetectionResult != null) {
//
//            debugController.renderSelectedStage(
//                    rgbaFrame
//            );
//
//            return lastDetectionResult;
//        }
//
//        debugController.beginFrame();
//
//        debugController.publish(
//                VisionStage.ORIGINAL,
//                rgbaFrame
//        );
//
//        debugController.publish(
//                VisionStage.GRAYSCALE,
//                grayFrame
//        );
//
//        /*
//         * A etapa 7 usa uma cópia limpa do frame,
//         * sem candidatos e sem o resultado por frame.
//         */
//        Mat cleanStabilityFrame = null;
//
//        if (debugController.getSelectedStage()
//                == VisionStage.STABLE_MARKERS) {
//
//            cleanStabilityFrame =
//                    rgbaFrame.clone();
//        }
//
//        try {
//
//            lastDetectionResult =
//                    detector.detect(
//                            grayFrame,
//                            debugController
//                    );
//
//            markerRenderer.draw(
//                    rgbaFrame,
//                    lastDetectionResult
//            );
//
//            debugController.publish(
//                    VisionStage.ACCEPTED_CANDIDATES,
//                    rgbaFrame
//            );
//
//            lastResolutionResult =
//                    markerSetResolver.resolve(
//                            lastDetectionResult.getMarkers(),
//                            rgbaFrame.cols(),
//                            rgbaFrame.rows()
//                    );
//
//            resolvedMarkerRenderer.draw(
//                    rgbaFrame,
//                    lastResolutionResult
//            );
//
//            debugController.publish(
//                    VisionStage.RESOLVED_MARKERS,
//                    rgbaFrame
//            );
//
//            lastStabilityResult =
//                    markerSetStabilizer.update(
//                            lastResolutionResult
//                    );
//
//            if (cleanStabilityFrame != null) {
//
//                stableMarkerRenderer.draw(
//                        cleanStabilityFrame,
//                        lastStabilityResult
//                );
//
//                debugController.publish(
//                        VisionStage.STABLE_MARKERS,
//                        cleanStabilityFrame
//                );
//            }
//
//            /*
//             * A pausa automática só pode acontecer depois de a
//             * imagem da etapa 7 ter sido publicada no controlador.
//             */
//            applyAutomaticFreezeIfEligible();
//
//            debugController.renderSelectedStage(
//                    rgbaFrame
//            );
//
//            return lastDetectionResult;
//
//        } finally {
//
//            if (cleanStabilityFrame != null) {
//                cleanStabilityFrame.release();
//            }
//        }
//    }
//
//    public String getDetectorName() {
//        return detector.getName();
//    }
//
//    public MarkerSetResolutionResult
//    getLastResolutionResult() {
//
//        return lastResolutionResult;
//    }
//
//    public MarkerStabilityResult
//    getLastStabilityResult() {
//
//        return lastStabilityResult;
//    }
//
//    public void resetStability() {
//
//        markerSetStabilizer.reset();
//
//        lastStabilityResult = null;
//
//        autoFreezeConsumedForCurrentStableSet =
//                false;
//    }
//
//}
