package com.example.leitorgabaritoomr.presentation.grading;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.google.android.material.chip.ChipGroup;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Protege o encaminhamento entre a lista de gabaritos e o editor dinâmico.
 *
 * O armazenamento da lista é isolado e nenhum gabarito é salvo por estes
 * testes. Assim, os dados reais do aplicativo permanecem intocados.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrAnswerKeyDynamicCreationFlowInstrumentedTest {

    private static final String TEST_STORAGE_NAMESPACE =
            "answer_key_dynamic_creation_flow_test";

    private static final long ACTIVITY_TIMEOUT_MILLIS =
            10_000L;

    @Test
    public void createActionShowsPublishedRangeFromOneToNinety() {
        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            onView(
                    withId(
                            R.id.buttonOmrAnswerKeyListCreate
                    )
            ).perform(click());

            onView(
                    withText(
                            R.string
                                    .omr_answer_key_list_create_count_title
                    )
            ).inRoot(isDialog())
                    .check(matches(isDisplayed()));

            Context context =
                    ApplicationProvider.getApplicationContext();

            int[] representativeCounts = {
                    1,
                    10,
                    11,
                    18,
                    19,
                    90
            };

            for (int questionCount : representativeCounts) {

                String expectedLabel = context
                        .getResources()
                        .getQuantityString(
                                R.plurals
                                        .omr_answer_key_list_create_question_count,
                                questionCount,
                                questionCount
                        );

                onData(anything())
                        .inRoot(isDialog())
                        .atPosition(questionCount - 1)
                        .check(matches(withText(expectedLabel)));
            }

        }
    }

    @Test
    public void selectingNinetyOpensStandardV2EditorWithNinetyRows() {
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(
                        OmrManualAnswerKeyActivity.class.getName(),
                        null,
                        false
                );

        Activity openedActivity = null;

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            onView(
                    withId(
                            R.id.buttonOmrAnswerKeyListCreate
                    )
            ).perform(click());

            onData(anything())
                    .inRoot(isDialog())
                    .atPosition(89)
                    .perform(click());

            onView(
                    withText(
                            R.string
                                    .omr_answer_key_list_create_four_options
                    )
            ).inRoot(isDialog())
                    .perform(click());

            openedActivity =
                    instrumentation.waitForMonitorWithTimeout(
                            monitor,
                            ACTIVITY_TIMEOUT_MILLIS
                    );

            assertNotNull(openedActivity);
            assertTrue(
                    openedActivity
                            instanceof OmrManualAnswerKeyActivity
            );

            Activity activityToInspect = openedActivity;

            instrumentation.runOnMainSync(
                    () -> {
                        ListView questionList =
                                activityToInspect.findViewById(
                                        R.id.listOmrManualQuestions
                                );

                        assertNotNull(questionList);

                        ListAdapter adapter =
                                questionList.getAdapter();

                        assertNotNull(adapter);
                        assertEquals(90, adapter.getCount());

                        android.widget.TextView layoutText =
                                activityToInspect.findViewById(
                                        R.id.textOmrManualLayout
                                );

                        assertNotNull(layoutText);
                        assertTrue(
                                layoutText.getText()
                                        .toString()
                                        .contains(
                                                "Cartao padronizado v2"
                                        )
                        );
                    }
            );

        } finally {
            finishActivity(
                    instrumentation,
                    openedActivity
            );

            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void selectingFiveOptionsOpensAeEditorWithFiveChips() {
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(
                        OmrManualAnswerKeyActivity.class.getName(),
                        null,
                        false
                );

        Activity openedActivity = null;

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            onView(
                    withId(
                            R.id.buttonOmrAnswerKeyListCreate
                    )
            ).perform(click());

            onData(anything())
                    .inRoot(isDialog())
                    .atPosition(1)
                    .perform(click());

            onView(
                    withText(
                            R.string
                                    .omr_answer_key_list_create_five_options
                    )
            ).inRoot(isDialog())
                    .perform(click());

            openedActivity =
                    instrumentation.waitForMonitorWithTimeout(
                            monitor,
                            ACTIVITY_TIMEOUT_MILLIS
                    );

            assertNotNull(openedActivity);

            Activity activityToInspect = openedActivity;

            instrumentation.runOnMainSync(
                    () -> {
                        ListView questionList =
                                activityToInspect.findViewById(
                                        R.id.listOmrManualQuestions
                                );

                        assertNotNull(questionList);

                        ListAdapter adapter =
                                questionList.getAdapter();

                        assertNotNull(adapter);
                        assertEquals(2, adapter.getCount());

                        View firstQuestion = adapter.getView(
                                0,
                                null,
                                questionList
                        );

                        ChipGroup options =
                                firstQuestion.findViewById(
                                        R.id
                                                .chipGroupOmrManualQuestionOptions
                                );

                        assertNotNull(options);
                        assertEquals(5, options.getChildCount());

                        android.widget.TextView layoutText =
                                activityToInspect.findViewById(
                                        R.id.textOmrManualLayout
                                );

                        assertNotNull(layoutText);
                        assertTrue(
                                layoutText.getText()
                                        .toString()
                                        .contains("A-E")
                        );
                    }
            );

        } finally {
            finishActivity(
                    instrumentation,
                    openedActivity
            );

            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void cancelClosesDialogAndKeepsListOpen() {
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(
                        OmrManualAnswerKeyActivity.class.getName(),
                        null,
                        false
                );

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            onView(
                    withId(
                            R.id.buttonOmrAnswerKeyListCreate
                    )
            ).perform(click());

            onView(
                    withText(
                            R.string
                                    .omr_answer_key_list_create_count_cancel
                    )
            ).inRoot(isDialog())
                    .perform(click());

            onView(
                    withText(
                            R.string
                                    .omr_answer_key_list_create_count_title
                    )
            ).check(doesNotExist());

            scenario.onActivity(
                    activity -> {
                        assertFalse(activity.isFinishing());

                        Button createButton =
                                activity.findViewById(
                                        R.id
                                                .buttonOmrAnswerKeyListCreate
                                );

                        assertNotNull(createButton);
                        assertTrue(createButton.isEnabled());
                    }
            );

            assertEquals(0, monitor.getHits());

        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private static android.content.Intent
    createActivityIntent() {
        Context context =
                ApplicationProvider.getApplicationContext();

        return OmrAnswerKeyListActivity
                .createIsolatedStorageIntent(
                        context,
                        TEST_STORAGE_NAMESPACE
                );
    }

    private static void finishActivity(
            Instrumentation instrumentation,
            Activity activity
    ) {
        if (activity == null) {
            return;
        }

        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
