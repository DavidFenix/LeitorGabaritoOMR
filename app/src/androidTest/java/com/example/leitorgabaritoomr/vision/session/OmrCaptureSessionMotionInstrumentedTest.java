package com.example.leitorgabaritoomr.vision.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.vision.fixture.OmrControlledFixtureAssertions;
import com.example.leitorgabaritoomr.vision.fixture.OmrFixtureCameraMotionProvider;
import com.example.leitorgabaritoomr.vision.fixture.OmrFixtureFrameInterruptionProvider;
import com.example.leitorgabaritoomr.vision.fixture.OmrFixtureFrameProvider;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

/**
 * Valida os estados publicos de OmrCaptureSession diante de
 * movimento e interrupcoes temporais deterministicas.
 *
 * Estes testes passam exclusivamente pela API publica da sessao.
 * O Laboratorio OMR e o MarkerFrameProcessor nao sao acessados.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrCaptureSessionMotionInstrumentedTest {

    private static final String TAG =
            "OMR_SESSION_MOTION";

    private static final String ASSET_PATH =
            "omr/gabarito_casos_controlados_v3.png";

    private static final int EXPECTED_WIDTH = 1303;
    private static final int EXPECTED_HEIGHT = 602;

    private static final int MAXIMUM_FRAME_COUNT = 45;
    private static final int SAFETY_FRAME_COUNT = 30;

    private static Mat sourceRgba;

    @BeforeClass
    public static void initializeFixture()
            throws IOException {

        assertTrue(
                "OpenCV nao foi inicializado no ambiente de teste.",
                OpenCVLoader.initDebug()
        );

        Context testContext =
                InstrumentationRegistry
                        .getInstrumentation()
                        .getContext();

        sourceRgba = loadRgbaFixture(testContext);

        assertNotNull(
                "A fixture RGBA nao foi carregada.",
                sourceRgba
        );

        assertFalse(
                "A fixture RGBA esta vazia.",
                sourceRgba.empty()
        );

        assertEquals(
                EXPECTED_WIDTH,
                sourceRgba.cols()
        );

        assertEquals(
                EXPECTED_HEIGHT,
                sourceRgba.rows()
        );
    }

    @AfterClass
    public static void releaseFixture() {
        if (sourceRgba != null) {
            sourceRgba.release();
            sourceRgba = null;
        }
    }

    @Test
    public void gentleMotionCompletesThroughSessionApi() {
        String sequenceId = "session-gentle-motion";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id=" + sequenceId
        );

        try (OmrCaptureSession session =
                     OmrCaptureSession.createDefault();

             OmrFixtureFrameProvider provider =
                     OmrFixtureCameraMotionProvider
                             .gentle(sourceRgba)) {

            SessionRunResult runResult =
                    runSequence(
                            session,
                            provider,
                            MAXIMUM_FRAME_COUNT
                    );

            SheetInterpretationResult interpretation =
                    assertCompletedAndCorrect(
                            sequenceId,
                            session,
                            runResult
                    );

            assertTrue(
                    "A sessao deveria observar leitura da folha.",
                    runResult.hasObserved(
                            OmrCaptureSessionState.READING_SHEET
                    )
            );

            Log.i(
                    TAG,
                    "SEQUENCE_OK | id="
                            + sequenceId
                            + " | frames="
                            + runResult.getProcessedFrameCount()
                            + " | states="
                            + runResult.getObservedStates()
                            + " | "
                            + OmrControlledFixtureAssertions
                            .summarize(interpretation)
            );
        }
    }

    @Test
    public void isolatedInterruptionsReacquireAndComplete() {
        String sequenceId =
                "session-isolated-interruptions";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | pattern=4-visible-1-missing"
        );

        try (OmrCaptureSession session =
                     OmrCaptureSession.createDefault();

             OmrFixtureFrameProvider provider =
                     OmrFixtureFrameInterruptionProvider
                             .repeatingPattern(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     4,
                                     1
                             )) {

            SessionRunResult runResult =
                    runSequence(
                            session,
                            provider,
                            MAXIMUM_FRAME_COUNT
                    );

            SheetInterpretationResult interpretation =
                    assertCompletedAndCorrect(
                            sequenceId,
                            session,
                            runResult
                    );

            assertTrue(
                    "A interrupcao isolada deveria produzir REACQUIRING_SHEET.",
                    runResult.hasObserved(
                            OmrCaptureSessionState.REACQUIRING_SHEET
                    )
            );

            assertFalse(
                    "A interrupcao isolada nao pode falhar a sessao.",
                    runResult.hasObserved(
                            OmrCaptureSessionState.FAILED
                    )
            );

            Log.i(
                    TAG,
                    "SEQUENCE_OK | id="
                            + sequenceId
                            + " | frames="
                            + runResult.getProcessedFrameCount()
                            + " | reacquiringSeen=true"
                            + " | states="
                            + runResult.getObservedStates()
                            + " | "
                            + OmrControlledFixtureAssertions
                            .summarize(interpretation)
            );
        }
    }

    @Test
    public void excessiveInterruptionsNeverCompleteSession() {
        String sequenceId =
                "session-excessive-interruptions";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | pattern=1-visible-3-missing"
        );

        try (OmrCaptureSession session =
                     OmrCaptureSession.createDefault();

             OmrFixtureFrameProvider provider =
                     OmrFixtureFrameInterruptionProvider
                             .repeatingPattern(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     1,
                                     3
                             )) {

            SessionRunResult runResult =
                    runSequence(
                            session,
                            provider,
                            SAFETY_FRAME_COUNT
                    );

            assertEquals(
                    SAFETY_FRAME_COUNT,
                    runResult.getProcessedFrameCount()
            );

            assertFalse(
                    "A sequencia insegura nao pode concluir.",
                    runResult.hasObserved(
                            OmrCaptureSessionState.COMPLETED
                    )
            );

            assertFalse(
                    "Uma captura insegura nao e erro interno.",
                    runResult.hasObserved(
                            OmrCaptureSessionState.FAILED
                    )
            );

            assertTrue(
                    "A sessao deve continuar aguardando novos frames.",
                    session.getState().canAcceptFrames()
            );

            assertNull(
                    "Captura insegura nao pode produzir interpretacao.",
                    session.getCompletedInterpretation()
            );

            assertNotNull(
                    "O ultimo resultado de frame deveria existir.",
                    runResult.getLastFrameResult()
            );

            assertNull(
                    "O ultimo frame nao pode conter interpretacao completa.",
                    runResult
                            .getLastFrameResult()
                            .getInterpretationResult()
            );

            Log.i(
                    TAG,
                    "SEQUENCE_REJECTED_OK | id="
                            + sequenceId
                            + " | frames="
                            + runResult.getProcessedFrameCount()
                            + " | finalState="
                            + session.getState()
                            + " | states="
                            + runResult.getObservedStates()
            );
        }
    }

    private static SheetInterpretationResult
    assertCompletedAndCorrect(
            String sequenceId,
            OmrCaptureSession session,
            SessionRunResult runResult
    ) {
        OmrCaptureFrameResult lastFrameResult =
                runResult.getLastFrameResult();

        assertNotNull(
                sequenceId + " | ultimo resultado ausente",
                lastFrameResult
        );

        assertEquals(
                sequenceId,
                OmrCaptureSessionState.COMPLETED,
                session.getState()
        );

        assertEquals(
                sequenceId,
                OmrCaptureSessionState.COMPLETED,
                lastFrameResult.getSessionState()
        );

        assertEquals(
                runResult.getProcessedFrameCount(),
                lastFrameResult.getFrameNumber()
        );

        return OmrControlledFixtureAssertions
                .assertCompleteAndCorrect(
                        sequenceId,
                        lastFrameResult
                                .getInterpretationResult()
                );
    }

    private static SessionRunResult runSequence(
            OmrCaptureSession session,
            OmrFixtureFrameProvider provider,
            int maximumFrameCount
    ) {
        if (session == null || provider == null) {
            throw new IllegalArgumentException(
                    "Sessao e provedor sao obrigatorios."
            );
        }

        if (maximumFrameCount <= 0) {
            throw new IllegalArgumentException(
                    "maximumFrameCount deve ser positivo."
            );
        }

        EnumSet<OmrCaptureSessionState> observedStates =
                EnumSet.noneOf(
                        OmrCaptureSessionState.class
                );

        OmrCaptureFrameResult lastFrameResult = null;
        int processedFrameCount = 0;

        for (int frameIndex = 0;
             frameIndex < maximumFrameCount
                     && session.getState().canAcceptFrames();
             frameIndex++) {

            Mat rgbaFrame =
                    provider.createRgbaFrame(frameIndex);

            Mat grayFrame = new Mat();

            try {
                Imgproc.cvtColor(
                        rgbaFrame,
                        grayFrame,
                        Imgproc.COLOR_RGBA2GRAY
                );

                lastFrameResult =
                        session.processFrame(
                                grayFrame,
                                rgbaFrame
                        );

                processedFrameCount++;

                observedStates.add(
                        lastFrameResult
                                .getSessionState()
                );
            } finally {
                grayFrame.release();
                rgbaFrame.release();
            }
        }

        return new SessionRunResult(
                processedFrameCount,
                lastFrameResult,
                observedStates
        );
    }

    private static Mat loadRgbaFixture(
            Context testContext
    ) throws IOException {
        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        Bitmap decodedBitmap = null;
        Bitmap rgbaBitmap = null;
        Mat rgba = new Mat();

        try (InputStream inputStream =
                     testContext
                             .getAssets()
                             .open(ASSET_PATH)) {

            decodedBitmap =
                    BitmapFactory.decodeStream(
                            inputStream,
                            null,
                            options
                    );

            assertNotNull(
                    "A fixture v3 nao foi decodificada.",
                    decodedBitmap
            );

            rgbaBitmap =
                    decodedBitmap.copy(
                            Bitmap.Config.ARGB_8888,
                            false
                    );

            assertNotNull(
                    "Nao foi possivel obter Bitmap ARGB_8888.",
                    rgbaBitmap
            );

            Utils.bitmapToMat(
                    rgbaBitmap,
                    rgba
            );

            return rgba;
        } catch (IOException | RuntimeException | Error exception) {
            rgba.release();
            throw exception;
        } finally {
            if (rgbaBitmap != null
                    && rgbaBitmap != decodedBitmap) {

                rgbaBitmap.recycle();
            }

            if (decodedBitmap != null) {
                decodedBitmap.recycle();
            }
        }
    }

    private static final class SessionRunResult {

        private final int processedFrameCount;
        private final OmrCaptureFrameResult lastFrameResult;
        private final EnumSet<OmrCaptureSessionState>
                observedStates;

        private SessionRunResult(
                int processedFrameCount,
                OmrCaptureFrameResult lastFrameResult,
                EnumSet<OmrCaptureSessionState> observedStates
        ) {
            this.processedFrameCount = processedFrameCount;
            this.lastFrameResult = lastFrameResult;
            this.observedStates =
                    EnumSet.copyOf(observedStates);
        }

        private int getProcessedFrameCount() {
            return processedFrameCount;
        }

        private OmrCaptureFrameResult getLastFrameResult() {
            return lastFrameResult;
        }

        private boolean hasObserved(
                OmrCaptureSessionState state
        ) {
            return observedStates.contains(state);
        }

        private EnumSet<OmrCaptureSessionState>
        getObservedStates() {
            return EnumSet.copyOf(observedStates);
        }
    }
}
