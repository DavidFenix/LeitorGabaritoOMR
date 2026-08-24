package com.example.leitorgabaritoomr.vision.fixture;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.geometry.CornerRole;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetCandidateEvaluation;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.processing.DefaultMarkerFrameProcessorFactory;
import com.example.leitorgabaritoomr.vision.processing.MarkerFrameProcessor;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

/**
 * Executa imagens fixas ou sequencias de frames pelo mesmo pipeline
 * usado pela Activity.
 *
 * O executor nao conhece resultados esperados e nao contem regras
 * de classificacao. Ele apenas alimenta frames limpos ate o consenso
 * terminar ou o limite de seguranca ser atingido.
 */
public final class OmrFixturePipelineRunner {

    public static final int DEFAULT_MAX_FRAME_COUNT = 30;

    private OmrFixturePipelineRunner() {
        throw new AssertionError(
                "Esta classe nao deve ser instanciada."
        );
    }

    public static Result run(Mat sourceRgba) {
        return run(
                sourceRgba,
                DEFAULT_MAX_FRAME_COUNT
        );
    }

    public static Result run(
            Mat sourceRgba,
            int maximumFrameCount
    ) {
        validateRgbaFrame(sourceRgba);
        validateMaximumFrameCount(maximumFrameCount);

        return runFrames(
                frameIndex -> sourceRgba.clone(),
                maximumFrameCount
        );
    }

    public static Result run(
            OmrFixtureFrameProvider frameProvider
    ) {
        return run(
                frameProvider,
                DEFAULT_MAX_FRAME_COUNT
        );
    }

    /**
     * Executa uma sequencia mantendo o mesmo processador, estabilizador
     * e acumuladores durante todos os frames.
     *
     * O provedor continua pertencendo ao chamador e nao e fechado por
     * este metodo. Cada Mat devolvido por ele e liberado pelo executor.
     */
    public static Result run(
            OmrFixtureFrameProvider frameProvider,
            int maximumFrameCount
    ) {
        if (frameProvider == null) {
            throw new IllegalArgumentException(
                    "O provedor de frames e obrigatorio."
            );
        }

        validateMaximumFrameCount(maximumFrameCount);

        return runFrames(
                frameProvider::createRgbaFrame,
                maximumFrameCount
        );
    }

    private static Result runFrames(
            RgbaFrameFactory frameFactory,
            int maximumFrameCount
    ) {


        VisionDebugController debugController =
                new VisionDebugController();

        MarkerFrameProcessor processor = null;

        SheetInterpretationResult finalResult = null;
        int processedFrameCount = 0;
        int expectedFrameWidth = -1;
        int expectedFrameHeight = -1;

        ProgressTracker progressTracker =
                new ProgressTracker();

        try {
            debugController
                    .setAutoFreezeOnStableEnabled(false);

            selectFinalStage(debugController);

            processor =
                    DefaultMarkerFrameProcessorFactory.create(
                            MarkerDetectorMode.SOLID_SQUARE,
                            debugController
                    );

            for (int frameIndex = 0;
                 frameIndex < maximumFrameCount;
                 frameIndex++) {

                Mat rgbaFrame = null;
                Mat grayFrame = new Mat();

                MarkerDetectionResult detectionResult;

                try {
                    rgbaFrame =
                            frameFactory.createRgbaFrame(
                                    frameIndex
                            );

                    validateRgbaFrame(rgbaFrame);

                    if (expectedFrameWidth < 0) {
                        expectedFrameWidth = rgbaFrame.cols();
                        expectedFrameHeight = rgbaFrame.rows();

                    } else if (rgbaFrame.cols()
                            != expectedFrameWidth
                            || rgbaFrame.rows()
                            != expectedFrameHeight) {

                        throw new IllegalArgumentException(
                                "Todos os frames da sequencia devem"
                                        + " possuir as mesmas dimensoes."
                        );
                    }

                    Imgproc.cvtColor(
                            rgbaFrame,
                            grayFrame,
                            Imgproc.COLOR_RGBA2GRAY
                    );

                    detectionResult = processor.process(
                            grayFrame,
                            rgbaFrame
                    );
                } finally {
                    grayFrame.release();

                    if (rgbaFrame != null) {
                        rgbaFrame.release();
                    }
                }

                processedFrameCount++;

                finalResult =
                        processor
                                .getLastSheetInterpretationResult();

                progressTracker.observe(
                        detectionResult,
                        processor
                );

                if (finalResult != null
                        && finalResult.isComplete()) {
                    break;
                }
            }

            return new Result(
                    processedFrameCount,
                    maximumFrameCount,
                    finalResult,
                    progressTracker.snapshot()
            );
        } finally {
            if (processor != null) {
                processor.resetStability();
            }

            debugController.release();
        }
    }

