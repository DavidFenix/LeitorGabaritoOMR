package com.example.leitorgabaritoomr.vision.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.vision.fixture.OmrControlledFixtureAssertions;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

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

/**
 * Valida o ciclo de vida publico da sessao com a fixture controlada.
 *
 * O teste nao acessa MarkerFrameProcessor nem VisionDebugController.
 * Toda a leitura acontece exclusivamente pela API de sessao.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrCaptureSessionInstrumentedTest {

    private static final String TAG =
            "OMR_SESSION_TEST";

    private static final String ASSET_PATH =
            "omr/gabarito_casos_controlados_v3.png";

    private static final int EXPECTED_WIDTH = 1303;
    private static final int EXPECTED_HEIGHT = 602;
    private static final int MAXIMUM_FRAME_COUNT = 30;

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

        assertTrue(
                "A fixture RGBA esta vazia.",
                !sourceRgba.empty()
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
    public void sessionCompletesBlocksResetsAndCompletesAgain() {
        OmrCaptureSession session =
                OmrCaptureSession.createDefault();

        long firstCaptureFrames;
        long secondCaptureFrames;

        try {
            assertEquals(
                    OmrCaptureSessionState.READY,
                    session.getState()
            );

            assertEquals(
                    0L,
                    session.getProcessedFrameCount()
            );

            assertNull(
                    session.getLastFrameResult()
            );

            OmrCaptureFrameResult firstResult =
                    processUntilTerminal(session);

            assertCompleted(
                    "capture=first",
                    session,
                    firstResult
            );

            firstCaptureFrames =
                    session.getProcessedFrameCount();

            assertFrameAfterCompletionIsRejected(
                    session
            );

            session.reset();

            assertEquals(
                    OmrCaptureSessionState.READY,
                    session.getState()
            );

            assertEquals(
                    0L,
                    session.getProcessedFrameCount()
            );

            assertNull(
                    session.getLastFrameResult()
            );

            assertNull(
                    session.getCompletedInterpretation()
            );

            OmrCaptureFrameResult secondResult =
                    processUntilTerminal(session);

            assertCompleted(
                    "capture=second",
                    session,
                    secondResult
            );

            secondCaptureFrames =
                    session.getProcessedFrameCount();

            assertEquals(
                    "O reset deve reproduzir a mesma captura deterministica.",
                    firstCaptureFrames,
                    secondCaptureFrames
            );

            Log.i(
                    TAG,
                    "SESSION_OK"
                            + " | firstFrames="
                            + firstCaptureFrames
                            + " | secondFrames="
                            + secondCaptureFrames
                            + " | "
                            + OmrControlledFixtureAssertions
                            .summarize(
                                    secondResult
                                            .getInterpretationResult()
                            )
            );
        } finally {
            session.close();
        }

        assertTrue(
                "A sessao deveria estar encerrada.",
                session.isClosed()
        );

        assertEquals(
                OmrCaptureSessionState.CLOSED,
                session.getState()
        );
    }

    @Test
    public void explicitlyProvidedLayoutCompletesCorrectly() {
        OmrCaptureSession session =
                OmrCaptureSession.create(
                        AvalieCeDevelopmentLayoutFactory.create()
                );

        try {
            assertEquals(
                    OmrCaptureSessionState.READY,
                    session.getState()
            );

            OmrCaptureFrameResult result =
                    processUntilTerminal(session);

            assertCompleted(
                    "capture=explicit-layout",
                    session,
                    result
            );

            Log.i(
                    TAG,
                    "EXPLICIT_LAYOUT_OK"
                            + " | frames="
                            + session.getProcessedFrameCount()
                            + " | "
                            + OmrControlledFixtureAssertions
                            .summarize(
                                    result.getInterpretationResult()
                            )
            );
        } finally {
            session.close();
        }

        assertTrue(
                "A sessao configuravel deveria estar encerrada.",
                session.isClosed()
        );
    }

    private static OmrCaptureFrameResult
    processUntilTerminal(
            OmrCaptureSession session
    ) {
        OmrCaptureFrameResult lastResult = null;

        for (int frameIndex = 0;
             frameIndex < MAXIMUM_FRAME_COUNT
                     && session.getState().canAcceptFrames();
             frameIndex++) {

            Mat rgbaFrame = sourceRgba.clone();
            Mat grayFrame = new Mat();

            try {
                Imgproc.cvtColor(
                        rgbaFrame,
                        grayFrame,
                        Imgproc.COLOR_RGBA2GRAY
                );

                lastResult =
                        session.processFrame(
                                grayFrame,
                                rgbaFrame
                        );
            } finally {
                grayFrame.release();
                rgbaFrame.release();
            }
        }

        return lastResult;
    }

    private static void assertCompleted(
            String scenario,
            OmrCaptureSession session,
            OmrCaptureFrameResult frameResult
    ) {
        assertNotNull(
                scenario + " | resultado do frame ausente",
                frameResult
        );

        assertEquals(
                scenario + " | " + describe(frameResult),
                OmrCaptureSessionState.COMPLETED,
                frameResult.getSessionState()
        );

        assertEquals(
                scenario,
                OmrCaptureSessionState.COMPLETED,
                session.getState()
        );

        assertEquals(
                session.getProcessedFrameCount(),
                frameResult.getFrameNumber()
        );

        assertTrue(
                scenario + " | quantidade invalida de frames",
                session.getProcessedFrameCount() > 0
                        && session.getProcessedFrameCount()
                        <= MAXIMUM_FRAME_COUNT
        );

        SheetInterpretationResult interpretation =
                OmrControlledFixtureAssertions
                        .assertCompleteAndCorrect(
                                scenario,
                                frameResult
                                        .getInterpretationResult()
                        );

        assertSame(
                scenario
                        + " | a sessao deve preservar"
                        + " a interpretacao concluida",
                interpretation,
                session.getCompletedInterpretation()
        );
    }

    private static void
    assertFrameAfterCompletionIsRejected(
            OmrCaptureSession session
    ) {
        Mat rgbaFrame = sourceRgba.clone();
        Mat grayFrame = new Mat();

        boolean rejected = false;

        try {
            Imgproc.cvtColor(
                    rgbaFrame,
                    grayFrame,
                    Imgproc.COLOR_RGBA2GRAY
            );

            session.processFrame(
                    grayFrame,
                    rgbaFrame
            );
        } catch (IllegalStateException expected) {
            rejected = true;
        } finally {
            grayFrame.release();
            rgbaFrame.release();
        }

        assertTrue(
                "COMPLETED deve rejeitar frames ate reset().",
                rejected
        );
    }

    private static String describe(
            OmrCaptureFrameResult result
    ) {
        return "state="
                + result.getSessionState()
                + " | frame="
                + result.getFrameNumber()
                + " | markers="
                + result.getDetectedMarkerCount()
                + " | geometryAccepted="
                + result.isGeometryAccepted()
                + " | stability="
                + result.getMarkerStabilityState()
                + " | evidence="
                + result.getAccumulatedEvidenceFrames()
                + "/"
                + result.getRequiredEvidenceFrames()
                + " | failure="
                + result.getFailureMessage();
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
}
