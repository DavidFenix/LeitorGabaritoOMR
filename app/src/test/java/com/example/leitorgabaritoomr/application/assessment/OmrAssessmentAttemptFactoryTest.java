package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentAttemptFactoryTest {

    private static final long APPLICATION_CREATED_AT =
            1_799_000_000_000L;
    private static final long HISTORY_STORED_AT =
            1_799_100_000_000L;
    private static final long CAPTURED_AT =
            1_799_099_000_000L;

    private final OmrAssessmentAttemptFactory factory =
            new OmrAssessmentAttemptFactory();

    @Test
    public void createsFirstRevisionFromExactContext() {
        OmrGradingHistoryRecord history = history(
                "history-001",
                "reading-001"
        );

        OmrAssessmentAttempt attempt = factory.create(
                " attempt-001 ",
                application(),
                assessment(),
                participant(),
                history,
                Collections.emptyList()
        );

        assertEquals("attempt-001", attempt.getAttemptId());
        assertEquals(
                "application-001",
                attempt.getApplicationId()
        );
        assertEquals("student-001", attempt.getStudentId());
        assertEquals(1, attempt.getRevision());
        assertEquals(
                history.getHistoryRecordId(),
                attempt.getHistoryRecordId()
        );
        assertEquals(
                history.getStoredAtEpochMillis(),
                attempt.getRecordedAtEpochMillis()
        );
    }

    @Test
    public void createsNextContinuousRevisionForSameParticipant() {
        List<OmrAssessmentAttempt> existing = Arrays.asList(
                attempt(
                        "attempt-001",
                        "application-001",
                        "student-001",
                        1,
                        "history-001"
                ),
                attempt(
                        "attempt-002",
                        "application-001",
                        "student-001",
                        2,
                        "history-002"
                )
        );

        OmrAssessmentAttempt created = factory.create(
                "attempt-003",
                application(),
                assessment(),
                participant(),
                history("history-003", "reading-003"),
                existing
        );

        assertEquals(3, created.getRevision());
    }

    @Test
    public void unrelatedApplicationsDoNotAffectRevision() {
        OmrAssessmentAttempt unrelated = attempt(
                "attempt-other",
                "application-999",
                "student-001",
                8,
                "history-other"
        );

        OmrAssessmentAttempt created = factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Collections.singletonList(unrelated)
        );

        assertEquals(1, created.getRevision());
    }

    @Test
    public void unrelatedStudentsDoNotAffectRevision() {
        OmrAssessmentAttempt unrelated = attempt(
                "attempt-other",
                "application-001",
                "student-999",
                8,
                "history-other"
        );

        OmrAssessmentAttempt created = factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Collections.singletonList(unrelated)
        );

        assertEquals(1, created.getRevision());
    }

    @Test
    public void rejectsInvalidAttemptId() {
        expectIllegalArgument(() -> factory.create(
                null,
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));

        expectIllegalArgument(() -> factory.create(
                " ",
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsMissingApplication() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                null,
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsMissingAssessment() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                null,
                participant(),
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsMissingParticipant() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                null,
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsMissingHistoryRecord() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                null,
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsMissingExistingAttemptCollection() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                null
        ));
    }

    @Test
    public void rejectsAssessmentFromAnotherApplication() {
        OmrAssessmentDefinition anotherAssessment =
                new OmrAssessmentDefinition(
                        "assessment-999",
                        2,
                        "Outra prova",
                        "Matemática",
                        "9º ano",
                        "2º bimestre",
                        2026,
                        "answer-key-001",
                        3
                );

        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                anotherAssessment,
                participant(),
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsAnotherAssessmentVersion() {
        OmrAssessmentDefinition anotherVersion =
                new OmrAssessmentDefinition(
                        "assessment-001",
                        3,
                        "Prova de Matemática",
                        "Matemática",
                        "9º ano",
                        "2º bimestre",
                        2026,
                        "answer-key-001",
                        3
                );

        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                anotherVersion,
                participant(),
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsParticipantFromAnotherApplication() {
        OmrAssessmentParticipant anotherParticipant =
                new OmrAssessmentParticipant(
                        "application-999",
                        "student-001",
                        "000123",
                        "Ana Beatriz",
                        "9º A",
                        1
                );

        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                anotherParticipant,
                history("history-001", "reading-001"),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsHistoryFromAnotherStudent() {
        OmrStudentIdentity anotherStudent =
                new OmrStudentIdentity(
                        "student-999",
                        "000999",
                        "Outro Aluno",
                        "9º A"
                );

        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history(
                        "history-001",
                        "reading-001",
                        anotherStudent,
                        "answer-key-001",
                        3
                ),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsDifferentHistoricalStudentSnapshot() {
        expectSnapshotMismatch(
                new OmrStudentIdentity(
                        "student-001",
                        "999999",
                        "Ana Beatriz",
                        "9º A"
                )
        );
        expectSnapshotMismatch(
                new OmrStudentIdentity(
                        "student-001",
                        "000123",
                        "Nome diferente",
                        "9º A"
                )
        );
        expectSnapshotMismatch(
                new OmrStudentIdentity(
                        "student-001",
                        "000123",
                        "Ana Beatriz",
                        "9º B"
                )
        );
    }

    @Test
    public void rejectsAnotherAnswerKeyIdentity() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history(
                        "history-001",
                        "reading-001",
                        participant().toStudentIdentity(),
                        "answer-key-999",
                        3
                ),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsAnotherAnswerKeyVersion() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history(
                        "history-001",
                        "reading-001",
                        participant().toStudentIdentity(),
                        "answer-key-001",
                        4
                ),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsAttemptIdAlreadyInUse() {
        OmrAssessmentAttempt existing = attempt(
                "attempt-001",
                "application-999",
                "student-999",
                1,
                "history-old"
        );

        expectIllegalArgument(() -> factory.create(
                " attempt-001 ",
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Collections.singletonList(existing)
        ));
    }

    @Test
    public void rejectsHistoryRecordAlreadyLinked() {
        OmrAssessmentAttempt existing = attempt(
                "attempt-old",
                "application-999",
                "student-999",
                1,
                "history-001"
        );

        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history(" history-001 ", "reading-001"),
                Collections.singletonList(existing)
        ));
    }

    @Test
    public void rejectsNullExistingAttempt() {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history("history-001", "reading-001"),
                Arrays.asList(
                        attempt(
                                "attempt-old",
                                "application-999",
                                "student-999",
                                1,
                                "history-old"
                        ),
                        null
                )
        ));
    }

    @Test
    public void rejectsRepeatedExistingAttemptIdentity() {
        expectIllegalArgument(() -> factory.create(
                "attempt-new",
                application(),
                assessment(),
                participant(),
                history("history-new", "reading-new"),
                Arrays.asList(
                        attempt(
                                "attempt-old",
                                "application-999",
                                "student-998",
                                1,
                                "history-old-1"
                        ),
                        attempt(
                                "attempt-old",
                                "application-999",
                                "student-999",
                                1,
                                "history-old-2"
                        )
                )
        ));
    }

    @Test
    public void rejectsRepeatedExistingHistoryReference() {
        expectIllegalArgument(() -> factory.create(
                "attempt-new",
                application(),
                assessment(),
                participant(),
                history("history-new", "reading-new"),
                Arrays.asList(
                        attempt(
                                "attempt-old-1",
                                "application-999",
                                "student-998",
                                1,
                                "history-old"
                        ),
                        attempt(
                                "attempt-old-2",
                                "application-999",
                                "student-999",
                                1,
                                "history-old"
                        )
                )
        ));
    }

    @Test
    public void rejectsRepeatedParticipantRevision() {
        expectIllegalArgument(() -> factory.create(
                "attempt-003",
                application(),
                assessment(),
                participant(),
                history("history-003", "reading-003"),
                Arrays.asList(
                        attempt(
                                "attempt-001",
                                "application-001",
                                "student-001",
                                1,
                                "history-001"
                        ),
                        attempt(
                                "attempt-002",
                                "application-001",
                                "student-001",
                                1,
                                "history-002"
                        )
                )
        ));
    }

    @Test
    public void rejectsGapInParticipantRevisions() {
        expectIllegalArgument(() -> factory.create(
                "attempt-003",
                application(),
                assessment(),
                participant(),
                history("history-003", "reading-003"),
                Arrays.asList(
                        attempt(
                                "attempt-001",
                                "application-001",
                                "student-001",
                                1,
                                "history-001"
                        ),
                        attempt(
                                "attempt-002",
                                "application-001",
                                "student-001",
                                3,
                                "history-002"
                        )
                )
        ));
    }

    private void expectSnapshotMismatch(
            OmrStudentIdentity student
    ) {
        expectIllegalArgument(() -> factory.create(
                "attempt-001",
                application(),
                assessment(),
                participant(),
                history(
                        "history-001",
                        "reading-001",
                        student,
                        "answer-key-001",
                        3
                ),
                Collections.emptyList()
        ));
    }

    private OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-001",
                2,
                "Prova de Matemática",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                3
        );
    }

    private OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-001",
                "assessment-001",
                2,
                "class-001",
                APPLICATION_CREATED_AT
        );
    }

    private OmrAssessmentParticipant participant() {
        return new OmrAssessmentParticipant(
                "application-001",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9º A",
                1
        );
    }

    private OmrAssessmentAttempt attempt(
            String attemptId,
            String applicationId,
            String studentId,
            int revision,
            String historyRecordId
    ) {
        return new OmrAssessmentAttempt(
                attemptId,
                applicationId,
                studentId,
                revision,
                historyRecordId,
                HISTORY_STORED_AT
        );
    }

    private OmrGradingHistoryRecord history(
            String historyRecordId,
            String readingId
    ) {
        return history(
                historyRecordId,
                readingId,
                participant().toStudentIdentity(),
                "answer-key-001",
                3
        );
    }

    private OmrGradingHistoryRecord history(
            String historyRecordId,
            String readingId,
            OmrStudentIdentity student,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrGradingHistoryRecord(
                historyRecordId,
                HISTORY_STORED_AT,
                student,
                gradingResult(
                        readingId,
                        answerKeyId,
                        answerKeyVersion
                )
        );
    }

    private OmrGradingResult gradingResult(
            String readingId,
            String answerKeyId,
            int answerKeyVersion
    ) {
        OmrQuestionResult question =
                new OmrQuestionResult(
                        1,
                        "Q01",
                        OmrQuestionResult.Status.SINGLE_MARK,
                        Collections.singletonList(
                                new OmrQuestionResult.Option(
                                        "A",
                                        "A"
                                )
                        ),
                        0.98
                );

        OmrReadingResult reading =
                new OmrReadingResult(
                        readingId,
                        CAPTURED_AT,
                        "layout-controlled",
                        1,
                        "Layout controlado",
                        Collections.singletonList(question)
                );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        answerKeyId,
                        answerKeyVersion,
                        "Gabarito da prova",
                        "layout-controlled",
                        1,
                        Collections.singletonList(
                                OmrAnswerKeyEntry.singleAnswer(
                                        "Q01",
                                        "A",
                                        1.0
                                )
                        )
                );

        return new OmrReadingGrader().grade(
                reading,
                answerKey
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }
}
