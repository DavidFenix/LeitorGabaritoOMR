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
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;

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
            5_000L;

    @Test
    public void createActionShowsEveryCountFromOneToTen() {
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

            for (int questionCount = 1;
                 questionCount <= 10;
                 questionCount++) {

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

            onData(anything())
                    .inRoot(isDialog())
                    .atPosition(9)
                    .check(matches(withText("10 questões")));

            onView(withText("11 questões"))
                    .inRoot(isDialog())
                    .check(doesNotExist());
        }
    }

    @Test
    public void selectingSevenOpensEditorWithExactlySevenRows() {
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
                    .atPosition(6)
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
                        assertEquals(7, adapter.getCount());
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
