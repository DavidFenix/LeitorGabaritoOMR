package com.example.leitorgabaritoomr.presentation.capture;

import com.example.leitorgabaritoomr.vision.session.OmrCaptureFrameResult;
import com.example.leitorgabaritoomr.vision.session.OmrCaptureSessionState;

/**
 * Estado imutavel que uma tela de captura OMR pode apresentar.
 *
 * Esta classe nao depende de Activity, View, recursos Android,
 * camera ou OpenCV. A interface recebe apenas uma instrucao
 * semantica e valores de progresso. Os textos e o desenho visual
 * permanecem sob responsabilidade da camada Android.
 */
public final class OmrCaptureViewState {

    public enum Instruction {
        POSITION_SHEET,
        KEEP_SHEET_STEADY,
        READING_SHEET,
        REPOSITION_SHEET,
        READING_COMPLETED,
        CAPTURE_FAILED,
        SESSION_CLOSED
    }

    public enum ProgressMode {
        HIDDEN,
        MARKER_STABILITY,
        EVIDENCE_ACCUMULATION,
        COMPLETE
    }

    private final OmrCaptureSessionState sessionState;
    private final Instruction instruction;
    private final ProgressMode progressMode;

    private final int progressCurrent;
    private final int progressRequired;

    private final String failureMessage;

    private OmrCaptureViewState(
            OmrCaptureSessionState sessionState,
            Instruction instruction,
            ProgressMode progressMode,
            int progressCurrent,
            int progressRequired,
            String failureMessage
    ) {
        if (sessionState == null
                || instruction == null
                || progressMode == null) {

            throw new IllegalArgumentException(
                    "Estado, instrucao e modo de progresso"
                            + " sao obrigatorios."
            );
        }

        if (progressCurrent < 0
                || progressRequired < 0) {

            throw new IllegalArgumentException(
                    "Os valores de progresso nao podem ser negativos."
            );
        }

        if (progressRequired == 0
                && progressCurrent != 0) {

            throw new IllegalArgumentException(
                    "Progresso atual exige um total positivo."
            );
        }

        if (progressRequired > 0
                && progressCurrent > progressRequired) {

            throw new IllegalArgumentException(
                    "O progresso atual nao pode superar o total."
            );
        }

        if (progressMode == ProgressMode.HIDDEN
                && (progressCurrent != 0
                || progressRequired != 0)) {

            throw new IllegalArgumentException(
                    "Progresso oculto deve usar valores zerados."
            );
        }

        if (progressMode == ProgressMode.COMPLETE
                && (progressCurrent != 1
                || progressRequired != 1)) {

            throw new IllegalArgumentException(
                    "Progresso completo deve ser representado por 1/1."
            );
        }

        String normalizedFailure =
                normalizeFailureMessage(failureMessage);

        if (sessionState != OmrCaptureSessionState.FAILED
                && normalizedFailure != null) {

            throw new IllegalArgumentException(
                    "Somente FAILED pode apresentar falha."
            );
        }

        this.sessionState = sessionState;
        this.instruction = instruction;
        this.progressMode = progressMode;
        this.progressCurrent = progressCurrent;
        this.progressRequired = progressRequired;
        this.failureMessage = normalizedFailure;
    }

    public static OmrCaptureViewState initial() {
        return createHidden(
                OmrCaptureSessionState.READY,
                Instruction.POSITION_SHEET,
                null
        );
    }

    /**
     * Converte o resultado mais recente da sessao em informacao de
     * apresentacao. Um resultado nulo representa a tela inicial.
     */
    public static OmrCaptureViewState from(
            OmrCaptureFrameResult frameResult
    ) {
        if (frameResult == null) {
            return initial();
        }

        return from(
                frameResult.getSessionState(),
                frameResult
        );
    }

