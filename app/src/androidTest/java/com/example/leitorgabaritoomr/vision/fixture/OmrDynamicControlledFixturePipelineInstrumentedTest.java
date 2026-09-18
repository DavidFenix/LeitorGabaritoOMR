package com.example.leitorgabaritoomr.vision.fixture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.application.grading.OmrGradingService;
import com.example.leitorgabaritoomr.application.layout.OmrCaptureLayoutProvider;
import com.example.leitorgabaritoomr.application.reading.OmrReadingResultMapper;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

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
 * Regressao ponta a ponta dos cartoes controlados v1 e v2.
 *
 * As fixtures foram renderizadas a partir dos SVGs exportados pelo
 * aplicativo e possuem as respostas A, B, C, D, A, B e C. Cada teste usa o
 * layout resolvido do proprio gabarito, atravessa o pipeline OpenCV real,
 * mapeia a leitura e executa o mesmo servico de correcao usado pela tela de
 * captura.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrDynamicControlledFixturePipelineInstrumentedTest {

    private static final String V1_ASSET_PATH =
            "omr/cartao_resposta_dinamico_007_controlado_v1.png";

    private static final String V2_ASSET_PATH =
            "omr/cartao_resposta_padronizado_007_controlado_v2.png";

    private static final int V1_EXPECTED_WIDTH = 1265;
    private static final int V1_EXPECTED_HEIGHT = 565;

    private static final int V2_EXPECTED_WIDTH = 1277;
    private static final int V2_EXPECTED_HEIGHT = 649;

    private static final int QUESTION_COUNT = 7;

    private static final String[] EXPECTED_ANSWERS = {
            "A",
            "B",
            "C",
            "D",
            "A",
            "B",
            "C"
    };

    @BeforeClass
    public static void initializeOpenCv() {
        assertTrue(
                "OpenCV nao foi inicializado no ambiente de teste.",
                OpenCVLoader.initDebug()
        );
    }

    @Test
    public void controlledDynamicFixtureLoadsWithExpectedDimensions()
            throws IOException {

        assertFixtureDimensions(
                V1_ASSET_PATH,
                V1_EXPECTED_WIDTH,
                V1_EXPECTED_HEIGHT
        );
    }

    @Test
    public void controlledStandardV2FixtureLoadsWithExpectedDimensions()
            throws IOException {

        assertFixtureDimensions(
                V2_ASSET_PATH,
                V2_EXPECTED_WIDTH,
                V2_EXPECTED_HEIGHT
        );
    }

    @Test
    public void controlledDynamicFixtureCrossesPipelineAndGradesOneHundredPercent()
            throws IOException {

        assertFixtureCrossesPipelineAndGradesOneHundredPercent(
                V1_ASSET_PATH,
                OmrSheetTemplateCatalog
                        .compactFourOptions(QUESTION_COUNT),
                "dynamic-q007-controlled-key",
                "Gabarito controlado v1 de 7 questoes",
                "dynamic-q007-controlled-reading"
        );
    }

    @Test
    public void controlledStandardV2FixtureCrossesPipelineAndGradesOneHundredPercent()
            throws IOException {

        assertFixtureCrossesPipelineAndGradesOneHundredPercent(
                V2_ASSET_PATH,
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(QUESTION_COUNT),
                "standard-v2-q007-controlled-key",
                "Gabarito controlado v2 de 7 questoes",
                "standard-v2-q007-controlled-reading"
        );
    }

    private void assertFixtureDimensions(
            String assetPath,
            int expectedWidth,
            int expectedHeight
    ) throws IOException {

        Bitmap bitmap = loadBitmap(assetPath);

        try {
            assertNotNull(
                    "O PNG controlado nao foi decodificado: "
                            + assetPath,
                    bitmap
            );

            assertEquals(expectedWidth, bitmap.getWidth());
            assertEquals(expectedHeight, bitmap.getHeight());

        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private void
    assertFixtureCrossesPipelineAndGradesOneHundredPercent(
            String assetPath,
            OmrSheetTemplateSpec spec,
            String answerKeyId,
            String answerKeyName,
            String readingId
    ) throws IOException {

        Bitmap decodedBitmap = loadBitmap(assetPath);

        assertNotNull(
                "O PNG controlado nao foi decodificado: "
                        + assetPath,
                decodedBitmap
        );

        Bitmap rgbaBitmap = decodedBitmap.copy(
                Bitmap.Config.ARGB_8888,
                false
        );

        assertNotNull(
                "Nao foi possivel obter Bitmap ARGB_8888.",
                rgbaBitmap
        );

        Mat sourceRgba = new Mat();

        try {
            Utils.bitmapToMat(rgbaBitmap, sourceRgba);

            OmrAnswerKeyDefinition answerKey =
                    createAnswerKey(
                            spec,
                            answerKeyId,
                            answerKeyName
                    );

            OmrLayoutDefinition layout =
                    new OmrCaptureLayoutProvider()
                            .resolve(answerKey);

            assertEquals(
                    answerKey.getLayoutId(),
                    layout.getId()
            );

            assertEquals(
                    answerKey.getLayoutVersion(),
                    layout.getVersion()
            );

            assertEquals(
                    QUESTION_COUNT,
                    layout.getQuestionCount()
            );

            OmrFixturePipelineRunner.Result runResult =
                    OmrFixturePipelineRunner.run(
                            sourceRgba,
                            layout
                    );

            assertNotNull(
                    "A execucao da fixture nao foi criada.",
                    runResult
            );

            SheetInterpretationResult interpretation =
                    runResult.getInterpretationResult();

            String diagnostic = describe(runResult);

            assertNotNull(
                    "Interpretacao ausente | " + diagnostic,
                    interpretation
            );

            assertTrue(
                    "Interpretacao incompleta | " + diagnostic,
                    interpretation.isComplete()
            );

            assertEquals(
                    "Quantidade de questoes | " + diagnostic,
                    QUESTION_COUNT,
                    interpretation.getQuestionCount()
            );

            assertEquals(
                    "Marcacoes unicas | " + diagnostic,
                    QUESTION_COUNT,
                    interpretation.getSingleMarkCount()
            );

            assertEquals(
                    "Questoes em branco | " + diagnostic,
                    0,
                    interpretation.getBlankCount()
            );

            assertEquals(
                    "Marcacoes multiplas | " + diagnostic,
                    0,
                    interpretation.getMultipleMarkCount()
            );

            assertEquals(
                    "Questoes ambiguas | " + diagnostic,
                    0,
                    interpretation.getAmbiguousCount()
            );

            assertFalse(
                    "A fixture nao deveria exigir revisao | "
                            + diagnostic,
                    interpretation.requiresReview()
            );

            assertExpectedAnswers(
                    interpretation,
                    diagnostic
            );

            OmrReadingResult readingResult =
                    new OmrReadingResultMapper().map(
                            interpretation,
                            readingId,
                            1_800_000_000_000L
                    );

            OmrGradingResult gradingResult =
                    new OmrGradingService().grade(
                            layout,
                            answerKey,
                            readingResult
                    );

            assertEquals(QUESTION_COUNT, gradingResult.getCorrectCount());
            assertEquals(0, gradingResult.getIncorrectCount());
            assertEquals(0, gradingResult.getBlankCount());
            assertEquals(0, gradingResult.getReviewRequiredCount());
            assertEquals(
                    100.0,
                    gradingResult.getAwardedPercentage(),
                    0.000001
            );

        } finally {
            sourceRgba.release();

            if (rgbaBitmap != decodedBitmap) {
                rgbaBitmap.recycle();
            }

            decodedBitmap.recycle();
        }
    }

    private static Bitmap loadBitmap(
            String assetPath
    )
            throws IOException {

        Context testContext =
                InstrumentationRegistry
                        .getInstrumentation()
                        .getContext();

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        try (InputStream inputStream =
                     testContext.getAssets().open(assetPath)) {

            return BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    options
            );
        }
    }

    private static OmrAnswerKeyDefinition
    createAnswerKey(
            OmrSheetTemplateSpec spec,
            String answerKeyId,
            String answerKeyName
    ) {
        List<OmrAnswerKeyEntry> entries =
                new ArrayList<>(QUESTION_COUNT);

        for (int index = 0;
             index < QUESTION_COUNT;
             index++) {

            int questionNumber = index + 1;
            int optionNumber = optionNumber(
                    EXPECTED_ANSWERS[index]
            );

            String questionId = String.format(
                    Locale.US,
                    "question-%03d",
                    questionNumber
            );

            String optionId = String.format(
                    Locale.US,
                    "%s-option-%02d",
                    questionId,
                    optionNumber
            );

            entries.add(
                    OmrAnswerKeyEntry.singleAnswer(
                            questionId,
                            optionId,
                            1.0
                    )
            );
        }

        return new OmrAnswerKeyDefinition(
                answerKeyId,
                1,
                answerKeyName,
                spec.getTemplateId(),
                spec.getTemplateVersion(),
                entries
        );
    }

    private static int optionNumber(
            String optionLabel
    ) {
        switch (optionLabel) {
            case "A":
                return 1;

            case "B":
                return 2;

            case "C":
                return 3;

            case "D":
                return 4;

            default:
                throw new IllegalArgumentException(
                        "Alternativa inesperada: "
                                + optionLabel
                );
        }
    }

    private static void assertExpectedAnswers(
            SheetInterpretationResult interpretation,
            String diagnostic
    ) {
        for (int index = 0;
             index < EXPECTED_ANSWERS.length;
             index++) {

            String questionId = String.format(
                    Locale.US,
                    "question-%03d",
                    index + 1
            );

            QuestionInterpretation question =
                    interpretation.findByQuestionId(
                            questionId
                    );

            assertNotNull(
                    "Questao ausente: "
                            + questionId
                            + " | "
                            + diagnostic,
                    question
            );

            assertEquals(
                    questionId + " | " + diagnostic,
                    QuestionMarkState.SINGLE_MARK,
                    question.getState()
            );

            assertNotNull(
                    questionId
                            + " sem resposta selecionada | "
                            + diagnostic,
                    question.getSelectedOption()
            );

            assertEquals(
                    questionId + " | " + diagnostic,
                    EXPECTED_ANSWERS[index],
                    question.getSelectedOption().getLabel()
            );
        }
    }

    private static String describe(
            OmrFixturePipelineRunner.Result runResult
    ) {
        StringBuilder description =
                new StringBuilder(runResult.toString());

        SheetInterpretationResult interpretation =
                runResult.getInterpretationResult();

        if (interpretation == null) {
            return description.toString();
        }

        description.append(" | answers=[");

        List<QuestionInterpretation> questions =
                interpretation.getQuestionInterpretations();

        for (int index = 0;
             index < questions.size();
             index++) {

            if (index > 0) {
                description.append(", ");
            }

            QuestionInterpretation question =
                    questions.get(index);

            description.append(index + 1)
                    .append(':')
                    .append(question.getState())
                    .append('/');

            if (question.getSelectedOption() == null) {
                description.append('-');
            } else {
                description.append(
                        question
                                .getSelectedOption()
                                .getLabel()
                );
            }
        }

        description.append(']');
        return description.toString();
    }
}
