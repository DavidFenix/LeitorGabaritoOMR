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

public final class OmrAssessmentApplicationRosterViewStateTest {

    @Test
    public void exposesApplicationHeaderAndProgress() {
        OmrAssessmentApplicationRosterViewState state = state("");

        assertEquals("application-1", state.getApplicationId());
        assertEquals("assessment-1", state.getAssessmentId());
        assertEquals(2, state.getAssessmentVersion());
        assertEquals("Avaliação bimestral", state.getAssessmentTitle());
        assertEquals("Matemática", state.getSubject());
        assertEquals("9º A original", state.getFrozenClassName());
        assertEquals(1_000L, state.getCreatedAtEpochMillis());
        assertEquals(3, state.getTotalCount());
        assertEquals(2, state.getCorrectedCount());
        assertEquals(1, state.getPendingCount());
    }

    @Test
    public void participantsAreOrderedByFrozenPosition() {
        OmrAssessmentApplicationRosterViewState state = state(" ");

        assertEquals(1, state.getVisibleParticipants()
                .get(0).getRosterPosition());
        assertEquals(5, state.getVisibleParticipants()
                .get(1).getRosterPosition());
        assertEquals(9, state.getVisibleParticipants()
                .get(2).getRosterPosition());
    }

    @Test
    public void correctedParticipantUsesLatestRevision() {
        OmrAssessmentApplicationRosterViewState.ParticipantItem item =
                state("Ana").getVisibleParticipants().get(0);

        assertTrue(item.isCorrected());
        assertEquals(2, item.getLatestRevision());
        assertEquals("history-2", item.getLatestHistoryRecordId());
        assertEquals(4_000L, item.getLatestRecordedAtEpochMillis());
    }

    @Test
    public void pendingParticipantHasNoAttemptMetadata() {
        OmrAssessmentApplicationRosterViewState.ParticipantItem item =
                state("Bruno").getVisibleParticipants().get(0);

        assertFalse(item.isCorrected());
        assertEquals(0, item.getLatestRevision());
        assertNull(item.getLatestHistoryRecordId());
        assertEquals(0L, item.getLatestRecordedAtEpochMillis());
    }

    @Test
    public void firstPendingParticipantUsesFrozenRosterOrder() {
        assertEquals(
                "student-bruno",
                state("Ana").getFirstPendingStudentIdOrNull()
        );
    }

    @Test
    public void completedRosterHasNoNextPendingParticipant() {
        List<OmrAssessmentAttempt> completed = new ArrayList<>(attempts());
        completed.add(attempt(
                "attempt-4",
                "student-bruno",
                1,
                "history-4",
                5_000L
        ));

        OmrAssessmentApplicationRosterViewState state = create(
                application(),
                assessment(),
                participants(),
                completed,
                ""
        );

        assertEquals(0, state.getPendingCount());
        assertNull(state.getFirstPendingStudentIdOrNull());
    }

    @Test
    public void missingLatestHistoryBecomesRecoverableInsteadOfCorrected() {
        OmrAssessmentApplicationRosterViewState state =
                OmrAssessmentApplicationRosterViewState.from(
                        application(),
                        assessment(),
                        participants(),
                        attempts(),
                        Collections.singleton("history-3"),
                        ""
                );

        assertEquals(1, state.getCorrectedCount());
        assertEquals(1, state.getPendingCount());
        assertEquals(1, state.getRecoveryCount());
        assertEquals(2, state.getRemainingCount());
        assertEquals(
                "student-ana",
                state.getFirstPendingStudentIdOrNull()
        );

        OmrAssessmentApplicationRosterViewState.ParticipantItem ana =
                state.findVisibleParticipantOrNull("student-ana");
        assertNotNull(ana);
        assertFalse(ana.isCorrected());
        assertTrue(ana.needsHistoryRecovery());
        assertEquals(2, ana.getLatestRevision());
        assertEquals("history-2", ana.getLatestHistoryRecordId());
    }

    @Test
    public void recoveryStatusCanBeFoundAndUsesLatestHistoryOnly() {
        OmrAssessmentApplicationRosterViewState state =
                OmrAssessmentApplicationRosterViewState.from(
                        application(),
                        assessment(),
                        participants(),
                        attempts(),
                        new java.util.HashSet<>(Arrays.asList(
                                "history-1",
                                "history-3"
                        )),
                        "histórico indisponível"
                );

        assertEquals(1, state.getVisibleCount());
        assertEquals(
                "student-ana",
                state.getVisibleParticipants().get(0).getStudentId()
        );
        assertTrue(
                state.getVisibleParticipants().get(0)
                        .needsHistoryRecovery()
        );
    }

    @Test
    public void searchMatchesNameRegistrationAndIdIgnoringAccents() {
        assertEquals(1, state("JOAO").getVisibleCount());
        assertEquals(1, state("MAT-001").getVisibleCount());
        assertEquals(1, state("student-bruno").getVisibleCount());
    }

    @Test
    public void searchMatchesExactPositionOnly() {
        assertEquals(1, state("5").getVisibleCount());
        assertEquals(0, state("05").getVisibleCount());
    }

    @Test
    public void searchMatchesStatusAndRevision() {
        assertEquals(2, state("corrigido").getVisibleCount());
        assertEquals(1, state("pendente").getVisibleCount());
        assertEquals(1, state("revisão 2").getVisibleCount());
    }