    /**
     * Converte o estado atual e, quando existente, o ultimo frame.
     *
     * A sobrecarga e util depois de reset() ou close(), quando o
     * estado da sessao pode ser mais recente que o ultimo resultado.
     */
    public static OmrCaptureViewState from(
            OmrCaptureSessionState sessionState,
            OmrCaptureFrameResult frameResult
    ) {
        if (sessionState == null) {
            throw new IllegalArgumentException(
                    "O estado da sessao e obrigatorio."
            );
        }

        switch (sessionState) {
            case READY:
            case SEARCHING_MARKERS:
                return createHidden(
                        sessionState,
                        Instruction.POSITION_SHEET,
                        null
                );

            case STABILIZING_MARKERS:
                return createProgress(
                        sessionState,
                        Instruction.KEEP_SHEET_STEADY,
                        ProgressMode.MARKER_STABILITY,
                        frameResult == null
                                ? 0
                                : frameResult
                                .getConsistentMarkerFrames(),
                        frameResult == null
                                ? 0
                                : frameResult
                                .getRequiredMarkerFrames()
                );

            case READING_SHEET:
                return createProgress(
                        sessionState,
                        Instruction.READING_SHEET,
                        ProgressMode.EVIDENCE_ACCUMULATION,
                        frameResult == null
                                ? 0
                                : frameResult
                                .getAccumulatedEvidenceFrames(),
                        frameResult == null
                                ? 0
                                : frameResult
                                .getRequiredEvidenceFrames()
                );

            case REACQUIRING_SHEET:
                return createProgress(
                        sessionState,
                        Instruction.REPOSITION_SHEET,
                        ProgressMode.EVIDENCE_ACCUMULATION,
                        frameResult == null
                                ? 0
                                : frameResult
                                .getAccumulatedEvidenceFrames(),
                        frameResult == null
                                ? 0
                                : frameResult
                                .getRequiredEvidenceFrames()
                );

            case COMPLETED:
                return new OmrCaptureViewState(
                        sessionState,
                        Instruction.READING_COMPLETED,
                        ProgressMode.COMPLETE,
                        1,
                        1,
                        null
                );

            case FAILED:
                return createHidden(
                        sessionState,
                        Instruction.CAPTURE_FAILED,
                        frameResult == null
                                ? null
                                : frameResult
                                .getFailureMessage()
                );

            case CLOSED:
                return createHidden(
                        sessionState,
                        Instruction.SESSION_CLOSED,
                        null
                );

            default:
                throw new IllegalStateException(
                        "Estado de sessao nao suportado: "
                                + sessionState
                );
        }
    }

    private static OmrCaptureViewState createHidden(
            OmrCaptureSessionState sessionState,
            Instruction instruction,
            String failureMessage
    ) {
        return new OmrCaptureViewState(
                sessionState,
                instruction,
                ProgressMode.HIDDEN,
                0,
                0,
                failureMessage
        );
    }

    private static OmrCaptureViewState createProgress(
            OmrCaptureSessionState sessionState,
            Instruction instruction,
            ProgressMode progressMode,
            int progressCurrent,
            int progressRequired
    ) {
        int safeRequired =
                Math.max(0, progressRequired);

        int safeCurrent =
                safeRequired == 0
                        ? 0
                        : Math.max(
                                0,
                                Math.min(
                                        progressCurrent,
                                        safeRequired
                                )
                        );

        return new OmrCaptureViewState(
                sessionState,
                instruction,
                progressMode,
                safeCurrent,
                safeRequired,
                null
        );
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

    public OmrCaptureSessionState getSessionState() {
        return sessionState;
    }

    public Instruction getInstruction() {
        return instruction;
    }

    public ProgressMode getProgressMode() {
        return progressMode;
    }

    public int getProgressCurrent() {
        return progressCurrent;
    }

    public int getProgressRequired() {
        return progressRequired;
    }

    public boolean isProgressVisible() {
        return progressMode != ProgressMode.HIDDEN
                && progressRequired > 0;
    }

    public double getProgressRatio() {
        if (progressRequired <= 0) {
            return 0.0;
        }

        return progressCurrent
                / (double) progressRequired;
    }

    public int getProgressPercent() {
        return (int) Math.round(
                getProgressRatio() * 100.0
        );
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public boolean isSuccessful() {
        return sessionState.isSuccessful();
    }

    public boolean isTerminal() {
        return sessionState.isTerminal();
    }

    public boolean canRetry() {
        return sessionState
                .requiresResetBeforeProcessing();
    }
}