    @FunctionalInterface
    private interface RgbaFrameFactory {

        Mat createRgbaFrame(int frameIndex);
    }

    /**
     * Acumula somente metadados leves. Nenhum Mat ou resultado
     * mutavel do processador e retido depois da execucao.
     */
    private static final class ProgressTracker {

        private int maximumDetectedMarkerCount;

        private boolean resolutionObserved;
        private boolean resolutionAcceptedObserved;
        private boolean stabilityObserved;
        private boolean heldStableObserved;
        private boolean lostStabilityObserved;
        private boolean stableMarkerSetObserved;
        private boolean contourExtractionObserved;
        private boolean candidateMatchingObserved;
        private boolean translationObserved;
        private boolean gridRegistrationObserved;
        private boolean registeredRegionsObserved;
        private boolean samplingGeometryObserved;
        private boolean measurementObserved;
        private boolean questionMeasurementsObserved;
        private boolean evidenceReadyObserved;
        private boolean interpretationObserved;

        private String lastStabilityState = "NONE";
        private String lastResolutionReason = "NONE";
        private String lastBestCandidate = "NONE";
        private String lastSecondBestCandidate = "NONE";
        private String lastDifferingRoles = "NONE";

        private double lastResolutionBestScore;
        private double lastResolutionScoreDifference;

        private void observe(
                MarkerDetectionResult detectionResult,
                MarkerFrameProcessor processor
        ) {
            if (detectionResult != null
                    && detectionResult.getMarkers() != null) {

                maximumDetectedMarkerCount =
                        Math.max(
                                maximumDetectedMarkerCount,
                                detectionResult
                                        .getMarkers()
                                        .size()
                        );
            }

            MarkerSetResolutionResult resolutionResult =
                    processor.getLastResolutionResult();

            if (resolutionResult != null) {

                resolutionObserved = true;

                resolutionAcceptedObserved |=
                        resolutionResult.isAccepted();

                lastResolutionBestScore =
                        resolutionResult.getBestScore();

                lastResolutionScoreDifference =
                        resolutionResult
                                .getScoreDifference();

                lastResolutionReason =
                        String.valueOf(
                                resolutionResult.getReason()
                        );

                MarkerSetCandidateEvaluation best =
                        resolutionResult
                                .getBestCandidateEvaluation();

                MarkerSetCandidateEvaluation secondBest =
                        resolutionResult
                                .getSecondBestCandidateEvaluation();

                if (best != null) {
                    lastBestCandidate =
                            formatEvaluation(best);
                }

                if (secondBest != null) {
                    lastSecondBestCandidate =
                            formatEvaluation(secondBest);
                }

                if (best != null && secondBest != null) {
                    lastDifferingRoles =
                            findDifferingRoles(
                                    best.getMarkerSet(),
                                    secondBest.getMarkerSet()
                            );
                }
            }

            if (processor.getLastStabilityResult()
                    != null) {

                stabilityObserved = true;

                MarkerStabilityState stabilityState =
                        processor
                                .getLastStabilityResult()
                                .getState();

                heldStableObserved |=
                        stabilityState
                                == MarkerStabilityState.HELD_STABLE;

                lostStabilityObserved |=
                        stabilityState
                                == MarkerStabilityState.LOST;

                lastStabilityState =
                        String.valueOf(
                                stabilityState
                        );

                stableMarkerSetObserved |=
                        processor
                                .getLastStabilityResult()
                                .getMarkerSet()
                                != null;
            }

            contourExtractionObserved |=
                    processor
                            .getLastBubbleContourExtractionResult()
                            != null;

            candidateMatchingObserved |=
                    processor
                            .getLastBubbleCandidateMatchingResult()
                            != null;

            translationObserved |=
                    processor
                            .getLastBubbleTranslationEstimationResult()
                            != null;

            gridRegistrationObserved |=
                    processor
                            .getLastBubbleGridRegistrationResult()
                            != null;

            registeredRegionsObserved |=
                    processor
                            .getLastRegisteredBubbleRegionSet()
                            != null;

            samplingGeometryObserved |=
                    processor
                            .getLastBubbleSamplingGeometrySet()
                            != null;

            measurementObserved |=
                    processor.getLastMeasurementResult()
                            != null;

            questionMeasurementsObserved |=
                    processor.getLastQuestionMeasurements()
                            != null
                            && !processor
                            .getLastQuestionMeasurements()
                            .isEmpty();

            evidenceReadyObserved |=
                    processor.getLastSheetEvidenceAggregate()
                            != null
                            && processor
                            .getLastSheetEvidenceAggregate()
                            .isReady();

            interpretationObserved |=
                    processor
                            .getLastSheetInterpretationResult()
                            != null;
        }

