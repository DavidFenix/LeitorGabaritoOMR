package com.example.leitorgabaritoomr.vision.processing;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.debug.BubbleMeasurementDiagnosticLogger;
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

import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAccumulator;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAccumulatorConfig;
import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.drawing.TemporalConsensusOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.BubbleRegistrationCandidateOverlayRenderer;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractionResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractor;
import com.example.leitorgabaritoomr.vision.registration.BubbleGridRegistrarConfig;

import org.opencv.imgproc.Imgproc;

public final class MarkerFrameProcessor {

    private final BubbleMeasurementDiagnosticLogger
            bubbleMeasurementDiagnosticLogger =
            new BubbleMeasurementDiagnosticLogger();

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

    private final BubbleContourExtractor
            bubbleContourExtractor;

    private final BubbleRegistrationCandidateOverlayRenderer
            bubbleRegistrationCandidateOverlayRenderer;

    private final OmrLayoutMeasurer layoutMeasurer;

    private final BubbleMeasurementOverlayRenderer
            bubbleMeasurementOverlayRenderer;

    private final OmrQuestionMeasurer
            questionMeasurer;

    private final QuestionComparisonOverlayRenderer
            questionComparisonOverlayRenderer;

    private final QuestionEvidenceAccumulator
            questionEvidenceAccumulator;

    private final TemporalConsensusOverlayRenderer
            temporalConsensusOverlayRenderer;

    private volatile SheetEvidenceAggregate
            lastSheetEvidenceAggregate;

    private volatile List<QuestionMeasurement>
            lastQuestionMeasurements;

    private volatile OmrSheetMeasurementResult
            lastMeasurementResult;

