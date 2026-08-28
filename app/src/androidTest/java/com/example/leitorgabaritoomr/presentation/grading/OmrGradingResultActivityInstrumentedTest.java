package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Protege o contrato Android da tela de resultado da correção.
 *
 * O teste não inicializa câmera, OpenCV nem o pipeline de leitura.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrGradingResultActivityInstrumentedTest {

    private static final int MIXED_QUESTION_COUNT = 8;

    @Test
    public void rendersSummaryStatesAndQuestionList() {
        try (ActivityScenario<OmrGradingResultActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent(
                                     createMixedGradingResult()
                             )
                     )) {

            scenario.onActivity(
                    activity -> {
                        assertText(
                                activity,
                                R.id.textOmrGradingOverall,
                                activity.getString(
                                        R.string
                                        .omr_grading_overall_incomplete
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrGradingAnswerKey,
                                activity.getString(
                                        R.string
                                        .omr_grading_answer_key_format,
                                        "Gabarito instrumentado",
                                        1
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrGradingPercentage,
                                activity.getString(
                                        R.string
                                        .omr_grading_percentage_format,
                                        38
                                )
                        );

                        assertText(
                                activity,
                                R.id.textOmrGradingPoints,
                                activity.getString(
                                        R.string
                                        .omr_grading_points_format,
                                        "3",
                                        "8"
                                )
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountCorrect,
                                R.string
                                .omr_grading_count_correct_format,
                                3
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountIncorrect,
                                R.string
                                .omr_grading_count_incorrect_format,
                                1
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountBlank,
                                R.string
                                .omr_grading_count_blank_format,
                                1
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountReview,
                                R.string
                                .omr_grading_count_review_format,
                                2
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountMultiple,
                                R.string
                                .omr_grading_count_multiple_format,
                                1
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountAmbiguous,
                                R.string
                                .omr_grading_count_ambiguous_format,
                                1
                        );

                        assertCount(
                                activity,
                                R.id.textOmrGradingCountNotReady,
                                R.string
                                .omr_grading_count_not_ready_format,
                                1
                        );

                        assertReviewMessage(activity, 2);
                        assertQuestionList(activity);

                        assertQuestionStatus(
                                activity,
                                0,
                                activity.getString(
                                        R.string
                                        .omr_grading_question_correct_format,
                                        "A"
                                )
                        );

                        assertQuestionStatus(
                                activity,
                                1,
                                activity.getString(
                                        R.string
                                        .omr_grading_question_incorrect_format,
                                        "B",
                                        "A"
                                )
                        );

                        assertQuestionStatus(
                                activity,
                                2,
                                activity.getString(
                                        R.string
                                        .omr_grading_question_blank_format,
                                        "A"
                                )
                        );

                        assertQuestionStatus(
                                activity,
                                3,
                                activity.getString(
                                        R.string
                                        .omr_grading_question_multiple_format,
                                        "B, D",
                                        "A"
                                )
                        );

                        assertQuestionStatus(
                                activity,
                                4,
                                activity.getString(
                                        R.string
                                        .omr_grading_question_ambiguous_format,
                                        "C",
                                        "A"
                                )
                        );

                        assertQuestionStatus(
                                activity,
                                5,
                                activity.getString(
                                        R.string
                                        .omr_grading_question_not_ready_format,
                                        "A"
                                )
                        );

                        View notReadyRow = getQuestionRow(
                                activity,
                                5
                        );

                        TextView confidenceTextView =
                                notReadyRow.findViewById(
                                        R.id
                                        .textOmrGradeQuestionConfidence
                                );

                        assertNotNull(confidenceTextView);
                        assertEquals(
                                View.GONE,
                                confidenceTextView.getVisibility()
                        );
                    }
            );
        }
    }

    @Test
    public void readAgainReturnsDedicatedResultAndGrading() {
        OmrGradingResult original =
                createMinimalGradingResult();

        try (ActivityScenario<OmrGradingResultActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent(original)
                     )) {

            scenario.onActivity(
                    activity -> clickButton(
                            activity,
                            R.id.buttonOmrGradingReadAgain
                    )
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    OmrGradingResultActivity.RESULT_READ_AGAIN,
                    result.getResultCode()
            );

            assertReturnedGrading(
                    result.getResultData()
            );
        }
    }

    @Test
    public void finishReturnsOkAndGrading() {
        OmrGradingResult original =
                createMinimalGradingResult();

        try (ActivityScenario<OmrGradingResultActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent(original)
                     )) {

            scenario.onActivity(
                    activity -> clickButton(
                            activity,
                            R.id.buttonOmrGradingFinish
                    )
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_OK,
                    result.getResultCode()
            );

            assertReturnedGrading(
                    result.getResultData()
            );
        }
    }

    @Test
    public void missingResultFinishesCanceled() {
        Context context =
                ApplicationProvider
                        .getApplicationContext();

        Intent intent = new Intent(
                context,
                OmrGradingResultActivity.class
        );

        try (ActivityScenario<OmrGradingResultActivity>
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

            assertEquals(
                    null,
                    OmrGradingResultActivity
                            .extractGradingResult(
                                    result.getResultData()
                            )
            );
        }
    }

    private static Intent createActivityIntent(
            OmrGradingResult gradingResult
    ) {
        Context context =
                ApplicationProvider
                        .getApplicationContext();

        return OmrGradingResultActivity.createIntent(
                context,
                gradingResult
        );
    }

    private static OmrGradingResult
    createMixedGradingResult() {
        OmrLayoutDefinition layout =
                AvalieCeDevelopmentLayoutFactory.create();

        OmrAnswerKeyDefinition answerKey =
                createMixedAnswerKey(layout);

        List<OmrQuestionDefinition> layoutQuestions =
                layout.getAllQuestions();

        List<OmrQuestionResult> questions =
                new ArrayList<>(MIXED_QUESTION_COUNT);

        for (int index = 0;
             index < MIXED_QUESTION_COUNT;
             index++) {

            questions.add(
                    question(
                            index + 1,
                            layoutQuestions.get(index),
                            OmrQuestionResult.Status.SINGLE_MARK,
                            0.95,
                            0
                    )
            );
        }

        questions.set(
                1,
                question(
                        2,
                        layoutQuestions.get(1),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        0.92,
                        1
                )
        );

        questions.set(
                2,
                question(
                        3,
                        layoutQuestions.get(2),
                        OmrQuestionResult.Status.BLANK,
                        0.98
                )
        );

        questions.set(
                3,
                question(
                        4,
                        layoutQuestions.get(3),
                        OmrQuestionResult.Status.MULTIPLE_MARKS,
                        0.71,
                        1,
                        3
                )
        );

        questions.set(
                4,
                question(
                        5,
                        layoutQuestions.get(4),
                        OmrQuestionResult.Status.AMBIGUOUS,
                        0.524,
                        2
                )
        );

        questions.set(
                5,
                question(
                        6,
                        layoutQuestions.get(5),
                        OmrQuestionResult.Status.NOT_READY,
                        0.0
                )
        );

        OmrReadingResult readingResult =
                new OmrReadingResult(
                        "instrumented-mixed-reading",
                        1_800_000_000_000L,
                        layout.getId(),
                        layout.getVersion(),
                        layout.getName(),
                        questions
                );

        return new OmrReadingGrader().grade(
                readingResult,
                answerKey
        );
    }

    private static OmrAnswerKeyDefinition createMixedAnswerKey(
            OmrLayoutDefinition layout
    ) {
        List<OmrAnswerKeyEntry> entries =
                new ArrayList<>(MIXED_QUESTION_COUNT);

        List<OmrQuestionDefinition> questions =
                layout.getAllQuestions();

        for (int index = 0;
             index < MIXED_QUESTION_COUNT;
             index++) {

            OmrQuestionDefinition question =
                    questions.get(index);

            OmrOptionDefinition option =
                    question.getOptions().get(0);

            entries.add(
                    OmrAnswerKeyEntry.singleAnswer(
                            question.getId(),
                            option.getId(),
                            1.0
                    )
            );
        }

        return new OmrAnswerKeyDefinition(
                "instrumented-mixed-answer-key",
                1,
                "Gabarito instrumentado",
                layout.getId(),
                layout.getVersion(),
                entries
        );
    }

    private static OmrGradingResult
    createMinimalGradingResult() {
        OmrLayoutDefinition layout =
                AvalieCeDevelopmentLayoutFactory.create();

        OmrQuestionDefinition questionDefinition =
                layout.getAllQuestions().get(0);

        OmrOptionDefinition option =
                questionDefinition.getOptions().get(0);

        OmrAnswerKeyEntry entry =
                OmrAnswerKeyEntry.singleAnswer(
                        questionDefinition.getId(),
                        option.getId(),
                        1.0
                );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "instrumented-minimal-answer-key",
                        1,
                        "Gabarito mínimo",
                        layout.getId(),
                        layout.getVersion(),
                        Collections.singletonList(entry)
                );

        OmrQuestionResult questionResult =
                question(
                        1,
                        questionDefinition,
                        OmrQuestionResult.Status.SINGLE_MARK,
                        0.97,
                        0
                );

        OmrReadingResult readingResult =
                new OmrReadingResult(
                        "instrumented-minimal-reading",
                        1_800_000_000_000L,
                        layout.getId(),
                        layout.getVersion(),
                        layout.getName(),
                        Collections.singletonList(
                                questionResult
                        )
                );

        return new OmrReadingGrader().grade(
                readingResult,
                answerKey
        );
    }

    private static OmrQuestionResult question(
            int position,
            OmrQuestionDefinition questionDefinition,
            OmrQuestionResult.Status status,
            double confidence,
            int... optionIndexes
    ) {
        List<OmrQuestionResult.Option> options =
                new ArrayList<>(optionIndexes.length);

        for (int optionIndex : optionIndexes) {
            OmrOptionDefinition option =
                    questionDefinition
                            .getOptions()
                            .get(optionIndex);

            options.add(
                    new OmrQuestionResult.Option(
                            option.getId(),
                            option.getLabel()
                    )
            );
        }

        return new OmrQuestionResult(
                position,
                questionDefinition.getId(),
                status,
                options,
                confidence
        );
    }

    private static void assertQuestionList(
            Activity activity
    ) {
        ListView questionList = getQuestionList(activity);

        ListAdapter adapter = questionList.getAdapter();

        assertNotNull(adapter);
        assertEquals(MIXED_QUESTION_COUNT, adapter.getCount());
    }

    private static void assertQuestionStatus(
            Activity activity,
            int questionIndex,
            String expectedStatus
    ) {
        View row = getQuestionRow(
                activity,
                questionIndex
        );

        TextView statusTextView = row.findViewById(
                R.id.textOmrGradeQuestionStatus
        );

        assertNotNull(statusTextView);
        assertEquals(
                expectedStatus,
                statusTextView.getText().toString()
        );
    }

    private static View getQuestionRow(
            Activity activity,
            int questionIndex
    ) {
        ListView questionList = getQuestionList(activity);

        ListAdapter adapter = questionList.getAdapter();

        assertNotNull(adapter);
        assertTrue(questionIndex >= 0);
        assertTrue(questionIndex < adapter.getCount());

        return adapter.getView(
                questionIndex,
                null,
                questionList
        );
    }

    private static ListView getQuestionList(
            Activity activity
    ) {
        ListView questionList = activity.findViewById(
                R.id.listOmrGradingQuestions
        );

        assertNotNull(questionList);
        return questionList;
    }

    private static void assertCount(
            Activity activity,
            int textViewId,
            int stringResourceId,
            int count
    ) {
        assertText(
                activity,
                textViewId,
                activity.getString(
                        stringResourceId,
                        count
                )
        );
    }

    private static void assertReviewMessage(
            Activity activity,
            int reviewCount
    ) {
        TextView reviewTextView = activity.findViewById(
                R.id.textOmrGradingReview
        );

        assertNotNull(reviewTextView);
        assertEquals(View.VISIBLE, reviewTextView.getVisibility());

        assertEquals(
                activity
                        .getResources()
                        .getQuantityString(
                                R.plurals.omr_grading_review_message,
                                reviewCount,
                                reviewCount
                        ),
                reviewTextView.getText().toString()
        );
    }

    private static void assertReturnedGrading(
            Intent resultData
    ) {
        OmrGradingResult gradingResult =
                OmrGradingResultActivity
                        .extractGradingResult(resultData);

        assertNotNull(gradingResult);

        assertEquals(
                "instrumented-minimal-reading",
                gradingResult
                        .getReadingResult()
                        .getReadingId()
        );

        assertEquals(
                "instrumented-minimal-answer-key",
                gradingResult
                        .getAnswerKeyDefinition()
                        .getId()
        );

        assertEquals(1, gradingResult.getQuestionCount());
        assertEquals(1, gradingResult.getCorrectCount());
        assertEquals(1.0, gradingResult.getAwardedPoints(), 0.0001);
    }

    private static void clickButton(
            Activity activity,
            int buttonId
    ) {
        Button button = activity.findViewById(buttonId);

        assertNotNull(button);
        button.performClick();
    }

    private static void assertText(
            Activity activity,
            int viewId,
            String expectedText
    ) {
        TextView textView = activity.findViewById(viewId);

        assertNotNull(textView);
        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }
}
