package com.example.leitorgabaritoomr.presentation.assessment;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationCsvParser;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationBatchRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassEnrollmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentApplicationListActivityInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private String answerKeyNamespace;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrSQLiteAssessmentApplicationRepository applicationRepository;
    private OmrSQLiteAssessmentParticipantRepository participantRepository;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrSQLiteAssessmentApplicationBatchRepository batchRepository;
    private OmrSharedPreferencesAnswerKeyRepository answerKeyRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        databaseName = "omr_application_list_activity_"
                + UUID.randomUUID() + ".db";
        answerKeyNamespace = "application_list_" + UUID.randomUUID();
        applicationContext.deleteDatabase(databaseName);
        assessmentRepository = new OmrSQLiteAssessmentRepository(
                applicationContext, databaseName
        );
        classRepository = new OmrSQLiteClassGroupRepository(
                applicationContext, databaseName
        );
        enrollmentRepository = new OmrSQLiteClassEnrollmentRepository(
                applicationContext, databaseName
        );
        studentRepository = new OmrSQLiteStudentRepository(
                applicationContext, databaseName
        );
        applicationRepository =
                new OmrSQLiteAssessmentApplicationRepository(
                        applicationContext, databaseName
                );
        participantRepository =
                new OmrSQLiteAssessmentParticipantRepository(
                        applicationContext, databaseName
                );
        attemptRepository = new OmrSQLiteAssessmentAttemptRepository(
                applicationContext, databaseName
        );
        batchRepository =
                new OmrSQLiteAssessmentApplicationBatchRepository(
                        applicationContext, databaseName
                );
        answerKeyRepository = new OmrSharedPreferencesAnswerKeyRepository(
                answerKeyContext()
        );
    }

    @After
    public void tearDown() {
        if (answerKeyRepository != null) {
            answerKeyRepository.delete("key-1", 1);
            answerKeyRepository.clearActive();
            answerKeyRepository = null;
        }
        if (batchRepository != null) {
            batchRepository.close();
        }
        if (attemptRepository != null) {
            attemptRepository.close();
        }
        if (participantRepository != null) {
            participantRepository.close();
        }
        if (applicationRepository != null) {
            applicationRepository.close();
        }
        if (studentRepository != null) {
            studentRepository.close();
        }
        if (enrollmentRepository != null) {
            enrollmentRepository.close();
        }
        if (classRepository != null) {
            classRepository.close();
        }
        if (assessmentRepository != null) {
            assessmentRepository.close();
        }
        applicationContext.deleteDatabase(databaseName);
    }

    @Test
    public void intentFactoriesValidateRequiredArguments() {
        expectIllegalArgument(() ->
                OmrAssessmentApplicationListActivity.createIntent(null));
        expectIllegalArgument(() ->
                OmrAssessmentApplicationListActivity
                        .createIsolatedStorageIntent(
                                applicationContext, " "
                        ));
        assertNotNull(createActivityIntent());
    }

    @Test
    public void emptyRepositoryShowsActionsAndEmptyState() {
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.onActivity(activity -> {
                assertText(activity,
                        R.id.textOmrAssessmentApplicationListCount,
                        "0 aplicações cadastradas");
                assertVisibility(activity,
                        R.id.containerOmrAssessmentApplicationListEmpty,
                        View.VISIBLE);
                assertTrue(button(activity,
                        R.id.buttonOmrAssessmentApplicationListCreate)
                        .isEnabled());
                assertTrue(button(activity,
                        R.id.buttonOmrAssessmentApplicationListImportCsv)
                        .isEnabled());
            });
        }
    }

    @Test
    public void storedApplicationShowsFrozenSnapshotAndProgress() {
        seedSchoolData();
        saveApplicationSnapshot();
        attemptRepository.save(new OmrAssessmentAttempt(
                "attempt-1", "application-1", "student-1", 1,
                "history-1", 3_000L
        ));

        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(1, listView(activity).getAdapter().getCount());
                View row = rowAt(activity, 0);
                assertRowText(row,
                        R.id.textOmrAssessmentApplicationItemTitle,
                        "Avaliação bimestral");
                assertRowText(row,
                        R.id.textOmrAssessmentApplicationItemClass,
                        "Turma congelada: 9º A original");
                assertRowText(row,
                        R.id.textOmrAssessmentApplicationItemProgress,
                        "1 de 2 corrigidos • 1 pendente");
            });
        }
    }

    @Test
    public void searchFiltersStoredApplications() {
        seedSchoolData();
        saveApplicationSnapshot();
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            onView(withId(
                    R.id.editOmrAssessmentApplicationSearch
            )).perform(replaceText("9º A"), closeSoftKeyboard());
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
            onView(withId(
                    R.id.editOmrAssessmentApplicationSearch
            )).perform(replaceText("inexistente"), closeSoftKeyboard());
            scenario.onActivity(activity -> assertVisibility(
                    activity,
                    R.id.containerOmrAssessmentApplicationListNoResults,
                    View.VISIBLE
            ));
        }
    }

    @Test
    public void manualDialogListsEveryVersionAndCompatibleClassesOnly() {
        seedSchoolData();
        assessmentRepository.save(new OmrAssessmentDefinition(
                "assessment-1", 1, "Avaliação bimestral",
                "Matemática", "9º ano", "1º bimestre", 2026,
                "key-1", 1
        ));
        OmrClassGroup oldClass = new OmrClassGroup(
                "class-old", "9º antigo", "9º ano", 2025
        );
        classRepository.save(oldClass);
        OmrStudentProfile oldStudent = new OmrStudentProfile(
                "student-old", "OLD-1", "Aluno antigo"
        );
        studentRepository.save(oldStudent);
        enrollmentRepository.save(new OmrClassEnrollment(
                oldClass.getClassGroupId(), oldStudent.getStudentId(), 1
        ));

        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrAssessmentApplicationListCreate
            ).performClick());

            onView(withId(
                    R.id.spinnerOmrAssessmentApplicationAssessment
            )).inRoot(isDialog()).check((view, noViewFoundException) ->
                    assertEquals(2, ((Spinner) view)
                            .getAdapter().getCount()));
            onView(withId(
                    R.id.spinnerOmrAssessmentApplicationClass
            )).inRoot(isDialog()).check((view, noViewFoundException) ->
                    assertEquals(1, ((Spinner) view)
                            .getAdapter().getCount()));
            onView(withText(
                    R.string
                            .omr_assessment_application_registration_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void manualRegistrationPersistsApplicationAndFrozenRoster() {
        seedSchoolData();
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrAssessmentApplicationListCreate
            ).performClick());
            onView(withText(
                    R.string
                            .omr_assessment_application_registration_action_save
            )).inRoot(isDialog()).perform(click());

            assertEquals(1, applicationRepository.loadAll().size());
            OmrAssessmentApplication application =
                    applicationRepository.loadAll().get(0);
            assertTrue(application.hasFrozenStructure());
            assertTrue(application.getFrozenStructureOrNull()
                    .isLegacySingleBlock());
            assertEquals(2, participantRepository.loadForApplication(
                    application.getApplicationId()
            ).size());
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void invalidCsvPreviewExplainsErrorsWithoutImportAction() {
        OmrAssessmentApplicationCsvParser.Preview preview =
                new OmrAssessmentApplicationCsvParser().parse(
                        "codigo_aplicacao;prova\nAPP-1;Prova\n"
                );
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.onActivity(activity -> activity.showCsvPreview(preview));
            onView(withText(
                    R.string
                            .omr_assessment_application_csv_preview_error_title
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(containsString("inconsistências")))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(
                    R.string.omr_assessment_application_csv_action_close
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void validCsvPreviewImportsApplicationInBackground() {
        seedSchoolData();
        OmrAssessmentApplicationCsvParser.Preview preview =
                new OmrAssessmentApplicationCsvParser().parse(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP-CSV;assessment-1;2;class-1\n"
                );
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.onActivity(activity -> activity.showCsvPreview(preview));
            onView(withText(
                    R.string.omr_assessment_application_csv_action_import
            )).inRoot(isDialog()).perform(click());
            waitUntil(() -> applicationRepository.loadAll().size() == 1);
            OmrAssessmentApplication application =
                    applicationRepository.loadAll().get(0);
            assertEquals("application-csv:APP-CSV",
                    application.getApplicationId());
            assertEquals(2, participantRepository.loadForApplication(
                    application.getApplicationId()
            ).size());
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void recreationKeepsPersistedApplicationVisible() {
        seedSchoolData();
        saveApplicationSnapshot();
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launch(
                createActivityIntent())) {
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void backButtonFinishesActivity() {
        try (ActivityScenario<OmrAssessmentApplicationListActivity>
                     scenario = ActivityScenario.launchActivityForResult(
                createActivityIntent())) {
            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrAssessmentApplicationListBack
            ).performClick());
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
        }
    }

    private android.content.Intent createActivityIntent() {
        return OmrAssessmentApplicationListActivity
                .createIsolatedStorageIntent(
                        applicationContext,
                        databaseName,
                        answerKeyNamespace
                );
    }

    private void seedSchoolData() {
        assessmentRepository.save(new OmrAssessmentDefinition(
                "assessment-1", 2, "Avaliação bimestral",
                "Matemática", "9º ano", "2º bimestre", 2026,
                "key-1", 1
        ));
        answerKeyRepository.save(new OmrAnswerKeyDefinition(
                "key-1", 1, "Gabarito", "layout-1", 1,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-1", "A", 1.0
                        )
                )
        ));
        classRepository.save(new OmrClassGroup(
                "class-1", "9º A atual", "9º ano", 2026
        ));
        studentRepository.saveAll(Arrays.asList(
                new OmrStudentProfile(
                        "student-1", "MAT-001", "Ana"
                ),
                new OmrStudentProfile(
                        "student-2", "MAT-002", "Bruno"
                )
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-1", "student-1", 1
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-1", "student-2", 2
        ));
    }

    private void saveApplicationSnapshot() {
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                );
        batchRepository.saveAllNew(
                Collections.singletonList(application),
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-1", "student-1", "MAT-001",
                                "Ana", "9º A original", 1
                        ),
                        new OmrAssessmentParticipant(
                                "application-1", "student-2", "MAT-002",
                                "Bruno", "9º A original", 2
                        )
                )
        );
    }

    private Context answerKeyContext() {
        Context baseContext = applicationContext;
        String suffix = "." + answerKeyNamespace;
        return new ContextWrapper(baseContext) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(
                    String name,
                    int mode
            ) {
                return baseContext.getSharedPreferences(
                        name + suffix,
                        mode
                );
            }
        };
    }

    private static ListView listView(Activity activity) {
        return activity.findViewById(
                R.id.listOmrAssessmentApplications
        );
    }

    private static View rowAt(Activity activity, int position) {
        ListView list = listView(activity);
        ListAdapter adapter = list.getAdapter();
        return adapter.getView(position, null, list);
    }

    private static Button button(Activity activity, int id) {
        return activity.findViewById(id);
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

    private static void waitUntil(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 5_000L;
        while (!condition.isSatisfied()
                && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L);
        }
        assertTrue("A operação assíncrona não terminou.",
                condition.isSatisfied());
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private interface Condition {
        boolean isSatisfied();
    }
}