    @Test
    public void queryWithoutMatchesSetsExpectedFlags() {
        OmrAssessmentApplicationRosterViewState state = state("ninguém");

        assertTrue(state.hasQuery());
        assertTrue(state.hasNoMatches());
        assertEquals(0, state.getVisibleCount());
    }

    @Test
    public void findsOnlyVisibleParticipant() {
        OmrAssessmentApplicationRosterViewState state = state("Ana");

        assertNotNull(state.findVisibleParticipantOrNull(" student-ana "));
        assertNull(state.findVisibleParticipantOrNull("student-bruno"));
        assertNull(state.findVisibleParticipantOrNull(null));
    }

    @Test
    public void visibleCollectionIsImmutable() {
        expectUnsupported(() -> state("").getVisibleParticipants().clear());
    }

    @Test
    public void rejectsNullInputs() {
        expectIllegalArgument(() -> create(
                null, assessment(), participants(), attempts(), ""
        ));
        expectIllegalArgument(() -> create(
                application(), null, participants(), attempts(), ""
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(), null, attempts(), ""
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(), participants(), null, ""
        ));
    }

    @Test
    public void rejectsAssessmentFromAnotherVersion() {
        expectIllegalArgument(() -> create(
                application(),
                new OmrAssessmentDefinition(
                        "assessment-1", 1, "Outra", "Matemática",
                        "9º ano", "2º bimestre", 2026, "key", 1
                ),
                participants(), attempts(), ""
        ));
    }

    @Test
    public void rejectsEmptyNullOrForeignParticipant() {
        expectIllegalArgument(() -> create(
                application(), assessment(), Collections.emptyList(),
                attempts(), ""
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(),
                Arrays.asList(participant1(), null), attempts(), ""
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(),
                Collections.singletonList(new OmrAssessmentParticipant(
                        "application-other", "student-1", "MAT-001",
                        "Ana", "9º A original", 1
                )),
                Collections.emptyList(), ""
        ));
    }

    @Test
    public void rejectsRepeatedRosterDataOrMixedClassName() {
        expectIllegalArgument(() -> create(
                application(), assessment(),
                Arrays.asList(
                        participant1(),
                        participant("student-ana", "OTHER", "Outra", 8)
                ),
                attempts(), ""
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(),
                Arrays.asList(
                        participant1(),
                        new OmrAssessmentParticipant(
                                "application-1", "student-x", "MAT-X",
                                "X", "Outra turma", 8
                        )
                ),
                attempts(), ""
        ));
    }

    @Test
    public void rejectsOrphanRepeatedOrDuplicateRevisionAttempts() {
        List<OmrAssessmentAttempt> orphan = new ArrayList<>(attempts());
        orphan.add(attempt(
                "attempt-x", "student-x", 1, "history-x", 5_000L
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(), participants(), orphan, ""
        ));

        List<OmrAssessmentAttempt> repeatedId = new ArrayList<>(attempts());
        repeatedId.add(attempt(
                "attempt-1", "student-joao", 3, "history-x", 5_000L
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(), participants(), repeatedId, ""
        ));

        List<OmrAssessmentAttempt> repeatedRevision = new ArrayList<>(attempts());
        repeatedRevision.add(attempt(
                "attempt-x", "student-ana", 2, "history-x", 5_000L
        ));
        expectIllegalArgument(() -> create(
                application(), assessment(), participants(),
                repeatedRevision, ""
        ));
    }

    private static OmrAssessmentApplicationRosterViewState state(
            String query
    ) {
        return create(
                application(), assessment(), participants(), attempts(), query
        );
    }

    private static OmrAssessmentApplicationRosterViewState create(
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            List<OmrAssessmentParticipant> participants,
            List<OmrAssessmentAttempt> attempts,
            String query
    ) {
        return OmrAssessmentApplicationRosterViewState.from(
                application, assessment, participants, attempts, query
        );
    }

    private static OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-1", "assessment-1", 2, "class-1", 1_000L
        );
    }

    private static OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-1", 2, "Avaliação bimestral", "Matemática",
                "9º ano", "2º bimestre", 2026, "key-1", 1
        );
    }

    private static List<OmrAssessmentParticipant> participants() {
        return Arrays.asList(
                participant("student-joao", "MAT-003", "João", 9),
                participant1(),
                participant("student-bruno", "MAT-002", "Bruno", 5)
        );
    }

    private static OmrAssessmentParticipant participant1() {
        return participant("student-ana", "MAT-001", "Ana", 1);
    }

    private static OmrAssessmentParticipant participant(
            String studentId,
            String registration,
            String name,
            int position
    ) {
        return new OmrAssessmentParticipant(
                "application-1", studentId, registration, name,
                "9º A original", position
        );
    }

    private static List<OmrAssessmentAttempt> attempts() {
        return Arrays.asList(
                attempt("attempt-1", "student-ana", 1, "history-1", 3_000L),
                attempt("attempt-2", "student-ana", 2, "history-2", 4_000L),
                attempt("attempt-3", "student-joao", 1, "history-3", 3_500L)
        );
    }

    private static OmrAssessmentAttempt attempt(
            String attemptId,
            String studentId,
            int revision,
            String historyId,
            long recordedAt
    ) {
        return new OmrAssessmentAttempt(
                attemptId, "application-1", studentId, revision,
                historyId, recordedAt
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
