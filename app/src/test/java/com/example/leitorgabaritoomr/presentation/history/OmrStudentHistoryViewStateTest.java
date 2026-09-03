package com.example.leitorgabaritoomr.presentation.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrStudentHistoryViewStateTest {

    private static final double DELTA = 0.000001;

    @Test
    public void emptyHistoryPreservesStudentAndZeroCounts() {
        OmrStudentIdentity student = currentStudent();

        OmrStudentHistoryViewState state =
                OmrStudentHistoryViewState.from(
                        student,
                        Collections.emptyList()
                );

        assertSame(student, state.getStudent());
        assertTrue(state.isEmpty());
        assertEquals(0, state.getResultCount());
        assertEquals(0, state.getFinalResultCount());
        assertEquals(0, state.getReviewRequiredCount());
        assertEquals(0, state.getPendingCount());
        assertNull(state.getLatestItemOrNull());
    }

    @Test
    public void itemPreservesCompleteRecordData() {
        OmrStudentIdentity student = currentStudent();

        OmrGradingHistoryRecord record = record(
                "history-001",
                "reading-001",
                student,
                3_000L,
                OmrQuestionResult.Status.SINGLE_MARK
        );

        OmrStudentHistoryViewState.HistoryItem item =
                OmrStudentHistoryViewState
                        .from(
                                student,
                                Collections.singletonList(record)
                        )
                        .getLatestItemOrNull();

        assertEquals(
                "history-001",
                item.getHistoryRecordId()
        );
        assertEquals("reading-001", item.getReadingId());
        assertEquals(3_000L, item.getStoredAtEpochMillis());
        assertEquals(2_900L, item.getCapturedAtEpochMillis());
        assertEquals("answer-key-history", item.getAnswerKeyId());
        assertEquals(4, item.getAnswerKeyVersion());
        assertEquals(
                "Avaliacao de Matematica",
                item.getAnswerKeyName()
        );
        assertEquals(1.0, item.getAwardedPoints(), DELTA);
        assertEquals(1.0, item.getPossiblePoints(), DELTA);
        assertEquals(100.0, item.getAwardedPercentage(), DELTA);
        assertTrue(item.isFinal());
        assertFalse(item.requiresReview());
        assertFalse(item.isPending());
        assertSame(student, item.getStudentSnapshot());
        assertSame(record.getGradingResult(), item.getGradingResult());
    }

    @Test
    public void ordersNewestFirstAndPreservesTieOrder() {
        OmrStudentIdentity student = currentStudent();

        OmrGradingHistoryRecord oldest = record(
                "history-oldest",
                "reading-oldest",
                student,
                1_000L,
                OmrQuestionResult.Status.SINGLE_MARK
        );

        OmrGradingHistoryRecord firstTie = record(
                "history-first-tie",
                "reading-first-tie",
                student,
                3_000L,
                OmrQuestionResult.Status.SINGLE_MARK
        );

        OmrGradingHistoryRecord secondTie = record(
                "history-second-tie",
                "reading-second-tie",
                student,
                3_000L,
                OmrQuestionResult.Status.SINGLE_MARK
        );

        List<OmrGradingHistoryRecord> source =
                Arrays.asList(
                        oldest,
                        firstTie,
                        secondTie
                );

        OmrStudentHistoryViewState state =
                OmrStudentHistoryViewState.from(
                        student,
                        source
                );

        assertEquals(
                "history-first-tie",
                idAt(state, 0)
        );
        assertEquals(
                "history-second-tie",
                idAt(state, 1)
        );
        assertEquals("history-oldest", idAt(state, 2));

        assertSame(oldest, source.get(0));
        assertSame(firstTie, source.get(1));
        assertSame(secondTie, source.get(2));
    }

    @Test
    public void classifiesFinalReviewAndPendingResults() {
        OmrStudentIdentity student = currentStudent();

        OmrStudentHistoryViewState state =
                OmrStudentHistoryViewState.from(
                        student,
                        Arrays.asList(
                                record(
                                        "history-final",
                                        "reading-final",
                                        student,
                                        3_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                ),
                                record(
                                        "history-review",
                                        "reading-review",
                                        student,
                                        2_000L,
                                        OmrQuestionResult.Status.AMBIGUOUS
                                ),
                                record(
                                        "history-pending",
                                        "reading-pending",
                                        student,
                                        1_000L,
                                        OmrQuestionResult.Status.NOT_READY
                                )
                        )
                );

        assertEquals(3, state.getResultCount());
        assertEquals(1, state.getFinalResultCount());
        assertEquals(1, state.getReviewRequiredCount());
        assertEquals(1, state.getPendingCount());

        assertTrue(
                state.findItemOrNull("history-final")
                        .isFinal()
        );
        assertTrue(
                state.findItemOrNull("history-review")
                        .requiresReview()
        );
        assertTrue(
                state.findItemOrNull("history-pending")
                        .isPending()
        );
    }

    @Test
    public void acceptsHistoricalSnapshotWithSameStableStudentId() {
        OmrStudentIdentity current = currentStudent();

        OmrStudentIdentity historicalSnapshot =
                new OmrStudentIdentity(
                        current.getStudentId(),
                        "000123",
                        "Ana B. Costa",
                        "8 A"
                );

        OmrStudentHistoryViewState state =
                OmrStudentHistoryViewState.from(
                        current,
                        Collections.singletonList(
                                record(
                                        "history-old-snapshot",
                                        "reading-old-snapshot",
                                        historicalSnapshot,
                                        2_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                )
                        )
                );

        assertSame(current, state.getStudent());
        assertEquals(
                "Ana B. Costa",
                state.getLatestItemOrNull()
                        .getStudentSnapshot()
                        .getName()
        );
        assertEquals(
                "8 A",
                state.getLatestItemOrNull()
                        .getStudentSnapshot()
                        .getClassName()
        );
    }

    @Test
    public void findsItemByNormalizedId() {
        OmrStudentIdentity student = currentStudent();

        OmrStudentHistoryViewState state =
                OmrStudentHistoryViewState.from(
                        student,
                        Collections.singletonList(
                                record(
                                        "history-001",
                                        "reading-001",
                                        student,
                                        2_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                )
                        )
                );

        assertEquals(
                "history-001",
                state.findItemOrNull(" history-001 ")
                        .getHistoryRecordId()
        );
        assertNull(state.findItemOrNull("missing"));
        assertNull(state.findItemOrNull(null));
    }

    @Test
    public void returnedHistoryListIsImmutable() {
        OmrStudentIdentity student = currentStudent();

        OmrStudentHistoryViewState state =
                OmrStudentHistoryViewState.from(
                        student,
                        Collections.singletonList(
                                record(
                                        "history-001",
                                        "reading-001",
                                        student,
                                        2_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                )
                        )
                );

        expectUnsupportedOperation(() ->
                state.getHistoryItems().clear()
        );
    }

    @Test
    public void rejectsMissingInputsAndNullRecord() {
        expectIllegalArgument(() ->
                OmrStudentHistoryViewState.from(
                        null,
                        Collections.emptyList()
                )
        );

        expectIllegalArgument(() ->
                OmrStudentHistoryViewState.from(
                        currentStudent(),
                        null
                )
        );

        expectIllegalArgument(() ->
                OmrStudentHistoryViewState.from(
                        currentStudent(),
                        Collections.singletonList(null)
                )
        );
    }

    @Test
    public void rejectsRecordBelongingToAnotherStudent() {
        OmrStudentIdentity current = currentStudent();

        OmrStudentIdentity anotherStudent =
                new OmrStudentIdentity(
                        "manual:000999",
                        "000999",
                        "Bruno Lima",
                        "9 B"
                );

        expectIllegalArgument(() ->
                OmrStudentHistoryViewState.from(
                        current,
                        Collections.singletonList(
                                record(
                                        "history-other",
                                        "reading-other",
                                        anotherStudent,
                                        2_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                )
                        )
                )
        );
    }

    @Test
    public void rejectsRepeatedRecordOrReadingIdentity() {
        OmrStudentIdentity student = currentStudent();

        expectIllegalArgument(() ->
                OmrStudentHistoryViewState.from(
                        student,
                        Arrays.asList(
                                record(
                                        "history-repeated",
                                        "reading-001",
                                        student,
                                        1_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                ),
                                record(
                                        "history-repeated",
                                        "reading-002",
                                        student,
                                        2_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                )
                        )
                )
        );

        expectIllegalArgument(() ->
                OmrStudentHistoryViewState.from(
                        student,
                        Arrays.asList(
                                record(
                                        "history-001",
                                        "reading-repeated",
                                        student,
                                        1_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                ),
                                record(
                                        "history-002",
                                        "reading-repeated",
                                        student,
                                        2_000L,
                                        OmrQuestionResult.Status.SINGLE_MARK
                                )
                        )
                )
        );
    }

    private static OmrStudentIdentity currentStudent() {
        return new OmrStudentIdentity(
                "manual:000123",
                "000123",
                "Ana Beatriz",
                "9 A"
        );
    }

    private static OmrGradingHistoryRecord record(
            String historyRecordId,
            String readingId,
            OmrStudentIdentity student,
            long storedAtEpochMillis,
            OmrQuestionResult.Status status
    ) {
        OmrQuestionResult question = question(status);

        OmrReadingResult reading =
                new OmrReadingResult(
                        readingId,
                        storedAtEpochMillis - 100L,
                        "layout-history",
                        1,
                        "Layout do historico",
                        Collections.singletonList(question)
                );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-history",
                        4,
                        "Avaliacao de Matematica",
                        "layout-history",
                        1,
                        Collections.singletonList(
                                OmrAnswerKeyEntry.singleAnswer(
                                        "Q01",
                                        "A",
                                        1.0
                                )
                        )
                );

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        reading,
                        answerKey
                );

        return new OmrGradingHistoryRecord(
                historyRecordId,
                storedAtEpochMillis,
                student,
                gradingResult
        );
    }

    private static OmrQuestionResult question(
            OmrQuestionResult.Status status
    ) {
        if (status == OmrQuestionResult.Status.NOT_READY) {
            return new OmrQuestionResult(
                    1,
                    "Q01",
                    status,
                    Collections.emptyList(),
                    0.0
            );
        }

        double confidence =
                status == OmrQuestionResult.Status.AMBIGUOUS
                        ? 0.52
                        : 0.97;

        return new OmrQuestionResult(
                1,
                "Q01",
                status,
                Collections.singletonList(
                        new OmrQuestionResult.Option(
                                "A",
                                "A"
                        )
                ),
                confidence
        );
    }

    private static String idAt(
            OmrStudentHistoryViewState state,
            int index
    ) {
        return state.getHistoryItems()
                .get(index)
                .getHistoryRecordId();
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

    private static void expectUnsupportedOperation(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
