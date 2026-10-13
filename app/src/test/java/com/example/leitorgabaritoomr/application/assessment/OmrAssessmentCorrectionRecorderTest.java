package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentCorrectionRecorderTest {

    @Test
    public void rejectsMissingRepository() {
        expectIllegalArgument(() ->
                new OmrAssessmentCorrectionRecorder(null)
        );
    }

    @Test
    public void rejectsMissingContextOrHistory() {
        OmrAssessmentCorrectionRecorder recorder =
                new OmrAssessmentCorrectionRecorder(new FakeAttempts());
        expectIllegalArgument(() -> recorder.record(null, history(
                "history-1", "reading-1", student(), "key-1", 3, 2_000L
        )));
        expectIllegalArgument(() -> recorder.record(context(), null));
    }

    @Test
    public void recordsFirstRevisionWithDeterministicIdentity() {
        FakeAttempts repository = new FakeAttempts();
        OmrGradingHistoryRecord history = history(
                "history-1", "reading-1", student(), "key-1", 3, 2_000L
        );

        OmrAssessmentAttempt attempt = recorder(repository).record(
                context(), history
        );

        assertEquals("attempt:history-1", attempt.getAttemptId());
        assertEquals("application-1", attempt.getApplicationId());
        assertEquals("student-1", attempt.getStudentId());
        assertEquals(1, attempt.getRevision());
        assertEquals("history-1", attempt.getHistoryRecordId());
        assertEquals(2_000L, attempt.getRecordedAtEpochMillis());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    public void appendsContinuousRevisionWithoutReplacingFirst() {
        FakeAttempts repository = new FakeAttempts();
        repository.records.add(attempt(
                "attempt:history-1", "student-1", 1,
                "history-1", 2_000L
        ));

        OmrAssessmentAttempt second = recorder(repository).record(
                context(),
                history(
                        "history-2", "reading-2", student(),
                        "key-1", 3, 3_000L
                )
        );

        assertEquals(2, second.getRevision());
        assertEquals(2, repository.records.size());
        assertEquals(1, repository.records.get(0).getRevision());
    }

    @Test
    public void repeatedHistoryReturnsExistingWithoutSavingAgain() {
        FakeAttempts repository = new FakeAttempts();
        OmrAssessmentAttempt existing = attempt(
                "attempt:history-1", "student-1", 1,
                "history-1", 2_000L
        );
        repository.records.add(existing);

        OmrAssessmentAttempt returned = recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        );

        assertSame(existing, returned);
        assertEquals(0, repository.saveCalls);
    }

    @Test
    public void concurrentEquivalentSaveReturnsWinner() {
        FakeAttempts repository = new FakeAttempts();
        repository.storeButReportRefusal = true;

        OmrAssessmentAttempt returned = recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        );

        assertNotNull(returned);
        assertEquals("attempt:history-1", returned.getAttemptId());
        assertEquals(1, repository.records.size());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    public void concurrentConstraintWithEquivalentWinnerReturnsWinner() {
        FakeAttempts repository = new FakeAttempts();
        repository.storeThenThrowConstraint = true;

        OmrAssessmentAttempt returned = recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        );

        assertNotNull(returned);
        assertEquals("attempt:history-1", returned.getAttemptId());
        assertEquals(1, repository.records.size());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    public void constraintWithoutConcurrentWinnerIsPropagated() {
        FakeAttempts repository = new FakeAttempts();
        repository.throwConstraintWithoutRecord = true;

        expectIllegalArgument(() -> recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        ));
    }

    @Test
    public void repositoryRefusalWithoutWinnerFails() {
        FakeAttempts repository = new FakeAttempts();
        repository.rejectWithoutRecord = true;

        expectIllegalState("sem preservar", () -> recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        ));
    }

    @Test
    public void rejectsHistoryAlreadyLinkedToAnotherParticipant() {
        FakeAttempts repository = new FakeAttempts();
        repository.records.add(attempt(
                "attempt:history-1", "student-other", 1,
                "history-1", 2_000L
        ));

        expectIllegalState("outro participante", () ->
                recorder(repository).record(
                        context(),
                        history(
                                "history-1", "reading-1", student(),
                                "key-1", 3, 2_000L
                        )
                )
        );
    }

    @Test
    public void rejectsExistingAttemptWithDifferentTimestamp() {
        FakeAttempts repository = new FakeAttempts();
        repository.records.add(attempt(
                "attempt:history-1", "student-1", 1,
                "history-1", 9_000L
        ));

        expectIllegalState("instante", () -> recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        ));
    }

    @Test
    public void rejectsHistoryFromAnotherStudentSnapshot() {
        OmrStudentIdentity another = new OmrStudentIdentity(
                "student-1", "MAT-999", "Outro nome", "9º A"
        );
        expectIllegalArgument(() -> recorder(new FakeAttempts()).record(
                context(),
                history(
                        "history-1", "reading-1", another,
                        "key-1", 3, 2_000L
                )
        ));
    }

    @Test
    public void rejectsHistoryUsingAnotherAnswerKeyVersion() {
        expectIllegalArgument(() -> recorder(new FakeAttempts()).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 4, 2_000L
                )
        ));
    }

    @Test
    public void rejectsNonContinuousExistingRevisions() {
        FakeAttempts repository = new FakeAttempts();
        repository.records.add(attempt(
                "attempt:history-old", "student-1", 2,
                "history-old", 1_000L
        ));

        expectIllegalArgument(() -> recorder(repository).record(
                context(),
                history(
                        "history-1", "reading-1", student(),
                        "key-1", 3, 2_000L
                )
        ));
    }

    private static OmrAssessmentCorrectionRecorder recorder(
            FakeAttempts repository
    ) {
        return new OmrAssessmentCorrectionRecorder(repository);
    }

    private static OmrAssessmentCorrectionContext context() {
        return new OmrAssessmentCorrectionContext(
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                ),
                new OmrAssessmentDefinition(
                        "assessment-1", 2, "Avaliação", "Matemática",
                        "9º ano", "2º bimestre", 2026, "key-1", 3
                ),
                new OmrAssessmentParticipant(
                        "application-1", "student-1", "MAT-001",
                        "Ana", "9º A", 1
                ),
                answerKey("key-1", 3)
        );
    }

    private static OmrStudentIdentity student() {
        return new OmrStudentIdentity(
                "student-1", "MAT-001", "Ana", "9º A"
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
                attemptId,
                "application-1",
                studentId,
                revision,
                historyId,
                recordedAt
        );
    }

    private static OmrGradingHistoryRecord history(
            String historyId,
            String readingId,
            OmrStudentIdentity student,
            String answerKeyId,
            int answerKeyVersion,
            long storedAt
    ) {
        return new OmrGradingHistoryRecord(
                historyId,
                storedAt,
                student,
                gradingResult(readingId, answerKeyId, answerKeyVersion)
        );
    }

    private static OmrGradingResult gradingResult(
            String readingId,
            String answerKeyId,
            int answerKeyVersion
    ) {
        OmrQuestionResult question = new OmrQuestionResult(
                1,
                "question-001",
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        new OmrQuestionResult.Option(
                                "question-001-option-01",
                                "A"
                        )
                ),
                0.98
        );
        OmrReadingResult reading = new OmrReadingResult(
                readingId,
                1_500L,
                "omr-flex-ad-q001-r001",
                3,
                "Folha flexível",
                Collections.singletonList(question)
        );
        return new OmrReadingGrader().grade(
                reading,
                answerKey(answerKeyId, answerKeyVersion)
        );
    }

    private static OmrAnswerKeyDefinition answerKey(String id, int version) {
        return new OmrAnswerKeyDefinition(
                id,
                version,
                "Gabarito",
                "omr-flex-ad-q001-r001",
                3,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        )
                )
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static void expectIllegalState(
            String messagePart,
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalStateException.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(messagePart));
        }
    }

    private static final class FakeAttempts
            implements OmrAssessmentAttemptRepository {
        private final List<OmrAssessmentAttempt> records = new ArrayList<>();
        private int saveCalls;
        private boolean rejectWithoutRecord;
        private boolean storeButReportRefusal;
        private boolean storeThenThrowConstraint;
        private boolean throwConstraintWithoutRecord;

        @Override
        public boolean save(OmrAssessmentAttempt attempt) {
            saveCalls++;
            if (rejectWithoutRecord) {
                return false;
            }
            if (throwConstraintWithoutRecord) {
                throw new IllegalArgumentException("constraint");
            }
            records.add(attempt);
            if (storeThenThrowConstraint) {
                throw new IllegalArgumentException("constraint");
            }
            return !storeButReportRefusal;
        }

        @Override
        public List<OmrAssessmentAttempt> loadForApplication(String id) {
            List<OmrAssessmentAttempt> matches = new ArrayList<>();
            for (OmrAssessmentAttempt attempt : records) {
                if (attempt.belongsToApplication(id)) {
                    matches.add(attempt);
                }
            }
            return Collections.unmodifiableList(matches);
        }

        @Override
        public List<OmrAssessmentAttempt> loadForParticipant(
                String applicationId, String studentId
        ) {
            List<OmrAssessmentAttempt> matches = new ArrayList<>();
            for (OmrAssessmentAttempt attempt : records) {
                if (attempt.belongsToParticipant(applicationId, studentId)) {
                    matches.add(attempt);
                }
            }
            return Collections.unmodifiableList(matches);
        }

        @Override
        public OmrAssessmentAttempt findLatestForParticipantOrNull(
                String applicationId, String studentId
        ) {
            OmrAssessmentAttempt latest = null;
            for (OmrAssessmentAttempt attempt
                    : loadForParticipant(applicationId, studentId)) {
                if (latest == null
                        || attempt.getRevision() > latest.getRevision()) {
                    latest = attempt;
                }
            }
            return latest;
        }

        @Override
        public OmrAssessmentAttempt findByIdOrNull(String attemptId) {
            for (OmrAssessmentAttempt attempt : records) {
                if (attempt.hasAttemptId(attemptId)) {
                    return attempt;
                }
            }
            return null;
        }

        @Override
        public OmrAssessmentAttempt findByHistoryRecordIdOrNull(
                String historyRecordId
        ) {
            for (OmrAssessmentAttempt attempt : records) {
                if (attempt.referencesHistoryRecord(historyRecordId)) {
                    return attempt;
                }
            }
            return null;
        }
    }
}
