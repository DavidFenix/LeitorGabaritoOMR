package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class
OmrManualDynamicAnswerKeyActivityInstrumentedTest {

    @Test
    public void everyCompactCountFromOneToTenRendersExactRows() {
        for (int questionCount = 1;
             questionCount <= 10;
             questionCount++) {

            int expectedQuestionCount = questionCount;

            try (ActivityScenario<OmrManualAnswerKeyActivity>
                         scenario = ActivityScenario.launch(
                                 createCompactIntent(
                                         questionCount
                                 )
                         )) {

                scenario.onActivity(
                        activity -> {
                            ListAdapter adapter =
                                    getQuestionList(activity)
                                            .getAdapter();

                            assertNotNull(adapter);
                            assertEquals(
                                    expectedQuestionCount,
                                    adapter.getCount()
                            );

                            assertText(
                                    activity,
                                    R.id.textOmrManualProgress,
                                    activity.getString(
                                            R.string
                                                    .omr_manual_key_progress_format,
                                            0,
                                            expectedQuestionCount,
                                            0
                                    )
                            );

                            Button saveButton =
                                    activity.findViewById(
                                            R.id.buttonOmrManualSave
                                    );

                            assertNotNull(saveButton);
                            assertFalse(saveButton.isEnabled());
                        }
                );
            }
        }
    }

    @Test
    public void completingThreeQuestionsReturnsDynamicAnswerKey() {
        try (ActivityScenario<OmrManualAnswerKeyActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createCompactIntent(3)
                     )) {

            scenario.onActivity(
                    activity -> setAnswerKeyName(
                            activity,
                            "Avaliação dinâmica de três questões"
                    )
            );

            for (int questionIndex = 0;
                 questionIndex < 3;
                 questionIndex++) {

                int currentQuestionIndex = questionIndex;
                int optionIndex = questionIndex % 4;

                scenario.onActivity(
                        activity -> selectOptionAt(
                                activity,
                                currentQuestionIndex,
                                optionIndex
                        )
                );
            }

            scenario.onActivity(
                    activity -> {
                        assertText(
                                activity,
                                R.id.textOmrManualProgress,
                                activity.getString(
                                        R.string
                                                .omr_manual_key_progress_format,
                                        3,
                                        3,
                                        100
                                )
                        );

                        Button saveButton =
                                activity.findViewById(
                                        R.id.buttonOmrManualSave
                                );

                        assertNotNull(saveButton);
                        assertTrue(saveButton.isEnabled());
                        assertTrue(saveButton.performClick());
                    }
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_OK,
                    result.getResultCode()
            );

            OmrAnswerKeyDefinition answerKey =
                    OmrManualAnswerKeyActivity
                            .extractCreatedAnswerKey(
                                    result.getResultData()
                            );

            assertNotNull(answerKey);
            assertEquals(
                    "Avaliação dinâmica de três questões",
                    answerKey.getName()
            );
            assertEquals(
                    "omr-compact-ad-q003",
                    answerKey.getLayoutId()
            );
            assertEquals(1, answerKey.getLayoutVersion());
            assertEquals(3, answerKey.getQuestionCount());
            assertEquals(
                    3.0,
                    answerKey.getTotalWeight(),
                    0.0001
            );

            assertAcceptedOption(
                    answerKey,
                    "question-001",
                    "question-001-option-01"
            );
            assertAcceptedOption(
                    answerKey,
                    "question-002",
                    "question-002-option-02"
            );
            assertAcceptedOption(
                    answerKey,
                    "question-003",
                    "question-003-option-03"
            );
        }
    }

    @Test
    public void dynamicNameAndSelectionsSurviveRecreation() {
        try (ActivityScenario<OmrManualAnswerKeyActivity>
                     scenario = ActivityScenario.launch(
                             createCompactIntent(7)
                     )) {

            scenario.onActivity(
                    activity -> {
                        setAnswerKeyName(
                                activity,
                                "Gabarito de sete questões"
                        );

                        selectOptionAt(activity, 0, 0);
                        selectOptionAt(activity, 6, 3);
                    }
            );

            scenario.recreate();

            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                "Gabarito de sete questões",
                                getAnswerKeyName(activity)
                        );

                        assertEquals(
                                7,
                                getQuestionList(activity)
                                        .getAdapter()
                                        .getCount()
                        );

                        assertQuestionSelection(
                                activity,
                                0,
                                "A"
                        );
                        assertQuestionSelection(
                                activity,
                                6,
                                "D"
                        );
                    }
            );
        }
    }

    @Test
    public void compactIntentRejectsCountsOutsidePublishedRange() {
        Context context =
                ApplicationProvider.getApplicationContext();

        expectIllegalArgument(() ->
                OmrManualAnswerKeyActivity
                        .createCompactIntent(context, 0)
        );

        expectIllegalArgument(() ->
                OmrManualAnswerKeyActivity
                        .createCompactIntent(context, 11)
        );

        expectIllegalArgument(() ->
                OmrManualAnswerKeyActivity
                        .createCompactIntent(null, 10)
        );
    }

    private static Intent createCompactIntent(
            int questionCount
    ) {
        Context context =
                ApplicationProvider.getApplicationContext();

        return OmrManualAnswerKeyActivity
                .createCompactIntent(
                        context,
                        questionCount
                );
    }

    private static void assertAcceptedOption(
            OmrAnswerKeyDefinition answerKey,
            String questionId,
            String optionId
    ) {
        OmrAnswerKeyEntry entry =
                answerKey.findEntryByQuestionId(questionId);

        assertNotNull(entry);
        assertEquals(1, entry.getAcceptedOptionCount());
        assertTrue(entry.acceptsOption(optionId));
    }

    private static void setAnswerKeyName(
            Activity activity,
            String answerKeyName
    ) {
        TextInputEditText editText =
                activity.findViewById(
                        R.id.editOmrManualName
                );

        assertNotNull(editText);
        editText.setText(answerKeyName);
    }

    private static String getAnswerKeyName(
            Activity activity
    ) {
        TextInputEditText editText =
                activity.findViewById(
                        R.id.editOmrManualName
                );

        assertNotNull(editText);

        return editText.getText() == null
                ? ""
                : editText.getText().toString();
    }

    private static void selectOptionAt(
            Activity activity,
            int questionIndex,
            int optionIndex
    ) {
        ChipGroup optionGroup = getOptionGroupAt(
                activity,
                questionIndex
        );

        assertTrue(optionIndex >= 0);
        assertTrue(optionIndex < optionGroup.getChildCount());

        View optionView =
                optionGroup.getChildAt(optionIndex);

        assertTrue(optionView instanceof Chip);

        String expectedLabel =
                ((Chip) optionView)
                        .getText()
                        .toString();

        optionView.performClick();

        assertQuestionSelection(
                activity,
                questionIndex,
                expectedLabel
        );
    }

    private static void assertQuestionSelection(
            Activity activity,
            int questionIndex,
            String expectedLabel
    ) {
        ChipGroup optionGroup = getOptionGroupAt(
                activity,
                questionIndex
        );

        int checkedChipId =
                optionGroup.getCheckedChipId();

        assertTrue(checkedChipId != View.NO_ID);

        Chip checkedChip =
                optionGroup.findViewById(
                        checkedChipId
                );

        assertNotNull(checkedChip);
        assertEquals(
                expectedLabel,
                checkedChip.getText().toString()
        );
    }

    private static ChipGroup getOptionGroupAt(
            Activity activity,
            int questionIndex
    ) {
        ListView questionList =
                getQuestionList(activity);

        ListAdapter adapter =
                questionList.getAdapter();

        assertNotNull(adapter);
        assertTrue(questionIndex >= 0);
        assertTrue(questionIndex < adapter.getCount());

        View questionItem = adapter.getView(
                questionIndex,
                null,
                questionList
        );

        ChipGroup optionGroup =
                questionItem.findViewById(
                        R.id.chipGroupOmrManualQuestionOptions
                );

        assertNotNull(optionGroup);
        return optionGroup;
    }

    private static ListView getQuestionList(
            Activity activity
    ) {
        ListView listView =
                activity.findViewById(
                        R.id.listOmrManualQuestions
                );

        assertNotNull(listView);
        return listView;
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

    private static void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