    private volatile BubbleContourExtractionResult
            lastBubbleContourExtractionResult;

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
                new QuestionComparisonOverlayRenderer(),
                new QuestionEvidenceAccumulator(
                        QuestionEvidenceAccumulatorConfig
                                .developmentDefaults(),
                        AvalieCeDevelopmentLayoutFactory.create()
                ),
                new TemporalConsensusOverlayRenderer()
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
            QuestionComparisonOverlayRenderer questionComparisonOverlayRenderer,
            QuestionEvidenceAccumulator questionEvidenceAccumulator,
            TemporalConsensusOverlayRenderer temporalConsensusOverlayRenderer
    ) {
        this(
                detector,
                markerRenderer,
                markerSetResolver,
                resolvedMarkerRenderer,
                markerSetStabilizer,
                stableMarkerRenderer,
                debugController,
                regionNormalizer,
                normalizedRegionPreviewRenderer,
                layoutDefinition,
                layoutOverlayRenderer,
                layoutMeasurer,
                bubbleMeasurementOverlayRenderer,
                questionMeasurer,
                questionComparisonOverlayRenderer,
                questionEvidenceAccumulator,
                temporalConsensusOverlayRenderer,
                new BubbleContourExtractor(
                        BubbleGridRegistrarConfig
                                .developmentDefaults()
                ),
                new BubbleRegistrationCandidateOverlayRenderer()
        );
    }

    /**
     * Construtor mais completo, incluindo o laboratório de
     * registro geométrico das bolhas.
     *
     * Mantemos o construtor anterior para não quebrar código
     * e testes que já o utilizem.
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
            QuestionComparisonOverlayRenderer questionComparisonOverlayRenderer,
            QuestionEvidenceAccumulator questionEvidenceAccumulator,
            TemporalConsensusOverlayRenderer temporalConsensusOverlayRenderer,
            BubbleContourExtractor bubbleContourExtractor,
            BubbleRegistrationCandidateOverlayRenderer
                    bubbleRegistrationCandidateOverlayRenderer
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
                || questionComparisonOverlayRenderer == null
                || questionEvidenceAccumulator == null
                || temporalConsensusOverlayRenderer == null
                || bubbleContourExtractor == null
                || bubbleRegistrationCandidateOverlayRenderer == null) {

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

        this.bubbleContourExtractor =
                bubbleContourExtractor;

        this.bubbleRegistrationCandidateOverlayRenderer =
                bubbleRegistrationCandidateOverlayRenderer;

        this.layoutMeasurer =
                layoutMeasurer;

        this.bubbleMeasurementOverlayRenderer =
                bubbleMeasurementOverlayRenderer;

        this.questionMeasurer =
                questionMeasurer;

        this.questionComparisonOverlayRenderer =
                questionComparisonOverlayRenderer;

        this.questionEvidenceAccumulator =
                questionEvidenceAccumulator;

        this.temporalConsensusOverlayRenderer =
                temporalConsensusOverlayRenderer;

        this.lastSheetEvidenceAggregate =
                questionEvidenceAccumulator
                        .getCurrentSnapshot();
    }

    private void applyAutomaticFreezeIfEligible(
            boolean normalizedRegionAvailable,
            boolean bubbleRegistrationAvailable,
            boolean measurementsAvailable,
            boolean questionComparisonAvailable,
            boolean temporalConsensusAvailable
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
                != VisionStage.BUBBLE_REGISTRATION
                && selectedStage
                != VisionStage.BUBBLE_MEASUREMENTS
                && selectedStage
                != VisionStage.QUESTION_COMPARISON
                && selectedStage
                != VisionStage.TEMPORAL_CONSENSUS) {

            return;
        }

        boolean requiresNormalizedRegion =
                selectedStage
                        == VisionStage.NORMALIZED_REGION
                        || selectedStage
                        == VisionStage.LAYOUT_MAP
                        || selectedStage
                        == VisionStage.BUBBLE_REGISTRATION
                        || selectedStage
                        == VisionStage.BUBBLE_MEASUREMENTS
                        || selectedStage
                        == VisionStage.QUESTION_COMPARISON
                        || selectedStage
                        == VisionStage.TEMPORAL_CONSENSUS;

        if (requiresNormalizedRegion
                && !normalizedRegionAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.BUBBLE_REGISTRATION
                && !bubbleRegistrationAvailable) {

            return;
        }

        if ((selectedStage
                == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage
                == VisionStage.QUESTION_COMPARISON
                || selectedStage
                == VisionStage.TEMPORAL_CONSENSUS)
                && !measurementsAvailable) {

            return;
        }

        if ((selectedStage
                == VisionStage.QUESTION_COMPARISON
                || selectedStage
                == VisionStage.TEMPORAL_CONSENSUS)
                && !questionComparisonAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.TEMPORAL_CONSENSUS
                && !temporalConsensusAvailable) {

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

        /*
         * A normalização precisa utilizar o frame original(limpo),
         * antes que candidatos, textos e quadriláteros sejam
         * desenhados sobre ele.
         */
        if (selectedStage == VisionStage.NORMALIZED_REGION
                || selectedStage == VisionStage.LAYOUT_MAP
                || selectedStage == VisionStage.BUBBLE_REGISTRATION
                || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage == VisionStage.QUESTION_COMPARISON
                || selectedStage == VisionStage.TEMPORAL_CONSENSUS) {

            cleanNormalizationFrame =
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

            resetTemporalConsensusIfGeometryChanged();

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
                    || selectedStage == VisionStage.BUBBLE_REGISTRATION
                    || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
                    || selectedStage == VisionStage.QUESTION_COMPARISON
                    || selectedStage == VisionStage.TEMPORAL_CONSENSUS) {

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
                            == VisionStage.BUBBLE_REGISTRATION) {

                        lastBubbleContourExtractionResult =
                                null;

                        Mat normalizedGray =
                                createGrayImage(
                                        normalizationResult
                                                .getNormalizedRegion()
                                );

                        try {
                            lastBubbleContourExtractionResult =
                                    bubbleContourExtractor.extract(
                                            normalizedGray,
                                            layoutDefinition
                                    );

                            bubbleRegistrationCandidateOverlayRenderer.draw(
                                    normalizationResult
                                            .getNormalizedRegion(),
                                    lastBubbleContourExtractionResult
                            );
                        } finally {
                            normalizedGray.release();
                        }
                    }

                    /*
                     * O registro ainda está somente no Laboratório.
                     * As medições continuam usando o layout esperado
                     * até validarmos os candidatos extraídos.
                     */
                    if (selectedStage
                            == VisionStage.BUBBLE_MEASUREMENTS
                            || selectedStage
                            == VisionStage.QUESTION_COMPARISON
                            || selectedStage
                            == VisionStage.TEMPORAL_CONSENSUS) {

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

                        if (selectedStage
                                == VisionStage.TEMPORAL_CONSENSUS
                                && lastQuestionMeasurements != null) {

                            /*
                             * Somente confirmações STABLE reais entram no consenso.
                             * HELD_STABLE conserva o resultado, mas não conta frame.
                             */
                            if (lastStabilityResult != null
                                    && lastStabilityResult.getState()
                                    == MarkerStabilityState.STABLE) {

                                lastSheetEvidenceAggregate =
                                        questionEvidenceAccumulator.update(
                                                lastQuestionMeasurements
                                        );

                            } else {
                                lastSheetEvidenceAggregate =
                                        questionEvidenceAccumulator
                                                .getCurrentSnapshot();
                            }

                            temporalConsensusOverlayRenderer.draw(
                                    normalizationResult
                                            .getNormalizedRegion(),
                                    lastSheetEvidenceAggregate
                            );

                            if (lastSheetEvidenceAggregate != null
                                    && lastSheetEvidenceAggregate.isReady()) {

                                bubbleMeasurementDiagnosticLogger.logOnce(
                                        lastQuestionMeasurements
                                );
                            }

                        }
                    }

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

            boolean bubbleRegistrationAvailable =
                    normalizedRegionAvailable
                            && selectedStage
                            == VisionStage.BUBBLE_REGISTRATION
                            && lastBubbleContourExtractionResult
                            != null;

            boolean measurementsAvailable =
                    normalizedRegionAvailable
                            && lastMeasurementResult != null
                            && lastMeasurementResult.isComplete();

            boolean questionComparisonAvailable =
                    normalizedRegionAvailable
                            && lastQuestionMeasurements != null
                            && lastQuestionMeasurements.size()
                            == layoutDefinition.getQuestionCount();

            boolean temporalConsensusAvailable =
                    normalizedRegionAvailable
                            && lastSheetEvidenceAggregate != null
                            && lastSheetEvidenceAggregate.isReady();

            //chamada da pausa
            applyAutomaticFreezeIfEligible(
                    normalizedRegionAvailable,
                    bubbleRegistrationAvailable,
                    measurementsAvailable,
                    questionComparisonAvailable,
                    temporalConsensusAvailable
            );

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

    /**
     * Produz a imagem de um canal que realmente será entregue
     * ao BubbleContourExtractor.
     */
    private Mat createGrayImage(Mat source) {
        if (source == null || source.empty()) {
            throw new IllegalArgumentException(
                    "A imagem normalizada é obrigatória."
            );
        }

        Mat gray = new Mat();

        int channels = source.channels();

        if (channels == 1) {
            source.copyTo(gray);

            return gray;
        }

        if (channels == 4) {
            Imgproc.cvtColor(
                    source,
                    gray,
                    Imgproc.COLOR_RGBA2GRAY
            );

            return gray;
        }

        if (channels == 3) {
            Imgproc.cvtColor(
                    source,
                    gray,
                    Imgproc.COLOR_BGR2GRAY
            );

            return gray;
        }

        gray.release();

        throw new IllegalArgumentException(
                "Quantidade de canais não suportada: "
                        + channels
        );
    }

    private void resetTemporalConsensusIfGeometryChanged() {
        if (lastStabilityResult == null) {
            return;
        }

        MarkerStabilityState state =
                lastStabilityResult.getState();

        /*
         * HELD_STABLE representa uma falha breve e conserva
         * o consenso já acumulado.
         *
         * ACCUMULATING representa uma nova sequência geométrica,
         * portanto não deve ser misturada com a anterior.
         */
        if (state == MarkerStabilityState.SEARCHING
                || state == MarkerStabilityState.LOST
                || state == MarkerStabilityState.ACCUMULATING) {

            if (questionEvidenceAccumulator
                    .getAccumulatedFrames() > 0) {

                questionEvidenceAccumulator.reset();
                bubbleMeasurementDiagnosticLogger.reset();
            }

            lastSheetEvidenceAggregate =
                    questionEvidenceAccumulator
                            .getCurrentSnapshot();
        }
    }

    private OmrRegionNormalizationResult
    normalizeStableRegion(Mat sourceFrame) {

        if (lastStabilityResult == null) {
            return OmrRegionNormalizationResult.failure(
                    "Ainda não existe resultado de estabilidade."
            );
        }

        /*
         * Somente STABLE significa:
         *
         * - os quatro marcadores foram encontrados no frame atual;
         * - suas posições pertencem ao próprio frame que será
         *   normalizado.
         *
         * HELD_STABLE conserva confiança histórica, mas contém
         * coordenadas de um frame anterior. Portanto, não pode
         * normalizar nem medir o frame atual.
         */
        if (lastStabilityResult.getState()
                != MarkerStabilityState.STABLE) {

            return OmrRegionNormalizationResult.failure(
                    "Frame atual sem quatro marcadores confirmados. Estado: "
                            + lastStabilityResult.getState()
            );
        }

        if (lastStabilityResult.getMarkerSet()
                == null) {

            return OmrRegionNormalizationResult.failure(
                    "O frame está estável, mas não possui marcadores."
            );
        }

        ResolvedMarkerSet markerSet =
                lastStabilityResult.getMarkerSet();

        Point topLeft =
                markerSet.get(
                        CornerRole.TOP_LEFT
                ).getCenter();

        Point topRight =
                markerSet.get(
                        CornerRole.TOP_RIGHT
                ).getCenter();

        Point bottomRight =
                markerSet.get(
                        CornerRole.BOTTOM_RIGHT
                ).getCenter();

        Point bottomLeft =
                markerSet.get(
                        CornerRole.BOTTOM_LEFT
                ).getCenter();

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

    public BubbleContourExtractionResult
    getLastBubbleContourExtractionResult() {

        return lastBubbleContourExtractionResult;
    }

    public List<QuestionMeasurement>
    getLastQuestionMeasurements() {

        return lastQuestionMeasurements;
    }

    public SheetEvidenceAggregate
    getLastSheetEvidenceAggregate() {

        return lastSheetEvidenceAggregate;
    }

    public void resetStability() {
        markerSetStabilizer.reset();

        lastStabilityResult = null;
        lastBubbleContourExtractionResult = null;
        lastMeasurementResult = null;
        lastQuestionMeasurements = null;

        questionEvidenceAccumulator.reset();

        bubbleMeasurementDiagnosticLogger.reset();

        lastSheetEvidenceAggregate =
                questionEvidenceAccumulator
                        .getCurrentSnapshot();

        autoFreezeConsumedStage = null;

    }

}

