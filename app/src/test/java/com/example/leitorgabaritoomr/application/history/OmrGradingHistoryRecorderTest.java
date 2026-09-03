package com.example.leitorgabaritoomr.application.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

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

public final class OmrGradingHistoryRecorderTest {

    private static final long CAPTURED_AT =
            1_800_000_000_000L;

    @Test
    public void rejectsMissingRepository() {
        expectIllegalArgument(() ->
                new OmrGradingHistoryRecorder(null)
        );
    }

    @Test
    public void rejectsMissingStudentOrGradingResult() {
        FakeRepository repository =
                new FakeRepository();

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        expectIllegalArgument(() ->
                recorder.record(
                        null,
                        gradingResult("reading-001", "A")
                )
        );

        expectIllegalArgument(() ->
                recorder.record(
                        student("student-001", "000123"),
                        null
                )
        );

        assertEquals(0, repository.getSaveCallCount());
        assertEquals(0, repository.loadAll().size());
    }

    @Test
    public void recordsNewGradingResultOnlyOnce() {
        FakeRepository repository =
                new FakeRepository();

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        OmrStudentIdentity student =
                student("student-001", "000123");

        OmrGradingResult gradingResult =
                gradingResult("reading-001", "A");

        OmrGradingHistoryRecord record =
                recorder.record(student, gradingResult);

        assertNotNull(record);
        assertSame(student, record.getStudent());
        assertSame(
                gradingResult,
                record.getGradingResult()
        );
        assertEquals("reading-001", record.getReadingId());
        assertEquals(1, repository.getSaveCallCount());
        assertEquals(1, repository.loadAll().size());
        assertSame(
                record,
                repository.findByReadingIdOrNull(
                        "reading-001"
                )
        );
    }

