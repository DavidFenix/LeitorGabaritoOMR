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

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentApplicationRosterViewBinderInstrumentedTest {

    private View rootView;
    private OmrAssessmentApplicationRosterViewBinder binder;
    private RecordingSearch search;
    private RecordingCorrection correction;

    @Before
    public void setUp() {
        runOnMain(() -> {
            Context context = new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.Theme_LeitorGabaritoOMR
            );
            FrameLayout parent = new FrameLayout(context);
            rootView = LayoutInflater.from(context).inflate(
                    R.layout.activity_omr_assessment_application_roster,
                    parent,
                    false
            );
            binder = new OmrAssessmentApplicationRosterViewBinder(rootView);
            search = new RecordingSearch();
            correction = new RecordingCorrection();
            binder.setOnSearchQueryChangedListener(search);
            binder.setOnBackClickListener(view -> search.backCount++);
            binder.setOnParticipantCorrectionListener(correction);
            binder.setOnNextPendingClickListener(
                    view -> correction.nextPendingCount++
            );
            binder.setOnResultsClickListener(
                    view -> correction.resultsCount++
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
            search = null;
            correction = null;
            rootView = null;
        });
    }

    @Test
    public void rejectsMissingRootOrState() {
        runOnMain(() -> {
            expectIllegalArgument(() ->
                    new OmrAssessmentApplicationRosterViewBinder(null));
            expectIllegalArgument(() -> binder.render(null));
        });
    }

    @Test
    public void headerRendersExactAssessmentFrozenClassAndProgress() {
        runOnMain(() -> {
            binder.render(state(""));
            assertText(R.id.textOmrApplicationRosterTitle,
                    "Avaliação bimestral");
            assertText(R.id.textOmrApplicationRosterAssessment,
                    "Matemática • prova v2 • assessment-1");
            assertText(R.id.textOmrApplicationRosterClass,
                    "Chamada congelada: 9º A original");
            assertText(R.id.textOmrApplicationRosterProgress,
                    "1 de 2 corrigidos • 1 pendente");
            assertText(R.id.textOmrApplicationRosterCount,
                    "2 participantes");
        });
    }

    @Test
    public void rowsRenderFrozenOrderAndIdentity() {
        runOnMain(() -> {
            binder.render(state(""));
            assertEquals(2, listView().getAdapter().getCount());
            View first = rowAt(0);
            assertRowText(first,
                    R.id.textOmrApplicationParticipantPosition, "#01");
            assertRowText(first,
                    R.id.textOmrApplicationParticipantName, "Ana");
            assertRowText(first,
                    R.id.textOmrApplicationParticipantRegistration,
                    "Matrícula MAT-001 • student-ana");
        });
    }

    @Test
    public void correctedAndPendingRowsUseDifferentStatus() {
        runOnMain(() -> {
            binder.render(state(""));
            TextView corrected = rowAt(0).findViewById(
                    R.id.textOmrApplicationParticipantStatus
            );
            TextView pending = rowAt(1).findViewById(
                    R.id.textOmrApplicationParticipantStatus
            );
            assertTrue(corrected.getText().toString().startsWith(
                    "Corrigido • revisão 2 •"
            ));
            assertEquals("Pendente de correção",
                    pending.getText().toString());
            assertFalse(corrected.getCurrentTextColor()
                    == pending.getCurrentTextColor());
        });
    }

    @Test
    public void missingHistoryShowsRecoveryStatusAndBatchAction() {
        runOnMain(() -> {
            binder.render(recoveryState());
            assertText(
                    R.id.textOmrApplicationRosterProgress,
                    "Corrigidos: 0/2 • pendentes: 1 • recuperar: 1"
            );
            View recovery = rowAt(0);
            assertRowText(
                    recovery,
                    R.id.textOmrApplicationParticipantStatus,
                    "Histórico da revisão 2 indisponível"
                            + " • corrigir novamente"
            );
            assertEquals(
                    "Recuperar nota",
                    ((Button) recovery.findViewById(
                            R.id.buttonOmrApplicationParticipantCorrect
                    )).getText().toString()
            );
            assertEquals(
                    "Corrigir pendentes e recuperar notas",
                    button(R.id.buttonOmrApplicationRosterNextPending)
                            .getText().toString()
            );
        });
    }

    @Test
    public void rowActionsDistinguishFirstCorrectionFromNewRevision() {
        runOnMain(() -> {
            binder.render(state(""));
            Button corrected = rowAt(0).findViewById(
                    R.id.buttonOmrApplicationParticipantCorrect
            );
            Button pending = rowAt(1).findViewById(
                    R.id.buttonOmrApplicationParticipantCorrect
            );
            assertEquals("Nova revisão", corrected.getText().toString());
            assertEquals("Corrigir", pending.getText().toString());

            corrected.performClick();
            assertEquals("student-ana", correction.lastStudentId);
            pending.performClick();
            assertEquals("student-bruno", correction.lastStudentId);
            assertEquals(2, correction.participantCount);
        });
    }

    @Test
    public void nextPendingActionIsForwardedAndDisabledWhenComplete() {
        runOnMain(() -> {
            binder.render(state(""));
            Button next = button(
                    R.id.buttonOmrApplicationRosterNextPending
            );
            assertTrue(next.isEnabled());
            assertEquals(
                    "Corrigir próximo pendente",
                    next.getText().toString()
            );
            next.performClick();
            assertEquals(1, correction.nextPendingCount);

            binder.render(completedState());
            assertFalse(next.isEnabled());
            assertEquals("Chamada concluída", next.getText().toString());
        });
    }

    @Test
    public void resultsActionIsForwarded() {
        runOnMain(() -> {
            binder.render(state(""));
            button(R.id.buttonOmrApplicationRosterResults).performClick();
            assertEquals(1, correction.resultsCount);
        });
    }

    @Test
    public void filteredStateUpdatesCountAndRows() {
        runOnMain(() -> {
            binder.render(state("pendente"));
            assertText(R.id.textOmrApplicationRosterCount,
                    "1 de 2 participantes encontrados");
            assertEquals(1, listView().getAdapter().getCount());
            assertRowText(rowAt(0),
                    R.id.textOmrApplicationParticipantName, "Bruno");
        });
    }

    @Test
    public void unmatchedFilterShowsNoResults() {
        runOnMain(() -> {
            binder.render(state("inexistente"));
            assertVisibility(
                    R.id.containerOmrApplicationRosterNoResults,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrApplicationRoster, View.GONE);
        });
    }

    @Test
    public void searchAndBackActionsAreForwarded() {
        runOnMain(() -> {
            searchEditText().setText("revisão 2");
            button(R.id.buttonOmrApplicationRosterBack).performClick();
            assertEquals(1, search.count);
            assertEquals("revisão 2", search.lastQuery);
            assertEquals("revisão 2", binder.getSearchQuery());
            assertEquals(1, search.backCount);
        });
    }

    @Test
    public void busyStateHidesContentAndDisablesActions() {
        runOnMain(() -> {
            binder.render(state(""));
            binder.setBusy(true);
            Button rowAction = rowAt(0).findViewById(
                    R.id.buttonOmrApplicationParticipantCorrect
            );
            assertVisibility(
                    R.id.progressOmrApplicationRoster, View.VISIBLE
            );
            assertVisibility(R.id.listOmrApplicationRoster, View.GONE);
            assertFalse(searchEditText().isEnabled());
            assertFalse(button(
                    R.id.buttonOmrApplicationRosterBack
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrApplicationRosterNextPending
            ).isEnabled());
            assertFalse(rowAction.isEnabled());
            binder.setBusy(false);
            assertTrue(searchEditText().isEnabled());
            assertVisibility(R.id.listOmrApplicationRoster, View.VISIBLE);
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();
            expectIllegalState(binder::getSearchQuery);
            expectIllegalState(() -> binder.render(state("")));
        });
    }

    private OmrAssessmentApplicationRosterViewState state(String query) {
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                );
        OmrAssessmentDefinition assessment =
                new OmrAssessmentDefinition(
                        "assessment-1", 2, "Avaliação bimestral",
                        "Matemática", "9º ano", "2º bimestre", 2026,
                        "key-1", 1
                );
        return OmrAssessmentApplicationRosterViewState.from(
                application,
                assessment,
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-1", "student-bruno",
                                "MAT-002", "Bruno",
                                "9º A original", 5
                        ),
                        new OmrAssessmentParticipant(
                                "application-1", "student-ana",
                                "MAT-001", "Ana",
                                "9º A original", 1
                        )
                ),
                Arrays.asList(
                        new OmrAssessmentAttempt(
                                "attempt-1", "application-1",
                                "student-ana", 1, "history-1", 2_000L
                        ),
                        new OmrAssessmentAttempt(
                                "attempt-2", "application-1",
                                "student-ana", 2, "history-2", 3_000L
                        )
                ),
                query
        );
    }

    private OmrAssessmentApplicationRosterViewState completedState() {
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                );
        OmrAssessmentDefinition assessment =
                new OmrAssessmentDefinition(
                        "assessment-1", 2, "Avaliação bimestral",
                        "Matemática", "9º ano", "2º bimestre", 2026,
                        "key-1", 1
                );
        return OmrAssessmentApplicationRosterViewState.from(
                application,
                assessment,
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-1", "student-bruno",
                                "MAT-002", "Bruno", "9º A original", 5
                        ),
                        new OmrAssessmentParticipant(
                                "application-1", "student-ana",
                                "MAT-001", "Ana", "9º A original", 1
                        )
                ),
                Arrays.asList(
                        new OmrAssessmentAttempt(
                                "attempt-1", "application-1",
                                "student-ana", 1, "history-1", 2_000L
                        ),
                        new OmrAssessmentAttempt(
                                "attempt-2", "application-1",
                                "student-ana", 2, "history-2", 3_000L
                        ),
                        new OmrAssessmentAttempt(
                                "attempt-3", "application-1",
                                "student-bruno", 1, "history-3", 4_000L
                        )
                ),
                ""
        );
    }

    private OmrAssessmentApplicationRosterViewState recoveryState() {
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                );
        OmrAssessmentDefinition assessment =
                new OmrAssessmentDefinition(
                        "assessment-1", 2, "Avaliação bimestral",
                        "Matemática", "9º ano", "2º bimestre", 2026,
                        "key-1", 1
                );
        return OmrAssessmentApplicationRosterViewState.from(
                application,
                assessment,
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-1", "student-bruno",
                                "MAT-002", "Bruno",
                                "9º A original", 5
                        ),
                        new OmrAssessmentParticipant(
                                "application-1", "student-ana",
                                "MAT-001", "Ana",
                                "9º A original", 1
                        )
                ),
                Arrays.asList(
                        new OmrAssessmentAttempt(
                                "attempt-1", "application-1",
                                "student-ana", 1, "history-1", 2_000L
                        ),
                        new OmrAssessmentAttempt(
                                "attempt-2", "application-1",
                                "student-ana", 2, "history-2", 3_000L
                        )
                ),
                Collections.emptySet(),
                ""
        );
    }

    private ListView listView() {
        return rootView.findViewById(R.id.listOmrApplicationRoster);
    }

    private View rowAt(int position) {
        return listView().getAdapter().getView(
                position, null, listView()
        );
    }

    private TextInputEditText searchEditText() {
        return rootView.findViewById(
                R.id.editOmrApplicationRosterSearch
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

    private static final class RecordingSearch
            implements OmrAssessmentApplicationRosterViewBinder
            .OnSearchQueryChangedListener {
        private int count;
        private int backCount;
        private String lastQuery;

        @Override
        public void onSearchQueryChanged(String query) {
            count++;
            lastQuery = query;
        }
    }

    private static final class RecordingCorrection
            implements OmrAssessmentApplicationRosterViewBinder
            .OnParticipantCorrectionListener {
        private int participantCount;
        private int nextPendingCount;
        private int resultsCount;
        private String lastStudentId;

        @Override
        public void onCorrectParticipant(String studentId) {
            participantCount++;
            lastStudentId = studentId;
        }
    }
}
