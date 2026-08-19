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

import com.example.leitorgabaritoomr.vision.drawing.LayoutOverlayRenderer;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
//import com.example.leitorgabaritoomr.vision.layout.factory.ThreeColumnsFifteenLayoutFactory;

import com.example.leitorgabaritoomr.vision.drawing.BubbleMeasurementOverlayRenderer;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurementConfig;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurer;
import com.example.leitorgabaritoomr.vision.measurement.OmrLayoutMeasurer;
import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;

import com.example.leitorgabaritoomr.vision.drawing.QuestionComparisonOverlayRenderer;
import com.example.leitorgabaritoomr.vision.measurement.OmrQuestionMeasurer;
import com.example.leitorgabaritoomr.vision.measurement.QuestionMeasurement;
import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorer;
import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorerConfig;

import java.util.List;

public final class MarkerFrameProcessor {

    /*
     * Registra em qual etapa ocorreu a última pausa automática.
     *
     * Assim, a etapa 7 pode pausar uma vez e a etapa 8 também
     * pode pausar uma vez para o mesmo conjunto estável.
     */
    private VisionStage autoFreezeConsumedStage = null;

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

    private final OmrLayoutDefinition layoutDefinition;

    private final LayoutOverlayRenderer
            layoutOverlayRenderer;

    private final OmrLayoutMeasurer layoutMeasurer;

    private final BubbleMeasurementOverlayRenderer
            bubbleMeasurementOverlayRenderer;

    private final OmrQuestionMeasurer
            questionMeasurer;

    private final QuestionComparisonOverlayRenderer
            questionComparisonOverlayRenderer;

    private volatile List<QuestionMeasurement>
            lastQuestionMeasurements;

