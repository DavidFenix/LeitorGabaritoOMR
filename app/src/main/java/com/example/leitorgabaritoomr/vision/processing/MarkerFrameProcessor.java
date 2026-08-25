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
import com.example.leitorgabaritoomr.vision.drawing.BubbleCandidateMatchingOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.BubbleGridRegistrationOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.RegisteredBubbleRegionOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.BubbleSamplingGeometryOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.BubbleTranslationSeedOverlayRenderer;
import com.example.leitorgabaritoomr.vision.registration.BubbleBlockTranslationEstimator;
import com.example.leitorgabaritoomr.vision.registration.BubbleCandidateMatcher;
import com.example.leitorgabaritoomr.vision.registration.BubbleCandidateMatchingResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractionResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractor;
import com.example.leitorgabaritoomr.vision.registration.BubbleGridRegistrarConfig;
import com.example.leitorgabaritoomr.vision.registration.BubbleGridRegistrar;
import com.example.leitorgabaritoomr.vision.registration.BubbleGridRegistrationResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleTranslationEstimationResult;
import com.example.leitorgabaritoomr.vision.registration.ExpectedBubbleTarget;
import com.example.leitorgabaritoomr.vision.registration.ExpectedBubbleTargetFactory;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegionFactory;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegionSet;
import com.example.leitorgabaritoomr.vision.measurement.BubbleSamplingGeometrySet;

import org.opencv.imgproc.Imgproc;

import com.example.leitorgabaritoomr.vision.image.OpenCvGrayImageBufferAdapter;
import com.example.leitorgabaritoomr.vision.measurement.BubbleSamplingMeasurer;
import com.example.leitorgabaritoomr.vision.measurement.OmrSamplingSheetMeasurer;

import com.example.leitorgabaritoomr.vision.drawing.SheetInterpretationOverlayRenderer;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpreter;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpreterConfig;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpreter;

