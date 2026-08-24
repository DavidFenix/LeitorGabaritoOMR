package com.example.leitorgabaritoomr.vision.fixture;

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

import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.IOException;
import java.io.InputStream;

/**
 * Exercita estabilidade, normalizacao, medicao e consenso enquanto
 * a fixture muda de posicao e perspectiva entre os frames.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrControlledFixtureCameraMotionInstrumentedTest {

    private static final String TAG =
            "OMR_MOTION_TEST";

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
    public void gentleCameraMotionReachesCorrectConsensus() {
        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id=gentle-camera-motion"
                        + " | maxFrames="
                        + MAXIMUM_FRAME_COUNT
        );

        OmrFixturePipelineRunner.Result runResult;

        try (OmrFixtureCameraMotionProvider provider =
                     OmrFixtureCameraMotionProvider
                             .gentle(sourceRgba)) {

            runResult =
                    OmrFixturePipelineRunner.run(
                            provider,
                            MAXIMUM_FRAME_COUNT
                    );
        }

        SheetInterpretationResult interpretation =
                OmrControlledFixtureAssertions
                        .assertCompleteAndCorrect(
                                "sequence=gentle-camera-motion",
                                runResult
                        );

        assertTrue(
                "A sequencia deve atravessar varios frames.",
                runResult.getProcessedFrameCount() >= 3
        );

        Log.i(
                TAG,
                "SEQUENCE_OK | id=gentle-camera-motion"
                        + " | frames="
                        + runResult.getProcessedFrameCount()
                        + " | "
                        + OmrControlledFixtureAssertions
                        .summarize(interpretation)
        );
    }

    @Test
    public void initialMissingFramesThenMotionRecovers() {
        String sequenceId =
                "initially-missing-then-motion";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | interruptedInitialFrames=4"
                        + " | maxFrames="
                        + MAXIMUM_FRAME_COUNT
        );

        OmrFixturePipelineRunner.Result runResult;

        try (OmrFixtureFrameInterruptionProvider provider =
                     OmrFixtureFrameInterruptionProvider
                             .initiallyUnavailable(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     4
                             )) {

            runResult =
                    OmrFixturePipelineRunner.run(
                            provider,
                            MAXIMUM_FRAME_COUNT
                    );
        }

        SheetInterpretationResult interpretation =
                OmrControlledFixtureAssertions
                        .assertCompleteAndCorrect(
                                "sequence=" + sequenceId,
                                runResult
                        );

        assertTrue(
                "Os frames iniciais ausentes devem atrasar o consenso.",
                runResult.getProcessedFrameCount() > 9
        );

        assertFalse(
                "Ausencia antes da primeira referencia nao e perda.",
                runResult
                        .getProgress()
                        .wasLostStabilityObserved()
        );

        Log.i(
                TAG,
                "SEQUENCE_OK | id="
                        + sequenceId
                        + " | frames="
                        + runResult.getProcessedFrameCount()
                        + " | heldStableSeen="
                        + runResult
                        .getProgress()
                        .wasHeldStableObserved()
                        + " | lostSeen="
                        + runResult
                        .getProgress()
                        .wasLostStabilityObserved()
                        + " | "
                        + OmrControlledFixtureAssertions
                        .summarize(interpretation)
        );
    }

    @Test
    public void isolatedInterruptionsAreHeldAndRemainCorrect() {
        String sequenceId =
                "isolated-interruptions";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | pattern=4-visible-1-missing"
                        + " | maxFrames="
                        + MAXIMUM_FRAME_COUNT
        );

        OmrFixturePipelineRunner.Result runResult;

        try (OmrFixtureFrameInterruptionProvider provider =
                     OmrFixtureFrameInterruptionProvider
                             .repeatingPattern(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     4,
                                     1
                             )) {

            runResult =
                    OmrFixturePipelineRunner.run(
                            provider,
                            MAXIMUM_FRAME_COUNT
                    );
        }

        SheetInterpretationResult interpretation =
                OmrControlledFixtureAssertions
                        .assertCompleteAndCorrect(
                                "sequence=" + sequenceId,
                                runResult
                        );

        assertTrue(
                "O estado HELD_STABLE deveria ter sido observado.",
                runResult
                        .getProgress()
                        .wasHeldStableObserved()
        );

        assertFalse(
                "Uma falha isolada nao deveria causar LOST.",
                runResult
                        .getProgress()
                        .wasLostStabilityObserved()
        );

        Log.i(
                TAG,
                "SEQUENCE_OK | id="
                        + sequenceId
                        + " | frames="
                        + runResult.getProcessedFrameCount()
                        + " | heldStableSeen=true"
                        + " | lostSeen=false"
                        + " | "
                        + OmrControlledFixtureAssertions
                        .summarize(interpretation)
        );
    }

    @Test
    public void excessiveInterruptionsNeverProduceInterpretation() {
        String sequenceId =
                "excessive-interruptions";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | pattern=1-visible-3-missing"
                        + " | maxFrames="
                        + SAFETY_FRAME_COUNT
        );

        OmrFixturePipelineRunner.Result runResult;

        try (OmrFixtureFrameInterruptionProvider provider =
                     OmrFixtureFrameInterruptionProvider
                             .repeatingPattern(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     1,
                                     3
                             )) {

            runResult =
                    OmrFixturePipelineRunner.run(
                            provider,
                            SAFETY_FRAME_COUNT
                    );
        }

        assertFalse(
                "Captura persistentemente interrompida nao pode concluir.",
                runResult.isComplete()
        );

        assertNull(
                "Captura insegura nao pode produzir interpretacao.",
                runResult.getInterpretationResult()
        );

        assertTrue(
                "O estado LOST deveria ter sido observado.",
                runResult
                        .getProgress()
                        .wasLostStabilityObserved()
        );

        Log.i(
                TAG,
                "SEQUENCE_REJECTED_OK | id="
                        + sequenceId
                        + " | frames="
                        + runResult.getProcessedFrameCount()
                        + " | heldStableSeen="
                        + runResult
                        .getProgress()
                        .wasHeldStableObserved()
                        + " | lostSeen=true"
                        + " | furthest="
                        + runResult
                        .getProgress()
                        .getFurthestStage()
        );
    }

    @Test
    public void temporaryPartialExitThenReturnRecovers() {
        String sequenceId =
                "partial-exit-then-return";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | firstAffectedFrame=3"
                        + " | affectedFrames=7"
                        + " | peakHorizontalOffset=0.55"
                        + " | maxFrames="
                        + MAXIMUM_FRAME_COUNT
        );

        OmrFixturePipelineRunner.Result runResult;

        try (OmrFixturePartialVisibilityProvider provider =
                     OmrFixturePartialVisibilityProvider
                             .temporaryHorizontalExit(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     3,
                                     7,
                                     0.55
                             )) {

            runResult =
                    OmrFixturePipelineRunner.run(
                            provider,
                            MAXIMUM_FRAME_COUNT
                    );
        }

        SheetInterpretationResult interpretation =
                OmrControlledFixtureAssertions
                        .assertCompleteAndCorrect(
                                "sequence=" + sequenceId,
                                runResult
                        );

        assertTrue(
                "A saida parcial deve atrasar o consenso final.",
                runResult.getProcessedFrameCount() > 9
        );

        assertTrue(
                "A ausencia prolongada de marcadores deve causar LOST.",
                runResult
                        .getProgress()
                        .wasLostStabilityObserved()
        );

        Log.i(
                TAG,
                "SEQUENCE_OK | id="
                        + sequenceId
                        + " | frames="
                        + runResult.getProcessedFrameCount()
                        + " | heldStableSeen="
                        + runResult
                        .getProgress()
                        .wasHeldStableObserved()
                        + " | lostSeen=true"
                        + " | "
                        + OmrControlledFixtureAssertions
                        .summarize(interpretation)
        );
    }

    @Test
    public void persistentPartialVisibilityNeverProducesInterpretation() {
        String sequenceId =
                "persistent-partial-visibility";

        Log.i(
                TAG,
                "SEQUENCE_BEGIN | id="
                        + sequenceId
                        + " | horizontalOffset=0.45"
                        + " | maxFrames="
                        + SAFETY_FRAME_COUNT
        );

        OmrFixturePipelineRunner.Result runResult;

        try (OmrFixturePartialVisibilityProvider provider =
                     OmrFixturePartialVisibilityProvider
                             .persistentOffset(
                                     OmrFixtureCameraMotionProvider
                                             .gentle(sourceRgba),
                                     0.45,
                                     0.0
                             )) {

            runResult =
                    OmrFixturePipelineRunner.run(
                            provider,
                            SAFETY_FRAME_COUNT
                    );
        }

        assertFalse(
                "Folha permanentemente cortada nao pode concluir.",
                runResult.isComplete()
        );

        assertNull(
                "Folha incompleta nao pode produzir interpretacao.",
                runResult.getInterpretationResult()
        );

        Log.i(
                TAG,
                "SEQUENCE_REJECTED_OK | id="
                        + sequenceId
                        + " | frames="
                        + runResult.getProcessedFrameCount()
                        + " | heldStableSeen="
                        + runResult
                        .getProgress()
                        .wasHeldStableObserved()
                        + " | lostSeen="
                        + runResult
                        .getProgress()
                        .wasLostStabilityObserved()
                        + " | furthest="
                        + runResult
                        .getProgress()
                        .getFurthestStage()
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
}
