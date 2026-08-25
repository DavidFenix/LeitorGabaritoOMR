package com.example.leitorgabaritoomr.presentation.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.vision.session.OmrCaptureFrameResult;
import com.example.leitorgabaritoomr.vision.session.OmrCaptureSessionState;

import org.junit.Test;

/**
 * Testes JVM do contrato de apresentacao da captura.
 */
public final class OmrCaptureViewStateTest {

    private static final double DELTA = 0.000001;

    @Test
    public void initialStateAsksToPositionSheet() {
        OmrCaptureViewState viewState =
                OmrCaptureViewState.initial();

        assertEquals(
                OmrCaptureSessionState.READY,
                viewState.getSessionState()
        );

        assertEquals(
                OmrCaptureViewState.Instruction.POSITION_SHEET,
                viewState.getInstruction()
        );

        assertEquals(
                OmrCaptureViewState.ProgressMode.HIDDEN,
                viewState.getProgressMode()
        );

        assertFalse(viewState.isProgressVisible());
        assertFalse(viewState.isTerminal());
        assertFalse(viewState.isSuccessful());
        assertFalse(viewState.canRetry());

        assertEquals(
                0.0,
                viewState.getProgressRatio(),
                DELTA
        );
    }

    @Test
    public void activeSessionStatesProduceExpectedInstructions() {
        assertInstruction(
                OmrCaptureSessionState.SEARCHING_MARKERS,
                OmrCaptureViewState.Instruction.POSITION_SHEET,
                OmrCaptureViewState.ProgressMode.HIDDEN
        );

        assertInstruction(
                OmrCaptureSessionState.STABILIZING_MARKERS,
                OmrCaptureViewState.Instruction.KEEP_SHEET_STEADY,
                OmrCaptureViewState.ProgressMode.MARKER_STABILITY
        );

        assertInstruction(
                OmrCaptureSessionState.READING_SHEET,
                OmrCaptureViewState.Instruction.READING_SHEET,
                OmrCaptureViewState.ProgressMode.EVIDENCE_ACCUMULATION
        );

        assertInstruction(
                OmrCaptureSessionState.REACQUIRING_SHEET,
                OmrCaptureViewState.Instruction.REPOSITION_SHEET,
                OmrCaptureViewState.ProgressMode.EVIDENCE_ACCUMULATION
        );
    }

    @Test
    public void completedStateShowsFullProgress() {
        OmrCaptureViewState viewState =
                OmrCaptureViewState.from(
                        OmrCaptureSessionState.COMPLETED,
                        null
                );

        assertEquals(
                OmrCaptureViewState.Instruction.READING_COMPLETED,
                viewState.getInstruction()
        );

        assertEquals(
                OmrCaptureViewState.ProgressMode.COMPLETE,
                viewState.getProgressMode()
        );

        assertTrue(viewState.isProgressVisible());
        assertTrue(viewState.isSuccessful());
        assertTrue(viewState.isTerminal());
        assertTrue(viewState.canRetry());

        assertEquals(1, viewState.getProgressCurrent());
        assertEquals(1, viewState.getProgressRequired());
        assertEquals(100, viewState.getProgressPercent());

        assertEquals(
                1.0,
                viewState.getProgressRatio(),
                DELTA
        );
    }

    @Test
    public void failedFramePreservesDiagnosticMessage() {
        OmrCaptureFrameResult frameResult =
                OmrCaptureFrameResult.failed(
                        1L,
                        null,
                        null,
                        null,
                        null,
                        "  falha controlada  "
                );

        OmrCaptureViewState viewState =
                OmrCaptureViewState.from(frameResult);

        assertEquals(
                OmrCaptureSessionState.FAILED,
                viewState.getSessionState()
        );

        assertEquals(
                OmrCaptureViewState.Instruction.CAPTURE_FAILED,
                viewState.getInstruction()
        );

        assertEquals(
                "falha controlada",
                viewState.getFailureMessage()
        );

        assertTrue(viewState.isTerminal());
        assertTrue(viewState.canRetry());
        assertFalse(viewState.isSuccessful());
        assertFalse(viewState.isProgressVisible());
    }

    @Test
    public void closedStateCannotRetryOrShowStaleFailure() {
        OmrCaptureFrameResult previousFailure =
                OmrCaptureFrameResult.failed(
                        1L,
                        null,
                        null,
                        null,
                        null,
                        "falha anterior"
                );

        OmrCaptureViewState viewState =
                OmrCaptureViewState.from(
                        OmrCaptureSessionState.CLOSED,
                        previousFailure
                );

        assertEquals(
                OmrCaptureViewState.Instruction.SESSION_CLOSED,
                viewState.getInstruction()
        );

        assertTrue(viewState.isTerminal());
        assertFalse(viewState.isSuccessful());
        assertFalse(viewState.canRetry());
        assertFalse(viewState.isProgressVisible());
        assertNull(viewState.getFailureMessage());
    }

    @Test
    public void nullFrameResultMapsToInitialState() {
        OmrCaptureViewState viewState =
                OmrCaptureViewState.from(
                        (OmrCaptureFrameResult) null
                );

        assertEquals(
                OmrCaptureSessionState.READY,
                viewState.getSessionState()
        );

        assertEquals(
                OmrCaptureViewState.Instruction.POSITION_SHEET,
                viewState.getInstruction()
        );
    }

    private static void assertInstruction(
            OmrCaptureSessionState sessionState,
            OmrCaptureViewState.Instruction expectedInstruction,
            OmrCaptureViewState.ProgressMode expectedProgressMode
    ) {
        OmrCaptureViewState viewState =
                OmrCaptureViewState.from(
                        sessionState,
                        null
                );

        assertEquals(
                sessionState,
                viewState.getSessionState()
        );

        assertEquals(
                expectedInstruction,
                viewState.getInstruction()
        );

        assertEquals(
                expectedProgressMode,
                viewState.getProgressMode()
        );

        assertFalse(viewState.isTerminal());
        assertFalse(viewState.isSuccessful());
        assertFalse(viewState.canRetry());
        assertFalse(viewState.isProgressVisible());
        assertNull(viewState.getFailureMessage());
    }
}
