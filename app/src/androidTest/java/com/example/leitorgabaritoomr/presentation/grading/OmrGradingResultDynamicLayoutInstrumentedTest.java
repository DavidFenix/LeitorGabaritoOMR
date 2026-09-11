package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyDefinitionFactory;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Protege a apresentação de uma correção produzida por um layout
 * compacto dinâmico, em vez do layout legado fixo.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrGradingResultDynamicLayoutInstrumentedTest {

    private static final String[] ANSWERS = {
            "A", "B", "C", "D", "A", "B", "C"
    };

    @Test
    public void dynamicSevenQuestionResultIsReceivedAndRendered() {
        OmrGradingResult gradingResult =
                createDynamicSevenQuestionGradingResult();

        Context context =
                ApplicationProvider.getApplicationContext();

        try (ActivityScenario<OmrGradingResultActivity>
                     scenario = ActivityScenario.launch(
                             OmrGradingResultActivity.createIntent(
                                     context,
                                     gradingResult
                             )
                     )) {

            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());

                assertText(
                        activity,
                        R.id.textOmrGradingAnswerKey,
                        activity.getString(
                                R.string.omr_grading_answer_key_format,
                                "Gabarito dinâmico 7",
                                1
                        )
                );

                assertText(
                        activity,
                        R.id.textOmrGradingPercentage,
                        activity.getString(
                                R.string.omr_grading_percentage_format,
                                100
                        )
                );

                ListView questionList = activity.findViewById(
                        R.id.listOmrGradingQuestions
                );

                assertNotNull(questionList);

                ListAdapter adapter = questionList.getAdapter();

                assertNotNull(adapter);
                assertEquals(ANSWERS.length, adapter.getCount());

                for (int index = 0;
                     index < ANSWERS.length;
                     index++) {

                    View row = adapter.getView(
                            index,
                            null,
                            questionList
                    );

                    TextView status = row.findViewById(
                            R.id.textOmrGradeQuestionStatus
                    );

                    assertNotNull(status);
                    assertEquals(
                            activity.getString(
                                    R.string
                                            .omr_grading_question_correct_format,
                                    ANSWERS[index]
                            ),
                            status.getText().toString()
                    );
                }
            });
        }
    }

    private static OmrGradingResult
    createDynamicSevenQuestionGradingResult() {
        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(
                        OmrSheetTemplateCatalog
                                .compactFourOptions(
                                        ANSWERS.length
                                )
                );

        List<String> answerLabels = new ArrayList<>();
        Collections.addAll(answerLabels, ANSWERS);

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinitionFactory()
                        .createSingleAnswerKey(
                                "dynamic-seven-answer-key",
                                1,
                                "Gabarito dinâmico 7",
                                layout,
                                answerLabels,
                                1.0
                        );

        List<OmrQuestionResult> questionResults =
                new ArrayList<>(ANSWERS.length);

        List<OmrQuestionDefinition> questions =
                layout.getAllQuestions();

        for (int index = 0;
             index < ANSWERS.length;
             index++) {

            OmrQuestionDefinition question =
                    questions.get(index);

            OmrOptionDefinition option =
                    question.findOptionByLabel(
                            ANSWERS[index]
                    );

            assertNotNull(option);

            questionResults.add(
                    new OmrQuestionResult(
                            index + 1,
                            question.getId(),
                            OmrQuestionResult.Status.SINGLE_MARK,
                            Collections.singletonList(
                                    new OmrQuestionResult.Option(
                                            option.getId(),
                                            option.getLabel()
                                    )
                            ),
                            0.99
                    )
            );
        }

        OmrReadingResult readingResult =
                new OmrReadingResult(
                        "dynamic-seven-reading",
                        1_800_000_000_000L,
                        layout.getId(),
                        layout.getVersion(),
                        layout.getName(),
                        questionResults
                );

        return new OmrReadingGrader().grade(
                readingResult,
                answerKey
        );
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
