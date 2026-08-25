package com.example.leitorgabaritoomr.vision.session;

import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.processing.DefaultMarkerFrameProcessorFactory;
import com.example.leitorgabaritoomr.vision.processing.MarkerFrameProcessor;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;

import org.opencv.core.Mat;

/**
 * Coordena uma captura OMR completa ao longo de varios frames.
 *
 * A sessao possui um processador e um controlador visual exclusivos,
 * configurados permanentemente para FINAL_INTERPRETATION. Portanto,
 * a navegacao do Laboratorio OMR nao controla o quanto esta sessao
 * calcula.
 *
 * Os Mat recebidos continuam pertencendo ao chamador. A sessao pode
 * desenhar no rgbaFrame por meio do pipeline, mas nunca o armazena,
 * libera ou substitui.
 */
public final class OmrCaptureSession
        implements AutoCloseable {

    private final VisionDebugController debugController;
    private final MarkerFrameProcessor frameProcessor;

    private OmrCaptureSessionState state =
            OmrCaptureSessionState.READY;

    private long processedFrameCount;
    private boolean stableReferenceObserved;

    private OmrCaptureFrameResult lastFrameResult;

    private OmrCaptureSession(
            VisionDebugController debugController,
            MarkerFrameProcessor frameProcessor
    ) {
        if (debugController == null
                || frameProcessor == null) {

            throw new IllegalArgumentException(
                    "Controlador e processador sao obrigatorios."
            );
        }

        this.debugController = debugController;
        this.frameProcessor = frameProcessor;
    }

    public static OmrCaptureSession createDefault() {
        return createDefault(
                MarkerDetectorMode.SOLID_SQUARE
        );
    }

    /**
     * Cria uma sessao independente do Laboratorio OMR da Activity.
     */
    public static OmrCaptureSession createDefault(
            MarkerDetectorMode detectorMode
    ) {
        return create(
                detectorMode,
                AvalieCeDevelopmentLayoutFactory.create()
        );
    }

    /**
     * Cria uma sessao com quadrados solidos e o layout informado.
     */
    public static OmrCaptureSession create(
            OmrLayoutDefinition layoutDefinition
    ) {
        return create(
                MarkerDetectorMode.SOLID_SQUARE,
                layoutDefinition
        );
    }

    /**
     * Cria uma sessao independente do Laboratorio OMR usando o
     * detector e o layout informados.
     *
     * O layout passa a ser uma dependencia da sessao. Assim novos
     * modelos de folha podem reutilizar todo o ciclo de captura sem
     * alterar esta classe ou a Activity que fornecer os frames.
     */
    public static OmrCaptureSession create(
            MarkerDetectorMode detectorMode,
            OmrLayoutDefinition layoutDefinition
    ) {
        if (detectorMode == null) {
            throw new IllegalArgumentException(
                    "O modo do detector e obrigatorio."
            );
        }

        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "OmrLayoutDefinition e obrigatorio."
            );
        }

        VisionDebugController debugController =
                new VisionDebugController();

        try {
            debugController
                    .setAutoFreezeOnStableEnabled(false);

            selectFinalProcessingStage(
                    debugController
            );

            MarkerFrameProcessor frameProcessor =
                    DefaultMarkerFrameProcessorFactory.create(
                            detectorMode,
                            debugController,
                            layoutDefinition
                    );

            return new OmrCaptureSession(
                    debugController,
                    frameProcessor
            );
        } catch (RuntimeException | Error exception) {
            debugController.release();
            throw exception;
        }
    }

    /**
     * Processa um frame e devolve apenas um instantaneo leve.
     *
     * Erros de execucao do pipeline transformam a sessao em FAILED e
     * sao descritos no resultado. Entradas invalidas continuam sendo
     * erros de programacao e geram IllegalArgumentException.
     */
    public synchronized OmrCaptureFrameResult processFrame(
            Mat grayFrame,
            Mat rgbaFrame
    ) {
        ensureCanProcess();
        validateFrames(grayFrame, rgbaFrame);

        processedFrameCount++;

        MarkerDetectionResult detectionResult = null;

        try {
            detectionResult =
                    frameProcessor.process(
                            grayFrame,
                            rgbaFrame
                    );

            MarkerSetResolutionResult resolutionResult =
                    frameProcessor
                            .getLastResolutionResult();

            MarkerStabilityResult stabilityResult =
                    frameProcessor
                            .getLastStabilityResult();

            SheetEvidenceAggregate evidenceAggregate =
                    frameProcessor
                            .getLastSheetEvidenceAggregate();

            SheetInterpretationResult interpretationResult =
                    frameProcessor
                            .getLastSheetInterpretationResult();

            state = resolveState(
                    resolutionResult,
                    stabilityResult,
                    interpretationResult
            );

            lastFrameResult =
                    OmrCaptureFrameResult.snapshot(
                            processedFrameCount,
                            state,
                            detectionResult,
                            resolutionResult,
                            stabilityResult,
                            evidenceAggregate,
                            interpretationResult
                    );

            return lastFrameResult;

        } catch (RuntimeException exception) {
            state = OmrCaptureSessionState.FAILED;

            lastFrameResult =
                    OmrCaptureFrameResult.failed(
                            processedFrameCount,
                            detectionResult,
                            frameProcessor
                                    .getLastResolutionResult(),
                            frameProcessor
                                    .getLastStabilityResult(),
                            frameProcessor
                                    .getLastSheetEvidenceAggregate(),
                            describeFailure(exception)
                    );

            return lastFrameResult;
        }
    }

    private OmrCaptureSessionState resolveState(
            MarkerSetResolutionResult resolutionResult,
            MarkerStabilityResult stabilityResult,
            SheetInterpretationResult interpretationResult
    ) {
        if (interpretationResult != null
                && interpretationResult.isComplete()) {

            stableReferenceObserved = true;

            return OmrCaptureSessionState.COMPLETED;
        }

        if (stabilityResult == null
                || stabilityResult.getState() == null) {

            return resolutionResult != null
                    && resolutionResult.isAccepted()
                    ? OmrCaptureSessionState.STABILIZING_MARKERS
                    : stateBeforeFirstStableReference();
        }

        MarkerStabilityState markerState =
                stabilityResult.getState();

        switch (markerState) {
            case STABLE:
                stableReferenceObserved = true;
                return OmrCaptureSessionState.READING_SHEET;

            case ACCUMULATING:
                return stableReferenceObserved
                        ? OmrCaptureSessionState.REACQUIRING_SHEET
                        : OmrCaptureSessionState.STABILIZING_MARKERS;

            case HELD_STABLE:
                return OmrCaptureSessionState.REACQUIRING_SHEET;

            case LOST:
            case SEARCHING:
                return stateBeforeFirstStableReference();

            default:
                throw new IllegalStateException(
                        "Estado de estabilidade nao suportado: "
                                + markerState
                );
        }
    }

    private OmrCaptureSessionState
    stateBeforeFirstStableReference() {
        return stableReferenceObserved
                ? OmrCaptureSessionState.REACQUIRING_SHEET
                : OmrCaptureSessionState.SEARCHING_MARKERS;
    }

    /**
     * Reinicia consenso e contadores, conservando as configuracoes e
     * os componentes pesados da sessao.
     */
    public synchronized void reset() {
        ensureNotClosed();

        frameProcessor.resetStability();

        processedFrameCount = 0;
        stableReferenceObserved = false;
        lastFrameResult = null;
        state = OmrCaptureSessionState.READY;
    }

    @Override
    public synchronized void close() {
        if (state == OmrCaptureSessionState.CLOSED) {
            return;
        }

        try {
            frameProcessor.resetStability();
        } finally {
            debugController.release();
            stableReferenceObserved = false;
            state = OmrCaptureSessionState.CLOSED;
        }
    }

    public synchronized OmrCaptureSessionState getState() {
        return state;
    }

    public synchronized long getProcessedFrameCount() {
        return processedFrameCount;
    }

    public synchronized OmrCaptureFrameResult
    getLastFrameResult() {
        return lastFrameResult;
    }

    public synchronized SheetInterpretationResult
    getCompletedInterpretation() {
        if (lastFrameResult == null
                || !lastFrameResult
                .hasCompleteInterpretation()) {

            return null;
        }

        return lastFrameResult
                .getInterpretationResult();
    }

    public synchronized boolean isClosed() {
        return state == OmrCaptureSessionState.CLOSED;
    }

    private void ensureCanProcess() {
        if (state.canAcceptFrames()) {
            return;
        }

        if (state == OmrCaptureSessionState.CLOSED) {
            throw new IllegalStateException(
                    "A sessao de captura ja foi encerrada."
            );
        }

        throw new IllegalStateException(
                "A sessao esta em "
                        + state
                        + ". Execute reset() antes de novos frames."
        );
    }

    private void ensureNotClosed() {
        if (state == OmrCaptureSessionState.CLOSED) {
            throw new IllegalStateException(
                    "A sessao de captura ja foi encerrada."
            );
        }
    }

    private static void validateFrames(
            Mat grayFrame,
            Mat rgbaFrame
    ) {
        if (grayFrame == null || grayFrame.empty()) {
            throw new IllegalArgumentException(
                    "O frame em escala de cinza e obrigatorio."
            );
        }

        if (rgbaFrame == null || rgbaFrame.empty()) {
            throw new IllegalArgumentException(
                    "O frame RGBA e obrigatorio."
            );
        }

        if (grayFrame.channels() != 1) {
            throw new IllegalArgumentException(
                    "O frame cinza deve possuir um canal."
            );
        }

        if (rgbaFrame.channels() != 4) {
            throw new IllegalArgumentException(
                    "O frame RGBA deve possuir quatro canais."
            );
        }

        if (grayFrame.cols() != rgbaFrame.cols()
                || grayFrame.rows() != rgbaFrame.rows()) {

            throw new IllegalArgumentException(
                    "Os frames cinza e RGBA devem possuir"
                            + " as mesmas dimensoes."
            );
        }
    }

    private static void selectFinalProcessingStage(
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
                            + " FINAL_INTERPRETATION."
            );
        }
    }

    private static String describeFailure(
            RuntimeException exception
    ) {
        String simpleName =
                exception
                        .getClass()
                        .getSimpleName();

        String message = exception.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return simpleName;
        }

        return simpleName
                + ": "
                + message.trim();
    }
}