import com.example.leitorgabaritoomr.vision.debug.QuestionInterpretationDiagnosticLogger;

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

    private final ExpectedBubbleTargetFactory
            expectedBubbleTargetFactory;

    private final BubbleCandidateMatcher
            bubbleCandidateMatcher;

    private final BubbleCandidateMatchingOverlayRenderer
            bubbleCandidateMatchingOverlayRenderer;

    private final BubbleBlockTranslationEstimator
            bubbleBlockTranslationEstimator;

    private final BubbleTranslationSeedOverlayRenderer
            bubbleTranslationSeedOverlayRenderer;

    private final BubbleGridRegistrar
            bubbleGridRegistrar;

    private final BubbleGridRegistrationOverlayRenderer
            bubbleGridRegistrationOverlayRenderer;

    private final RegisteredBubbleRegionFactory
            registeredBubbleRegionFactory;

    private final RegisteredBubbleRegionOverlayRenderer
            registeredBubbleRegionOverlayRenderer;

    private final BubbleMeasurementConfig
            bubbleSamplingConfig;

    private final BubbleSamplingGeometryOverlayRenderer
            bubbleSamplingGeometryOverlayRenderer;

    private final OmrSamplingSheetMeasurer
            samplingSheetMeasurer =
            new OmrSamplingSheetMeasurer(
                    new OpenCvGrayImageBufferAdapter(),
                    new BubbleSamplingMeasurer()
            );

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

    private final SheetInterpreter
            sheetInterpreter =
            new SheetInterpreter(
                    new QuestionInterpreter(
                            QuestionInterpreterConfig
                                    .developmentDefaults()
                    )
            );

    private final QuestionInterpretationDiagnosticLogger
            questionInterpretationDiagnosticLogger =
            new QuestionInterpretationDiagnosticLogger(
                    sheetInterpreter
                            .getQuestionInterpreter()
                            .getConfig()
            );

    private final SheetInterpretationOverlayRenderer
            sheetInterpretationOverlayRenderer =
            new SheetInterpretationOverlayRenderer();

    private volatile SheetEvidenceAggregate
            lastSheetEvidenceAggregate;

    private volatile SheetInterpretationResult
            lastSheetInterpretationResult;

    private volatile List<QuestionMeasurement>
            lastQuestionMeasurements;

    private volatile OmrSheetMeasurementResult
            lastMeasurementResult;

    private volatile BubbleContourExtractionResult
            lastBubbleContourExtractionResult;

    private volatile BubbleCandidateMatchingResult
            lastBubbleCandidateMatchingResult;

    private volatile BubbleTranslationEstimationResult
            lastBubbleTranslationEstimationResult;

    private volatile BubbleGridRegistrationResult
            lastBubbleGridRegistrationResult;

    private volatile RegisteredBubbleRegionSet
            lastRegisteredBubbleRegionSet;

    private volatile BubbleSamplingGeometrySet
            lastBubbleSamplingGeometrySet;

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
                bubbleContourExtractor,
                bubbleRegistrationCandidateOverlayRenderer,
                new ExpectedBubbleTargetFactory(),
                new BubbleCandidateMatcher(
                        BubbleGridRegistrarConfig
                                .developmentDefaults()
                ),
                new BubbleCandidateMatchingOverlayRenderer()
        );
    }

    /**
     * Construtor com todas as dependências do registro de
     * bolhas explicitamente injetáveis.
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
                    bubbleRegistrationCandidateOverlayRenderer,
            ExpectedBubbleTargetFactory expectedBubbleTargetFactory,
            BubbleCandidateMatcher bubbleCandidateMatcher,
            BubbleCandidateMatchingOverlayRenderer
                    bubbleCandidateMatchingOverlayRenderer
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
                bubbleContourExtractor,
                bubbleRegistrationCandidateOverlayRenderer,
                expectedBubbleTargetFactory,
                bubbleCandidateMatcher,
                bubbleCandidateMatchingOverlayRenderer,
                new BubbleBlockTranslationEstimator(
                        BubbleGridRegistrarConfig
                                .developmentDefaults()
                ),
                new BubbleTranslationSeedOverlayRenderer()
        );
    }

    /**
     * Construtor com todas as dependencias da associacao
     * preliminar e da semente de translacao injetaveis.
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
                    bubbleRegistrationCandidateOverlayRenderer,
            ExpectedBubbleTargetFactory expectedBubbleTargetFactory,
            BubbleCandidateMatcher bubbleCandidateMatcher,
            BubbleCandidateMatchingOverlayRenderer
                    bubbleCandidateMatchingOverlayRenderer,
            BubbleBlockTranslationEstimator
                    bubbleBlockTranslationEstimator,
            BubbleTranslationSeedOverlayRenderer
                    bubbleTranslationSeedOverlayRenderer
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
                bubbleContourExtractor,
                bubbleRegistrationCandidateOverlayRenderer,
                expectedBubbleTargetFactory,
                bubbleCandidateMatcher,
                bubbleCandidateMatchingOverlayRenderer,
                bubbleBlockTranslationEstimator,
                bubbleTranslationSeedOverlayRenderer,
                new BubbleGridRegistrar(
                        BubbleGridRegistrarConfig
                                .developmentDefaults()
                ),
                new BubbleGridRegistrationOverlayRenderer()
        );
    }

    /**
     * Construtor final com a translacao e o registro geometrico
     * dos blocos explicitamente injetaveis.
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
                    bubbleRegistrationCandidateOverlayRenderer,
            ExpectedBubbleTargetFactory expectedBubbleTargetFactory,
            BubbleCandidateMatcher bubbleCandidateMatcher,
            BubbleCandidateMatchingOverlayRenderer
                    bubbleCandidateMatchingOverlayRenderer,
            BubbleBlockTranslationEstimator
                    bubbleBlockTranslationEstimator,
            BubbleTranslationSeedOverlayRenderer
                    bubbleTranslationSeedOverlayRenderer,
            BubbleGridRegistrar bubbleGridRegistrar,
            BubbleGridRegistrationOverlayRenderer
                    bubbleGridRegistrationOverlayRenderer
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
                bubbleContourExtractor,
                bubbleRegistrationCandidateOverlayRenderer,
                expectedBubbleTargetFactory,
                bubbleCandidateMatcher,
                bubbleCandidateMatchingOverlayRenderer,
                bubbleBlockTranslationEstimator,
                bubbleTranslationSeedOverlayRenderer,
                bubbleGridRegistrar,
                bubbleGridRegistrationOverlayRenderer,
                new RegisteredBubbleRegionFactory(),
                new RegisteredBubbleRegionOverlayRenderer()
        );
    }

    /**
     * Construtor final com a geometria registrada das bolhas
     * explicitamente injetavel.
     *
     * Todos os construtores anteriores continuam delegando para
     * este, preservando a compatibilidade com a MainActivity e com
     * os testes existentes.
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
                    bubbleRegistrationCandidateOverlayRenderer,
            ExpectedBubbleTargetFactory expectedBubbleTargetFactory,
            BubbleCandidateMatcher bubbleCandidateMatcher,
            BubbleCandidateMatchingOverlayRenderer
                    bubbleCandidateMatchingOverlayRenderer,
            BubbleBlockTranslationEstimator
                    bubbleBlockTranslationEstimator,
            BubbleTranslationSeedOverlayRenderer
                    bubbleTranslationSeedOverlayRenderer,
            BubbleGridRegistrar bubbleGridRegistrar,
            BubbleGridRegistrationOverlayRenderer
                    bubbleGridRegistrationOverlayRenderer,
            RegisteredBubbleRegionFactory
                    registeredBubbleRegionFactory,
            RegisteredBubbleRegionOverlayRenderer
                    registeredBubbleRegionOverlayRenderer
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
                bubbleContourExtractor,
                bubbleRegistrationCandidateOverlayRenderer,
                expectedBubbleTargetFactory,
                bubbleCandidateMatcher,
                bubbleCandidateMatchingOverlayRenderer,
                bubbleBlockTranslationEstimator,
                bubbleTranslationSeedOverlayRenderer,
                bubbleGridRegistrar,
                bubbleGridRegistrationOverlayRenderer,
                registeredBubbleRegionFactory,
                registeredBubbleRegionOverlayRenderer,
                BubbleMeasurementConfig
                        .developmentDefaults(),
                new BubbleSamplingGeometryOverlayRenderer()
        );
    }

    /**
     * Construtor final com a geometria de amostragem tambem
     * explicitamente injetavel.
     *
     * A configuracao recebida sera compartilhada por todas as
     * BubbleSamplingGeometry criadas para um frame.
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
                    bubbleRegistrationCandidateOverlayRenderer,
            ExpectedBubbleTargetFactory expectedBubbleTargetFactory,
            BubbleCandidateMatcher bubbleCandidateMatcher,
            BubbleCandidateMatchingOverlayRenderer
                    bubbleCandidateMatchingOverlayRenderer,
            BubbleBlockTranslationEstimator
                    bubbleBlockTranslationEstimator,
            BubbleTranslationSeedOverlayRenderer
                    bubbleTranslationSeedOverlayRenderer,
            BubbleGridRegistrar bubbleGridRegistrar,
            BubbleGridRegistrationOverlayRenderer
                    bubbleGridRegistrationOverlayRenderer,
            RegisteredBubbleRegionFactory
                    registeredBubbleRegionFactory,
            RegisteredBubbleRegionOverlayRenderer
                    registeredBubbleRegionOverlayRenderer,
            BubbleMeasurementConfig bubbleSamplingConfig,
            BubbleSamplingGeometryOverlayRenderer
                    bubbleSamplingGeometryOverlayRenderer
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
                || bubbleRegistrationCandidateOverlayRenderer == null
                || expectedBubbleTargetFactory == null
                || bubbleCandidateMatcher == null
                || bubbleCandidateMatchingOverlayRenderer == null
                || bubbleBlockTranslationEstimator == null
                || bubbleTranslationSeedOverlayRenderer == null
                || bubbleGridRegistrar == null
                || bubbleGridRegistrationOverlayRenderer == null
                || registeredBubbleRegionFactory == null
                || registeredBubbleRegionOverlayRenderer == null
                || bubbleSamplingConfig == null
                || bubbleSamplingGeometryOverlayRenderer == null) {

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

        this.expectedBubbleTargetFactory =
                expectedBubbleTargetFactory;

        this.bubbleCandidateMatcher =
                bubbleCandidateMatcher;

        this.bubbleCandidateMatchingOverlayRenderer =
                bubbleCandidateMatchingOverlayRenderer;

        this.bubbleBlockTranslationEstimator =
                bubbleBlockTranslationEstimator;

        this.bubbleTranslationSeedOverlayRenderer =
                bubbleTranslationSeedOverlayRenderer;

        this.bubbleGridRegistrar =
                bubbleGridRegistrar;

        this.bubbleGridRegistrationOverlayRenderer =
                bubbleGridRegistrationOverlayRenderer;

        this.registeredBubbleRegionFactory =
                registeredBubbleRegionFactory;

        this.registeredBubbleRegionOverlayRenderer =
                registeredBubbleRegionOverlayRenderer;

        this.bubbleSamplingConfig =
                bubbleSamplingConfig;

        this.bubbleSamplingGeometryOverlayRenderer =
                bubbleSamplingGeometryOverlayRenderer;

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
            boolean bubbleTranslationSeedAvailable,
            boolean bubbleGridRegistrationAvailable,
            boolean registeredBubbleRegionsAvailable,
            boolean bubbleSamplingGeometryAvailable,
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
                != VisionStage.BUBBLE_TRANSLATION_SEED
                && selectedStage
                != VisionStage.BUBBLE_GRID_REGISTRATION
                && selectedStage
                != VisionStage.REGISTERED_BUBBLE_REGIONS
                && selectedStage
                != VisionStage.BUBBLE_SAMPLING_GEOMETRY
                && selectedStage
                != VisionStage.BUBBLE_MEASUREMENTS
                && selectedStage
                != VisionStage.QUESTION_COMPARISON
                && selectedStage
                != VisionStage.TEMPORAL_CONSENSUS
                && selectedStage //pausa automatica para etapa 18
                != VisionStage.FINAL_INTERPRETATION) {

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
                        == VisionStage.BUBBLE_TRANSLATION_SEED
                        || selectedStage
                        == VisionStage.BUBBLE_GRID_REGISTRATION
                        || selectedStage
                        == VisionStage.REGISTERED_BUBBLE_REGIONS
                        || selectedStage
                        == VisionStage.BUBBLE_SAMPLING_GEOMETRY
                        || selectedStage
                        == VisionStage.BUBBLE_MEASUREMENTS
                        || selectedStage
                        == VisionStage.QUESTION_COMPARISON
                        || selectedStage
                        == VisionStage.TEMPORAL_CONSENSUS
                        || selectedStage
                        == VisionStage.FINAL_INTERPRETATION;

        if (requiresNormalizedRegion
                && !normalizedRegionAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.BUBBLE_REGISTRATION
                && !bubbleRegistrationAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.BUBBLE_TRANSLATION_SEED
                && !bubbleTranslationSeedAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.BUBBLE_GRID_REGISTRATION
                && !bubbleGridRegistrationAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.REGISTERED_BUBBLE_REGIONS
                && !registeredBubbleRegionsAvailable) {

            return;
        }

        if (selectedStage
                == VisionStage.BUBBLE_SAMPLING_GEOMETRY
                && !bubbleSamplingGeometryAvailable) {

            return;
        }

        if ((selectedStage
                == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage
                == VisionStage.QUESTION_COMPARISON
                || selectedStage
                == VisionStage.TEMPORAL_CONSENSUS
                || selectedStage
                == VisionStage.FINAL_INTERPRETATION)
                && !measurementsAvailable) {

            return;
        }

        if ((selectedStage
                == VisionStage.QUESTION_COMPARISON
                || selectedStage
                == VisionStage.TEMPORAL_CONSENSUS
                || selectedStage
                == VisionStage.FINAL_INTERPRETATION)
                && !questionComparisonAvailable) {

            return;
        }

        if ((selectedStage //verificação do consenso
                == VisionStage.TEMPORAL_CONSENSUS
                || selectedStage
                == VisionStage.FINAL_INTERPRETATION)
                && !temporalConsensusAvailable) {

            return;
        }
//        if (selectedStage
//                == VisionStage.TEMPORAL_CONSENSUS
//                && !temporalConsensusAvailable) {
//
//            return;
//        }

        if (selectedStage
                == VisionStage.FINAL_INTERPRETATION
                && (lastSheetInterpretationResult == null
                || !lastSheetInterpretationResult.isComplete())) {

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
                || selectedStage == VisionStage.BUBBLE_TRANSLATION_SEED
                || selectedStage == VisionStage.BUBBLE_GRID_REGISTRATION
                || selectedStage == VisionStage.REGISTERED_BUBBLE_REGIONS
                || selectedStage == VisionStage.BUBBLE_SAMPLING_GEOMETRY
                || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
                || selectedStage == VisionStage.QUESTION_COMPARISON
                || selectedStage == VisionStage.TEMPORAL_CONSENSUS
                || selectedStage == VisionStage.FINAL_INTERPRETATION) {

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
                    || selectedStage == VisionStage.BUBBLE_TRANSLATION_SEED
                    || selectedStage == VisionStage.BUBBLE_GRID_REGISTRATION
                    || selectedStage == VisionStage.REGISTERED_BUBBLE_REGIONS
                    || selectedStage == VisionStage.BUBBLE_SAMPLING_GEOMETRY
                    || selectedStage == VisionStage.BUBBLE_MEASUREMENTS
                    || selectedStage == VisionStage.QUESTION_COMPARISON
                    || selectedStage == VisionStage.TEMPORAL_CONSENSUS
                    || selectedStage == VisionStage.FINAL_INTERPRETATION) {

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

                        lastBubbleCandidateMatchingResult =
                                null;

                        lastBubbleTranslationEstimationResult =
                                null;

                        lastBubbleGridRegistrationResult =
                                null;

                        lastRegisteredBubbleRegionSet =
                                null;

                        lastBubbleSamplingGeometrySet =
                                null;

                        Mat normalizedGray =
                                createGrayImage(
                                        normalizationResult
                                                .getNormalizedRegion()
                                );

                        try {
                            List<ExpectedBubbleTarget> targets =
                                    expectedBubbleTargetFactory.create(
                                            layoutDefinition,
                                            normalizedGray.cols(),
                                            normalizedGray.rows()
                                    );

                            lastBubbleContourExtractionResult =
                                    bubbleContourExtractor.extract(
                                            normalizedGray,
                                            layoutDefinition
                                    );

                            lastBubbleCandidateMatchingResult =
                                    bubbleCandidateMatcher.match(
                                            targets,
                                            lastBubbleContourExtractionResult
                                    );

                            bubbleCandidateMatchingOverlayRenderer.draw(
                                    normalizationResult
                                            .getNormalizedRegion(),
                                    lastBubbleCandidateMatchingResult
                            );
                        } finally {
                            normalizedGray.release();
                        }
                    }

                    if (selectedStage
                            == VisionStage.BUBBLE_TRANSLATION_SEED) {

                        lastBubbleContourExtractionResult =
                                null;

                        lastBubbleCandidateMatchingResult =
                                null;

                        lastBubbleTranslationEstimationResult =
                                null;

                        lastBubbleGridRegistrationResult =
                                null;

                        lastRegisteredBubbleRegionSet =
                                null;

                        lastBubbleSamplingGeometrySet =
                                null;

                        Mat normalizedGray =
                                createGrayImage(
                                        normalizationResult
                                                .getNormalizedRegion()
                                );

                        try {
                            List<ExpectedBubbleTarget> targets =
                                    expectedBubbleTargetFactory.create(
                                            layoutDefinition,
                                            normalizedGray.cols(),
                                            normalizedGray.rows()
                                    );

                            lastBubbleContourExtractionResult =
                                    bubbleContourExtractor.extract(
                                            normalizedGray,
                                            layoutDefinition
                                    );

                            lastBubbleTranslationEstimationResult =
                                    bubbleBlockTranslationEstimator
                                            .estimate(
                                                    targets,
                                                    lastBubbleContourExtractionResult
                                            );

                            bubbleTranslationSeedOverlayRenderer.draw(
                                    normalizationResult
                                            .getNormalizedRegion(),
                                    targets,
                                    lastBubbleContourExtractionResult,
                                    lastBubbleTranslationEstimationResult
                            );
                        } finally {
                            normalizedGray.release();
                        }
                    }

                    if (selectedStage
                            == VisionStage.BUBBLE_GRID_REGISTRATION
                            || selectedStage
                            == VisionStage.REGISTERED_BUBBLE_REGIONS
                            || selectedStage
                            == VisionStage.BUBBLE_SAMPLING_GEOMETRY
                            || selectedStage
                            == VisionStage.BUBBLE_MEASUREMENTS
                            || selectedStage //adiciona etapa 16 no registro
                            == VisionStage.QUESTION_COMPARISON
                            || selectedStage //adiciona etapa 17 no registro
                            == VisionStage.TEMPORAL_CONSENSUS
                            || selectedStage //adiciona etapa 18 no bloco geometrico
                            == VisionStage.FINAL_INTERPRETATION) {

                        lastBubbleContourExtractionResult =
                                null;

                        lastBubbleCandidateMatchingResult =
                                null;

                        lastBubbleTranslationEstimationResult =
                                null;

                        lastBubbleGridRegistrationResult =
                                null;

                        lastRegisteredBubbleRegionSet =
                                null;

                        lastBubbleSamplingGeometrySet =
                                null;

                        if (selectedStage
                                == VisionStage.BUBBLE_MEASUREMENTS
                                || selectedStage //amplia limpeza dos resultados antigos
                                == VisionStage.QUESTION_COMPARISON
                                || selectedStage //Amplie a limpeza dos resultados do frame
                                == VisionStage.TEMPORAL_CONSENSUS
                                || selectedStage
                                == VisionStage.FINAL_INTERPRETATION) {

                            lastMeasurementResult = null;
                            lastQuestionMeasurements = null;

                            if (selectedStage
                                    == VisionStage.FINAL_INTERPRETATION) {

                                lastSheetInterpretationResult = null;
                            }
                        }

                        Mat normalizedGray =
                                createGrayImage(
                                        normalizationResult
                                                .getNormalizedRegion()
                                );

                        try {
                            List<ExpectedBubbleTarget> targets =
                                    expectedBubbleTargetFactory.create(
                                            layoutDefinition,
                                            normalizedGray.cols(),
                                            normalizedGray.rows()
                                    );

                            lastBubbleContourExtractionResult =
                                    bubbleContourExtractor.extract(
                                            normalizedGray,
                                            layoutDefinition
                                    );

                            lastBubbleTranslationEstimationResult =
                                    bubbleBlockTranslationEstimator
                                            .estimate(
                                                    targets,
                                                    lastBubbleContourExtractionResult
                                            );

                            lastBubbleGridRegistrationResult =
                                    bubbleGridRegistrar.register(
                                            targets,
                                            lastBubbleTranslationEstimationResult
                                    );

                            if (selectedStage
                                    == VisionStage.BUBBLE_GRID_REGISTRATION) {

                                bubbleGridRegistrationOverlayRenderer.draw(
                                        normalizationResult
                                                .getNormalizedRegion(),
                                        targets,
                                        lastBubbleContourExtractionResult,
                                        lastBubbleTranslationEstimationResult,
                                        lastBubbleGridRegistrationResult
                                );

                            } else if (lastBubbleGridRegistrationResult
                                    != null
                                    && lastBubbleGridRegistrationResult
                                    .isSuccess()
                                    && lastBubbleGridRegistrationResult
                                    .areAllBlocksAccepted()) {

                                lastRegisteredBubbleRegionSet =
                                        registeredBubbleRegionFactory.create(
                                                targets,
                                                lastBubbleGridRegistrationResult,
                                                normalizedGray.cols(),
                                                normalizedGray.rows()
                                        );

                                if (selectedStage
                                        == VisionStage
                                        .REGISTERED_BUBBLE_REGIONS) {

                                    registeredBubbleRegionOverlayRenderer.draw(
                                            normalizationResult
                                                    .getNormalizedRegion(),
                                            lastRegisteredBubbleRegionSet
                                    );

                                } else {
                                    lastBubbleSamplingGeometrySet =
                                            new BubbleSamplingGeometrySet(
                                                    lastRegisteredBubbleRegionSet,
                                                    bubbleSamplingConfig
                                            );

                                    if (selectedStage
                                            == VisionStage
                                            .BUBBLE_SAMPLING_GEOMETRY) {

                                        bubbleSamplingGeometryOverlayRenderer.draw(
                                                normalizationResult
                                                        .getNormalizedRegion(),
                                                lastBubbleSamplingGeometrySet
                                        );

                                    } else if (selectedStage //bloco da medição precisa
                                            == VisionStage.BUBBLE_MEASUREMENTS
                                            || selectedStage
                                            == VisionStage.QUESTION_COMPARISON
                                            || selectedStage
                                            == VisionStage.TEMPORAL_CONSENSUS
                                            || selectedStage
                                            == VisionStage.FINAL_INTERPRETATION) {

                                        lastMeasurementResult =
                                                samplingSheetMeasurer.measure(
                                                        normalizedGray,
                                                        layoutDefinition,
                                                        lastBubbleSamplingGeometrySet
                                                );

                                        if (selectedStage
                                                == VisionStage.BUBBLE_MEASUREMENTS) {

                                            bubbleMeasurementOverlayRenderer.draw(
                                                    normalizationResult
                                                            .getNormalizedRegion(),
                                                    lastMeasurementResult
                                            );

                                        } else if (lastMeasurementResult.isComplete()) {

                                            lastQuestionMeasurements =
                                                    questionMeasurer.measure(
                                                            lastMeasurementResult
                                                    );

                                            if (selectedStage
                                                    == VisionStage.QUESTION_COMPARISON) {

                                                questionComparisonOverlayRenderer.draw(
                                                        normalizationResult
                                                                .getNormalizedRegion(),
                                                        lastQuestionMeasurements
                                                );

                                            } else {
                                                /*
                                                 * Depois de pronto, o consenso fica congelado.
                                                 * A etapa 18 reutiliza a mesma fotografia temporal.
                                                 */
                                                if (lastSheetEvidenceAggregate != null
                                                        && lastSheetEvidenceAggregate.isReady()) {

                                                    lastSheetEvidenceAggregate =
                                                            questionEvidenceAccumulator
                                                                    .getCurrentSnapshot();

                                                } else if (lastStabilityResult != null
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

                                                if (selectedStage
                                                        == VisionStage.TEMPORAL_CONSENSUS) {

                                                    temporalConsensusOverlayRenderer.draw(
                                                            normalizationResult
                                                                    .getNormalizedRegion(),
                                                            lastSheetEvidenceAggregate,
                                                            lastMeasurementResult
                                                    );

                                                } else {
                                                    if (lastSheetEvidenceAggregate != null) {
                                                        lastSheetInterpretationResult =
                                                                sheetInterpreter.interpret(
                                                                        lastSheetEvidenceAggregate
                                                                );

                                                        questionInterpretationDiagnosticLogger.logOnce(
                                                                lastSheetInterpretationResult
                                                        );
                                                    }

                                                    sheetInterpretationOverlayRenderer.draw(
                                                            normalizationResult
                                                                    .getNormalizedRegion(),
                                                            lastSheetInterpretationResult,
                                                            lastMeasurementResult
                                                    );
                                                }

                                                if (lastSheetEvidenceAggregate != null
                                                        && lastSheetEvidenceAggregate.isReady()) {

                                                    bubbleMeasurementDiagnosticLogger.logOnce(
                                                            lastQuestionMeasurements
                                                    );
                                                }
//                                                /*
//                                                 * Somente frames realmente STABLE entram no consenso.
//                                                 * HELD_STABLE conserva o resultado sem contar um frame.
//                                                 */
//                                                if (lastStabilityResult != null
//                                                        && lastStabilityResult.getState()
//                                                        == MarkerStabilityState.STABLE) {
//
//                                                    lastSheetEvidenceAggregate =
//                                                            questionEvidenceAccumulator.update(
//                                                                    lastQuestionMeasurements
//                                                            );
//
//                                                } else {
//                                                    lastSheetEvidenceAggregate =
//                                                            questionEvidenceAccumulator
//                                                                    .getCurrentSnapshot();
//                                                }
//
//                                                temporalConsensusOverlayRenderer.draw(
//                                                        normalizationResult
//                                                                .getNormalizedRegion(),
//                                                        lastSheetEvidenceAggregate,
//                                                        lastMeasurementResult
//                                                );
//
//                                                if (lastSheetEvidenceAggregate != null
//                                                        && lastSheetEvidenceAggregate.isReady()) {
//
//                                                    bubbleMeasurementDiagnosticLogger.logOnce(
//                                                            lastQuestionMeasurements
//                                                    );
//                                                }
                                            }
                                        }
                                    }

                                }

                            } else {
                                if (selectedStage
                                        == VisionStage
                                        .REGISTERED_BUBBLE_REGIONS) {

                                    registeredBubbleRegionOverlayRenderer.draw(
                                            normalizationResult
                                                    .getNormalizedRegion(),
                                            null
                                    );
                                } else {
                                    bubbleSamplingGeometryOverlayRenderer.draw(
                                            normalizationResult
                                                    .getNormalizedRegion(),
                                            null
                                    );
                                }
                            }
                        } finally {
                            normalizedGray.release();
                        }
                    }

                }

                normalizedPreviewFrame =
                        normalizedRegionPreviewRenderer
                                .render(
                                        normalizationResult,
                                        cleanNormalizationFrame,
                                        rgbaFrame.cols(),
                                        rgbaFrame.rows()
                                );
