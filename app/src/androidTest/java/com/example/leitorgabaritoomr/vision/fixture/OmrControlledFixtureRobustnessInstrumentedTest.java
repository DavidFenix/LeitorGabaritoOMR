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

import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Matriz inicial de robustez da fixture controlada v3.
 *
 * Cada metodo e um teste independente. Assim o relatorio continua
 * executando as demais condicoes mesmo quando uma variante falha.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrControlledFixtureRobustnessInstrumentedTest {

    private static final String TAG =
            "OMR_ROBUSTNESS_TEST";

    private static final String ASSET_PATH =
            "omr/gabarito_casos_controlados_v3.png";

    private static final int EXPECTED_WIDTH = 1303;
    private static final int EXPECTED_HEIGHT = 602;
    private static final int EXPECTED_VARIANT_COUNT = 8;

    private static List<OmrFixtureVariant> variants;

    @BeforeClass
    public static void initializeSuite()
            throws IOException {

        assertTrue(
                "OpenCV nao foi inicializado no ambiente de teste.",
                OpenCVLoader.initDebug()
        );

        Context testContext =
                InstrumentationRegistry
                        .getInstrumentation()
                        .getContext();

        Bitmap decodedBitmap = null;
        Bitmap rgbaBitmap = null;
        Mat originalRgba = new Mat();

        try {
            decodedBitmap =
                    loadBitmap(testContext);

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
                    originalRgba
            );

            variants =
                    OmrFixtureVariantFactory
                            .createStandardSuite(
                                    originalRgba
                            );

            assertEquals(
                    EXPECTED_VARIANT_COUNT,
                    variants.size()
            );
        } finally {
            originalRgba.release();

            if (rgbaBitmap != null
                    && rgbaBitmap != decodedBitmap) {

                rgbaBitmap.recycle();
            }

            if (decodedBitmap != null) {
                decodedBitmap.recycle();
            }
        }
    }

    @AfterClass
    public static void releaseSuite() {
        OmrFixtureVariantFactory.releaseAll(
                variants
        );

        variants = null;
    }

    @Test
    public void originalRemainsCorrect() {
        verifyVariant("original");
    }

    @Test
    public void darkerImageRemainsCorrect() {
        verifyVariant("brightness-darker");
    }

    @Test
    public void brighterImageRemainsCorrect() {
        verifyVariant("brightness-brighter");
    }

    @Test
    public void lightBlurRemainsCorrect() {
        verifyVariant("blur-light");
    }

    @Test
    public void reducedResolutionRemainsCorrect() {
        verifyVariant("resolution-75-percent");
    }

    @Test
    public void centeredScaleRemainsCorrect() {
        verifyVariant("scale-90-percent");
    }

    @Test
    public void leftPerspectiveRemainsCorrect() {
        verifyVariant("perspective-left");
    }

    @Test
    public void rightPerspectiveRemainsCorrect() {
        verifyVariant("perspective-right");
    }

    private void verifyVariant(String variantId) {
        OmrFixtureVariant variant =
                requireVariant(variantId);

        Log.i(
                TAG,
                "VARIANT_BEGIN | id="
                        + variant.getId()
                        + " | description="
                        + variant.getDescription()
        );

        Mat rgbaFrame =
                variant.createRgbaFrame();

        OmrFixturePipelineRunner.Result runResult;

        try {
            runResult =
                    OmrFixturePipelineRunner.run(
                            rgbaFrame
                    );
        } finally {
            rgbaFrame.release();
        }

        String prefix =
                failurePrefix(
                        variant,
                        runResult
                );

        assertNotNull(
                prefix + " | interpretacao ausente",
                runResult.getInterpretationResult()
        );

        assertTrue(
                prefix + " | consenso incompleto",
                runResult.isComplete()
        );

        SheetInterpretationResult result =
                runResult.getInterpretationResult();

        assertFinalTotals(
                prefix,
                result
        );

        assertControlledCases(
                prefix,
                result
        );

        Log.i(
                TAG,
                String.format(
                        Locale.US,
                        "VARIANT_OK | id=%s | frames=%d"
                                + " | questions=%d | single=%d"
                                + " | blank=%d | multiple=%d"
                                + " | ambiguous=%d | notReady=%d"
                                + " | review=%d",
                        variant.getId(),
                        runResult.getProcessedFrameCount(),
                        result.getQuestionCount(),
                        result.getSingleMarkCount(),
                        result.getBlankCount(),
                        result.getMultipleMarkCount(),
                        result.getAmbiguousCount(),
                        result.getNotReadyCount(),
                        result.getReviewRequiredCount()
                )
        );
    }

    private void assertFinalTotals(
            String prefix,
            SheetInterpretationResult result
    ) {
        assertEquals(
                prefix + " | questions",
                52,
                result.getQuestionCount()
        );

        assertEquals(
                prefix + " | single",
                47,
                result.getSingleMarkCount()
        );

        assertEquals(
                prefix + " | blank",
                3,
                result.getBlankCount()
        );

        assertEquals(
                prefix + " | multiple",
                1,
                result.getMultipleMarkCount()
        );

        assertEquals(
                prefix + " | ambiguous",
                1,
                result.getAmbiguousCount()
        );

        assertEquals(
                prefix + " | notReady",
                0,
                result.getNotReadyCount()
        );

        assertEquals(
                prefix + " | review",
                2,
                result.getReviewRequiredCount()
        );

        assertTrue(
                prefix + " | revisao esperada",
                result.requiresReview()
        );
    }

    private void assertControlledCases(
            String prefix,
            SheetInterpretationResult result
    ) {
        assertState(
                prefix,
                result,
                "block-01-row-01",
                QuestionMarkState.BLANK
        );

        assertRelevantOptions(
                prefix,
                result,
                "block-02-row-02",
                QuestionMarkState.MULTIPLE_MARKS,
                "B",
                "D"
        );

        assertState(
                prefix,
                result,
                "block-03-row-02",
                QuestionMarkState.BLANK
        );

        assertState(
                prefix,
                result,
                "block-03-row-03",
                QuestionMarkState.BLANK
        );

        assertSingle(
                prefix,
                result,
                "block-03-row-04",
                "A"
        );

        assertRelevantOptions(
                prefix,
                result,
                "block-04-row-02",
                QuestionMarkState.AMBIGUOUS,
                "C"
        );

        assertSingle(
                prefix,
                result,
                "block-04-row-03",
                "B"
        );
    }

    private void assertState(
            String prefix,
            SheetInterpretationResult result,
            String questionId,
            QuestionMarkState expectedState
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        prefix,
                        result,
                        questionId
                );

        assertEquals(
                prefix + " | " + questionId,
                expectedState,
                interpretation.getState()
        );
    }

    private void assertSingle(
            String prefix,
            SheetInterpretationResult result,
            String questionId,
            String expectedOptionLabel
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        prefix,
                        result,
                        questionId
                );

        assertEquals(
                prefix + " | " + questionId,
                QuestionMarkState.SINGLE_MARK,
                interpretation.getState()
        );

        assertNotNull(
                prefix
                        + " | "
                        + questionId
                        + " sem alternativa selecionada",
                interpretation.getSelectedOption()
        );

        assertEquals(
                prefix + " | " + questionId,
                expectedOptionLabel,
                interpretation
                        .getSelectedOption()
                        .getLabel()
        );
    }

    private void assertRelevantOptions(
            String prefix,
            SheetInterpretationResult result,
            String questionId,
            QuestionMarkState expectedState,
            String... expectedLabels
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        prefix,
                        result,
                        questionId
                );

        assertEquals(
                prefix + " | " + questionId,
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
                prefix + " | " + questionId,
                expectedLabelList,
                actualLabels
        );
    }

    private QuestionInterpretation requireQuestion(
            String prefix,
            SheetInterpretationResult result,
            String questionId
    ) {
        QuestionInterpretation interpretation =
                result.findByQuestionId(questionId);

        assertNotNull(
                prefix
                        + " | questao ausente: "
                        + questionId,
                interpretation
        );

        return interpretation;
    }

    private OmrFixtureVariant requireVariant(
            String variantId
    ) {
        assertNotNull(
                "A suite de variantes nao foi inicializada.",
                variants
        );

        for (OmrFixtureVariant variant : variants) {
            if (variant.getId().equals(variantId)) {
                return variant;
            }
        }

        throw new AssertionError(
                "Variante ausente na suite: "
                        + variantId
        );
    }

    private String failurePrefix(
            OmrFixtureVariant variant,
            OmrFixturePipelineRunner.Result runResult
    ) {
        return "variant="
                + variant.getId()
                + " | "
                + runResult;
    }

    private static Bitmap loadBitmap(
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
}
