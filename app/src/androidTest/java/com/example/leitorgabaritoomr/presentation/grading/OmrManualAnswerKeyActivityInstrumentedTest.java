package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Protege o contrato Android do cadastro manual do gabarito oficial.
 *
 * O teste usa a tela e os Chips reais, mas não inicializa câmera,
 * OpenCV nem o pipeline de leitura.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrManualAnswerKeyActivityInstrumentedTest {

    @Test
    public void editingAndNameSurviveActivityRecreation() {
        try (ActivityScenario<OmrManualAnswerKeyActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> {
                        assertInitialState(activity);

                        setAnswerKeyName(
                                activity,
                                "Gabarito de teste"
                        );

                        selectOptionAt(activity, 0, 0);
                        selectOptionAt(activity, 1, 3);
                        selectOptionAt(activity, 2, 1);

                        selectOptionAt(activity, 1, 2);
                        clearSelectionAt(activity, 1);

                        assertText(
                                activity,
                                R.id.textOmrManualProgress,
                                activity.getString(
                                        R.string
                                        .omr_manual_key_progress_format,
                                        2,
                                        52,
                                        4
                                )
                        );

                        assertQuestionSelection(
                                activity,
                                0,
                                "A"
                        );

                        assertQuestionSelection(
                                activity,
                                1,
                                null
                        );

                        assertQuestionSelection(
                                activity,
                                2,
                                "B"
                        );
                    }
            );

            scenario.recreate();

            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                "Gabarito de teste",
                                getAnswerKeyName(activity)
                        );

                        assertText(
                                activity,
                                R.id.textOmrManualProgress,
                                activity.getString(
                                        R.string
                                        .omr_manual_key_progress_format,
                                        2,
                                        52,
                                        4
                                )
                        );

                        assertQuestionSelection(
                                activity,
                                0,
                                "A"
                        );

                        assertQuestionSelection(
                                activity,
                                1,
                                null
                        );

                        assertQuestionSelection(
                                activity,
                                2,
                                "B"
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

    @Test
    public void completingAllQuestionsReturnsOfficialAnswerKey() {
        try (ActivityScenario<OmrManualAnswerKeyActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> setAnswerKeyName(
                            activity,
                            "Gabarito oficial controlado"
                    )
            );

            /*
             * Cada seleção é executada em um ciclo separado da thread
             * principal. Isso permite que as linhas substituídas pelo
             * Binder sejam coletadas durante o teste no emulador.
             */
            for (int questionIndex = 0;
                 questionIndex < 52;
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
                                        52,
                                        52,
                                        100
                                )
                        );

                        Button saveButton =
                                activity.findViewById(
                                        R.id.buttonOmrManualSave
                                );

                        assertNotNull(saveButton);
                        assertTrue(saveButton.isEnabled());
                        saveButton.performClick();
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

            assertReturnedAnswerKey(answerKey);
        }
    }

    @Test
    public void cancelReturnsCanceledWithoutAnswerKey() {
        try (ActivityScenario<OmrManualAnswerKeyActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> {
                        Button cancelButton =
                                activity.findViewById(
                                        R.id.buttonOmrManualCancel
                                );

                        assertNotNull(cancelButton);
                        cancelButton.performClick();
                    }
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );

            assertEquals(
                    null,
                    OmrManualAnswerKeyActivity
                            .extractCreatedAnswerKey(
                                    result.getResultData()
                            )
            );
        }
    }

    private static void assertInitialState(
            Activity activity
    ) {
        assertText(
                activity,
                R.id.textOmrManualProgress,
                activity.getString(
                        R.string.omr_manual_key_progress_format,
                        0,
                        52,
                        0
                )
        );

        LinearLayout questionContainer =
                getQuestionContainer(activity);

        assertEquals(
                52,
                questionContainer.getChildCount()
        );

        ChipGroup firstOptionGroup =
                getOptionGroupAt(activity, 0);

        assertEquals(
                4,
                firstOptionGroup.getChildCount()
        );

        assertQuestionSelection(activity, 0, null);

        Button saveButton =
                activity.findViewById(
                        R.id.buttonOmrManualSave
                );

        assertNotNull(saveButton);
        assertFalse(saveButton.isEnabled());
    }

    private static void assertReturnedAnswerKey(
            OmrAnswerKeyDefinition answerKey
    ) {
        assertNotNull(answerKey);

        OmrLayoutDefinition layout =
                AvalieCeDevelopmentLayoutFactory.create();

        assertTrue(
                answerKey.getId().startsWith("manual-")
        );

        assertEquals(1, answerKey.getVersion());

        assertEquals(
                "Gabarito oficial controlado",
                answerKey.getName()
        );

        assertEquals(
                layout.getId(),
                answerKey.getLayoutId()
        );

        assertEquals(
                layout.getVersion(),
                answerKey.getLayoutVersion()
        );

        assertEquals(52, answerKey.getQuestionCount());
        assertEquals(52.0, answerKey.getTotalWeight(), 0.0001);

        List<OmrQuestionDefinition> questions =
                layout.getAllQuestions();

        for (int questionIndex = 0;
             questionIndex < questions.size();
             questionIndex++) {

            OmrQuestionDefinition question =
                    questions.get(questionIndex);

            List<OmrOptionDefinition> options =
                    question.getOptions();

            OmrOptionDefinition expectedOption =
                    options.get(
                            questionIndex % options.size()
                    );

            OmrAnswerKeyEntry entry =
                    answerKey.findEntryByQuestionId(
                            question.getId()
                    );

            assertNotNull(entry);
            assertEquals(1, entry.getAcceptedOptionCount());
            assertTrue(
                    entry.acceptsOption(
                            expectedOption.getId()
                    )
            );
            assertEquals(1.0, entry.getWeight(), 0.0001);
        }
    }

    private static Intent createActivityIntent() {
        Context context =
                ApplicationProvider
                        .getApplicationContext();

        return OmrManualAnswerKeyActivity.createIntent(
                context
        );
    }

    private static void setAnswerKeyName(
            Activity activity,
            String answerKeyName
    ) {
        TextInputEditText nameEditText =
                activity.findViewById(
                        R.id.editOmrManualName
                );

        assertNotNull(nameEditText);
        nameEditText.setText(answerKeyName);
    }

    private static String getAnswerKeyName(
            Activity activity
    ) {
        TextInputEditText nameEditText =
                activity.findViewById(
                        R.id.editOmrManualName
                );

        assertNotNull(nameEditText);

        return nameEditText.getText() == null
                ? ""
                : nameEditText.getText().toString();
    }

    private static void selectOptionAt(
            Activity activity,
            int questionIndex,
            int optionIndex
    ) {
        ChipGroup optionGroup =
                getOptionGroupAt(
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

    private static void clearSelectionAt(
            Activity activity,
            int questionIndex
    ) {
        ChipGroup optionGroup =
                getOptionGroupAt(
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
        checkedChip.performClick();

        assertQuestionSelection(
                activity,
                questionIndex,
                null
        );
    }

    private static void assertQuestionSelection(
            Activity activity,
            int questionIndex,
            String expectedLabel
    ) {
        ChipGroup optionGroup =
                getOptionGroupAt(
                        activity,
                        questionIndex
                );

        int checkedChipId =
                optionGroup.getCheckedChipId();

        if (expectedLabel == null) {
            assertEquals(View.NO_ID, checkedChipId);
            return;
        }

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
        LinearLayout questionContainer =
                getQuestionContainer(activity);

        assertTrue(questionIndex >= 0);
        assertTrue(
                questionIndex
                        < questionContainer.getChildCount()
        );

        View questionItem =
                questionContainer.getChildAt(
                        questionIndex
                );

        ChipGroup optionGroup =
                questionItem.findViewById(
                        R.id
                        .chipGroupOmrManualQuestionOptions
                );

        assertNotNull(optionGroup);
        return optionGroup;
    }

    private static LinearLayout getQuestionContainer(
            Activity activity
    ) {
        LinearLayout questionContainer =
                activity.findViewById(
                        R.id.containerOmrManualQuestions
                );

        assertNotNull(questionContainer);
        return questionContainer;
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
}
