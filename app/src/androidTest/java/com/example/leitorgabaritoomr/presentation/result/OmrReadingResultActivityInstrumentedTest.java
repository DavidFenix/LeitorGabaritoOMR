package com.example.leitorgabaritoomr.presentation.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Protege o contrato Android da tela final da leitura.
 *
 * O teste usa um resultado de domínio controlado e não executa
 * câmera, OpenCV ou o pipeline OMR.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrReadingResultActivityInstrumentedTest {

    @Test
    public void controlledResultRendersSummaryAndAllQuestions() {
        OmrReadingResult readingResult =
                createControlledReadingResult();

        Intent intent = createActivityIntent(
                readingResult
        );

        try (ActivityScenario<OmrReadingResultActivity>
                     scenario = ActivityScenario.launch(intent)) {

            scenario.onActivity(
                    activity -> {
                        assertText(
                                activity,
                                R.id.textOmrResultOverall,
                                activity.getString(
                                        R.string
                                        .omr_result_overall_completed_with_review
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultCountSingle,
                                activity.getString(
                                        R.string
                                        .omr_result_count_single_format,
                                        47
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultCountBlank,
                                activity.getString(
                                        R.string
                                        .omr_result_count_blank_format,
                                        3
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultCountReview,
                                activity.getString(
                                        R.string
                                        .omr_result_count_review_format,
                                        2
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultCountMultiple,
                                activity.getString(
                                        R.string
                                        .omr_result_count_multiple_format,
                                        1
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultCountAmbiguous,
                                activity.getString(
                                        R.string
                                        .omr_result_count_ambiguous_format,
                                        1
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultCountNotReady,
                                activity.getString(
                                        R.string
                                        .omr_result_count_not_ready_format,
                                        0
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrResultReview,
                                activity
                                .getResources()
                                .getQuantityString(
                                        R.plurals
                                        .omr_result_review_message,
                                        2,
                                        2
                                )
                        );

                        LinearLayout questionContainer =
                                activity.findViewById(
                                        R.id
                                        .containerOmrResultQuestions
                                );

                        assertNotNull(questionContainer);
                        assertEquals(
                                52,
                                questionContainer.getChildCount()
                        );

                        assertQuestionItem(
                                questionContainer.getChildAt(0),
                                "Questão 1",
                                "Resposta: A",
                                "Confiança: 95%"
                        );

                        assertQuestionItem(
                                questionContainer.getChildAt(47),
                                "Questão 48",
                                "Em branco",
                                "Confiança: 90%"
                        );

                        assertQuestionItem(
                                questionContainer.getChildAt(50),
                                "Questão 51",
                                "Múltiplas marcações: B, D",
                                "Confiança: 74%"
                        );

                        assertQuestionItem(
                                questionContainer.getChildAt(51),
                                "Questão 52",
                                "Marcação ambígua: C",
                                "Confiança: 61%"
                        );
                    }
            );
        }
    }

    @Test
    public void readAgainReturnsItsSpecificResultAndReading() {
        OmrReadingResult expected =
                createMinimalReadingResult();

        try (ActivityScenario<OmrReadingResultActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent(expected)
                     )) {

            scenario.onActivity(
                    activity -> activity
                            .findViewById(
                                    R.id.buttonOmrResultReadAgain
                            )
                            .performClick()
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    OmrReadingResultActivity.RESULT_READ_AGAIN,
                    result.getResultCode()
            );

            assertEquals(
                    expected,
                    extractReturnedReadingResult(
                            result.getResultData()
                    )
            );
        }
    }

    @Test
    public void finishReturnsOkAndReading() {
        OmrReadingResult expected =
                createMinimalReadingResult();

        try (ActivityScenario<OmrReadingResultActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent(expected)
                     )) {

            scenario.onActivity(
                    activity -> activity
                            .findViewById(
                                    R.id.buttonOmrResultFinish
                            )
                            .performClick()
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_OK,
                    result.getResultCode()
            );

            assertEquals(
                    expected,
                    extractReturnedReadingResult(
                            result.getResultData()
                    )
            );
        }
    }

    @Test
    public void missingReadingFinishesSafelyAsCanceled() {
        Context context =
                ApplicationProvider
                        .getApplicationContext();

        Intent intent = new Intent(
                context,
                OmrReadingResultActivity.class
        );

        try (ActivityScenario<OmrReadingResultActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             intent
                     )) {

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );
        }
    }

    private static Intent createActivityIntent(
            OmrReadingResult readingResult
    ) {
        Context context =
                ApplicationProvider
                        .getApplicationContext();

        return OmrReadingResultActivity.createIntent(
                context,
                readingResult
        );
    }

    private static OmrReadingResult
    createControlledReadingResult() {
        List<OmrQuestionResult> questions =
                new ArrayList<>(52);

        String[] labels = {
                "A", "B", "C", "D"
        };

        for (int position = 1;
             position <= 47;
             position++) {

            String label =
                    labels[(position - 1)
                            % labels.length];

            questions.add(
                    new OmrQuestionResult(
                            position,
                            questionId(position),
                            OmrQuestionResult.Status
                                    .SINGLE_MARK,
                            Collections.singletonList(
                                    option(position, label)
                            ),
                            0.95
                    )
            );
        }

        for (int position = 48;
             position <= 50;
             position++) {

            questions.add(
                    new OmrQuestionResult(
                            position,
                            questionId(position),
                            OmrQuestionResult.Status.BLANK,
                            Collections.emptyList(),
                            0.90
                    )
            );
        }

        questions.add(
                new OmrQuestionResult(
                        51,
                        questionId(51),
                        OmrQuestionResult.Status
                                .MULTIPLE_MARKS,
                        Arrays.asList(
                                option(51, "B"),
                                option(51, "D")
                        ),
                        0.74
                )
        );

        questions.add(
                new OmrQuestionResult(
                        52,
                        questionId(52),
                        OmrQuestionResult.Status.AMBIGUOUS,
                        Collections.singletonList(
                                option(52, "C")
                        ),
                        0.61
                )
        );

        return new OmrReadingResult(
                "reading-ui-controlled",
                1_787_693_400_000L,
                "avalie-ce-development",
                1,
                "Gabarito Avalie CE",
                questions
        );
    }

    /**
     * Os testes de contrato dos botões não precisam inflar 52 linhas.
     * Uma questão é suficiente para validar código de retorno e
     * transporte do mesmo objeto de domínio, reduzindo a pressão de
     * memória do processo auxiliar do ActivityScenario.
     */
    private static OmrReadingResult
    createMinimalReadingResult() {
        OmrQuestionResult question =
                new OmrQuestionResult(
                        1,
                        questionId(1),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        Collections.singletonList(
                                option(1, "A")
                        ),
                        0.95
                );

        return new OmrReadingResult(
                "reading-ui-action",
                1_787_693_400_000L,
                "avalie-ce-development",
                1,
                "Gabarito Avalie CE",
                Collections.singletonList(question)
        );
    }

    private static String questionId(
            int position
    ) {
        return "question-" + position;
    }

    private static OmrQuestionResult.Option option(
            int position,
            String label
    ) {
        return new OmrQuestionResult.Option(
                questionId(position)
                        + "-option-"
                        + label,
                label
        );
    }

    private static void assertText(
            Activity activity,
            int viewId,
            String expectedText
    ) {
        TextView textView =
                activity.findViewById(viewId);

        assertNotNull(textView);
        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }

    private static void assertQuestionItem(
            View itemView,
            String expectedPosition,
            String expectedStatus,
            String expectedConfidence
    ) {
        assertNotNull(itemView);

        TextView positionTextView =
                itemView.findViewById(
                        R.id.textOmrQuestionPosition
                );

        TextView statusTextView =
                itemView.findViewById(
                        R.id.textOmrQuestionStatus
                );

        TextView confidenceTextView =
                itemView.findViewById(
                        R.id.textOmrQuestionConfidence
                );

        assertNotNull(positionTextView);
        assertNotNull(statusTextView);
        assertNotNull(confidenceTextView);

        assertEquals(
                expectedPosition,
                positionTextView.getText().toString()
        );

        assertEquals(
                expectedStatus,
                statusTextView.getText().toString()
        );

        assertEquals(
                expectedConfidence,
                confidenceTextView.getText().toString()
        );
    }

    @SuppressWarnings("deprecation")
    private static OmrReadingResult
    extractReturnedReadingResult(
            Intent resultIntent
    ) {
        assertNotNull(resultIntent);

        Serializable value =
                resultIntent.getSerializableExtra(
                        OmrReadingResultActivity
                                .EXTRA_READING_RESULT
                );

        assertTrue(value instanceof OmrReadingResult);

        return (OmrReadingResult) value;
    }
}
