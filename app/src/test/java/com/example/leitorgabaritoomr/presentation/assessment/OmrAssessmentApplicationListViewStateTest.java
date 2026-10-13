package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentApplicationListViewStateTest {

    @Test
    public void emptyCatalogHasExpectedFlags() {
        OmrAssessmentApplicationListViewState state = state(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );

        assertTrue(state.isCatalogEmpty());
        assertFalse(state.hasNoMatches());
        assertFalse(state.hasQuery());
        assertEquals(0, state.getTotalCount());
    }

    @Test
    public void blankQueryPreservesRepositoryOrder() {
        OmrAssessmentApplicationListViewState state = state(
                applications(), assessments(), participants(), attempts(), " "
        );

        assertEquals(2, state.getVisibleCount());
        assertEquals("application-2", state.getVisibleApplications()
                .get(0).getApplicationId());
        assertEquals("application-1", state.getVisibleApplications()
                .get(1).getApplicationId());
    }

    @Test
    public void itemExposesCompleteApplicationSnapshot() {
        OmrAssessmentApplicationListViewState.ApplicationItem item = state(
                applications(), assessments(), participants(), attempts(), "9 A"
        ).getVisibleApplications().get(0);

        assertEquals("application-1", item.getApplicationId());
        assertEquals("assessment-math", item.getAssessmentId());
        assertEquals(2, item.getAssessmentVersion());
        assertEquals("Avaliação Matemática", item.getAssessmentTitle());
        assertEquals("Matemática", item.getSubject());
        assertEquals("class-9a", item.getClassGroupId());
        assertEquals("9º A original", item.getFrozenClassName());
        assertEquals(2026, item.getSchoolYear());
        assertEquals(1_000L, item.getCreatedAtEpochMillis());
        assertEquals(2, item.getParticipantCount());
    }

    @Test
    public void progressCountsStudentsInsteadOfAttempts() {
        OmrAssessmentApplicationListViewState.ApplicationItem item = state(
                applications(), assessments(), participants(), attempts(), "9 A"
        ).getVisibleApplications().get(0);

        assertEquals(1, item.getCorrectedParticipantCount());
        assertEquals(1, item.getPendingParticipantCount());
        assertFalse(item.isComplete());

        OmrAssessmentApplicationListViewState.ApplicationItem complete = state(
                applications(), assessments(), participants(), attempts(), "9 B"
        ).getVisibleApplications().get(0);
        assertEquals(1, complete.getCorrectedParticipantCount());
        assertTrue(complete.isComplete());
    }

    @Test
    public void searchMatchesTitleSubjectAndClassIgnoringAccents() {
        assertEquals(1, view("AVALIACAO MATEMATICA").getVisibleCount());
        assertEquals(1, view("ciencias").getVisibleCount());
        assertEquals(1, view("9 A ORIGINAL").getVisibleCount());
    }

    @Test
    public void searchMatchesIdsYearAndExactVersion() {
        assertEquals(1, view("application-2").getVisibleCount());
        assertEquals(1, view("assessment-math").getVisibleCount());
        assertEquals(1, view("class-9b").getVisibleCount());
        assertEquals(2, view("2026").getVisibleCount());
        assertEquals(2, view("2").getVisibleCount());
    }

    @Test
    public void queryWithoutMatchesIsNotEmptyCatalog() {
        OmrAssessmentApplicationListViewState state = view("inexistente");

        assertFalse(state.isCatalogEmpty());
        assertTrue(state.hasNoMatches());
        assertTrue(state.hasQuery());
        assertEquals(0, state.getVisibleCount());
    }

    @Test
    public void findsOnlyVisibleApplication() {
        OmrAssessmentApplicationListViewState state = view("9 A");

        assertNotNull(state.findVisibleApplicationOrNull(" application-1 "));
        assertNull(state.findVisibleApplicationOrNull("application-2"));
        assertNull(state.findVisibleApplicationOrNull(null));
    }

    @Test
    public void visibleCollectionIsImmutable() {
        expectUnsupported(() -> view("").getVisibleApplications().clear());
    }

    @Test
    public void rejectsNullCatalogs() {
        expectIllegalArgument(() -> state(
                null, assessments(), participants(), attempts(), ""
        ));
        expectIllegalArgument(() -> state(
                applications(), null, participants(), attempts(), ""
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), null, attempts(), ""
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), participants(), null, ""
        ));
    }

    @Test
    public void rejectsNullOrRepeatedApplicationsAndAssessments() {
        expectIllegalArgument(() -> state(
                Arrays.asList(application1(), null),
                assessments(), participants(), attempts(), ""
        ));
        expectIllegalArgument(() -> state(
                Arrays.asList(application1(), application1()),
                assessments(), participants(), attempts(), ""
        ));
        expectIllegalArgument(() -> state(
                applications(),
                Arrays.asList(assessmentMath(), assessmentMath()),
                participants(), attempts(), ""
        ));
    }

    @Test
    public void rejectsApplicationWithoutExactAssessment() {
        expectIllegalArgument(() -> state(
                applications(),
                Collections.singletonList(assessmentScience()),
                participants(), attempts(), ""
        ));
    }

    @Test
    public void rejectsMissingOrInvalidFrozenRoster() {
        expectIllegalArgument(() -> state(
                applications(), assessments(),
                participantsForSecondApplication(), attempts(), ""
        ));

        List<OmrAssessmentParticipant> orphan = new ArrayList<>(
                participants()
        );
        orphan.add(participant(
                "application-other", "student-x", "X", "X", "X", 1
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), orphan, attempts(), ""
        ));

        List<OmrAssessmentParticipant> repeated = new ArrayList<>(
                participants()
        );
        repeated.add(participant(
                "application-1", "student-1", "OTHER", "Outra", "9º A original", 8
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), repeated, attempts(), ""
        ));

        List<OmrAssessmentParticipant> mixedClass = new ArrayList<>(
                participants()
        );
        mixedClass.set(1, participant(
                "application-1", "student-2", "MAT-002", "Bruno", "Outra turma", 2
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), mixedClass, attempts(), ""
        ));
    }

    @Test
    public void rejectsInvalidAttempts() {
        List<OmrAssessmentAttempt> orphan = new ArrayList<>(attempts());
        orphan.add(attempt(
                "attempt-x", "application-1", "student-x", 1, 5_000L
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), participants(), orphan, ""
        ));

        List<OmrAssessmentAttempt> repeated = new ArrayList<>(attempts());
        repeated.add(attempt(
                "attempt-1", "application-2", "student-3", 2, 5_000L
        ));
        expectIllegalArgument(() -> state(
                applications(), assessments(), participants(), repeated, ""
        ));
    }

    private static OmrAssessmentApplicationListViewState view(String query) {
        return state(
                applications(), assessments(), participants(), attempts(), query
        );
    }

    private static OmrAssessmentApplicationListViewState state(
            List<OmrAssessmentApplication> applications,
            List<OmrAssessmentDefinition> assessments,
            List<OmrAssessmentParticipant> participants,
            List<OmrAssessmentAttempt> attempts,
            String query
    ) {
        return OmrAssessmentApplicationListViewState.from(
                applications, assessments, participants, attempts, query
        );
    }

    private static List<OmrAssessmentApplication> applications() {
        return Arrays.asList(
                application2(),
                application1()
        );
    }

    private static OmrAssessmentApplication application1() {
        return new OmrAssessmentApplication(
                "application-1", "assessment-math", 2, "class-9a", 1_000L
        );
    }

    private static OmrAssessmentApplication application2() {
        return new OmrAssessmentApplication(
                "application-2", "assessment-science", 1, "class-9b", 2_000L
        );
    }

    private static List<OmrAssessmentDefinition> assessments() {
        return Arrays.asList(assessmentMath(), assessmentScience());
    }

    private static OmrAssessmentDefinition assessmentMath() {
        return assessment(
                "assessment-math", 2, "Avaliação Matemática", "Matemática"
        );
    }

    private static OmrAssessmentDefinition assessmentScience() {
        return assessment(
                "assessment-science", 1, "Prova de Ciências", "Ciências"
        );
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title,
            String subject
    ) {
        return new OmrAssessmentDefinition(
                id, version, title, subject, "9º ano", "2º bimestre",
                2026, "answer-key", 1
        );
    }

    private static List<OmrAssessmentParticipant> participants() {
        List<OmrAssessmentParticipant> values = new ArrayList<>();
        values.add(participant(
                "application-1", "student-1", "MAT-001", "Ana",
                "9º A original", 1
        ));
        values.add(participant(
                "application-1", "student-2", "MAT-002", "Bruno",
                "9º A original", 2
        ));
        values.addAll(participantsForSecondApplication());
        return values;
    }

    private static List<OmrAssessmentParticipant>
    participantsForSecondApplication() {
        return Collections.singletonList(participant(
                "application-2", "student-3", "MAT-003", "Carla",
                "9º B original", 1
        ));
    }

    private static OmrAssessmentParticipant participant(
            String applicationId,
            String studentId,
            String registration,
            String name,
            String className,
            int position
    ) {
        return new OmrAssessmentParticipant(
                applicationId, studentId, registration, name,
                className, position
        );
    }

    private static List<OmrAssessmentAttempt> attempts() {
        return Arrays.asList(
                attempt("attempt-1", "application-1", "student-1", 1, 3_000L),
                attempt("attempt-2", "application-1", "student-1", 2, 4_000L),
                attempt("attempt-3", "application-2", "student-3", 1, 3_500L)
        );
    }

    private static OmrAssessmentAttempt attempt(
            String attemptId,
            String applicationId,
            String studentId,
            int revision,
            long recordedAt
    ) {
        return new OmrAssessmentAttempt(
                attemptId, applicationId, studentId, revision,
                "history-" + attemptId, recordedAt
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

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
