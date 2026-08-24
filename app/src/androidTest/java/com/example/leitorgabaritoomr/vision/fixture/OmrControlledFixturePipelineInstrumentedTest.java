package com.example.leitorgabaritoomr.vision.fixture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.processing.DefaultMarkerFrameProcessorFactory;
import com.example.leitorgabaritoomr.vision.processing.MarkerFrameProcessor;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Teste ponta a ponta da fixture controlada.
 *
 * Executa a mesma composicao de MarkerFrameProcessor usada pela
 * MainActivity. Nenhuma medicao ou classificacao e reproduzida no
 * teste: as assercoes observam somente o resultado publico final do
 * pipeline real.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrControlledFixturePipelineInstrumentedTest {

    private static final String TAG =
            "OMR_PIPELINE_TEST";

    private static final String ASSET_PATH =
            "omr/gabarito_casos_controlados_v3.png";

    private static final int EXPECTED_WIDTH = 1303;
    private static final int EXPECTED_HEIGHT = 602;

    /*
     * O consenso atual precisa de poucos frames. O limite maior
     * evita um laco sem fim e deixa margem para as camadas de
     * estabilidade sem tornar o teste lento quando houver falha.
     */
    private static final int MAX_FRAME_COUNT = 30;

    @BeforeClass
    public static void initializeOpenCv() {
        assertTrue(
                "OpenCV nao foi inicializado no ambiente de teste.",
                OpenCVLoader.initDebug()
        );
    }

    @Test
    public void controlledFixtureCrossesTheRealPipeline()
            throws IOException {

        Context testContext =
                InstrumentationRegistry
                        .getInstrumentation()
                        .getContext();

        Bitmap decodedBitmap =
                loadBitmap(testContext);

        assertNotNull(
                "O PNG de regressao nao foi decodificado.",
                decodedBitmap
        );

        Bitmap rgbaBitmap =
                decodedBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                );

        assertNotNull(
                "Nao foi possivel obter Bitmap ARGB_8888.",
                rgbaBitmap
        );

        Mat sourceRgba = new Mat();
        Mat sourceGray = new Mat();

        VisionDebugController debugController =
                new VisionDebugController();

        MarkerFrameProcessor processor = null;

        int processedFrameCount = 0;

        try {
            assertEquals(
                    EXPECTED_WIDTH,
                    rgbaBitmap.getWidth()
            );

            assertEquals(
                    EXPECTED_HEIGHT,
                    rgbaBitmap.getHeight()
            );

            Utils.bitmapToMat(
                    rgbaBitmap,
                    sourceRgba
            );

            Imgproc.cvtColor(
                    sourceRgba,
                    sourceGray,
                    Imgproc.COLOR_RGBA2GRAY
            );

            debugController
                    .setAutoFreezeOnStableEnabled(false);

            selectFinalStage(debugController);

            processor =
                    DefaultMarkerFrameProcessorFactory.create(
                            MarkerDetectorMode.SOLID_SQUARE,
                            debugController
                    );

            SheetInterpretationResult finalResult = null;

            for (int frameIndex = 0;
                 frameIndex < MAX_FRAME_COUNT;
                 frameIndex++) {

                /*
                 * O processador pode desenhar sobre o frame RGBA.
                 * Cada iteracao recebe clones da fixture original,
                 * exatamente como receberia um novo frame da camera.
                 */
                Mat rgbaFrame = sourceRgba.clone();
                Mat grayFrame = sourceGray.clone();

                try {
                    processor.process(
                            grayFrame,
                            rgbaFrame
                    );
                } finally {
                    grayFrame.release();
                    rgbaFrame.release();
                }

                processedFrameCount++;

                finalResult =
                        processor
                                .getLastSheetInterpretationResult();

                if (finalResult != null
                        && finalResult.isComplete()) {
                    break;
                }
            }

            assertNotNull(
                    incompletePipelineMessage(
                            processedFrameCount,
                            null
                    ),
                    finalResult
            );

            assertTrue(
                    incompletePipelineMessage(
                            processedFrameCount,
                            finalResult
                    ),
                    finalResult.isComplete()
            );

            assertFinalTotals(finalResult);
            assertControlledCases(finalResult);

            Log.i(
                    TAG,
                    String.format(
                            Locale.US,
                            "PIPELINE_OK | frames=%d | questions=%d"
                                    + " | single=%d | blank=%d"
                                    + " | multiple=%d | ambiguous=%d"
                                    + " | notReady=%d | review=%d",
                            processedFrameCount,
                            finalResult.getQuestionCount(),
                            finalResult.getSingleMarkCount(),
                            finalResult.getBlankCount(),
                            finalResult.getMultipleMarkCount(),
                            finalResult.getAmbiguousCount(),
                            finalResult.getNotReadyCount(),
                            finalResult.getReviewRequiredCount()
                    )
            );
        } finally {
            if (processor != null) {
                processor.resetStability();
            }

            debugController.release();

            sourceGray.release();
            sourceRgba.release();

            if (rgbaBitmap != decodedBitmap) {
                rgbaBitmap.recycle();
            }

            decodedBitmap.recycle();
        }
    }

    private Bitmap loadBitmap(
            Context testContext
    ) throws IOException {

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        try (InputStream inputStream =
                     testContext
                             .getAssets()
                             .open(ASSET_PATH)) {

            return BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    options
            );
        }
    }

    private void selectFinalStage(
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

        assertEquals(
                "Nao foi possivel selecionar a etapa final.",
                VisionStage.FINAL_INTERPRETATION,
                debugController.getSelectedStage()
        );
    }

    private void assertFinalTotals(
            SheetInterpretationResult result
    ) {
        assertEquals(52, result.getQuestionCount());
        assertEquals(47, result.getSingleMarkCount());
        assertEquals(3, result.getBlankCount());
        assertEquals(1, result.getMultipleMarkCount());
        assertEquals(1, result.getAmbiguousCount());
        assertEquals(0, result.getNotReadyCount());
        assertEquals(2, result.getReviewRequiredCount());
        assertTrue(result.requiresReview());
    }

    private void assertControlledCases(
            SheetInterpretationResult result
    ) {
        assertState(
                result,
                "block-01-row-01",
                QuestionMarkState.BLANK
        );

        assertRelevantOptions(
                result,
                "block-02-row-02",
                QuestionMarkState.MULTIPLE_MARKS,
                "B",
                "D"
        );

        assertState(
                result,
                "block-03-row-02",
                QuestionMarkState.BLANK
        );

        assertState(
                result,
                "block-03-row-03",
                QuestionMarkState.BLANK
        );

        assertSingle(
                result,
                "block-03-row-04",
                "A"
        );

        assertRelevantOptions(
                result,
                "block-04-row-02",
                QuestionMarkState.AMBIGUOUS,
                "C"
        );

        assertSingle(
                result,
                "block-04-row-03",
                "B"
        );
    }

    private void assertState(
            SheetInterpretationResult result,
            String questionId,
            QuestionMarkState expectedState
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        result,
                        questionId
                );

        assertEquals(
                questionId,
                expectedState,
                interpretation.getState()
        );
    }

    private void assertSingle(
            SheetInterpretationResult result,
            String questionId,
            String expectedOptionLabel
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        result,
                        questionId
                );

        assertEquals(
                questionId,
                QuestionMarkState.SINGLE_MARK,
                interpretation.getState()
        );

        assertNotNull(
                questionId + " ficou sem alternativa selecionada.",
                interpretation.getSelectedOption()
        );

        assertEquals(
                questionId,
                expectedOptionLabel,
                interpretation
                        .getSelectedOption()
                        .getLabel()
        );
    }

    private void assertRelevantOptions(
            SheetInterpretationResult result,
            String questionId,
            QuestionMarkState expectedState,
            String... expectedLabels
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        result,
                        questionId
                );

        assertEquals(
                questionId,
                expectedState,
                interpretation.getState()
        );

        List<String> actualLabels =
                new ArrayList<>();

        for (OmrOptionDefinition option
                : interpretation.getRelevantOptions()) {

            actualLabels.add(option.getLabel());
        }

        List<String> expectedLabelList =
                new ArrayList<>();

        for (String expectedLabel : expectedLabels) {
            expectedLabelList.add(expectedLabel);
        }

        assertEquals(
                questionId,
                expectedLabelList,
                actualLabels
        );
    }

    private QuestionInterpretation requireQuestion(
            SheetInterpretationResult result,
            String questionId
    ) {
        QuestionInterpretation interpretation =
                result.findByQuestionId(questionId);

        assertNotNull(
                "Questao ausente no resultado: "
                        + questionId,
                interpretation
        );

        return interpretation;
    }

    private String incompletePipelineMessage(
            int processedFrameCount,
            SheetInterpretationResult result
    ) {
        return "O pipeline nao concluiu a fixture em "
                + processedFrameCount
                + " frames. Resultado final="
                + (result == null ? "null" : result);
    }
}
