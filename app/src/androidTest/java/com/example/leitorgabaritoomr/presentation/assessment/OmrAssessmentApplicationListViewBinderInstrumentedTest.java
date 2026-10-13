package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentApplicationListViewBinderInstrumentedTest {

    private View rootView;
    private OmrAssessmentApplicationListViewBinder binder;
    private RecordingActions actions;

    @Before
    public void setUp() {
        runOnMain(() -> {
            Context context = new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.Theme_LeitorGabaritoOMR
            );
            FrameLayout parent = new FrameLayout(context);
            rootView = LayoutInflater.from(context).inflate(
                    R.layout.activity_omr_assessment_application_list,
                    parent,
                    false
            );
            binder = new OmrAssessmentApplicationListViewBinder(rootView);
            actions = new RecordingActions();
            binder.setOnApplicationActionListener(actions);
            binder.setOnSearchQueryChangedListener(actions);
            binder.setOnBackClickListener(view -> actions.backCount++);
            binder.setOnCreateClickListener(view -> actions.createCount++);
            binder.setOnImportCsvClickListener(
                    view -> actions.importCount++
            );
        });
    }

    @After
    public void tearDown() {
        runOnMain(() -> {
            if (binder != null) {
                binder.release();
            }
            binder = null;
            actions = null;
            rootView = null;
        });
    }

    @Test
    public void rejectsMissingRootOrState() {
        runOnMain(() -> {
            expectIllegalArgument(() ->
                    new OmrAssessmentApplicationListViewBinder(null));
            expectIllegalArgument(() -> binder.render(null));
        });
    }

    @Test
    public void emptyCatalogShowsExpectedState() {
        runOnMain(() -> {
            binder.render(emptyState());
            assertText(
                    R.id.textOmrAssessmentApplicationListCount,
                    "0 aplicações cadastradas"
            );
            assertVisibility(
                    R.id.containerOmrAssessmentApplicationListEmpty,
                    View.VISIBLE
            );
            assertVisibility(
                    R.id.listOmrAssessmentApplications,
                    View.GONE
            );
        });
    }

    @Test
    public void populatedCatalogRendersSnapshotAndProgress() {
        runOnMain(() -> {
            binder.render(state(""));
            assertText(
                    R.id.textOmrAssessmentApplicationListCount,
                    "2 aplicações cadastradas"
            );
            assertEquals(2, listView().getAdapter().getCount());
            View first = rowAt(0);
            assertRowText(first,
                    R.id.textOmrAssessmentApplicationItemTitle,
                    "Avaliação bimestral");
            assertRowText(first,
                    R.id.textOmrAssessmentApplicationItemAssessment,
                    "Matemática • prova v2 • 2026");
            assertRowText(first,
                    R.id.textOmrAssessmentApplicationItemClass,
                    "Turma congelada: 9º A original");
            assertRowText(first,
                    R.id.textOmrAssessmentApplicationItemProgress,
                    "1 de 2 corrigidos • 1 pendente");
        });
    }

    @Test
    public void completeApplicationShowsCompleteMessage() {
        runOnMain(() -> {
            binder.render(state("8º B"));
            assertRowText(rowAt(0),
                    R.id.textOmrAssessmentApplicationItemProgress,
                    "1 de 1 corrigidos • chamada concluída");
        });
    }

    @Test
    public void filteredStateShowsVisibleAndTotalCounts() {
        runOnMain(() -> {
            binder.render(state("ciências"));
            assertText(
                    R.id.textOmrAssessmentApplicationListCount,
                    "1 de 2 aplicações encontradas"
            );
            assertEquals(1, listView().getAdapter().getCount());
        });
    }

    @Test
    public void unmatchedFilterShowsNoResults() {
        runOnMain(() -> {
            binder.render(state("inexistente"));
            assertVisibility(
                    R.id.containerOmrAssessmentApplicationListNoResults,
                    View.VISIBLE
            );
            assertVisibility(
                    R.id.listOmrAssessmentApplications,
                    View.GONE
            );
        });
    }

    @Test
    public void searchTextDispatchesExactQuery() {
        runOnMain(() -> {
            searchEditText().setText("9º A");
            assertEquals(1, actions.searchCount);
            assertEquals("9º A", actions.lastQuery);
            assertEquals("9º A", binder.getSearchQuery());
        });
    }

    @Test
    public void footerButtonsDispatchActions() {
        runOnMain(() -> {
            button(R.id.buttonOmrAssessmentApplicationListBack)
                    .performClick();
            button(R.id.buttonOmrAssessmentApplicationListCreate)
                    .performClick();
            button(R.id.buttonOmrAssessmentApplicationListImportCsv)
                    .performClick();
            assertEquals(1, actions.backCount);
            assertEquals(1, actions.createCount);
            assertEquals(1, actions.importCount);
        });
    }

    @Test
    public void rowAndRosterButtonDispatchApplicationId() {
        runOnMain(() -> {
            binder.render(state(""));
            View first = rowAt(0);
            first.performClick();
            assertEquals("application-1", actions.lastApplicationId);
            Button roster = first.findViewById(
                    R.id.buttonOmrAssessmentApplicationItemRoster
            );
            roster.performClick();
            assertEquals(2, actions.openCount);
            assertEquals("application-1", actions.lastApplicationId);
        });
    }

    @Test
    public void busyAndReleaseLifecycleAreEnforced() {
        runOnMain(() -> {
            binder.render(state(""));
            binder.setBusy(true);
            assertVisibility(
                    R.id.progressOmrAssessmentApplicationList,
                    View.VISIBLE
            );
            assertFalse(searchEditText().isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAssessmentApplicationListCreate
            ).isEnabled());
            binder.setBusy(false);
            assertTrue(searchEditText().isEnabled());
            binder.release();
            binder.release();
            expectIllegalState(binder::getSearchQuery);
            expectIllegalState(() -> binder.setBusy(false));
        });
    }

    private OmrAssessmentApplicationListViewState emptyState() {
        return OmrAssessmentApplicationListViewState.from(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                ""
        );
    }

    private OmrAssessmentApplicationListViewState state(String query) {
        List<OmrAssessmentApplication> applications = Arrays.asList(
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                ),
                new OmrAssessmentApplication(
                        "application-2", "assessment-2", 1,
                        "class-2", 2_000L
                )
        );
        List<OmrAssessmentDefinition> assessments = Arrays.asList(
                new OmrAssessmentDefinition(
                        "assessment-1", 2, "Avaliação bimestral",
                        "Matemática", "9º ano", "2º bimestre", 2026,
                        "key-1", 1
                ),
                new OmrAssessmentDefinition(
                        "assessment-2", 1, "Diagnóstico",
                        "Ciências", "8º ano", "Inicial", 2025,
                        "key-2", 1
                )
        );
        List<OmrAssessmentParticipant> participants = Arrays.asList(
                participant("application-1", "student-1", "Ana",
                        "9º A original", 1),
                participant("application-1", "student-2", "Bruno",
                        "9º A original", 2),
                participant("application-2", "student-3", "Carla",
                        "8º B", 1)
        );
        List<OmrAssessmentAttempt> attempts = Arrays.asList(
                attempt("attempt-1", "application-1", "student-1", 1),
                attempt("attempt-2", "application-2", "student-3", 1)
        );
        return OmrAssessmentApplicationListViewState.from(
                applications, assessments, participants, attempts, query
        );
    }

    private static OmrAssessmentParticipant participant(
            String applicationId,
            String studentId,
            String name,
            String className,
            int position
    ) {
        return new OmrAssessmentParticipant(
                applicationId, studentId, "MAT-" + position,
                name, className, position
        );
    }

    private static OmrAssessmentAttempt attempt(
            String attemptId,
            String applicationId,
            String studentId,
            int revision
    ) {
        return new OmrAssessmentAttempt(
                attemptId, applicationId, studentId, revision,
                "history-" + attemptId, 3_000L + revision
        );
    }

    private ListView listView() {
        return rootView.findViewById(
                R.id.listOmrAssessmentApplications
        );
    }

    private View rowAt(int position) {
        return listView().getAdapter().getView(
                position, null, listView()
        );
    }

    private TextInputEditText searchEditText() {
        return rootView.findViewById(
                R.id.editOmrAssessmentApplicationSearch
        );
    }

    private Button button(int id) {
        return rootView.findViewById(id);
    }

    private void assertText(int id, String expected) {
        TextView text = rootView.findViewById(id);
        assertEquals(expected, text.getText().toString());
    }

    private static void assertRowText(
            View row, int id, String expected
    ) {
        TextView text = row.findViewById(id);
        assertEquals(expected, text.getText().toString());
    }

    private void assertVisibility(int id, int expected) {
        assertEquals(expected, rootView.findViewById(id).getVisibility());
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalStateException.");
        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
    }

    private static final class RecordingActions
            implements OmrAssessmentApplicationListViewBinder
            .OnApplicationActionListener,
            OmrAssessmentApplicationListViewBinder
                    .OnSearchQueryChangedListener {
        private int openCount;
        private int searchCount;
        private int backCount;
        private int createCount;
        private int importCount;
        private String lastApplicationId;
        private String lastQuery;

        @Override
        public void onOpenFrozenRoster(String applicationId) {
            openCount++;
            lastApplicationId = applicationId;
        }

        @Override
        public void onSearchQueryChanged(String query) {
            searchCount++;
            lastQuery = query;
        }
    }
}
