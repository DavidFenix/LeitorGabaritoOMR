package com.example.leitorgabaritoomr.presentation.classroom;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.classroom.OmrClassRosterCsvParser;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassEnrollmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class OmrClassListActivityInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;
    private OmrSQLiteStudentRepository studentRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        databaseName = "omr_class_list_activity_test_"
                + UUID.randomUUID() + ".db";
        applicationContext.deleteDatabase(databaseName);
        classRepository = new OmrSQLiteClassGroupRepository(
                applicationContext, databaseName
        );
        enrollmentRepository = new OmrSQLiteClassEnrollmentRepository(
                applicationContext, databaseName
        );
        studentRepository = new OmrSQLiteStudentRepository(
                applicationContext, databaseName
        );
    }

    @After
    public void tearDown() {
        if (studentRepository != null) {
            studentRepository.close();
            studentRepository = null;
        }
        if (enrollmentRepository != null) {
            enrollmentRepository.close();
            enrollmentRepository = null;
        }
        if (classRepository != null) {
            classRepository.close();
            classRepository = null;
        }
        applicationContext.deleteDatabase(databaseName);
        applicationContext = null;
        databaseName = null;
    }

    @Test
    public void intentFactoriesValidateRequiredArguments() {
        expectIllegalArgument(() -> OmrClassListActivity.createIntent(null));
        expectIllegalArgument(() ->
                OmrClassListActivity.createIsolatedStorageIntent(
                        applicationContext, " "
                )
        );
        assertNotNull(createActivityIntent());
    }

    @Test
    public void emptyRepositoryShowsActionsAndEmptyState() {
        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> {
                assertText(
                        activity,
                        R.id.textOmrClassListCount,
                        "0 turmas cadastradas"
                );
                assertVisibility(
                        activity,
                        R.id.containerOmrClassListEmpty,
                        View.VISIBLE
                );
                assertTrue(button(
                        activity, R.id.buttonOmrClassListCreate
                ).isEnabled());
                assertTrue(button(
                        activity, R.id.buttonOmrClassListImportCsv
                ).isEnabled());
            });
        }
    }

    @Test
    public void storedClassesRenderWithRosterCounts() {
        OmrClassGroup classGroup = classGroup(
                "class-9a", "9º ano A", "Ensino Fundamental", 2026
        );
        OmrStudentProfile student = student(
                "student-1", "000001", "Ana"
        );
        classRepository.save(classGroup);
        studentRepository.save(student);
        enrollmentRepository.save(new OmrClassEnrollment(
                classGroup.getClassGroupId(), student.getStudentId(), 1
        ));

        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(1, listView(activity).getAdapter().getCount());
                View row = rowAt(activity, 0);
                assertRowText(row, R.id.textOmrClassItemName, "9º ano A");
                assertRowText(
                        row,
                        R.id.textOmrClassItemStudentCount,
                        "1 aluno na lista"
                );
            });
        }
    }

    @Test
    public void manualDialogKeepsInvalidFormOpen() {
        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openCreateDialog(scenario);
            onView(withText(
                    R.string.omr_class_registration_action_save
            )).inRoot(isDialog()).perform(click());

            onView(withId(R.id.editOmrClassNameDialog))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()));
            assertTrue(classRepository.loadAll().isEmpty());

            onView(withText(
                    R.string.omr_class_registration_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void manualDialogCreatesNormalizedClass() {
        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openCreateDialog(scenario);
            fillClassDialog(
                    "  9º   ano A  ",
                    "  Ensino   Fundamental  ",
                    "2026"
            );
            saveClassDialog();

            assertEquals(1, classRepository.loadAll().size());
            OmrClassGroup stored = classRepository.loadAll().get(0);
            assertEquals("9º ano A", stored.getName());
            assertEquals("Ensino Fundamental", stored.getGradeLevel());
            assertEquals(2026, stored.getSchoolYear());
        }
    }

    @Test
    public void repeatedNaturalClassUpdatesWithoutDuplicating() {
        classRepository.save(classGroup(
                "class-existing",
                "9º ano A",
                "Etapa anterior",
                2026
        ));

        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openCreateDialog(scenario);
            fillClassDialog("9º ANO A", "Ensino Fundamental", "2026");
            saveClassDialog();

            assertEquals(1, classRepository.loadAll().size());
            OmrClassGroup updated = classRepository.loadAll().get(0);
            assertEquals("class-existing", updated.getClassGroupId());
            assertEquals("Ensino Fundamental", updated.getGradeLevel());
        }
    }

    @Test
    public void editActionLocksYearAndUpdatesClass() {
        classRepository.save(classGroup(
                "class-existing", "9º ano A", "Etapa anterior", 2026
        ));

        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> {
                Button edit = rowAt(activity, 0).findViewById(
                        R.id.buttonOmrClassItemEdit
                );
                edit.performClick();
            });

            onView(withId(R.id.editOmrClassYearDialog))
                    .inRoot(isDialog())
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.editOmrClassGradeDialog))
                    .inRoot(isDialog())
                    .perform(
                            replaceText("Ensino Fundamental"),
                            closeSoftKeyboard()
                    );
            saveClassDialog();

            OmrClassGroup updated = classRepository.findByIdOrNull(
                    "class-existing"
            );
            assertNotNull(updated);
            assertEquals("Ensino Fundamental", updated.getGradeLevel());
            assertEquals(2026, updated.getSchoolYear());
        }
    }

    @Test
    public void searchFiltersByGradeAndShowsNoResults() {
        classRepository.save(classGroup(
                "class-9a", "9º ano A", "Ensino Fundamental", 2026
        ));
        classRepository.save(classGroup(
                "class-1a", "1ª série A", "Ensino Médio", 2026
        ));

        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            onView(withId(R.id.editOmrClassSearch)).perform(
                    replaceText("medio"),
                    closeSoftKeyboard()
            );
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));

            onView(withId(R.id.editOmrClassSearch)).perform(
                    replaceText("inexistente"),
                    closeSoftKeyboard()
            );
            scenario.onActivity(activity -> assertVisibility(
                    activity,
                    R.id.containerOmrClassListNoResults,
                    View.VISIBLE
            ));
        }
    }

    @Test
    public void validCsvPreviewImportsClassesStudentsAndEnrollments() {
        OmrClassRosterCsvParser.Preview preview =
                new OmrClassRosterCsvParser().parse(
                        "turma,etapa,ano_letivo,matricula,nome,posicao\n"
                                + "9A,Fundamental,2026,000001,Ana,1\n"
                                + "9A,Fundamental,2026,000002,Bruno,2"
                );

        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> activity.showCsvPreview(preview));

            onView(withText(containsString("9A • posição 1")))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.omr_class_csv_action_import))
                    .inRoot(isDialog())
                    .perform(click());

            waitForClassCount(1);
            OmrClassGroup imported = classRepository.loadAll().get(0);
            assertEquals(2, enrollmentRepository.loadForClassGroup(
                    imported.getClassGroupId()
            ).size());
            assertEquals(2, studentRepository.loadAll().size());
        }
    }

    @Test
    public void invalidCsvPreviewCannotImportValidSubset() {
        OmrClassRosterCsvParser.Preview preview =
                new OmrClassRosterCsvParser().parse(
                        "turma,etapa,ano_letivo,matricula,nome,posicao\n"
                                + "9A,Fundamental,2026,000001,Ana,1\n"
                                + "9A,Fundamental,2026,,Linha invalida,2"
                );

        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> activity.showCsvPreview(preview));
            onView(withText(R.string.omr_class_csv_preview_error_title))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.omr_class_csv_action_close))
                    .inRoot(isDialog())
                    .perform(click());
            assertTrue(classRepository.loadAll().isEmpty());
            assertTrue(studentRepository.loadAll().isEmpty());
        }
    }

    @Test
    public void recreationKeepsPersistedClassesVisible() {
        classRepository.save(classGroup(
                "class-9a", "9º ano A", "Ensino Fundamental", 2026
        ));
        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void backButtonFinishesActivity() {
        try (ActivityScenario<OmrClassListActivity> scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {
            scenario.onActivity(activity -> button(
                    activity, R.id.buttonOmrClassListBack
            ).performClick());
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
        }
    }

    private android.content.Intent createActivityIntent() {
        return OmrClassListActivity.createIsolatedStorageIntent(
                applicationContext, databaseName
        );
    }

    private void openCreateDialog(
            ActivityScenario<OmrClassListActivity> scenario
    ) {
        scenario.onActivity(activity -> button(
                activity, R.id.buttonOmrClassListCreate
        ).performClick());
    }

    private void fillClassDialog(String name, String grade, String year) {
        onView(withId(R.id.editOmrClassNameDialog))
                .inRoot(isDialog()).perform(replaceText(name));
        onView(withId(R.id.editOmrClassGradeDialog))
                .inRoot(isDialog()).perform(replaceText(grade));
        onView(withId(R.id.editOmrClassYearDialog))
                .inRoot(isDialog()).perform(
                        replaceText(year), closeSoftKeyboard()
                );
    }

    private void saveClassDialog() {
        onView(withText(R.string.omr_class_registration_action_save))
                .inRoot(isDialog()).perform(click());
    }

    private void waitForClassCount(int expectedCount) {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (classRepository.loadAll().size() == expectedCount) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("Espera da importacao interrompida.");
            }
        }
        fail("A importacao nao atingiu " + expectedCount + " turmas.");
    }

    private static OmrClassGroup classGroup(
            String id,
            String name,
            String grade,
            int year
    ) {
        return new OmrClassGroup(id, name, grade, year);
    }

    private static OmrStudentProfile student(
            String id,
            String registration,
            String name
    ) {
        return new OmrStudentProfile(id, registration, name);
    }

    private static ListView listView(Activity activity) {
        return activity.findViewById(R.id.listOmrClasses);
    }

    private static View rowAt(Activity activity, int position) {
        ListView list = listView(activity);
        ListAdapter adapter = list.getAdapter();
        return adapter.getView(position, null, list);
    }

    private static Button button(Activity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    private static void assertText(
            Activity activity,
            int viewId,
            String expected
    ) {
        TextView view = activity.findViewById(viewId);
        assertEquals(expected, view.getText().toString());
    }

    private static void assertRowText(
            View row,
            int viewId,
            String expected
    ) {
        TextView view = row.findViewById(viewId);
        assertEquals(expected, view.getText().toString());
    }

    private static void assertVisibility(
            Activity activity,
            int viewId,
            int expected
    ) {
        assertEquals(expected, activity.findViewById(viewId).getVisibility());
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