    @Test
    public void repeatedCallReturnsOriginalWithoutSecondSave() {
        FakeRepository repository =
                new FakeRepository();

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        OmrStudentIdentity student =
                student("student-001", "000123");

        OmrGradingResult gradingResult =
                gradingResult("reading-001", "A");

        OmrGradingHistoryRecord first =
                recorder.record(student, gradingResult);

        OmrGradingHistoryRecord repeated =
                recorder.record(student, gradingResult);

        assertSame(first, repeated);
        assertEquals(1, repository.getSaveCallCount());
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    public void rejectsReadingAlreadyLinkedToAnotherStudent() {
        FakeRepository repository =
                new FakeRepository();

        OmrGradingResult gradingResult =
                gradingResult("reading-001", "A");

        repository.addExisting(
                OmrGradingHistoryRecord.create(
                        student("student-002", "000999"),
                        gradingResult
                )
        );

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        expectIllegalState(
                "outro aluno",
                () -> recorder.record(
                        student("student-001", "000123"),
                        gradingResult
                )
        );

        assertEquals(0, repository.getSaveCallCount());
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    public void rejectsReadingAlreadyStoredWithAnotherResult() {
        FakeRepository repository =
                new FakeRepository();

        OmrStudentIdentity student =
                student("student-001", "000123");

        repository.addExisting(
                OmrGradingHistoryRecord.create(
                        student,
                        gradingResult("reading-001", "B")
                )
        );

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        expectIllegalState(
                "outro resultado",
                () -> recorder.record(
                        student,
                        gradingResult("reading-001", "A")
                )
        );

        assertEquals(0, repository.getSaveCallCount());
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    public void concurrentEquivalentSaveReturnsWinningRecord() {
        FakeRepository repository =
                new FakeRepository();

        OmrStudentIdentity student =
                student("student-001", "000123");

        OmrGradingResult gradingResult =
                gradingResult("reading-001", "A");

        OmrGradingHistoryRecord concurrentWinner =
                OmrGradingHistoryRecord.create(
                        student,
                        gradingResult
                );

        repository.simulateConcurrentWinner(
                concurrentWinner
        );

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        OmrGradingHistoryRecord returned =
                recorder.record(student, gradingResult);

        assertSame(concurrentWinner, returned);
        assertEquals(1, repository.getSaveCallCount());
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    public void repositoryRefusalWithoutPreservedRecordFails() {
        FakeRepository repository =
                new FakeRepository();

        repository.rejectNextSaveWithoutRecord();

        OmrGradingHistoryRecorder recorder =
                new OmrGradingHistoryRecorder(repository);

        expectIllegalState(
                "sem preservar",
                () -> recorder.record(
                        student("student-001", "000123"),
                        gradingResult("reading-001", "A")
                )
        );

        assertEquals(1, repository.getSaveCallCount());
        assertEquals(0, repository.loadAll().size());
    }

    private static OmrStudentIdentity student(
            String studentId,
            String registration
    ) {
        return new OmrStudentIdentity(
                studentId,
                registration,
                "Ana Beatriz",
                "9 A"
        );
    }

    private static OmrGradingResult gradingResult(
            String readingId,
            String selectedOptionId
    ) {
        OmrQuestionResult question =
                new OmrQuestionResult(
                        1,
                        "Q01",
                        OmrQuestionResult.Status.SINGLE_MARK,
                        Collections.singletonList(
                                new OmrQuestionResult.Option(
                                        selectedOptionId,
                                        selectedOptionId
                                )
                        ),
                        0.96
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
                        "answer-key-001",
                        2,
                        "Avaliacao de Matematica",
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

    private static void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void expectIllegalState(
            String expectedMessagePart,
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();

            if (message == null
                    || !message.contains(expectedMessagePart)) {

                fail(
                        "Mensagem inesperada: "
                                + message
                );
            }
        }
    }

    private static final class FakeRepository
            implements OmrGradingHistoryRepository {

        private final List<OmrGradingHistoryRecord> records =
                new ArrayList<>();

        private int saveCallCount;
        private boolean rejectWithoutRecord;
        private OmrGradingHistoryRecord concurrentWinner;

        @Override
        public boolean save(
                OmrGradingHistoryRecord record
        ) {
            if (record == null) {
                throw new IllegalArgumentException(
                        "O registro e obrigatorio."
                );
            }

            saveCallCount++;

            if (concurrentWinner != null) {
                addExisting(concurrentWinner);
                concurrentWinner = null;
                return false;
            }

            if (rejectWithoutRecord) {
                rejectWithoutRecord = false;
                return false;
            }

            if (findByIdOrNull(
                    record.getHistoryRecordId()
            ) != null
                    || findByReadingIdOrNull(
                    record.getReadingId()
            ) != null) {

                return false;
            }

            records.add(0, record);
            return true;
        }

        @Override
        public List<OmrGradingHistoryRecord> loadAll() {
            return Collections.unmodifiableList(
                    new ArrayList<>(records)
            );
        }

        @Override
        public OmrGradingHistoryRecord findByIdOrNull(
                String historyRecordId
        ) {
            if (historyRecordId == null) {
                return null;
            }

            String normalizedId =
                    historyRecordId.trim();

            for (OmrGradingHistoryRecord record : records) {
                if (record.getHistoryRecordId().equals(
                        normalizedId
                )) {
                    return record;
                }
            }

            return null;
        }

        @Override
        public OmrGradingHistoryRecord findByReadingIdOrNull(
                String readingId
        ) {
            if (readingId == null) {
                return null;
            }

            String normalizedId = readingId.trim();

            for (OmrGradingHistoryRecord record : records) {
                if (record.getReadingId().equals(
                        normalizedId
                )) {
                    return record;
                }
            }

            return null;
        }

        @Override
        public List<OmrGradingHistoryRecord> loadByStudentId(
                String studentId
        ) {
            if (studentId == null
                    || studentId.trim().isEmpty()) {

                return Collections.emptyList();
            }

            List<OmrGradingHistoryRecord> matches =
                    new ArrayList<>();

            for (OmrGradingHistoryRecord record : records) {
                if (record.belongsToStudent(studentId)) {
                    matches.add(record);
                }
            }

            return Collections.unmodifiableList(matches);
        }

        private void addExisting(
                OmrGradingHistoryRecord record
        ) {
            records.add(0, record);
        }

        private void simulateConcurrentWinner(
                OmrGradingHistoryRecord record
        ) {
            concurrentWinner = record;
        }

        private void rejectNextSaveWithoutRecord() {
            rejectWithoutRecord = true;
        }

        private int getSaveCallCount() {
            return saveCallCount;
        }
    }
}
