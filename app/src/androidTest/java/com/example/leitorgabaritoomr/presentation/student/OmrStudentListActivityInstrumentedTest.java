package com.example.leitorgabaritoomr.presentation.student;

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
import com.example.leitorgabaritoomr.application.student.OmrStudentCsvParser;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class OmrStudentListActivityInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private OmrSQLiteStudentRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();
        databaseName = "omr_student_list_activity_test_"
                + UUID.randomUUID()
                + ".db";
        applicationContext.deleteDatabase(databaseName);
        repository = new OmrSQLiteStudentRepository(
                applicationContext,
                databaseName
        );
    }

    @After
    public void tearDown() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
        applicationContext.deleteDatabase(databaseName);
        applicationContext = null;
        databaseName = null;
    }

    @Test
    public void intentFactoriesValidateRequiredArguments() {
        expectIllegalArgument(() ->
                OmrStudentListActivity.createIntent(null)
        );
        expectIllegalArgument(() ->
                OmrStudentListActivity.createIsolatedStorageIntent(
                        applicationContext,
                        " "
                )
        );

        assertNotNull(createActivityIntent());
    }

    @Test
    public void emptyRepositoryShowsActionsAndEmptyState() {
        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity -> {
                assertText(
                        activity,
                        R.id.textOmrStudentListCount,
                        "0 alunos cadastrados"
                );
                assertVisibility(
                        activity,
                        R.id.containerOmrStudentListEmpty,
                        View.VISIBLE
                );
                assertVisibility(
                        activity,
                        R.id.listOmrStudents,
                        View.GONE
                );
                assertTrue(button(
                        activity,
                        R.id.buttonOmrStudentListCreate
                ).isEnabled());
                assertTrue(button(
                        activity,
                        R.id.buttonOmrStudentListImportCsv
                ).isEnabled());
            });
        }
    }

    @Test
    public void storedStudentsRenderInRepositoryOrder() {
        repository.saveAll(Arrays.asList(
                student("student-2", "000002", "Bruno Lima"),
                student("student-1", "000001", "Ana Beatriz")
        ));

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity -> {
                ListView list = listView(activity);
                assertEquals(2, list.getAdapter().getCount());

                View firstRow = rowAt(activity, 0);
                assertRowText(
                        firstRow,
                        R.id.textOmrStudentItemName,
                        "Ana Beatriz"
                );
                assertRowText(
                        firstRow,
                        R.id.textOmrStudentItemRegistration,
                        "Matrícula: 000001"
                );
            });
        }
    }

    @Test
    public void manualDialogKeepsInvalidFormOpen() {
        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            openCreateDialog(scenario);

            onView(withText(
                    R.string.omr_student_registration_action_save
            )).inRoot(isDialog()).perform(click());

            onView(withId(
                    R.id.editOmrStudentRegistrationDialog
            )).inRoot(isDialog()).check(matches(isDisplayed()));

            assertTrue(repository.loadAll().isEmpty());

            onView(withText(
                    R.string.omr_student_registration_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void manualDialogCreatesNormalizedStudent() {
        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            openCreateDialog(scenario);
            fillRegistrationDialog(
                    " 000123a ",
                    "  Ana   Beatriz  "
            );
            saveRegistrationDialog();

            OmrStudentProfile stored =
                    repository.findByRegistrationOrNull("000123A");

            assertNotNull(stored);
            assertEquals("Ana Beatriz", stored.getName());

            scenario.onActivity(activity ->
                    assertEquals(
                            1,
                            listView(activity).getAdapter().getCount()
                    )
            );
        }
    }

    @Test
    public void repeatedManualRegistrationUpdatesWithoutDuplicating() {
        OmrStudentProfile original = student(
                "student-existing",
                "000123",
                "Nome anterior"
        );
        repository.save(original);

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            openCreateDialog(scenario);
            fillRegistrationDialog(
                    "000123",
                    "Ana atualizada"
            );
            saveRegistrationDialog();

            assertEquals(1, repository.loadAll().size());
            OmrStudentProfile updated =
                    repository.findByRegistrationOrNull("000123");
            assertNotNull(updated);
            assertEquals("student-existing", updated.getStudentId());
            assertEquals("Ana atualizada", updated.getName());
        }
    }

    @Test
    public void editActionLocksRegistrationAndUpdatesName() {
        repository.save(student(
                "student-existing",
                "000123",
                "Nome anterior"
        ));

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity -> {
                View row = rowAt(activity, 0);
                Button edit = row.findViewById(
                        R.id.buttonOmrStudentItemEdit
                );
                edit.performClick();
            });

            onView(withId(
                    R.id.editOmrStudentRegistrationDialog
            )).inRoot(isDialog()).check(matches(not(isEnabled())));
            onView(withId(
                    R.id.editOmrStudentRegistrationDialog
            )).inRoot(isDialog()).check(matches(withText("000123")));

            onView(withId(
                    R.id.editOmrStudentNameDialog
            )).inRoot(isDialog()).perform(
                    replaceText("Nome atualizado"),
                    closeSoftKeyboard()
            );
            saveRegistrationDialog();

            assertEquals(
                    "Nome atualizado",
                    repository.findByIdOrNull(
                            "student-existing"
                    ).getName()
            );
        }
    }

    @Test
    public void searchFiltersByNameAndShowsNoResults() {
        repository.saveAll(Arrays.asList(
                student("student-1", "000001", "Ana Beatriz"),
                student("student-2", "000002", "Bruno Lima")
        ));

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            onView(withId(R.id.editOmrStudentSearch)).perform(
                    replaceText("bruno"),
                    closeSoftKeyboard()
            );

            scenario.onActivity(activity -> {
                assertEquals(
                        1,
                        listView(activity).getAdapter().getCount()
                );
                assertText(
                        activity,
                        R.id.textOmrStudentListCount,
                        "1 de 2 alunos encontrados"
                );
            });

            onView(withId(R.id.editOmrStudentSearch)).perform(
                    replaceText("inexistente"),
                    closeSoftKeyboard()
            );

            scenario.onActivity(activity -> assertVisibility(
                    activity,
                    R.id.containerOmrStudentListNoResults,
                    View.VISIBLE
            ));
        }
    }

    @Test
    public void validCsvPreviewImportsBatchAfterConfirmation() {
        OmrStudentCsvParser.Preview preview =
                new OmrStudentCsvParser().parse(
                        "matricula,nome\n"
                                + "000001,Ana\n"
                                + "000002,Bruno"
                );

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity ->
                    activity.showCsvPreview(preview)
            );

            onView(withText(containsString("000001 — Ana")))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()));

            onView(withText(
                    R.string.omr_student_csv_action_import
            )).inRoot(isDialog()).perform(click());

            waitForStudentCount(2);

            scenario.onActivity(activity -> {
                assertEquals(
                        2,
                        listView(activity).getAdapter().getCount()
                );
                assertVisibility(
                        activity,
                        R.id.progressOmrStudentList,
                        View.GONE
                );
            });
        }
    }

    @Test
    public void invalidCsvPreviewCannotImportAnyValidSubset() {
        OmrStudentCsvParser.Preview preview =
                new OmrStudentCsvParser().parse(
                        "matricula,nome\n"
                                + "000001,Ana\n"
                                + ",Linha inválida"
                );

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity ->
                    activity.showCsvPreview(preview)
            );

            onView(withText(
                    R.string.omr_student_csv_preview_error_title
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(
                    R.string.omr_student_csv_action_close
            )).inRoot(isDialog()).perform(click());

            assertTrue(repository.loadAll().isEmpty());
        }
    }

    @Test
    public void recreationKeepsPersistedStudentsVisible() {
        repository.save(student(
                "student-1",
                "000001",
                "Ana"
        ));

        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.recreate();

            scenario.onActivity(activity -> assertEquals(
                    1,
                    listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void backButtonFinishesActivity() {
        try (ActivityScenario<OmrStudentListActivity> scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrStudentListBack
            ).performClick());

            Instrumentation.ActivityResult result =
                    scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED,
                    result.getResultCode());
        }
    }

    private android.content.Intent createActivityIntent() {
        return OmrStudentListActivity.createIsolatedStorageIntent(
                applicationContext,
                databaseName
        );
    }

    private void openCreateDialog(
            ActivityScenario<OmrStudentListActivity> scenario
    ) {
        scenario.onActivity(activity -> button(
                activity,
                R.id.buttonOmrStudentListCreate
        ).performClick());
    }

    private void fillRegistrationDialog(
            String registration,
            String name
    ) {
        onView(withId(
                R.id.editOmrStudentRegistrationDialog
        )).inRoot(isDialog()).perform(
                replaceText(registration)
        );
        onView(withId(
                R.id.editOmrStudentNameDialog
        )).inRoot(isDialog()).perform(
                replaceText(name),
                closeSoftKeyboard()
        );
    }

    private void saveRegistrationDialog() {
        onView(withText(
                R.string.omr_student_registration_action_save
        )).inRoot(isDialog()).perform(click());
    }

    private void waitForStudentCount(int expectedCount) {
        long deadline = System.currentTimeMillis() + 5000L;

        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation()
                    .waitForIdleSync();

            if (repository.loadAll().size() == expectedCount) {
                InstrumentationRegistry.getInstrumentation()
                        .waitForIdleSync();
                return;
            }

            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("Espera da importação interrompida.");
            }
        }

        fail("A importação não atingiu "
                + expectedCount + " alunos.");
    }

    private static OmrStudentProfile student(
            String id,
            String registration,
            String name
    ) {
        return new OmrStudentProfile(id, registration, name);
    }

    private static ListView listView(Activity activity) {
        return activity.findViewById(R.id.listOmrStudents);
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
        assertEquals(
                expected,
                activity.findViewById(viewId).getVisibility()
        );
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
