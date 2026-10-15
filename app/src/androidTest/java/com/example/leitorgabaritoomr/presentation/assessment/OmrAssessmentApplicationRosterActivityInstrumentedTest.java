package com.example.leitorgabaritoomr.presentation.assessment;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.appcompat.app.AlertDialog;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionContext;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationBatchRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;
import com.example.leitorgabaritoomr.presentation.capture.OmrCaptureActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentApplicationRosterActivityInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private String answerKeyNamespace;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrSQLiteAssessmentApplicationBatchRepository batchRepository;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrSharedPreferencesAnswerKeyRepository answerKeyRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        databaseName = "omr_application_roster_activity_"
                + UUID.randomUUID() + ".db";
        answerKeyNamespace = "application_roster_" + UUID.randomUUID();
        applicationContext.deleteDatabase(databaseName);
        clearAnswerKeyStorage();
        assessmentRepository = new OmrSQLiteAssessmentRepository(
                applicationContext, databaseName
        );
        classRepository = new OmrSQLiteClassGroupRepository(
                applicationContext, databaseName
        );
        studentRepository = new OmrSQLiteStudentRepository(
                applicationContext, databaseName
        );
        batchRepository =
                new OmrSQLiteAssessmentApplicationBatchRepository(
                        applicationContext, databaseName
                );
        attemptRepository = new OmrSQLiteAssessmentAttemptRepository(
                applicationContext, databaseName
        );
        answerKeyRepository = new OmrSharedPreferencesAnswerKeyRepository(
                isolatedAnswerKeyContext()
        );
        answerKeyRepository.save(answerKey());
        seedApplication();
    }

    @After
    public void tearDown() {
        if (attemptRepository != null) {
            attemptRepository.close();
        }
        if (batchRepository != null) {
            batchRepository.close();
        }
        if (studentRepository != null) {
            studentRepository.close();
        }
        if (classRepository != null) {
            classRepository.close();
        }
        if (assessmentRepository != null) {
            assessmentRepository.close();
        }
        clearAnswerKeyStorage();
        applicationContext.deleteDatabase(databaseName);
    }

    @Test
    public void intentFactoriesValidateAndExtractApplicationId() {
        expectIllegalArgument(() ->
                OmrAssessmentApplicationRosterActivity.createIntent(
                        null, "application-1"
                ));
        expectIllegalArgument(() ->
                OmrAssessmentApplicationRosterActivity.createIntent(
                        applicationContext, " "
                ));
        expectIllegalArgument(() ->
                OmrAssessmentApplicationRosterActivity
                        .createIsolatedStorageIntent(
                                applicationContext,
                                "application-1",
                                " "
                        ));
        expectIllegalArgument(() ->
                OmrAssessmentApplicationRosterActivity
                        .createIsolatedStorageIntent(
                                applicationContext,
                                "application-1",
                                databaseName,
                                " "
                        ));
        Intent intent = createActivityIntent(" application-1 ");
        assertNotNull(intent);
        assertEquals(
                "application-1",
                OmrAssessmentApplicationRosterActivity
                        .extractApplicationId(intent)
        );
        assertTrue(OmrAssessmentApplicationRosterActivity
                .extractApplicationId(null) == null);
    }

    @Test
    public void headerShowsExactAssessmentFrozenClassAndProgress() {
        saveAttempts();
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> {
                assertText(activity,
                        R.id.textOmrApplicationRosterTitle,
                        "Avaliação bimestral");
                assertText(activity,
                        R.id.textOmrApplicationRosterAssessment,
                        "Matemática • prova v2 • assessment-1");
                assertText(activity,
                        R.id.textOmrApplicationRosterClass,
                        "Chamada congelada: 9º A original");
                assertText(activity,
                        R.id.textOmrApplicationRosterProgress,
                        "1 de 2 corrigidos • 1 pendente");
            });
        }
    }

    @Test
    public void participantsRenderInFrozenRosterOrder() {
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> {
                assertEquals(2, listView(activity).getAdapter().getCount());
                View first = rowAt(activity, 0);
                assertRowText(first,
                        R.id.textOmrApplicationParticipantPosition,
                        "#01");
                assertRowText(first,
                        R.id.textOmrApplicationParticipantName,
                        "Ana original");
            });
        }
    }

    @Test
    public void latestAttemptRevisionWinsAndOtherStudentRemainsPending() {
        saveAttempts();
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> {
                TextView corrected = rowAt(activity, 0).findViewById(
                        R.id.textOmrApplicationParticipantStatus
                );
                TextView pending = rowAt(activity, 1).findViewById(
                        R.id.textOmrApplicationParticipantStatus
                );
                assertTrue(corrected.getText().toString().startsWith(
                        "Corrigido • revisão 2 •"
                ));
                assertEquals("Pendente de correção",
                        pending.getText().toString());
            });
        }
    }

    @Test
    public void searchFiltersByStatusAndShowsNoResults() {
        saveAttempts();
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            onView(withId(
                    R.id.editOmrApplicationRosterSearch
            )).perform(replaceText("pendente"), closeSoftKeyboard());
            scenario.onActivity(activity -> {
                assertEquals(1, listView(activity).getAdapter().getCount());
                assertRowText(rowAt(activity, 0),
                        R.id.textOmrApplicationParticipantName,
                        "Bruno original");
            });
            onView(withId(
                    R.id.editOmrApplicationRosterSearch
            )).perform(replaceText("inexistente"), closeSoftKeyboard());
            scenario.onActivity(activity -> assertVisibility(
                    activity,
                    R.id.containerOmrApplicationRosterNoResults,
                    View.VISIBLE
            ));
        }
    }

    @Test
    public void recreationPreservesStoredFrozenRoster() {
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(
                    2, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void missingApplicationFinishesWithCancelledResult() {
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launchActivityForResult(
                createActivityIntent("missing"))) {
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
        }
    }

    @Test
    public void backButtonFinishesActivity() {
        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launchActivityForResult(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrApplicationRosterBack
            ).performClick());
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
        }
    }

    @Test
    public void pendingParticipantLaunchesCaptureWithExactFrozenContext() {
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                OmrCaptureActivity.class.getName(),
                null,
                false
        );

        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> {
                View pendingRow = rowAt(activity, 1);
                button(
                        pendingRow,
                        R.id.buttonOmrApplicationParticipantCorrect
                ).performClick();
            });

            Activity capture = monitor.waitForActivityWithTimeout(5_000L);
            assertNotNull(capture);
            OmrAssessmentCorrectionContext context =
                    OmrCaptureActivity.extractAssessmentCorrectionContext(
                            capture.getIntent()
                    );
            assertNotNull(context);
            assertEquals("student-bruno",
                    context.getParticipant().getStudentId());
            assertEquals("Bruno original",
                    context.getStudentIdentity().getName());
            assertEquals("assessment-1",
                    context.getAssessment().getAssessmentId());
            assertEquals(2, context.getAssessment().getVersion());
            assertEquals("key-1", context.getAnswerKey().getId());
            assertEquals(1, context.getAnswerKey().getVersion());
            finishActivity(capture);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void correctedParticipantRequiresExplicitNewRevisionConfirmation() {
        saveAttempts();
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                OmrCaptureActivity.class.getName(),
                null,
                false
        );

        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> {
                button(
                        rowAt(activity, 0),
                        R.id.buttonOmrApplicationParticipantCorrect
                ).performClick();

                AlertDialog dialog = revisionDialog(activity);
                assertNotNull(dialog);
                assertTrue(dialog.isShowing());

                Button confirm = dialog.getButton(
                        DialogInterface.BUTTON_POSITIVE
                );
                assertNotNull(confirm);
                assertEquals(
                        "Corrigir novamente",
                        confirm.getText().toString()
                );
                assertTrue(monitor.getHits() == 0);
                confirm.performClick();
            });

            Activity capture = monitor.waitForActivityWithTimeout(5_000L);
            assertNotNull(capture);
            OmrAssessmentCorrectionContext context =
                    OmrCaptureActivity.extractAssessmentCorrectionContext(
                            capture.getIntent()
                    );
            assertNotNull(context);
            assertEquals("student-ana",
                    context.getParticipant().getStudentId());
            finishActivity(capture);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void nextPendingActionUsesFirstPendingFrozenPosition() {
        saveAttempts();
        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                OmrCaptureActivity.class.getName(),
                null,
                false
        );

        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrApplicationRosterNextPending
            ).performClick());

            Activity capture = monitor.waitForActivityWithTimeout(5_000L);
            assertNotNull(capture);
            OmrAssessmentCorrectionContext context =
                    OmrCaptureActivity.extractAssessmentCorrectionContext(
                            capture.getIntent()
                    );
            assertNotNull(context);
            assertEquals("student-bruno",
                    context.getParticipant().getStudentId());
            assertEquals(5, context.getParticipant().getRosterPosition());
            finishActivity(capture);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void completedRosterDisablesBatchCorrection() {
        saveAttempts();
        attemptRepository.save(new OmrAssessmentAttempt(
                "attempt-3", "application-1", "student-bruno", 1,
                "history-3", 4_000L
        ));

        try (ActivityScenario<OmrAssessmentApplicationRosterActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent("application-1"))) {
            scenario.onActivity(activity -> {
                Button next = button(
                        activity,
                        R.id.buttonOmrApplicationRosterNextPending
                );
                assertTrue(!next.isEnabled());
                assertEquals("Chamada concluída", next.getText().toString());
            });
        }
    }

    private Intent createActivityIntent(String applicationId) {
        return OmrAssessmentApplicationRosterActivity
                .createIsolatedStorageIntent(
                        applicationContext,
                        applicationId,
                        databaseName,
                        answerKeyNamespace
                );
    }

    private Context isolatedAnswerKeyContext() {
        Context base = applicationContext;
        String suffix = "." + answerKeyNamespace;
        return new ContextWrapper(base) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(
                    String name,
                    int mode
            ) {
                return base.getSharedPreferences(name + suffix, mode);
            }
        };
    }

    private void clearAnswerKeyStorage() {
        if (applicationContext == null || answerKeyNamespace == null) {
            return;
        }
        String suffix = "." + answerKeyNamespace;
        applicationContext.getSharedPreferences(
                "omr_answer_key_repository" + suffix,
                Context.MODE_PRIVATE
        ).edit().clear().commit();
        applicationContext.getSharedPreferences(
                "omr_active_answer_key" + suffix,
                Context.MODE_PRIVATE
        ).edit().clear().commit();
    }

    private static OmrAnswerKeyDefinition answerKey() {
        return new OmrAnswerKeyDefinition(
                "key-1", 1, "Gabarito exato",
                "omr-flex-ad-q001-r001", 3,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        )
                )
        );
    }

    private void seedApplication() {
        assessmentRepository.save(new OmrAssessmentDefinition(
                "assessment-1", 2, "Avaliação bimestral",
                "Matemática", "9º ano", "2º bimestre", 2026,
                "key-1", 1
        ));
        classRepository.save(new OmrClassGroup(
                "class-1", "9º A atual", "9º ano", 2026
        ));
        studentRepository.saveAll(Arrays.asList(
                new OmrStudentProfile(
                        "student-ana", "MAT-001", "Ana atual"
                ),
                new OmrStudentProfile(
                        "student-bruno", "MAT-002", "Bruno atual"
                )
        ));
        batchRepository.saveAllNew(
                Collections.singletonList(new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                )),
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-1", "student-bruno",
                                "MAT-002", "Bruno original",
                                "9º A original", 5
                        ),
                        new OmrAssessmentParticipant(
                                "application-1", "student-ana",
                                "MAT-001", "Ana original",
                                "9º A original", 1
                        )
                )
        );
    }

    private void saveAttempts() {
        attemptRepository.save(new OmrAssessmentAttempt(
                "attempt-1", "application-1", "student-ana", 1,
                "history-1", 2_000L
        ));
        attemptRepository.save(new OmrAssessmentAttempt(
                "attempt-2", "application-1", "student-ana", 2,
                "history-2", 3_000L
        ));
    }

    private static ListView listView(Activity activity) {
        return activity.findViewById(R.id.listOmrApplicationRoster);
    }

    private static View rowAt(Activity activity, int position) {
        ListView list = listView(activity);
        ListAdapter adapter = list.getAdapter();
        return adapter.getView(position, null, list);
    }

    private static Button button(Activity activity, int id) {
        return activity.findViewById(id);
    }

    private static Button button(View root, int id) {
        return root.findViewById(id);
    }

    private static void finishActivity(Activity activity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                activity::finish
        );
    }

    private static AlertDialog revisionDialog(Activity activity) {
        try {
            java.lang.reflect.Field field =
                    OmrAssessmentApplicationRosterActivity.class
                            .getDeclaredField("revisionDialog");
            field.setAccessible(true);
            return (AlertDialog) field.get(activity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Não foi possível acessar o diálogo de revisão.",
                    exception
            );
        }
    }

    private static void assertText(
            Activity activity, int id, String expected
    ) {
        TextView text = activity.findViewById(id);
        assertEquals(expected, text.getText().toString());
    }

    private static void assertRowText(
            View row, int id, String expected
    ) {
        TextView text = row.findViewById(id);
        assertEquals(expected, text.getText().toString());
    }

    private static void assertVisibility(
            Activity activity, int id, int expected
    ) {
        assertEquals(expected,
                activity.findViewById(id).getVisibility());
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
