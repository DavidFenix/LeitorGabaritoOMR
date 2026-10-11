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

import com.example.leitorgabaritoomr.R;
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
public final class OmrClassRosterActivityInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrClassGroup classGroup;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        databaseName = "omr_class_roster_activity_test_"
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
        classGroup = new OmrClassGroup(
                "class-9a", "9º ano A", "Ensino Fundamental", 2026
        );
        classRepository.save(classGroup);
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
        classGroup = null;
    }

    @Test
    public void intentFactoriesValidateRequiredArguments() {
        expectIllegalArgument(() ->
                OmrClassRosterActivity.createIntent(null, "class-9a")
        );
        expectIllegalArgument(() ->
                OmrClassRosterActivity.createIntent(
                        applicationContext, " "
                )
        );
        expectIllegalArgument(() ->
                OmrClassRosterActivity.createIsolatedStorageIntent(
                        applicationContext, "class-9a", " "
                )
        );
        assertNotNull(createActivityIntent());
    }

    @Test
    public void emptyRosterShowsClassMetadataAndActions() {
        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> {
                assertText(
                        activity,
                        R.id.textOmrClassRosterTitle,
                        "9º ano A"
                );
                assertText(
                        activity,
                        R.id.textOmrClassRosterMetadata,
                        "Ensino Fundamental • 2026"
                );
                assertVisibility(
                        activity,
                        R.id.containerOmrClassRosterEmpty,
                        View.VISIBLE
                );
                assertTrue(button(
                        activity, R.id.buttonOmrClassRosterEnroll
                ).isEnabled());
            });
        }
    }

    @Test
    public void storedEnrollmentsRenderInRosterOrder() {
        OmrStudentProfile ana = student(
                "student-1", "000001", "Ana Beatriz"
        );
        OmrStudentProfile bruno = student(
                "student-2", "000002", "Bruno Lima"
        );
        studentRepository.save(ana);
        studentRepository.save(bruno);
        enrollmentRepository.save(enrollment(bruno, 20));
        enrollmentRepository.save(enrollment(ana, 1));

        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(2, listView(activity).getAdapter().getCount());
                View first = rowAt(activity, 0);
                assertRowText(
                        first,
                        R.id.textOmrClassEnrollmentPosition,
                        "1"
                );
                assertRowText(
                        first,
                        R.id.textOmrClassEnrollmentName,
                        "Ana Beatriz"
                );
            });
        }
    }

    @Test
    public void manualDialogKeepsInvalidFormOpen() {
        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openEnrollDialog(scenario);
            saveEnrollmentDialog();

            onView(withId(
                    R.id.editOmrClassEnrollmentRegistrationDialog
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            assertTrue(enrollmentRepository.loadForClassGroup(
                    classGroup.getClassGroupId()
            ).isEmpty());

            onView(withText(
                    R.string.omr_class_enrollment_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void unknownRegistrationKeepsDialogOpenAndDoesNotWrite() {
        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openEnrollDialog(scenario);
            fillEnrollmentDialog("999999", "1");
            saveEnrollmentDialog();

            onView(withId(
                    R.id.editOmrClassEnrollmentRegistrationDialog
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            assertTrue(enrollmentRepository.loadForClassGroup(
                    classGroup.getClassGroupId()
            ).isEmpty());

            onView(withText(
                    R.string.omr_class_enrollment_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void manualDialogEnrollsExistingStudentByRegistration() {
        OmrStudentProfile student = student(
                "student-1", "000001", "Ana Beatriz"
        );
        studentRepository.save(student);

        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openEnrollDialog(scenario);
            fillEnrollmentDialog(" 000001 ", "12");
            saveEnrollmentDialog();

            OmrClassEnrollment stored = enrollmentRepository.findOrNull(
                    classGroup.getClassGroupId(), student.getStudentId()
            );
            assertNotNull(stored);
            assertEquals(12, stored.getRosterPosition());
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void occupiedPositionDoesNotOverwriteExistingStudent() {
        OmrStudentProfile ana = student(
                "student-1", "000001", "Ana"
        );
        OmrStudentProfile bruno = student(
                "student-2", "000002", "Bruno"
        );
        studentRepository.save(ana);
        studentRepository.save(bruno);
        enrollmentRepository.save(enrollment(ana, 1));

        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            openEnrollDialog(scenario);
            fillEnrollmentDialog("000002", "1");
            saveEnrollmentDialog();

            onView(withId(
                    R.id.editOmrClassEnrollmentRegistrationDialog
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            assertEquals(1, enrollmentRepository.loadForClassGroup(
                    classGroup.getClassGroupId()
            ).size());
            assertTrue(enrollmentRepository.findOrNull(
                    classGroup.getClassGroupId(), bruno.getStudentId()
            ) == null);

            onView(withText(
                    R.string.omr_class_enrollment_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void editActionLocksRegistrationAndUpdatesPosition() {
        OmrStudentProfile student = student(
                "student-1", "000001", "Ana"
        );
        studentRepository.save(student);
        enrollmentRepository.save(enrollment(student, 1));

        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(activity -> {
                Button edit = rowAt(activity, 0).findViewById(
                        R.id.buttonOmrClassEnrollmentEdit
                );
                edit.performClick();
            });

            onView(withId(
                    R.id.editOmrClassEnrollmentRegistrationDialog
            )).inRoot(isDialog()).check(matches(not(isEnabled())));
            onView(withId(
                    R.id.editOmrClassEnrollmentPositionDialog
            )).inRoot(isDialog()).perform(
                    replaceText("8"), closeSoftKeyboard()
            );
            saveEnrollmentDialog();

            assertEquals(8, enrollmentRepository.findOrNull(
                    classGroup.getClassGroupId(), student.getStudentId()
            ).getRosterPosition());
        }
    }

    @Test
    public void searchFiltersByNameAndShowsNoResults() {
        OmrStudentProfile ana = student(
                "student-1", "000001", "Ana Beatriz"
        );
        OmrStudentProfile bruno = student(
                "student-2", "000002", "Bruno Lima"
        );
        studentRepository.save(ana);
        studentRepository.save(bruno);
        enrollmentRepository.save(enrollment(ana, 1));
        enrollmentRepository.save(enrollment(bruno, 2));

        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            onView(withId(R.id.editOmrClassRosterSearch)).perform(
                    replaceText("bruno"), closeSoftKeyboard()
            );
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));

            onView(withId(R.id.editOmrClassRosterSearch)).perform(
                    replaceText("inexistente"), closeSoftKeyboard()
            );
            scenario.onActivity(activity -> assertVisibility(
                    activity,
                    R.id.containerOmrClassRosterNoResults,
                    View.VISIBLE
            ));
        }
    }

    @Test
    public void recreationKeepsPersistedRosterVisible() {
        OmrStudentProfile student = student(
                "student-1", "000001", "Ana"
        );
        studentRepository.save(student);
        enrollmentRepository.save(enrollment(student, 1));

        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(
                    1, listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void backButtonFinishesActivity() {
        try (ActivityScenario<OmrClassRosterActivity> scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {
            scenario.onActivity(activity -> button(
                    activity, R.id.buttonOmrClassRosterBack
            ).performClick());
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
        }
    }

    private android.content.Intent createActivityIntent() {
        return OmrClassRosterActivity.createIsolatedStorageIntent(
                applicationContext,
                classGroup.getClassGroupId(),
                databaseName
        );
    }

    private void openEnrollDialog(
            ActivityScenario<OmrClassRosterActivity> scenario
    ) {
        scenario.onActivity(activity -> button(
                activity, R.id.buttonOmrClassRosterEnroll
        ).performClick());
    }

    private void fillEnrollmentDialog(
            String registration,
            String position
    ) {
        onView(withId(
                R.id.editOmrClassEnrollmentRegistrationDialog
        )).inRoot(isDialog()).perform(replaceText(registration));
        onView(withId(
                R.id.editOmrClassEnrollmentPositionDialog
        )).inRoot(isDialog()).perform(
                replaceText(position), closeSoftKeyboard()
        );
    }

    private void saveEnrollmentDialog() {
        onView(withText(R.string.omr_class_enrollment_action_save))
                .inRoot(isDialog()).perform(click());
    }

    private OmrClassEnrollment enrollment(
            OmrStudentProfile student,
            int position
    ) {
        return new OmrClassEnrollment(
                classGroup.getClassGroupId(),
                student.getStudentId(),
                position
        );
    }

    private static OmrStudentProfile student(
            String id,
            String registration,
            String name
    ) {
        return new OmrStudentProfile(id, registration, name);
    }

    private static ListView listView(Activity activity) {
        return activity.findViewById(R.id.listOmrClassRoster);
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
