package com.example.leitorgabaritoomr.presentation.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.student.OmrManualStudentIdentityFactory;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;
import com.example.leitorgabaritoomr.presentation.history.OmrStudentHistoryActivity;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Protege o contrato Android da identificacao manual do aluno.
 *
 * Os testes usam um gabarito minimo de uma questao e nao inicializam camera,
 * OpenCV ou correcao. O banco do processo de teste e apagado antes e depois
 * de cada caso porque a acao de historico abre sua Activity real.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrStudentIdentificationActivityInstrumentedTest {

    private static final String HISTORY_DATABASE_NAME =
            "omr_grading_history.db";

    private Context applicationContext;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        applicationContext.deleteDatabase(
                HISTORY_DATABASE_NAME
        );
    }

    @After
    public void tearDown() {
        if (applicationContext != null) {
            applicationContext.deleteDatabase(
                    HISTORY_DATABASE_NAME
            );

            applicationContext = null;
        }
    }

    @Test
    public void initialStateRendersAnswerKeyAndDisablesStart() {
        OmrAnswerKeyDefinition answerKey =
                createAnswerKey();

        try (ActivityScenario<OmrStudentIdentificationActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent(answerKey)
                     )) {

            scenario.onActivity(
                    activity -> {
                        assertText(
                                activity,
                                R.id.textOmrStudentAnswerKey,
                                activity.getString(
                                        R.string
                                        .omr_student_identification_answer_key_format,
                                        answerKey.getName(),
                                        answerKey.getVersion(),
                                        answerKey.getQuestionCount()
                                )
                        );

                        assertEquals(
                                "",
                                getFieldText(
                                        activity,
                                        R.id.editOmrStudentRegistration
                                )
                        );

                        assertEquals(
                                "",
                                getFieldText(
                                        activity,
                                        R.id.editOmrStudentName
                                )
                        );

                        assertEquals(
                                "",
                                getFieldText(
                                        activity,
                                        R.id.editOmrStudentClass
                                )
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrStudentStart,
                                false
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrStudentHistory,
                                false
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrStudentCancel,
                                true
                        );
                    }
            );
        }
    }

    @Test
    public void validFormReturnsNormalizedStableStudentIdentity() {
        try (ActivityScenario<OmrStudentIdentificationActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent(
                                     createAnswerKey()
                             )
                     )) {

            scenario.onActivity(
                    activity -> {
                        setFieldText(
                                activity,
                                R.id.editOmrStudentRegistration,
                                "  ab-001  "
                        );

                        setFieldText(
                                activity,
                                R.id.editOmrStudentName,
                                "  Ana Beatriz  "
                        );

                        setFieldText(
                                activity,
                                R.id.editOmrStudentClass,
                                "  9o A  "
                        );

                        Button startButton =
                                activity.findViewById(
                                        R.id.buttonOmrStudentStart
                                );

                        assertNotNull(startButton);
                        assertTrue(startButton.isEnabled());

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrStudentHistory,
                                true
                        );

                        startButton.performClick();
                    }
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_OK,
                    result.getResultCode()
            );

            OmrStudentIdentity studentIdentity =
                    OmrStudentIdentificationActivity
                            .extractStudentIdentity(
                                    result.getResultData()
                            );

            assertNotNull(studentIdentity);

            assertEquals(
                    "AB-001",
                    studentIdentity.getRegistration()
            );

            assertEquals(
                    "Ana Beatriz",
                    studentIdentity.getName()
            );

            assertEquals(
                    "9o A",
                    studentIdentity.getClassName()
            );

            assertEquals(
                    new OmrManualStudentIdentityFactory()
                            .studentIdForRegistration("ab-001"),
                    studentIdentity.getStudentId()
            );
        }
    }

    @Test
    public void enteredFieldsSurviveBackgroundTransition() {
        try (ActivityScenario<OmrStudentIdentificationActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent(
                                     createAnswerKey()
                             )
                     )) {

            scenario.onActivity(
                    activity -> {
                        setFieldText(
                                activity,
                                R.id.editOmrStudentRegistration,
                                "000123"
                        );

                        setFieldText(
                                activity,
                                R.id.editOmrStudentName,
                                "Aluno Teste"
                        );

                        setFieldText(
                                activity,
                                R.id.editOmrStudentClass,
                                "9o B"
                        );
                    }
            );

            scenario.moveToState(
                    Lifecycle.State.CREATED
            );

            scenario.moveToState(
                    Lifecycle.State.RESUMED
            );

            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                "000123",
                                getFieldText(
                                        activity,
                                        R.id.editOmrStudentRegistration
                                )
                        );

                        assertEquals(
                                "Aluno Teste",
                                getFieldText(
                                        activity,
                                        R.id.editOmrStudentName
                                )
                        );

                        assertEquals(
                                "9o B",
                                getFieldText(
                                        activity,
                                        R.id.editOmrStudentClass
                                )
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrStudentStart,
                                true
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrStudentHistory,
                                true
                        );
                    }
            );
        }
    }

    @Test
    public void validFormOpensHistoryWithSameStableIdentity() {
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(
                        OmrStudentHistoryActivity.class.getName(),
                        null,
                        false
                );

        Activity openedActivity = null;

        try (ActivityScenario<OmrStudentIdentificationActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent(
                                     createAnswerKey()
                             )
                     )) {

            scenario.onActivity(activity -> {
                setFieldText(
                        activity,
                        R.id.editOmrStudentRegistration,
                        "  ab-001  "
                );

                setFieldText(
                        activity,
                        R.id.editOmrStudentName,
                        "  Ana Beatriz  "
                );

                setFieldText(
                        activity,
                        R.id.editOmrStudentClass,
                        "  9o A  "
                );

                Button historyButton =
                        activity.findViewById(
                                R.id.buttonOmrStudentHistory
                        );

                assertNotNull(historyButton);
                assertTrue(historyButton.isEnabled());
                assertTrue(historyButton.performClick());
            });

            openedActivity =
                    instrumentation.waitForMonitorWithTimeout(
                            monitor,
                            5_000L
                    );

            assertNotNull(openedActivity);
            assertTrue(
                    openedActivity
                            instanceof OmrStudentHistoryActivity
            );

            OmrStudentIdentity receivedStudent =
                    OmrStudentHistoryActivity
                            .extractStudentIdentity(
                                    openedActivity.getIntent()
                            );

            assertNotNull(receivedStudent);
            assertEquals(
                    "AB-001",
                    receivedStudent.getRegistration()
            );
            assertEquals(
                    "Ana Beatriz",
                    receivedStudent.getName()
            );
            assertEquals(
                    "9o A",
                    receivedStudent.getClassName()
            );
            assertEquals(
                    new OmrManualStudentIdentityFactory()
                            .studentIdForRegistration("ab-001"),
                    receivedStudent.getStudentId()
            );

            Activity activityToFinish = openedActivity;

            instrumentation.runOnMainSync(
                    activityToFinish::finish
            );

            openedActivity = null;
            instrumentation.waitForIdleSync();

            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());

                assertEquals(
                        "  ab-001  ",
                        getFieldText(
                                activity,
                                R.id.editOmrStudentRegistration
                        )
                );
            });

        } finally {
            if (openedActivity != null) {
                Activity activityToFinish = openedActivity;

                instrumentation.runOnMainSync(
                        activityToFinish::finish
                );

                instrumentation.waitForIdleSync();
            }

            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void cancelReturnsCanceledWithoutStudentIdentity() {
        try (ActivityScenario<OmrStudentIdentificationActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent(
                                     createAnswerKey()
                             )
                     )) {

            scenario.onActivity(
                    activity -> {
                        Button cancelButton =
                                activity.findViewById(
                                        R.id.buttonOmrStudentCancel
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

            assertNull(
                    OmrStudentIdentificationActivity
                            .extractStudentIdentity(
                                    result.getResultData()
                            )
            );
        }
    }

    @Test
    public void missingAnswerKeyFinishesSafelyAsCanceled() {
        Context context =
                ApplicationProvider.getApplicationContext();

        Intent intent = new Intent(
                context,
                OmrStudentIdentificationActivity.class
        );

        try (ActivityScenario<OmrStudentIdentificationActivity>
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

            assertNull(
                    OmrStudentIdentificationActivity
                            .extractStudentIdentity(
                                    result.getResultData()
                            )
            );
        }
    }

    private static Intent createActivityIntent(
            OmrAnswerKeyDefinition answerKey
    ) {
        Context context =
                ApplicationProvider.getApplicationContext();

        return OmrStudentIdentificationActivity.createIntent(
                context,
                answerKey
        );
    }

    private static OmrAnswerKeyDefinition createAnswerKey() {
        OmrAnswerKeyEntry entry =
                new OmrAnswerKeyEntry(
                        "Q1",
                        Collections.singleton("Q1-A"),
                        1.0
                );

        return new OmrAnswerKeyDefinition(
                "student-test-key",
                3,
                "Avaliacao controlada",
                "student-test-layout",
                1,
                Collections.singletonList(entry)
        );
    }

    private static void setFieldText(
            Activity activity,
            int viewId,
            String value
    ) {
        TextInputEditText editText =
                activity.findViewById(viewId);

        assertNotNull(editText);
        editText.setText(value);
    }

    private static String getFieldText(
            Activity activity,
            int viewId
    ) {
        TextInputEditText editText =
                activity.findViewById(viewId);

        assertNotNull(editText);

        return editText.getText() == null
                ? ""
                : editText.getText().toString();
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

    private static void assertButtonEnabled(
            Activity activity,
            int viewId,
            boolean expectedEnabled
    ) {
        Button button =
                activity.findViewById(viewId);

        assertNotNull(button);
        assertEquals(
                expectedEnabled,
                button.isEnabled()
        );

        if (expectedEnabled) {
            assertTrue(button.getAlpha() > 0.55f);
        } else {
            assertFalse(button.getAlpha() > 0.55f);
        }
    }
}