        private Progress snapshot() {
            return new Progress(
                    maximumDetectedMarkerCount,
                    resolutionObserved,
                    resolutionAcceptedObserved,
                    stabilityObserved,
                    heldStableObserved,
                    lostStabilityObserved,
                    stableMarkerSetObserved,
                    contourExtractionObserved,
                    candidateMatchingObserved,
                    translationObserved,
                    gridRegistrationObserved,
                    registeredRegionsObserved,
                    samplingGeometryObserved,
                    measurementObserved,
                    questionMeasurementsObserved,
                    evidenceReadyObserved,
                    interpretationObserved,
                    lastResolutionBestScore,
                    lastResolutionScoreDifference,
                    lastResolutionReason,
                    lastBestCandidate,
                    lastSecondBestCandidate,
                    lastDifferingRoles,
                    lastStabilityState
            );
        }

        private String formatEvaluation(
                MarkerSetCandidateEvaluation evaluation
        ) {
            ResolvedMarkerSet markerSet =
                    evaluation.getMarkerSet();

            return evaluation
                    + " markers=["
                    + formatRole(markerSet, CornerRole.TOP_LEFT)
                    + ","
                    + formatRole(markerSet, CornerRole.TOP_RIGHT)
                    + ","
                    + formatRole(markerSet, CornerRole.BOTTOM_RIGHT)
                    + ","
                    + formatRole(markerSet, CornerRole.BOTTOM_LEFT)
                    + "]";
        }

        private String formatRole(
                ResolvedMarkerSet markerSet,
                CornerRole role
        ) {
            Point center =
                    markerSet.get(role).getCenter();

            return String.format(
                    Locale.US,
                    "%s=(%.1f,%.1f)",
                    shortRole(role),
                    center.x,
                    center.y
            );
        }

        private String findDifferingRoles(
                ResolvedMarkerSet best,
                ResolvedMarkerSet secondBest
        ) {
            StringBuilder builder =
                    new StringBuilder();

            for (CornerRole role : CornerRole.values()) {
                boolean sameMarker =
                        best.get(role).getMarker()
                                == secondBest
                                .get(role)
                                .getMarker();

                if (sameMarker) {
                    continue;
                }

                if (builder.length() > 0) {
                    builder.append(',');
                }

                builder.append(shortRole(role));
            }

            return builder.length() == 0
                    ? "NONE"
                    : builder.toString();
        }

        private String shortRole(CornerRole role) {
            switch (role) {
                case TOP_LEFT:
                    return "TL";

                case TOP_RIGHT:
                    return "TR";

                case BOTTOM_RIGHT:
                    return "BR";

                case BOTTOM_LEFT:
                    return "BL";

                default:
                    return role.name();
            }
        }
    }

    private static void selectFinalStage(
            VisionDebugController debugController
    ) {
        int remainingTransitions =
                VisionStage.values().length;

        while (debugController.getSelectedStage()
                != VisionStage.FINAL_INTERPRETATION
                && remainingTransitions > 0) {

            debugController.selectNext();
            remainingTransitions--;
        }

        if (debugController.getSelectedStage()
                != VisionStage.FINAL_INTERPRETATION) {

            throw new IllegalStateException(
                    "Nao foi possivel selecionar"
                            + " a etapa final do Laboratorio."
            );
        }
    }

