package com.example.leitorgabaritoomr.vision.session;

import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;

/**
 * Fotografia imutavel e leve do resultado de um frame da sessao.
 *
 * Nenhum Mat e armazenado. Os resultados intermediarios do pipeline
 * sao convertidos em valores escalares no momento da criacao. Apenas
 * SheetInterpretationResult e preservado como objeto, pois ele ja e
 * um resultado semantico imutavel e independente de recursos nativos.
 */
public final class OmrCaptureFrameResult {

    private final long frameNumber;
    private final OmrCaptureSessionState sessionState;

    private final String detectorName;
    private final int detectedMarkerCount;
    private final int rejectedCandidateCount;
    private final double processingTimeMillis;

    private final boolean geometryEvaluationAvailable;
    private final boolean geometryAccepted;
    private final double geometryBestScore;
    private final double geometryScoreDifference;
    private final String geometryReason;

    private final MarkerStabilityState markerStabilityState;
    private final int consistentMarkerFrames;
    private final int requiredMarkerFrames;
    private final int missedMarkerFrames;

    private final int accumulatedEvidenceFrames;
    private final int requiredEvidenceFrames;

    private final SheetInterpretationResult interpretationResult;
    private final String failureMessage;

    private OmrCaptureFrameResult(
            long frameNumber,
            OmrCaptureSessionState sessionState,
            MarkerDetectionResult detectionResult,
            MarkerSetResolutionResult resolutionResult,
            MarkerStabilityResult stabilityResult,
            SheetEvidenceAggregate evidenceAggregate,
            SheetInterpretationResult interpretationResult,
            String failureMessage
    ) {
        if (frameNumber <= 0) {
            throw new IllegalArgumentException(
                    "frameNumber deve ser positivo."
            );
        }

        if (sessionState == null) {
            throw new IllegalArgumentException(
                    "O estado da sessao e obrigatorio."
            );
        }

        validateInterpretation(
                sessionState,
                interpretationResult
        );

        validateFailure(
                sessionState,
                failureMessage
        );

        this.frameNumber = frameNumber;
        this.sessionState = sessionState;

        if (detectionResult == null) {
            detectorName = null;
            detectedMarkerCount = 0;
            rejectedCandidateCount = 0;
            processingTimeMillis = 0.0;

        } else {
            detectorName = detectionResult.getDetectorName();
            detectedMarkerCount =
                    detectionResult.getMarkerCount();
            rejectedCandidateCount =
                    detectionResult.getRejectedCandidates();
            processingTimeMillis =
                    detectionResult.getProcessingTimeMillis();
        }

        geometryEvaluationAvailable =
                resolutionResult != null;

        if (resolutionResult == null) {
            geometryAccepted = false;
            geometryBestScore = 0.0;
            geometryScoreDifference = 0.0;
            geometryReason = null;

        } else {
            geometryAccepted =
                    resolutionResult.isAccepted();
            geometryBestScore =
                    resolutionResult.getBestScore();
            geometryScoreDifference =
                    resolutionResult.getScoreDifference();
            geometryReason =
                    resolutionResult.getReason();
        }

        if (stabilityResult == null) {
            markerStabilityState = null;
            consistentMarkerFrames = 0;
            requiredMarkerFrames = 0;
            missedMarkerFrames = 0;

        } else {
            markerStabilityState =
                    stabilityResult.getState();
            consistentMarkerFrames =
                    stabilityResult.getConsistentFrames();
            requiredMarkerFrames =
                    stabilityResult.getRequiredFrames();
            missedMarkerFrames =
                    stabilityResult.getMissedFrames();
        }

        if (evidenceAggregate == null) {
            accumulatedEvidenceFrames = 0;
            requiredEvidenceFrames = 0;

        } else {
            accumulatedEvidenceFrames =
                    evidenceAggregate.getAccumulatedFrames();
            requiredEvidenceFrames =
                    evidenceAggregate.getRequiredFrames();
        }

        this.interpretationResult =
                interpretationResult;

        this.failureMessage =
                normalizeFailureMessage(failureMessage);
    }