//                normalizedPreviewFrame =
//                        normalizedRegionPreviewRenderer
//                                .render(
//                                        normalizationResult,
//                                        rgbaFrame.cols(),
//                                        rgbaFrame.rows()
//                                );

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
                            && lastBubbleCandidateMatchingResult
                            != null
                            && lastBubbleCandidateMatchingResult
                            .isSuccess();

            boolean bubbleTranslationSeedAvailable =
                    normalizedRegionAvailable
                            && selectedStage
                            == VisionStage.BUBBLE_TRANSLATION_SEED
                            && lastBubbleTranslationEstimationResult
                            != null
                            && lastBubbleTranslationEstimationResult
                            .isSuccess()
                            && lastBubbleTranslationEstimationResult
                            .areAllBlocksAccepted();

            boolean bubbleGridRegistrationAvailable =
                    normalizedRegionAvailable
                            && selectedStage
                            == VisionStage.BUBBLE_GRID_REGISTRATION
                            && lastBubbleGridRegistrationResult
                            != null
                            && lastBubbleGridRegistrationResult
                            .isSuccess()
                            && lastBubbleGridRegistrationResult
                            .areAllBlocksAccepted();

            boolean registeredBubbleRegionsAvailable =
                    normalizedRegionAvailable
                            && selectedStage
                            == VisionStage.REGISTERED_BUBBLE_REGIONS
                            && lastRegisteredBubbleRegionSet
                            != null
                            && lastRegisteredBubbleRegionSet
                            .isComplete()
                            && !lastRegisteredBubbleRegionSet
                            .hasClippedRegions();

            boolean bubbleSamplingGeometryAvailable =
                    normalizedRegionAvailable
                            && selectedStage
                            == VisionStage.BUBBLE_SAMPLING_GEOMETRY
                            && lastBubbleSamplingGeometrySet
                            != null
                            && lastBubbleSamplingGeometrySet
                            .isComplete()
                            && !lastBubbleSamplingGeometrySet
                            .hasClippedBackgrounds();

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
                    bubbleTranslationSeedAvailable,
                    bubbleGridRegistrationAvailable,
                    registeredBubbleRegionsAvailable,
                    bubbleSamplingGeometryAvailable,
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

            lastSheetInterpretationResult = null;

            if (questionEvidenceAccumulator
                    .getAccumulatedFrames() > 0) {

                questionEvidenceAccumulator.reset();
                bubbleMeasurementDiagnosticLogger.reset();
                questionInterpretationDiagnosticLogger.reset();
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

    public BubbleCandidateMatchingResult
    getLastBubbleCandidateMatchingResult() {

        return lastBubbleCandidateMatchingResult;
    }

    public BubbleTranslationEstimationResult
    getLastBubbleTranslationEstimationResult() {

        return lastBubbleTranslationEstimationResult;
    }

    public BubbleGridRegistrationResult
    getLastBubbleGridRegistrationResult() {

        return lastBubbleGridRegistrationResult;
    }

    public RegisteredBubbleRegionSet
    getLastRegisteredBubbleRegionSet() {

        return lastRegisteredBubbleRegionSet;
    }

    public BubbleSamplingGeometrySet
    getLastBubbleSamplingGeometrySet() {

        return lastBubbleSamplingGeometrySet;
    }

    public List<QuestionMeasurement>
    getLastQuestionMeasurements() {

        return lastQuestionMeasurements;
    }

    public SheetEvidenceAggregate
    getLastSheetEvidenceAggregate() {

        return lastSheetEvidenceAggregate;
    }

    public SheetInterpretationResult
    getLastSheetInterpretationResult() {

        return lastSheetInterpretationResult;
    }

    public void resetStability() {
        markerSetStabilizer.reset();

        lastStabilityResult = null;
        lastBubbleContourExtractionResult = null;
        lastBubbleCandidateMatchingResult = null;
        lastBubbleTranslationEstimationResult = null;
        lastBubbleGridRegistrationResult = null;
        lastRegisteredBubbleRegionSet = null;
        lastBubbleSamplingGeometrySet = null;
        lastMeasurementResult = null;
        lastQuestionMeasurements = null;
        lastSheetInterpretationResult = null;

        questionEvidenceAccumulator.reset();

        bubbleMeasurementDiagnosticLogger.reset();

        questionInterpretationDiagnosticLogger.reset();

        lastSheetEvidenceAggregate =
                questionEvidenceAccumulator
                        .getCurrentSnapshot();

        autoFreezeConsumedStage = null;

    }

}