    private static void validateRgbaFrame(
            Mat sourceRgba
    ) {
        if (sourceRgba == null || sourceRgba.empty()) {
            throw new IllegalArgumentException(
                    "O frame RGBA da fixture e obrigatorio."
            );
        }

        if (sourceRgba.channels() != 4) {
            throw new IllegalArgumentException(
                    "O frame da fixture deve possuir"
                            + " quatro canais RGBA."
            );
        }
    }

    private static void validateMaximumFrameCount(
            int maximumFrameCount
    ) {
        if (maximumFrameCount <= 0) {
            throw new IllegalArgumentException(
                    "maximumFrameCount deve ser positivo."
            );
        }
    }

    /**
     * Resultado imutavel de uma execucao da fixture.
     */
    public static final class Result {

        private final int processedFrameCount;
        private final int maximumFrameCount;
        private final SheetInterpretationResult
                interpretationResult;

        private final Progress progress;

        private Result(
                int processedFrameCount,
                int maximumFrameCount,
                SheetInterpretationResult
                        interpretationResult,
                Progress progress
        ) {
            this.processedFrameCount =
                    processedFrameCount;

            this.maximumFrameCount =
                    maximumFrameCount;

            this.interpretationResult =
                    interpretationResult;

            this.progress = progress;
        }

        public int getProcessedFrameCount() {
            return processedFrameCount;
        }

        public int getMaximumFrameCount() {
            return maximumFrameCount;
        }

        public SheetInterpretationResult
        getInterpretationResult() {
            return interpretationResult;
        }

        public boolean isComplete() {
            return interpretationResult != null
                    && interpretationResult.isComplete();
        }

        public Progress getProgress() {
            return progress;
        }

        @Override
        public String toString() {
            return "frames="
                    + processedFrameCount
                    + "/"
                    + maximumFrameCount
                    + " | complete="
                    + isComplete()
                    + " | progress={"
                    + progress
                    + "}"
                    + " | interpretation="
                    + (interpretationResult == null
                    ? "null"
                    : interpretationResult);
        }
    }

    /**
     * Fotografia imutavel das camadas alcancadas durante a execucao.
     */
    public static final class Progress {

        private final int maximumDetectedMarkerCount;

        private final boolean resolutionObserved;
        private final boolean resolutionAcceptedObserved;
        private final boolean stabilityObserved;
        private final boolean heldStableObserved;
        private final boolean lostStabilityObserved;
        private final boolean stableMarkerSetObserved;
        private final boolean contourExtractionObserved;
        private final boolean candidateMatchingObserved;
        private final boolean translationObserved;
        private final boolean gridRegistrationObserved;
        private final boolean registeredRegionsObserved;
        private final boolean samplingGeometryObserved;
        private final boolean measurementObserved;
        private final boolean questionMeasurementsObserved;
        private final boolean evidenceReadyObserved;
        private final boolean interpretationObserved;

        private final String lastStabilityState;
        private final String lastResolutionReason;
        private final String lastBestCandidate;
        private final String lastSecondBestCandidate;
        private final String lastDifferingRoles;

        private final double lastResolutionBestScore;
        private final double lastResolutionScoreDifference;

        private Progress(
                int maximumDetectedMarkerCount,
                boolean resolutionObserved,
                boolean resolutionAcceptedObserved,
                boolean stabilityObserved,
                boolean heldStableObserved,
                boolean lostStabilityObserved,
                boolean stableMarkerSetObserved,
                boolean contourExtractionObserved,
                boolean candidateMatchingObserved,
                boolean translationObserved,
                boolean gridRegistrationObserved,
                boolean registeredRegionsObserved,
                boolean samplingGeometryObserved,
                boolean measurementObserved,
                boolean questionMeasurementsObserved,
                boolean evidenceReadyObserved,
                boolean interpretationObserved,
                double lastResolutionBestScore,
                double lastResolutionScoreDifference,
                String lastResolutionReason,
                String lastBestCandidate,
                String lastSecondBestCandidate,
                String lastDifferingRoles,
                String lastStabilityState
        ) {
            this.maximumDetectedMarkerCount =
                    maximumDetectedMarkerCount;

            this.resolutionObserved = resolutionObserved;
            this.resolutionAcceptedObserved =
                    resolutionAcceptedObserved;

            this.stabilityObserved = stabilityObserved;
            this.heldStableObserved = heldStableObserved;
            this.lostStabilityObserved =
                    lostStabilityObserved;

            this.stableMarkerSetObserved =
                    stableMarkerSetObserved;

            this.contourExtractionObserved =
                    contourExtractionObserved;

            this.candidateMatchingObserved =
                    candidateMatchingObserved;

            this.translationObserved =
                    translationObserved;

            this.gridRegistrationObserved =
                    gridRegistrationObserved;

            this.registeredRegionsObserved =
                    registeredRegionsObserved;

            this.samplingGeometryObserved =
                    samplingGeometryObserved;

            this.measurementObserved =
                    measurementObserved;

            this.questionMeasurementsObserved =
                    questionMeasurementsObserved;

            this.evidenceReadyObserved =
                    evidenceReadyObserved;

            this.interpretationObserved =
                    interpretationObserved;

            this.lastResolutionBestScore =
                    lastResolutionBestScore;

            this.lastResolutionScoreDifference =
                    lastResolutionScoreDifference;

            this.lastResolutionReason =
                    lastResolutionReason;

            this.lastBestCandidate =
                    lastBestCandidate;

            this.lastSecondBestCandidate =
                    lastSecondBestCandidate;

            this.lastDifferingRoles =
                    lastDifferingRoles;

            this.lastStabilityState =
                    lastStabilityState;
        }