    private volatile OmrSheetMeasurementResult
            lastMeasurementResult;

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
                new NormalizedRegionPreviewRenderer(),
                AvalieCeDevelopmentLayoutFactory.create(),
//                ThreeColumnsFifteenLayoutFactory.create(),
                new LayoutOverlayRenderer(),
                new OmrLayoutMeasurer(
                        new BubbleMeasurer(
                                BubbleMeasurementConfig
                                        .developmentDefaults()
                        )
                ),
                new BubbleMeasurementOverlayRenderer(),
                new OmrQuestionMeasurer(
                        new BubbleEvidenceScorer(
                                BubbleEvidenceScorerConfig
                                        .developmentDefaults()
                        )
                ),
                new QuestionComparisonOverlayRenderer()
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
            NormalizedRegionPreviewRenderer normalizedRegionPreviewRenderer,
            OmrLayoutDefinition layoutDefinition,
            LayoutOverlayRenderer layoutOverlayRenderer,
            OmrLayoutMeasurer layoutMeasurer,
            BubbleMeasurementOverlayRenderer bubbleMeasurementOverlayRenderer,
            OmrQuestionMeasurer questionMeasurer,
            QuestionComparisonOverlayRenderer questionComparisonOverlayRenderer
    ) {
        if (detector == null
                || markerRenderer == null
                || markerSetResolver == null
                || resolvedMarkerRenderer == null
                || markerSetStabilizer == null
                || stableMarkerRenderer == null
                || debugController == null
                || regionNormalizer == null
                || normalizedRegionPreviewRenderer == null
                || layoutDefinition == null
                || layoutOverlayRenderer == null
                || layoutMeasurer == null
                || bubbleMeasurementOverlayRenderer == null
                || questionMeasurer == null
                || questionComparisonOverlayRenderer == null) {

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

        this.layoutDefinition =
                layoutDefinition;

        this.layoutOverlayRenderer =
                layoutOverlayRenderer;

        this.layoutMeasurer =
                layoutMeasurer;

        this.bubbleMeasurementOverlayRenderer =
                bubbleMeasurementOverlayRenderer;

        this.questionMeasurer =
                questionMeasurer;

        this.questionComparisonOverlayRenderer =
                questionComparisonOverlayRenderer;
    }

    private void applyAutomaticFreezeIfEligible(
            boolean normalizedRegionAvailable,
            boolean measurementsAvailable,
            boolean questionComparisonAvailable
    ) {
        if (lastStabilityResult == null) {
            return;
        }

        MarkerStabilityState state =
                lastStabilityResult.getState();

        /*
         * Quando o conjunto é realmente perdido,
         * todas as etapas ficam novamente autorizadas
         * a realizar uma pausa automática.
         */
        if (state == MarkerStabilityState.SEARCHING
                || state == MarkerStabilityState.LOST) {

            autoFreezeConsumedStage = null;

            return;
        }

        /*
         * HELD_STABLE conserva a última solução,
         * mas não inicia uma nova pausa automática.
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

        if (selectedStage
                != VisionStage.STABLE_MARKERS
                && selectedStage
                != VisionStage.NORMALIZED_REGION
                && selectedStage
                != VisionStage.LAYOUT_MAP
                && selectedStage
                != VisionStage.BUBBLE_MEASUREMENTS
                && selectedStage
                != VisionStage.QUESTION_COMPARISON) {

            return;
        }
//        if (selectedStage
//                != VisionStage.STABLE_MARKERS
//                && selectedStage
//                != VisionStage.NORMALIZED_REGION) {
//
//            return;
//        }

        /*
         * Na etapa 8, o estado STABLE não é suficiente.
         *
         * A pausa somente pode ocorrer depois que a transformação
         * de perspectiva realmente produzir uma imagem válida.
         */
        if ((selectedStage == VisionStage.NORMALIZED_REGION
                || selectedStage == VisionStage.LAYOUT_MAP
                || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage == VisionStage.QUESTION_COMPARISON)
                && !normalizedRegionAvailable) {

            return;
        }
//        if ((selectedStage == VisionStage.NORMALIZED_REGION
//                || selectedStage == VisionStage.LAYOUT_MAP
//                || selectedStage == VisionStage.BUBBLE_MEASUREMENTS)
//                && !normalizedRegionAvailable) {
////        if (selectedStage == VisionStage.NORMALIZED_REGION
////                && !normalizedRegionAvailable) {
//
//            return;
//        }

        if ((selectedStage
                == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage
                == VisionStage.QUESTION_COMPARISON)
                && !measurementsAvailable) {

            return;
        }
//        if (selectedStage
//                == VisionStage.BUBBLE_MEASUREMENTS
//                && !measurementsAvailable) {
//
//            return;
//        }

        if (selectedStage
                == VisionStage.QUESTION_COMPARISON
                && !questionComparisonAvailable) {

            return;
        }

        /*
         * A etapa atual já utilizou sua pausa para
         * este conjunto estável.
         */
        if (selectedStage == autoFreezeConsumedStage) {
            return;
        }

        boolean frozen =
                debugController.freeze();

        if (frozen) {
            autoFreezeConsumedStage =
                    selectedStage;
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
        Mat cleanNormalizationFrame = null;
        Mat normalizedPreviewFrame = null;

        OmrRegionNormalizationResult
                normalizationResult = null;

        if (debugController.getSelectedStage()
                == VisionStage.STABLE_MARKERS) {

            cleanStabilityFrame =
                    rgbaFrame.clone();
        }

        VisionStage selectedStage =
                debugController.getSelectedStage();

        if (selectedStage == VisionStage.NORMALIZED_REGION
                || selectedStage == VisionStage.LAYOUT_MAP
                || selectedStage
                == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage
                == VisionStage.QUESTION_COMPARISON) {
//        if (selectedStage == VisionStage.NORMALIZED_REGION
//                || selectedStage == VisionStage.LAYOUT_MAP
//                || selectedStage
//                == VisionStage.BUBBLE_MEASUREMENTS) {
//        if (selectedStage == VisionStage.NORMALIZED_REGION
//                || selectedStage == VisionStage.LAYOUT_MAP) {

            cleanNormalizationFrame =
                    rgbaFrame.clone();
        }
        /*
         * A normalização precisa utilizar o frame original,
         * antes que candidatos, textos e quadriláteros sejam
         * desenhados sobre ele.
         */
//        if (debugController.getSelectedStage()
//                == VisionStage.NORMALIZED_REGION) {
//
//            cleanNormalizationFrame =
//                    rgbaFrame.clone();
//        }

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

            if (selectedStage == VisionStage.NORMALIZED_REGION
                    || selectedStage == VisionStage.LAYOUT_MAP
                    || selectedStage
                    == VisionStage.BUBBLE_MEASUREMENTS
                    || selectedStage
                    == VisionStage.QUESTION_COMPARISON) {

                normalizationResult =
                        normalizeStableRegion(
                                cleanNormalizationFrame
                        );

                if (normalizationResult.isSuccess()) {

                    if (selectedStage
                            == VisionStage.LAYOUT_MAP) {

                        layoutOverlayRenderer.draw(
                                normalizationResult
                                        .getNormalizedRegion(),
                                layoutDefinition
                        );
                    }

                    if (selectedStage
                            == VisionStage.BUBBLE_MEASUREMENTS
                            || selectedStage
                            == VisionStage.QUESTION_COMPARISON) {

                        lastMeasurementResult =
                                layoutMeasurer.measure(
                                        normalizationResult
                                                .getNormalizedRegion(),
                                        layoutDefinition
                                );

                        if (lastMeasurementResult.isComplete()) {
                            lastQuestionMeasurements =
                                    questionMeasurer.measure(
                                            lastMeasurementResult
                                    );
                        } else {
                            lastQuestionMeasurements = null;
                        }

                        if (selectedStage
                                == VisionStage.BUBBLE_MEASUREMENTS) {

                            bubbleMeasurementOverlayRenderer.draw(
                                    normalizationResult
                                            .getNormalizedRegion(),
                                    lastMeasurementResult
                            );
                        }

                        if (selectedStage
                                == VisionStage.QUESTION_COMPARISON
                                && lastQuestionMeasurements != null) {

                            questionComparisonOverlayRenderer.draw(
                                    normalizationResult
                                            .getNormalizedRegion(),
                                    lastQuestionMeasurements
                            );
                        }
                    }
//                    if (selectedStage
//                            == VisionStage.BUBBLE_MEASUREMENTS) {
//
//                        lastMeasurementResult =
//                                layoutMeasurer.measure(
//                                        normalizationResult
//                                                .getNormalizedRegion(),
//                                        layoutDefinition
//                                );
//
//                        bubbleMeasurementOverlayRenderer.draw(
//                                normalizationResult
//                                        .getNormalizedRegion(),
//                                lastMeasurementResult
//                        );
//                    }
                }

                normalizedPreviewFrame =
                        normalizedRegionPreviewRenderer
                                .render(
                                        normalizationResult,
                                        rgbaFrame.cols(),
                                        rgbaFrame.rows()
                                );

                debugController.publish(
                        selectedStage,
                        normalizedPreviewFrame
                );
            }
//            if (selectedStage == VisionStage.NORMALIZED_REGION
//                    || selectedStage == VisionStage.LAYOUT_MAP
//                    || selectedStage
//                    == VisionStage.BUBBLE_MEASUREMENTS) {
////            if (selectedStage == VisionStage.NORMALIZED_REGION
////                    || selectedStage == VisionStage.LAYOUT_MAP) {
//
//                normalizationResult =
//                        normalizeStableRegion(
//                                cleanNormalizationFrame
//                        );
//
//                /*
//                 * Na etapa 9 desenhamos o mapa diretamente sobre a
//                 * região normalizada, antes de montar a pré-visualização.
//                 */
//                if (selectedStage == VisionStage.LAYOUT_MAP
//                        && normalizationResult.isSuccess()) {
//
//                    layoutOverlayRenderer.draw(
//                            normalizationResult
//                                    .getNormalizedRegion(),
//                            layoutDefinition
//                    );
//                }
//
//                normalizedPreviewFrame =
//                        normalizedRegionPreviewRenderer
//                                .render(
//                                        normalizationResult,
//                                        rgbaFrame.cols(),
//                                        rgbaFrame.rows()
//                                );
//
//                debugController.publish(
//                        selectedStage,
//                        normalizedPreviewFrame
//                );
//            }
//            if (debugController.getSelectedStage()
//                    == VisionStage.NORMALIZED_REGION) {
//
//                normalizationResult =
//                        normalizeStableRegion(
//                                cleanNormalizationFrame
//                        );
//
//                normalizedPreviewFrame =
//                        normalizedRegionPreviewRenderer
//                                .render(
//                                        normalizationResult,
//                                        rgbaFrame.cols(),
//                                        rgbaFrame.rows()
//                                );
//
//                debugController.publish(
//                        VisionStage.NORMALIZED_REGION,
//                        normalizedPreviewFrame
//                );
//            }

            /*
             * A pausa automática ocorre somente depois que a
             * imagem da etapa selecionada foi publicada.
             */
            boolean normalizedRegionAvailable =
                    normalizationResult != null
                            && normalizationResult.isSuccess()
                            && normalizationResult.getNormalizedRegion() != null
                            && !normalizationResult
                            .getNormalizedRegion()
                            .empty();

            boolean measurementsAvailable =
                    lastMeasurementResult != null
                            && lastMeasurementResult.isComplete();

            boolean questionComparisonAvailable =
                    lastQuestionMeasurements != null
                            && lastQuestionMeasurements.size()
                            == layoutDefinition.getQuestionCount();

            applyAutomaticFreezeIfEligible(
                    normalizedRegionAvailable,
                    measurementsAvailable,
                    questionComparisonAvailable
            );
//            applyAutomaticFreezeIfEligible(
//                    normalizedRegionAvailable,
//                    measurementsAvailable
//            );
//            applyAutomaticFreezeIfEligible(
//                    normalizedRegionAvailable
//            );

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

            if (cleanNormalizationFrame != null) {
                cleanNormalizationFrame.release();
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

    public OmrSheetMeasurementResult
    getLastMeasurementResult() {

        return lastMeasurementResult;
    }

    public List<QuestionMeasurement>
    getLastQuestionMeasurements() {

        return lastQuestionMeasurements;
    }

    public void resetStability() {
        markerSetStabilizer.reset();

        lastStabilityResult = null;

        lastMeasurementResult = null;

        autoFreezeConsumedStage = null;

        lastQuestionMeasurements = null;
    }

}
