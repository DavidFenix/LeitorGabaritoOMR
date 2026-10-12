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
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCsvParser;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentListActivityInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private String answerKeyNamespace;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSharedPreferencesAnswerKeyRepository answerKeyRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        String unique = UUID.randomUUID().toString();
        databaseName = "omr_assessment_list_activity_"
                + unique + ".db";
        answerKeyNamespace = "assessment_list_" + unique;
        applicationContext.deleteDatabase(databaseName);
        clearAnswerKeyStorage();

        assessmentRepository = new OmrSQLiteAssessmentRepository(
                applicationContext,
                databaseName
        );
        answerKeyRepository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        isolatedAnswerKeyContext()
                );
    }

    @After
    public void tearDown() {
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
        clearAnswerKeyStorage();
        applicationContext.deleteDatabase(databaseName);
        answerKeyRepository = null;
        applicationContext = null;
        databaseName = null;
        answerKeyNamespace = null;
    }

    @Test
    public void intentFactoriesValidateRequiredArguments() {
        expectIllegalArgument(() ->
                OmrAssessmentListActivity.createIntent(null)
        );
        expectIllegalArgument(() ->
                OmrAssessmentListActivity.createIsolatedStorageIntent(
                        applicationContext,
                        " ",
                        answerKeyNamespace
                )
        );
        expectIllegalArgument(() ->
                OmrAssessmentListActivity.createIsolatedStorageIntent(
                        applicationContext,
                        databaseName,
                        " "
                )
        );
        assertNotNull(createActivityIntent());
    }

    @Test
    public void emptyRepositoryShowsActionsAndEmptyState() {
        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity -> {
                assertText(
                        activity,
                        R.id.textOmrAssessmentListCount,
                        "0 provas cadastradas"
                );
                assertVisibility(
                        activity,
                        R.id.containerOmrAssessmentListEmpty,
                        View.VISIBLE
                );
                assertVisibility(
                        activity,
                        R.id.listOmrAssessments,
                        View.GONE
                );
                assertTrue(button(
                        activity,
                        R.id.buttonOmrAssessmentListCreate
                ).isEnabled());
                assertTrue(button(
                        activity,
                        R.id.buttonOmrAssessmentListImportCsv
                ).isEnabled());
            });
        }
    }

    @Test
    public void listShowsOnlyLatestVersionOfEachAssessment() {
        assessmentRepository.save(assessment(
                "assessment-math", 1, "Versão antiga", "key-1", 1
        ));
        assessmentRepository.save(assessment(
                "assessment-math", 2, "Versão atual", "key-1", 2
        ));
        assessmentRepository.save(assessment(
                "assessment-science", 1, "Ciências", "key-2", 1
        ));

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity -> {
                assertEquals(2, listView(activity).getAdapter().getCount());
                assertText(
                        activity,
                        R.id.textOmrAssessmentListCount,
                        "2 provas cadastradas"
                );
                View mathRow = findRowByTitle(activity, "Versão atual");
                assertRowText(
                        mathRow,
                        R.id.textOmrAssessmentItemPeriodVersion,
                        "2º bimestre • versão 2"
                );
            });
        }
    }

    @Test
    public void createActionExplainsThatAnswerKeyIsRequired() {
        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            openCreateDialog(scenario);

            onView(withText(
                    R.string
                            .omr_assessment_registration_no_answer_keys_title
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(
                    R.string
                            .omr_assessment_registration_no_answer_keys_close
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void manualDialogKeepsInvalidFormOpen() {
        seedAnswerKeys();

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            openCreateDialog(scenario);
            saveRegistrationDialog();

            onView(withId(
                    R.id.editOmrAssessmentTitleDialog
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            assertTrue(assessmentRepository.loadAll().isEmpty());

            onView(withText(
                    R.string.omr_assessment_registration_action_cancel
            )).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void manualDialogCreatesNormalizedVersionOne() {
        seedAnswerKeys();

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            openCreateDialog(scenario);
            fillRegistrationDialog(
                    "  Avaliação   bimestral  ",
                    " Matemática ",
                    " 9º   ano ",
                    " 2º   bimestre ",
                    "2026"
            );
            saveRegistrationDialog();

            assertEquals(1, assessmentRepository.loadAll().size());
            OmrAssessmentDefinition stored =
                    assessmentRepository.loadAll().get(0);
            assertEquals(1, stored.getVersion());
            assertEquals("Avaliação bimestral", stored.getTitle());
            assertEquals("9º ano", stored.getGradeLevel());
            assertEquals("key-2", stored.getAnswerKeyId());
            assertEquals(1, stored.getAnswerKeyVersion());

            scenario.onActivity(activity -> assertEquals(
                    1,
                    listView(activity).getAdapter().getCount()
            ));
        }
    }

    @Test
    public void revisionPrefillsFieldsAndPreservesPreviousVersion() {
        seedAnswerKeys();
        assessmentRepository.save(assessment(
                "assessment-math",
                1,
                "Avaliação original",
                "key-1",
                1
        ));

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity -> {
                View row = rowAt(activity, 0);
                button(
                        row,
                        R.id.buttonOmrAssessmentItemNewVersion
                ).performClick();
            });

            onView(withText("A próxima publicação será a versão 2."))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withId(R.id.editOmrAssessmentTitleDialog))
                    .inRoot(isDialog())
                    .check(matches(withText("Avaliação original")))
                    .perform(replaceText("Avaliação revisada"));
            saveRegistrationDialog();

            assertEquals(2, assessmentRepository.loadAll().size());
            assertNotNull(assessmentRepository.findOrNull(
                    "assessment-math",
                    1
            ));
            OmrAssessmentDefinition latest = assessmentRepository
                    .findLatestVersionOrNull("assessment-math");
            assertNotNull(latest);
            assertEquals(2, latest.getVersion());
            assertEquals("Avaliação revisada", latest.getTitle());
        }
    }

    @Test
    public void searchFiltersByMetadataAndShowsNoResults() {
        assessmentRepository.save(assessment(
                "assessment-math", 1, "Matemática", "key-1", 1
        ));
        assessmentRepository.save(new OmrAssessmentDefinition(
                "assessment-science", 1, "Diagnóstico",
                "Ciências", "8º ano", "Inicial", 2025,
                "key-2", 1
        ));

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            onView(withId(R.id.editOmrAssessmentSearch)).perform(
                    replaceText("ciências"),
                    closeSoftKeyboard()
            );
            scenario.onActivity(activity -> {
                assertEquals(1, listView(activity).getAdapter().getCount());
                assertText(
                        activity,
                        R.id.textOmrAssessmentListCount,
                        "1 de 2 provas encontradas"
                );
            });

            onView(withId(R.id.editOmrAssessmentSearch)).perform(
                    replaceText("inexistente"),
                    closeSoftKeyboard()
            );
            scenario.onActivity(activity -> assertVisibility(
                    activity,
                    R.id.containerOmrAssessmentListNoResults,
                    View.VISIBLE
            ));
        }
    }

    @Test
    public void validCsvPreviewImportsVersionsAfterConfirmation() {
        seedAnswerKeys();
        OmrAssessmentCsvParser.Preview preview = csvPreview();

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity ->
                    activity.showCsvPreview(preview)
            );
            onView(withText(containsString(
                    "MAT-B2 v1 — Prova B2"
            ))).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(
                    R.string.omr_assessment_csv_action_import
            )).inRoot(isDialog()).perform(click());

            waitForAssessmentCount(2);
            scenario.onActivity(activity -> {
                assertEquals(1, listView(activity).getAdapter().getCount());
                assertVisibility(
                        activity,
                        R.id.progressOmrAssessmentList,
                        View.GONE
                );
            });
            OmrAssessmentDefinition latest = assessmentRepository
                    .findLatestVersionOrNull("assessment-csv:MAT-B2");
            assertNotNull(latest);
            assertEquals(2, latest.getVersion());
        }
    }

    @Test
    public void identicalCsvReimportIsIdempotent() {
        seedAnswerKeys();
        OmrAssessmentCsvParser.Preview preview = csvPreview();

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            importPreview(scenario, preview);
            waitForAssessmentCount(2);
            importPreview(scenario, preview);
            waitForImportToFinish(scenario);

            assertEquals(2, assessmentRepository.loadAll().size());
        }
    }

    @Test
    public void invalidCsvPreviewImportsNoValidSubset() {
        seedAnswerKeys();
        OmrAssessmentCsvParser.Preview preview =
                new OmrAssessmentCsvParser().parse(
                        "codigo_prova,versao_prova,titulo,disciplina,"
                                + "etapa,periodo,ano_letivo,gabarito,"
                                + "versao_gabarito\n"
                                + "MAT,1,Prova,Matemática,9º ano,B2,"
                                + "2026,key-1,1\n"
                                + ",2,Inválida,Matemática,9º ano,B2,"
                                + "2026,key-1,1"
                );

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {

            scenario.onActivity(activity ->
                    activity.showCsvPreview(preview)
            );
            onView(withText(
                    R.string.omr_assessment_csv_preview_error_title
            )).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(
                    R.string.omr_assessment_csv_action_close
            )).inRoot(isDialog()).perform(click());

            assertTrue(assessmentRepository.loadAll().isEmpty());
        }
    }

    @Test
    public void recreationKeepsLatestAssessmentsVisible() {
        assessmentRepository.save(assessment(
                "assessment-math", 1, "Avaliação", "key-1", 1
        ));

        try (ActivityScenario<OmrAssessmentListActivity> scenario =
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
        try (ActivityScenario<OmrAssessmentListActivity> scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(activity -> button(
                    activity,
                    R.id.buttonOmrAssessmentListBack
            ).performClick());

            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );
        }
    }

    private android.content.Intent createActivityIntent() {
        return OmrAssessmentListActivity.createIsolatedStorageIntent(
                applicationContext,
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
        if (applicationContext == null
                || answerKeyNamespace == null) {
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

    private void seedAnswerKeys() {
        answerKeyRepository.save(answerKey(
                "key-1", 1, "Gabarito Matemática"
        ));
        answerKeyRepository.save(answerKey(
                "key-2", 1, "Gabarito Ciências"
        ));
    }

    private void openCreateDialog(
            ActivityScenario<OmrAssessmentListActivity> scenario
    ) {
        scenario.onActivity(activity -> button(
                activity,
                R.id.buttonOmrAssessmentListCreate
        ).performClick());
    }

    private void fillRegistrationDialog(
            String title,
            String subject,
            String grade,
            String period,
            String year
    ) {
        replaceDialogText(R.id.editOmrAssessmentTitleDialog, title);
        replaceDialogText(R.id.editOmrAssessmentSubjectDialog, subject);
        replaceDialogText(R.id.editOmrAssessmentGradeDialog, grade);
        replaceDialogText(R.id.editOmrAssessmentPeriodDialog, period);
        onView(withId(R.id.editOmrAssessmentYearDialog))
                .inRoot(isDialog())
                .perform(replaceText(year), closeSoftKeyboard());
    }

    private static void replaceDialogText(int id, String value) {
        onView(withId(id)).inRoot(isDialog()).perform(replaceText(value));
    }

    private static void saveRegistrationDialog() {
        onView(withText(
                R.string.omr_assessment_registration_action_save
        )).inRoot(isDialog()).perform(click());
    }

    private void importPreview(
            ActivityScenario<OmrAssessmentListActivity> scenario,
            OmrAssessmentCsvParser.Preview preview
    ) {
        scenario.onActivity(activity ->
                activity.showCsvPreview(preview)
        );
        onView(withText(
                R.string.omr_assessment_csv_action_import
        )).inRoot(isDialog()).perform(click());
    }

    private OmrAssessmentCsvParser.Preview csvPreview() {
        return new OmrAssessmentCsvParser().parse(
                "codigo_prova,versao_prova,titulo,disciplina,"
                        + "etapa,periodo,ano_letivo,gabarito,"
                        + "versao_gabarito\n"
                        + "MAT-B2,1,Prova B2,Matemática,9º ano,"
                        + "2º bimestre,2026,key-1,1\n"
                        + "MAT-B2,2,Prova B2 revisada,Matemática,"
                        + "9º ano,2º bimestre,2026,key-1,1"
        );
    }

    private void waitForAssessmentCount(int expectedCount) {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (assessmentRepository.loadAll().size() == expectedCount) {
                InstrumentationRegistry.getInstrumentation()
                        .waitForIdleSync();
                return;
            }
            sleepBriefly();
        }
        fail("A importação não atingiu "
                + expectedCount + " versões de provas.");
    }

    private void waitForImportToFinish(
            ActivityScenario<OmrAssessmentListActivity> scenario
    ) {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] finished = {false};
            scenario.onActivity(activity -> finished[0] =
                    activity.findViewById(
                            R.id.progressOmrAssessmentList
                    ).getVisibility() == View.GONE);
            if (finished[0]) {
                return;
            }
            sleepBriefly();
        }
        fail("A reimportação não foi concluída.");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Espera da importação interrompida.");
        }
    }

    private static OmrAnswerKeyDefinition answerKey(
            String id,
            int version,
            String name
    ) {
        return new OmrAnswerKeyDefinition(
                id,
                version,
                name,
                "layout-test",
                1,
                Arrays.asList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q01", "Q01-A", 1.0
                        ),
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q02", "Q02-B", 1.0
                        )
                )
        );
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                id,
                version,
                title,
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                answerKeyId,
                answerKeyVersion
        );
    }

    private static ListView listView(Activity activity) {
        return activity.findViewById(R.id.listOmrAssessments);
    }

    private static View rowAt(Activity activity, int position) {
        ListView list = listView(activity);
        ListAdapter adapter = list.getAdapter();
        return adapter.getView(position, null, list);
    }

    private static View findRowByTitle(
            Activity activity,
            String title
    ) {
        ListView list = listView(activity);
        for (int index = 0; index < list.getAdapter().getCount(); index++) {
            View row = rowAt(activity, index);
            TextView value = row.findViewById(
                    R.id.textOmrAssessmentItemTitle
            );
            if (title.equals(value.getText().toString())) {
                return row;
            }
        }
        fail("A prova não foi renderizada: " + title);
        return null;
    }

    private static Button button(Activity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    private static Button button(View root, int viewId) {
        return root.findViewById(viewId);
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