        public int getMaximumDetectedMarkerCount() {
            return maximumDetectedMarkerCount;
        }

        public boolean wasHeldStableObserved() {
            return heldStableObserved;
        }

        public boolean wasLostStabilityObserved() {
            return lostStabilityObserved;
        }

        public String getFurthestStage() {
            if (interpretationObserved) {
                return "FINAL_INTERPRETATION";
            }

            if (evidenceReadyObserved) {
                return "TEMPORAL_CONSENSUS";
            }

            if (questionMeasurementsObserved) {
                return "QUESTION_COMPARISON";
            }

            if (measurementObserved) {
                return "BUBBLE_MEASUREMENTS";
            }

            if (samplingGeometryObserved) {
                return "BUBBLE_SAMPLING_GEOMETRY";
            }

            if (registeredRegionsObserved) {
                return "REGISTERED_BUBBLE_REGIONS";
            }

            if (gridRegistrationObserved) {
                return "BUBBLE_GRID_REGISTRATION";
            }

            if (translationObserved) {
                return "BUBBLE_TRANSLATION_SEED";
            }

            if (candidateMatchingObserved) {
                return "BUBBLE_REGISTRATION";
            }

            if (contourExtractionObserved) {
                return "NORMALIZED_CONTOURS";
            }

            if (stableMarkerSetObserved) {
                return "STABLE_MARKERS";
            }

            if (stabilityObserved) {
                return "MARKER_STABILITY";
            }

            if (resolutionObserved) {
                return "RESOLVED_MARKERS";
            }

            if (maximumDetectedMarkerCount > 0) {
                return "MARKER_CANDIDATES";
            }

            return "NO_MARKERS";
        }

        @Override
        public String toString() {
            return "furthest="
                    + getFurthestStage()
                    + ", markersMax="
                    + maximumDetectedMarkerCount
                    + ", resolutionAccepted="
                    + resolutionAcceptedObserved
                    + ", resolutionBest="
                    + lastResolutionBestScore
                    + ", resolutionDifference="
                    + lastResolutionScoreDifference
                    + ", resolutionReason="
                    + lastResolutionReason
                    + ", differingRoles="
                    + lastDifferingRoles
                    + ", best={"
                    + lastBestCandidate
                    + "}, second={"
                    + lastSecondBestCandidate
                    + "}"
                    + ", stability="
                    + lastStabilityState
                    + ", heldStableSeen="
                    + heldStableObserved
                    + ", lostSeen="
                    + lostStabilityObserved
                    + ", stableSet="
                    + stableMarkerSetObserved
                    + ", contours="
                    + contourExtractionObserved
                    + ", matching="
                    + candidateMatchingObserved
                    + ", translation="
                    + translationObserved
                    + ", grid="
                    + gridRegistrationObserved
                    + ", regions="
                    + registeredRegionsObserved
                    + ", sampling="
                    + samplingGeometryObserved
                    + ", measurement="
                    + measurementObserved
                    + ", questions="
                    + questionMeasurementsObserved
                    + ", evidenceReady="
                    + evidenceReadyObserved
                    + ", interpretation="
                    + interpretationObserved;
        }
    }
}