    public static OmrCaptureFrameResult snapshot(
            long frameNumber,
            OmrCaptureSessionState sessionState,
            MarkerDetectionResult detectionResult,
            MarkerSetResolutionResult resolutionResult,
            MarkerStabilityResult stabilityResult,
            SheetEvidenceAggregate evidenceAggregate,
            SheetInterpretationResult interpretationResult
    ) {
        if (sessionState == OmrCaptureSessionState.FAILED) {
            throw new IllegalArgumentException(
                    "Use failed() para criar um resultado com falha."
            );
        }

        return new OmrCaptureFrameResult(
                frameNumber,
                sessionState,
                detectionResult,
                resolutionResult,
                stabilityResult,
                evidenceAggregate,
                interpretationResult,
                null
        );
    }

    public static OmrCaptureFrameResult failed(
            long frameNumber,
            MarkerDetectionResult detectionResult,
            MarkerSetResolutionResult resolutionResult,
            MarkerStabilityResult stabilityResult,
            SheetEvidenceAggregate evidenceAggregate,
            String failureMessage
    ) {
        return new OmrCaptureFrameResult(
                frameNumber,
                OmrCaptureSessionState.FAILED,
                detectionResult,
                resolutionResult,
                stabilityResult,
                evidenceAggregate,
                null,
                failureMessage
        );
    }

    private static void validateInterpretation(
            OmrCaptureSessionState sessionState,
            SheetInterpretationResult interpretationResult
    ) {
        if (sessionState
                == OmrCaptureSessionState.COMPLETED
                && (interpretationResult == null
                || !interpretationResult.isComplete())) {

            throw new IllegalArgumentException(
                    "COMPLETED exige uma interpretacao completa."
            );
        }
    }

    private static void validateFailure(
            OmrCaptureSessionState sessionState,
            String failureMessage
    ) {
        boolean hasFailureMessage =
                failureMessage != null
                        && !failureMessage.trim().isEmpty();

        if (sessionState == OmrCaptureSessionState.FAILED
                && !hasFailureMessage) {

            throw new IllegalArgumentException(
                    "FAILED exige uma mensagem de falha."
            );
        }

        if (sessionState != OmrCaptureSessionState.FAILED
                && hasFailureMessage) {

            throw new IllegalArgumentException(
                    "Somente FAILED pode possuir mensagem de falha."
            );
        }
    }

    private static String normalizeFailureMessage(
            String failureMessage
    ) {
        if (failureMessage == null) {
            return null;
        }

        String normalized = failureMessage.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    public long getFrameNumber() {
        return frameNumber;
    }

    public OmrCaptureSessionState getSessionState() {
        return sessionState;
    }

    public String getDetectorName() {
        return detectorName;
    }

    public int getDetectedMarkerCount() {
        return detectedMarkerCount;
    }

    public int getRejectedCandidateCount() {
        return rejectedCandidateCount;
    }

    public double getProcessingTimeMillis() {
        return processingTimeMillis;
    }

    public boolean hasGeometryEvaluation() {
        return geometryEvaluationAvailable;
    }

    public boolean isGeometryAccepted() {
        return geometryAccepted;
    }

    public double getGeometryBestScore() {
        return geometryBestScore;
    }

    public double getGeometryScoreDifference() {
        return geometryScoreDifference;
    }

    public String getGeometryReason() {
        return geometryReason;
    }

    public boolean hasMarkerStability() {
        return markerStabilityState != null;
    }

    public MarkerStabilityState getMarkerStabilityState() {
        return markerStabilityState;
    }

    public int getConsistentMarkerFrames() {
        return consistentMarkerFrames;
    }

    public int getRequiredMarkerFrames() {
        return requiredMarkerFrames;
    }

    public int getMissedMarkerFrames() {
        return missedMarkerFrames;
    }

    public boolean hasEvidenceProgress() {
        return requiredEvidenceFrames > 0;
    }

    public int getAccumulatedEvidenceFrames() {
        return accumulatedEvidenceFrames;
    }

    public int getRequiredEvidenceFrames() {
        return requiredEvidenceFrames;
    }

    public double getEvidenceProgressRatio() {
        if (requiredEvidenceFrames <= 0) {
            return 0.0;
        }

        return Math.min(
                1.0,
                accumulatedEvidenceFrames
                        / (double) requiredEvidenceFrames
        );
    }

    public SheetInterpretationResult
    getInterpretationResult() {
        return interpretationResult;
    }

    public boolean hasCompleteInterpretation() {
        return interpretationResult != null
                && interpretationResult.isComplete();
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public boolean isFailed() {
        return sessionState
                == OmrCaptureSessionState.FAILED;
    }
}
