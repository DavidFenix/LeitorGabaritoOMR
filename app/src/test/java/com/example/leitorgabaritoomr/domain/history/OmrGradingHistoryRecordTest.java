package com.example.leitorgabaritoomr.domain.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;

public final class OmrGradingHistoryRecordTest {

    private static final double DELTA = 0.000001;
    private static final long CAPTURED_AT =
            1_800_000_000_000L;
    private static final long STORED_AT =
            1_800_000_000_500L;

    @Test
    public void preservesStudentAndCompleteGradingResult() {
        OmrStudentIdentity student = student();
        OmrGradingResult gradingResult = gradingResult();

        OmrGradingHistoryRecord record = record(
                "history-001",
                student,
                gradingResult
        );

        assertEquals(
                "history-001",
                record.getHistoryRecordId()
        );
        assertEquals(
                STORED_AT,
                record.getStoredAtEpochMillis()
        );
        assertSame(student, record.getStudent());
        assertSame(
                gradingResult,
                record.getGradingResult()
        );
        assertSame(
                gradingResult.getReadingResult(),
                record.getReadingResult()
        );
        assertSame(
                gradingResult.getAnswerKeyDefinition(),
                record.getAnswerKeyDefinition()
        );
    }

    @Test
    public void exposesReadingAndAnswerKeySnapshotMetadata() {
        OmrGradingHistoryRecord record = record(
                "history-001",
                student(),
                gradingResult()
        );

        assertEquals("reading-001", record.getReadingId());
        assertEquals(
                CAPTURED_AT,
                record.getCapturedAtEpochMillis()
        );
        assertEquals(
                "answer-key-001",
                record.getAnswerKeyId()
        );
        assertEquals(2, record.getAnswerKeyVersion());
        assertEquals(
                "Avaliacao de Matematica",
                record.getAnswerKeyName()
        );
    }

    @Test
    public void exposesPointsPercentageAndReviewState() {
        OmrGradingHistoryRecord record = record(
                "history-001",
                student(),
                gradingResult()
        );

        assertEquals(2.0, record.getAwardedPoints(), DELTA);
        assertEquals(3.0, record.getPossiblePoints(), DELTA);
        assertEquals(
                200.0 / 3.0,
                record.getAwardedPercentage(),
                DELTA
        );
        assertTrue(record.requiresReview());
        assertFalse(record.isFinal());
    }

    @Test
    public void recognizesStudentByNormalizedStableId() {
        OmrGradingHistoryRecord record = record(
                "history-001",
                student(),
                gradingResult()
        );

        assertTrue(record.belongsToStudent(" student-001 "));
        assertFalse(record.belongsToStudent("student-002"));
        assertFalse(record.belongsToStudent(null));
    }

    @Test
    public void identityUsesOnlyHistoryRecordId() {
        OmrGradingHistoryRecord first = record(
                "history-001",
                student(),
                gradingResult()
        );

        OmrGradingHistoryRecord sameIdentity = record(
                " history-001 ",
                new OmrStudentIdentity(
                        "student-002",
                        "000999",
                        "Outro aluno",
                        "8 B"
                ),
                gradingResult()
        );

        OmrGradingHistoryRecord anotherRecord = record(
                "history-002",
                student(),
                gradingResult()
        );

        assertEquals(first, sameIdentity);
        assertEquals(
                first.hashCode(),
                sameIdentity.hashCode()
        );
        assertNotEquals(first, anotherRecord);
    }

    @Test
    public void createGeneratesIdAndStorageInstant() {
        long beforeCreation = System.currentTimeMillis();

        OmrGradingHistoryRecord record =
                OmrGradingHistoryRecord.create(
                        student(),
                        gradingResult()
                );

        long afterCreation = System.currentTimeMillis();

        assertNotNull(record.getHistoryRecordId());
        assertFalse(
                record.getHistoryRecordId().trim().isEmpty()
        );
        assertTrue(
                record.getStoredAtEpochMillis()
                        >= beforeCreation
        );
        assertTrue(
                record.getStoredAtEpochMillis()
                        <= afterCreation
        );
    }

    @Test
    public void rejectsInvalidRecordId() {
        expectIllegalArgument(() ->
                new OmrGradingHistoryRecord(
                        " ",
                        STORED_AT,
                        student(),
                        gradingResult()
                )
        );

        expectIllegalArgument(() ->
                new OmrGradingHistoryRecord(
                        null,
                        STORED_AT,
                        student(),
                        gradingResult()
                )
        );
    }

    @Test
    public void rejectsInvalidStorageInstant() {
        expectIllegalArgument(() ->
                new OmrGradingHistoryRecord(
                        "history-001",
                        0L,
                        student(),
                        gradingResult()
                )
        );
    }

    @Test
    public void rejectsMissingStudentOrGradingResult() {
        expectIllegalArgument(() ->
                new OmrGradingHistoryRecord(
                        "history-001",
                        STORED_AT,
                        null,
                        gradingResult()
                )
        );

        expectIllegalArgument(() ->
                new OmrGradingHistoryRecord(
                        "history-001",
                        STORED_AT,
                        student(),
                        null
                )
        );
    }

    @Test
    public void completeSnapshotSurvivesSerialization()
            throws Exception {

        OmrGradingHistoryRecord original = record(
                "history-001",
                student(),
                gradingResult()
        );

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrGradingHistoryRecord restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrGradingHistoryRecord)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getStoredAtEpochMillis(),
                restored.getStoredAtEpochMillis()
        );
        assertEquals(
                original.getStudent().getRegistration(),
                restored.getStudent().getRegistration()
        );
        assertEquals(
                original.getStudent().getName(),
                restored.getStudent().getName()
        );
        assertEquals(
                original.getStudent().getClassName(),
                restored.getStudent().getClassName()
        );
        assertEquals(
                original.getGradingResult(),
                restored.getGradingResult()
        );
    }

    private OmrGradingHistoryRecord record(
            String historyRecordId,
            OmrStudentIdentity student,
            OmrGradingResult gradingResult
    ) {
        return new OmrGradingHistoryRecord(
                historyRecordId,
                STORED_AT,
                student,
                gradingResult
        );
    }

    private OmrStudentIdentity student() {
        return new OmrStudentIdentity(
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A"
        );
    }

    private OmrGradingResult gradingResult() {
        OmrReadingResult reading = new OmrReadingResult(
                "reading-001",
                CAPTURED_AT,
                "layout-controlled",
                1,
                "Layout controlado",
                Arrays.asList(
                        singleMark(1, "Q01", "A", 0.96),
                        ambiguous(2, "Q02", "C", 0.52)
                )
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-001",
                        2,
                        "Avaliacao de Matematica",
                        "layout-controlled",
                        1,
                        Arrays.asList(
                                entry("Q01", "A", 2.0),
                                entry("Q02", "B", 1.0)
                        )
                );

        return new OmrReadingGrader().grade(
                reading,
                answerKey
        );
    }

    private OmrAnswerKeyEntry entry(
            String questionId,
            String optionId,
            double weight
    ) {
        return OmrAnswerKeyEntry.singleAnswer(
                questionId,
                optionId,
                weight
        );
    }

    private OmrQuestionResult singleMark(
            int position,
            String questionId,
            String optionId,
            double confidence
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        option(optionId)
                ),
                confidence
        );
    }

    private OmrQuestionResult ambiguous(
            int position,
            String questionId,
            String optionId,
            double confidence
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.AMBIGUOUS,
                Collections.singletonList(
                        option(optionId)
                ),
                confidence
        );
    }

    private OmrQuestionResult.Option option(
            String id
    ) {
        return new OmrQuestionResult.Option(id, id);
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