//package com.example.leitorgabaritoomr.vision.processing;
//
//import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
//import com.example.leitorgabaritoomr.vision.debug.VisionStage;
//import com.example.leitorgabaritoomr.vision.debug.BubbleMeasurementDiagnosticLogger;
//import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
//import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.drawing.NormalizedRegionPreviewRenderer;
//import com.example.leitorgabaritoomr.vision.drawing.ResolvedMarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.drawing.StableMarkerOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.geometry.CornerRole;
//import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
//import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolver;
//import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;
//import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
//import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizationResult;
//import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizer;
//import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizerConfig;
//import com.example.leitorgabaritoomr.vision.stability.MarkerSetStabilizer;
//import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;
//import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;
//
//import org.opencv.core.Mat;
//import org.opencv.core.Point;
//
//import com.example.leitorgabaritoomr.vision.drawing.LayoutOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
//import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
////import com.example.leitorgabaritoomr.vision.layout.factory.ThreeColumnsFifteenLayoutFactory;
//
//import com.example.leitorgabaritoomr.vision.drawing.BubbleMeasurementOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurementConfig;
//import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurer;
//import com.example.leitorgabaritoomr.vision.measurement.OmrLayoutMeasurer;
//import com.example.leitorgabaritoomr.vision.measurement.OmrSheetMeasurementResult;
//
//import com.example.leitorgabaritoomr.vision.drawing.QuestionComparisonOverlayRenderer;
//import com.example.leitorgabaritoomr.vision.measurement.OmrQuestionMeasurer;
//import com.example.leitorgabaritoomr.vision.measurement.QuestionMeasurement;
//import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorer;
//import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorerConfig;
//
//import java.util.List;
//
//import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAccumulator;
//import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAccumulatorConfig;
//import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
//import com.example.leitorgabaritoomr.vision.drawing.TemporalConsensusOverlayRenderer;
//
//public final class MarkerFrameProcessor {
//
//    private final BubbleMeasurementDiagnosticLogger
//            bubbleMeasurementDiagnosticLogger =
//            new BubbleMeasurementDiagnosticLogger();
//
//    /*
//     * Registra em qual etapa ocorreu a última pausa automática.
//     *
//     * Assim, a etapa 7 pode pausar uma vez e a etapa 8 também
//     * pode pausar uma vez para o mesmo conjunto estável.
//     */
//    private VisionStage autoFreezeConsumedStage = null;
//
//    private final OmrMarkerDetector detector;
//
//    private final MarkerOverlayRenderer markerRenderer;
//
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
//    private final OmrRegionNormalizer regionNormalizer;
//
//    private final NormalizedRegionPreviewRenderer
//            normalizedRegionPreviewRenderer;
//
//    private final OmrLayoutDefinition layoutDefinition;
//
//    private final LayoutOverlayRenderer
//            layoutOverlayRenderer;
//
//    private final OmrLayoutMeasurer layoutMeasurer;
//
//    private final BubbleMeasurementOverlayRenderer
//            bubbleMeasurementOverlayRenderer;
//
//    private final OmrQuestionMeasurer
//            questionMeasurer;
//
//    private final QuestionComparisonOverlayRenderer
//            questionComparisonOverlayRenderer;
//
//    private final QuestionEvidenceAccumulator
//            questionEvidenceAccumulator;
//
//    private final TemporalConsensusOverlayRenderer
//            temporalConsensusOverlayRenderer;
//
//    private volatile SheetEvidenceAggregate
//            lastSheetEvidenceAggregate;
//
//    private volatile List<QuestionMeasurement>
//            lastQuestionMeasurements;
//
//    private volatile OmrSheetMeasurementResult
//            lastMeasurementResult;
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
//        this(
//                detector,
//                markerRenderer,
//                markerSetResolver,
//                resolvedMarkerRenderer,
//                markerSetStabilizer,
//                stableMarkerRenderer,
//                debugController,
//                new OmrRegionNormalizer(
//                        OmrRegionNormalizerConfig
//                                .developmentDefaults()
//                ),
//                new NormalizedRegionPreviewRenderer(),
//                AvalieCeDevelopmentLayoutFactory.create(),
//                new LayoutOverlayRenderer(),
//                new OmrLayoutMeasurer(
//                        new BubbleMeasurer(
//                                BubbleMeasurementConfig
//                                        .developmentDefaults()
//                        )
//                ),
//                new BubbleMeasurementOverlayRenderer(),
//                new OmrQuestionMeasurer(
//                        new BubbleEvidenceScorer(
//                                BubbleEvidenceScorerConfig
//                                        .developmentDefaults()
//                        )
//                ),
//                new QuestionComparisonOverlayRenderer(),
//                new QuestionEvidenceAccumulator(
//                        QuestionEvidenceAccumulatorConfig
//                                .developmentDefaults(),
//                        AvalieCeDevelopmentLayoutFactory.create()
//                ),
//                new TemporalConsensusOverlayRenderer()
//        );
//    }
//
//    /**
//     * Construtor completo para permitir testes e configurações
//     * diferentes no futuro.
//     *
//     * O construtor anterior continua existindo, portanto não é
//     * necessário alterar a MainActivity agora.
//     */
//    public MarkerFrameProcessor(
//            OmrMarkerDetector detector,
//            MarkerOverlayRenderer markerRenderer,
//            MarkerSetResolver markerSetResolver,
//            ResolvedMarkerOverlayRenderer resolvedMarkerRenderer,
//            MarkerSetStabilizer markerSetStabilizer,
//            StableMarkerOverlayRenderer stableMarkerRenderer,
//            VisionDebugController debugController,
//            OmrRegionNormalizer regionNormalizer,
//            NormalizedRegionPreviewRenderer normalizedRegionPreviewRenderer,
//            OmrLayoutDefinition layoutDefinition,
//            LayoutOverlayRenderer layoutOverlayRenderer,
//            OmrLayoutMeasurer layoutMeasurer,
//            BubbleMeasurementOverlayRenderer bubbleMeasurementOverlayRenderer,
//            OmrQuestionMeasurer questionMeasurer,
//            QuestionComparisonOverlayRenderer questionComparisonOverlayRenderer,
//            QuestionEvidenceAccumulator questionEvidenceAccumulator,
//            TemporalConsensusOverlayRenderer temporalConsensusOverlayRenderer
//    ) {
//        if (detector == null
//                || markerRenderer == null
//                || markerSetResolver == null
//                || resolvedMarkerRenderer == null
//                || markerSetStabilizer == null
//                || stableMarkerRenderer == null
//                || debugController == null
//                || regionNormalizer == null
//                || normalizedRegionPreviewRenderer == null
//                || layoutDefinition == null
//                || layoutOverlayRenderer == null
//                || layoutMeasurer == null
//                || bubbleMeasurementOverlayRenderer == null
//                || questionMeasurer == null
//                || questionComparisonOverlayRenderer == null
//                || questionEvidenceAccumulator == null
//                || temporalConsensusOverlayRenderer == null) {
//
//            throw new IllegalArgumentException(
//                    "Todos os componentes do processamento são obrigatórios."
//            );
//        }
//
//        this.detector = detector;
//        this.markerRenderer = markerRenderer;
//        this.markerSetResolver = markerSetResolver;
//
//        this.resolvedMarkerRenderer =
//                resolvedMarkerRenderer;
//
//        this.markerSetStabilizer =
//                markerSetStabilizer;
//
//        this.stableMarkerRenderer =
//                stableMarkerRenderer;
//
//        this.debugController =
//                debugController;
//
//        this.regionNormalizer =
//                regionNormalizer;
//
//        this.normalizedRegionPreviewRenderer =
//                normalizedRegionPreviewRenderer;
//
//        this.layoutDefinition =
//                layoutDefinition;
//
//        this.layoutOverlayRenderer =
//                layoutOverlayRenderer;
//
//        this.layoutMeasurer =
//                layoutMeasurer;
//
//        this.bubbleMeasurementOverlayRenderer =
//                bubbleMeasurementOverlayRenderer;
//
//        this.questionMeasurer =
//                questionMeasurer;
//
//        this.questionComparisonOverlayRenderer =
//                questionComparisonOverlayRenderer;
//
//        this.questionEvidenceAccumulator =
//                questionEvidenceAccumulator;
//
//        this.temporalConsensusOverlayRenderer =
//                temporalConsensusOverlayRenderer;
//
//        this.lastSheetEvidenceAggregate =
//                questionEvidenceAccumulator
//                        .getCurrentSnapshot();
//    }
//
//    private void applyAutomaticFreezeIfEligible(
//            boolean normalizedRegionAvailable,
//            boolean measurementsAvailable,
//            boolean questionComparisonAvailable,
//            boolean temporalConsensusAvailable
//    ) {
//        if (lastStabilityResult == null) {
//            return;
//        }
//
//        MarkerStabilityState state =
//                lastStabilityResult.getState();
//
//        /*
//         * Quando o conjunto é realmente perdido,
//         * todas as etapas ficam novamente autorizadas
//         * a realizar uma pausa automática.
//         */
//        if (state == MarkerStabilityState.SEARCHING
//                || state == MarkerStabilityState.LOST) {
//
//            autoFreezeConsumedStage = null;
//
//            return;
//        }
//
//        /*
//         * HELD_STABLE conserva a última solução,
//         * mas não inicia uma nova pausa automática.
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
//        VisionStage selectedStage =
//                debugController.getSelectedStage();
//
//        if (selectedStage
//                != VisionStage.STABLE_MARKERS
//                && selectedStage
//                != VisionStage.NORMALIZED_REGION
//                && selectedStage
//                != VisionStage.LAYOUT_MAP
//                && selectedStage
//                != VisionStage.BUBBLE_MEASUREMENTS
//                && selectedStage
//                != VisionStage.QUESTION_COMPARISON
//                && selectedStage
//                != VisionStage.TEMPORAL_CONSENSUS) {
//
//            return;
//        }
//
//        boolean requiresNormalizedRegion =
//                selectedStage
//                        == VisionStage.NORMALIZED_REGION
//                        || selectedStage
//                        == VisionStage.LAYOUT_MAP
//                        || selectedStage
//                        == VisionStage.BUBBLE_MEASUREMENTS
//                        || selectedStage
//                        == VisionStage.QUESTION_COMPARISON
//                        || selectedStage
//                        == VisionStage.TEMPORAL_CONSENSUS;
//
//        if (requiresNormalizedRegion
//                && !normalizedRegionAvailable) {
//
//            return;
//        }
//
//        if ((selectedStage
//                == VisionStage.BUBBLE_MEASUREMENTS
//                || selectedStage
//                == VisionStage.QUESTION_COMPARISON
//                || selectedStage
//                == VisionStage.TEMPORAL_CONSENSUS)
//                && !measurementsAvailable) {
//
//            return;
//        }
//
//        if ((selectedStage
//                == VisionStage.QUESTION_COMPARISON
//                || selectedStage
//                == VisionStage.TEMPORAL_CONSENSUS)
//                && !questionComparisonAvailable) {
//
//            return;
//        }
//
//        if (selectedStage
//                == VisionStage.TEMPORAL_CONSENSUS
//                && !temporalConsensusAvailable) {
//
//            return;
//        }
//
//        /*
//         * A etapa atual já utilizou sua pausa para
//         * este conjunto estável.
//         */
//        if (selectedStage == autoFreezeConsumedStage) {
//            return;
//        }
//
//        boolean frozen =
//                debugController.freeze();
//
//        if (frozen) {
//            autoFreezeConsumedStage =
//                    selectedStage;
//        }
//    }
//
//    public MarkerDetectionResult process(
//            Mat grayFrame,
//            Mat rgbaFrame
//    ) {
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
//         * Criamos cópias limpas somente quando a respectiva
//         * etapa está selecionada.
//         */
//        Mat cleanStabilityFrame = null;
//        Mat cleanNormalizationFrame = null;
//        Mat normalizedPreviewFrame = null;
//
//        OmrRegionNormalizationResult
//                normalizationResult = null;
//
//        if (debugController.getSelectedStage()
//                == VisionStage.STABLE_MARKERS) {
//
//            cleanStabilityFrame =
//                    rgbaFrame.clone();
//        }
//
//        VisionStage selectedStage =
//                debugController.getSelectedStage();
//
//        /*
//         * A normalização precisa utilizar o frame original(limpo),
//         * antes que candidatos, textos e quadriláteros sejam
//         * desenhados sobre ele.
//         */
//        if (selectedStage == VisionStage.NORMALIZED_REGION
//                || selectedStage == VisionStage.LAYOUT_MAP
//                || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
//                || selectedStage == VisionStage.QUESTION_COMPARISON
//                || selectedStage == VisionStage.TEMPORAL_CONSENSUS) {
//
//            cleanNormalizationFrame =
//                    rgbaFrame.clone();
//        }
//
//        try {
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
//            resetTemporalConsensusIfGeometryChanged();
//
//            if (cleanStabilityFrame != null) {
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
//            if (selectedStage == VisionStage.NORMALIZED_REGION
//                    || selectedStage == VisionStage.LAYOUT_MAP
//                    || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
//                    || selectedStage == VisionStage.QUESTION_COMPARISON
//                    || selectedStage == VisionStage.TEMPORAL_CONSENSUS) {
//
//                normalizationResult =
//                        normalizeStableRegion(
//                                cleanNormalizationFrame
//                        );
//
//                if (normalizationResult.isSuccess()) {
//
//                    if (selectedStage
//                            == VisionStage.LAYOUT_MAP) {
//
//                        layoutOverlayRenderer.draw(
//                                normalizationResult
//                                        .getNormalizedRegion(),
//                                layoutDefinition
//                        );
//                    }
//
//                    //etapa 12
//                    if (selectedStage
//                            == VisionStage.BUBBLE_MEASUREMENTS
//                            || selectedStage
//                            == VisionStage.QUESTION_COMPARISON
//                            || selectedStage
//                            == VisionStage.TEMPORAL_CONSENSUS) {
//
//                        lastMeasurementResult =
//                                layoutMeasurer.measure(
//                                        normalizationResult
//                                                .getNormalizedRegion(),
//                                        layoutDefinition
//                                );
//
//                        if (lastMeasurementResult.isComplete()) {
//                            lastQuestionMeasurements =
//                                    questionMeasurer.measure(
//                                            lastMeasurementResult
//                                    );
//                        } else {
//                            lastQuestionMeasurements = null;
//                        }
//
//                        if (selectedStage
//                                == VisionStage.BUBBLE_MEASUREMENTS) {
//
//                            bubbleMeasurementOverlayRenderer.draw(
//                                    normalizationResult
//                                            .getNormalizedRegion(),
//                                    lastMeasurementResult
//                            );
//                        }
//
//                        if (selectedStage
//                                == VisionStage.QUESTION_COMPARISON
//                                && lastQuestionMeasurements != null) {
//
//                            questionComparisonOverlayRenderer.draw(
//                                    normalizationResult
//                                            .getNormalizedRegion(),
//                                    lastQuestionMeasurements
//                            );
//                        }
//
//                        if (selectedStage
//                                == VisionStage.TEMPORAL_CONSENSUS
//                                && lastQuestionMeasurements != null) {
//
//                            /*
//                             * Somente confirmações STABLE reais entram no consenso.
//                             * HELD_STABLE conserva o resultado, mas não conta frame.
//                             */
//                            if (lastStabilityResult != null
//                                    && lastStabilityResult.getState()
//                                    == MarkerStabilityState.STABLE) {
//
//                                lastSheetEvidenceAggregate =
//                                        questionEvidenceAccumulator.update(
//                                                lastQuestionMeasurements
//                                        );
//
//                            } else {
//                                lastSheetEvidenceAggregate =
//                                        questionEvidenceAccumulator
//                                                .getCurrentSnapshot();
//                            }
//
//                            temporalConsensusOverlayRenderer.draw(
//                                    normalizationResult
//                                            .getNormalizedRegion(),
//                                    lastSheetEvidenceAggregate
//                            );
//
//                            if (lastSheetEvidenceAggregate != null
//                                    && lastSheetEvidenceAggregate.isReady()) {
//
//                                bubbleMeasurementDiagnosticLogger.logOnce(
//                                        lastQuestionMeasurements
//                                );
//                            }
//
//                        }
//                    }
//
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
//
//            /*
//             * A pausa automática ocorre somente depois que a
//             * imagem da etapa selecionada foi publicada.
//             */
//            boolean normalizedRegionAvailable =
//                    normalizationResult != null
//                            && normalizationResult.isSuccess()
//                            && normalizationResult.getNormalizedRegion() != null
//                            && !normalizationResult
//                            .getNormalizedRegion()
//                            .empty();
//
//            boolean measurementsAvailable =
//                    normalizedRegionAvailable
//                            && lastMeasurementResult != null
//                            && lastMeasurementResult.isComplete();
//
//            boolean questionComparisonAvailable =
//                    normalizedRegionAvailable
//                            && lastQuestionMeasurements != null
//                            && lastQuestionMeasurements.size()
//                            == layoutDefinition.getQuestionCount();
//
//            boolean temporalConsensusAvailable =
//                    normalizedRegionAvailable
//                            && lastSheetEvidenceAggregate != null
//                            && lastSheetEvidenceAggregate.isReady();
//
//            //chamada da pausa
//            applyAutomaticFreezeIfEligible(
//                    normalizedRegionAvailable,
//                    measurementsAvailable,
//                    questionComparisonAvailable,
//                    temporalConsensusAvailable
//            );
//
//            debugController.renderSelectedStage(
//                    rgbaFrame
//            );
//
//            return lastDetectionResult;
//
//        } finally {
//            if (normalizationResult != null) {
//                normalizationResult.release();
//            }
//
//            if (normalizedPreviewFrame != null) {
//                normalizedPreviewFrame.release();
//            }
//
//            if (cleanNormalizationFrame != null) {
//                cleanNormalizationFrame.release();
//            }
//
//            if (cleanStabilityFrame != null) {
//                cleanStabilityFrame.release();
//            }
//        }
//    }
//
//    private void resetTemporalConsensusIfGeometryChanged() {
//        if (lastStabilityResult == null) {
//            return;
//        }
//
//        MarkerStabilityState state =
//                lastStabilityResult.getState();
//
//        /*
//         * HELD_STABLE representa uma falha breve e conserva
//         * o consenso já acumulado.
//         *
//         * ACCUMULATING representa uma nova sequência geométrica,
//         * portanto não deve ser misturada com a anterior.
//         */
//        if (state == MarkerStabilityState.SEARCHING
//                || state == MarkerStabilityState.LOST
//                || state == MarkerStabilityState.ACCUMULATING) {
//
//            if (questionEvidenceAccumulator
//                    .getAccumulatedFrames() > 0) {
//
//                questionEvidenceAccumulator.reset();
//                bubbleMeasurementDiagnosticLogger.reset();
//            }
//
//            lastSheetEvidenceAggregate =
//                    questionEvidenceAccumulator
//                            .getCurrentSnapshot();
//        }
//    }
//
//    private OmrRegionNormalizationResult
//    normalizeStableRegion(Mat sourceFrame) {
//
//        if (lastStabilityResult == null) {
//            return OmrRegionNormalizationResult.failure(
//                    "Ainda não existe resultado de estabilidade."
//            );
//        }
//
//        /*
//         * Somente STABLE significa:
//         *
//         * - os quatro marcadores foram encontrados no frame atual;
//         * - suas posições pertencem ao próprio frame que será
//         *   normalizado.
//         *
//         * HELD_STABLE conserva confiança histórica, mas contém
//         * coordenadas de um frame anterior. Portanto, não pode
//         * normalizar nem medir o frame atual.
//         */
//        if (lastStabilityResult.getState()
//                != MarkerStabilityState.STABLE) {
//
//            return OmrRegionNormalizationResult.failure(
//                    "Frame atual sem quatro marcadores confirmados. Estado: "
//                            + lastStabilityResult.getState()
//            );
//        }
//
//        if (lastStabilityResult.getMarkerSet()
//                == null) {
//
//            return OmrRegionNormalizationResult.failure(
//                    "O frame está estável, mas não possui marcadores."
//            );
//        }
//
//        ResolvedMarkerSet markerSet =
//                lastStabilityResult.getMarkerSet();
//
//        Point topLeft =
//                markerSet.get(
//                        CornerRole.TOP_LEFT
//                ).getCenter();
//
//        Point topRight =
//                markerSet.get(
//                        CornerRole.TOP_RIGHT
//                ).getCenter();
//
//        Point bottomRight =
//                markerSet.get(
//                        CornerRole.BOTTOM_RIGHT
//                ).getCenter();
//
//        Point bottomLeft =
//                markerSet.get(
//                        CornerRole.BOTTOM_LEFT
//                ).getCenter();
//
//        return regionNormalizer.normalize(
//                sourceFrame,
//                topLeft,
//                topRight,
//                bottomRight,
//                bottomLeft
//        );
//    }
//
//    public String getDetectorName() {
//        return detector.getName();
//    }
//
//    public MarkerSetResolutionResult
//    getLastResolutionResult() {
//        return lastResolutionResult;
//    }
//
//    public MarkerStabilityResult
//    getLastStabilityResult() {
//        return lastStabilityResult;
//    }
//
//    public OmrSheetMeasurementResult
//    getLastMeasurementResult() {
//
//        return lastMeasurementResult;
//    }
//
//    public List<QuestionMeasurement>
//    getLastQuestionMeasurements() {
//
//        return lastQuestionMeasurements;
//    }
//
//    public SheetEvidenceAggregate
//    getLastSheetEvidenceAggregate() {
//
//        return lastSheetEvidenceAggregate;
//    }
//
//    public void resetStability() {
//        markerSetStabilizer.reset();
//
//        lastStabilityResult = null;
//        lastMeasurementResult = null;
//        lastQuestionMeasurements = null;
//
//        questionEvidenceAccumulator.reset();
//
//        bubbleMeasurementDiagnosticLogger.reset();
//
//        lastSheetEvidenceAggregate =
//                questionEvidenceAccumulator
//                        .getCurrentSnapshot();
//
//        autoFreezeConsumedStage = null;
//
//    }
//
//}
