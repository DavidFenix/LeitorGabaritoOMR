package com.example.leitorgabaritoomr.domain.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrReadingGraderTest {

    private static final double DELTA = 0.000001;

    private final OmrReadingGrader grader =
            new OmrReadingGrader();

    @Test
    public void completeCorrectionCalculatesAllCountsAndPoints() {
        OmrReadingResult reading = controlledReading();
        OmrAnswerKeyDefinition answerKey =
                controlledAnswerKeyInDifferentOrder();

        OmrGradingResult result = grader.grade(
                reading,
                answerKey
        );

        assertEquals(6, result.getQuestionCount());
        assertEquals(1, result.getCorrectCount());
        assertEquals(1, result.getIncorrectCount());
        assertEquals(1, result.getBlankCount());
        assertEquals(1, result.getMultipleMarkCount());
        assertEquals(1, result.getAmbiguousCount());
        assertEquals(1, result.getNotReadyCount());

        assertEquals(2, result.getReviewRequiredCount());
        assertEquals(3, result.getFinalQuestionCount());
        assertEquals(3, result.getUnresolvedCount());

        assertEquals(7.0, result.getPossiblePoints(), DELTA);
        assertEquals(2.0, result.getAwardedPoints(), DELTA);
        assertEquals(2.0 / 7.0, result.getAwardedFraction(), DELTA);
        assertEquals(
                200.0 / 7.0,
                result.getAwardedPercentage(),
                DELTA
        );

        assertFalse(result.isComplete());
        assertTrue(result.requiresReview());
        assertFalse(result.isFinal());
    }

    @Test
    public void answerKeyOrderDoesNotChangeQuestionMatching() {
        OmrGradingResult result = grader.grade(
                controlledReading(),
                controlledAnswerKeyInDifferentOrder()
        );

        assertEquals(
                "Q01",
                result.getQuestionAtPosition(1).getQuestionId()
        );

        assertEquals(
                OmrQuestionGrade.Status.CORRECT,
                result.getQuestionAtPosition(1).getStatus()
        );

        assertEquals(
                "Q06",
                result.getQuestionAtPosition(6).getQuestionId()
        );

        assertEquals(
                OmrQuestionGrade.Status.NOT_READY,
                result.getQuestionAtPosition(6).getStatus()
        );
    }

    @Test
    public void resolvedReadingProducesFinalResult() {
        OmrReadingResult reading = reading(
                "layout-controlled",
                1,
                Arrays.asList(
                        singleMark(1, "Q01", "A", 0.95),
                        singleMark(2, "Q02", "B", 0.91),
                        question(
                                3,
                                "Q03",
                                OmrQuestionResult.Status.BLANK,
                                0.98
                        )
                )
        );

        OmrAnswerKeyDefinition answerKey = answerKey(
                "layout-controlled",
                1,
                Arrays.asList(
                        entry("Q01", 2.0, "A"),
                        entry("Q02", 1.0, "C"),
                        entry("Q03", 1.0, "D")
                )
        );

        OmrGradingResult result = grader.grade(
                reading,
                answerKey
        );

        assertEquals(1, result.getCorrectCount());
        assertEquals(1, result.getIncorrectCount());
        assertEquals(1, result.getBlankCount());
        assertEquals(2.0, result.getAwardedPoints(), DELTA);
        assertEquals(4.0, result.getPossiblePoints(), DELTA);
        assertEquals(50.0, result.getAwardedPercentage(), DELTA);

        assertTrue(result.isComplete());
        assertFalse(result.requiresReview());
        assertTrue(result.isFinal());
    }

    @Test
    public void differentLayoutIdIsRejected() {
        OmrReadingResult reading = controlledReading();

        OmrAnswerKeyDefinition answerKey = answerKey(
                "another-layout",
                1,
                controlledAnswerKeyEntries()
        );

        expectIllegalArgument(() ->
                grader.grade(reading, answerKey)
        );
    }

    @Test
    public void differentLayoutVersionIsRejected() {
        OmrReadingResult reading = controlledReading();

        OmrAnswerKeyDefinition answerKey = answerKey(
                "layout-controlled",
                2,
                controlledAnswerKeyEntries()
        );

        expectIllegalArgument(() ->
                grader.grade(reading, answerKey)
        );
    }

    @Test
    public void differentQuestionCountIsRejected() {
        OmrReadingResult reading = controlledReading();

        OmrAnswerKeyDefinition answerKey = answerKey(
                "layout-controlled",
                1,
                Collections.singletonList(
                        entry("Q01", 1.0, "A")
                )
        );

        expectIllegalArgument(() ->
                grader.grade(reading, answerKey)
        );
    }

    @Test
    public void missingQuestionIsRejectedEvenWhenCountMatches() {
        OmrReadingResult reading = reading(
                "layout-controlled",
                1,
                Arrays.asList(
                        singleMark(1, "Q01", "A", 0.9),
                        singleMark(2, "Q02", "B", 0.9)
                )
        );

        OmrAnswerKeyDefinition answerKey = answerKey(
                "layout-controlled",
                1,
                Arrays.asList(
                        entry("Q01", 1.0, "A"),
                        entry("Q99", 1.0, "B")
                )
        );

        expectIllegalArgument(() ->
                grader.grade(reading, answerKey)
        );
    }

    @Test
    public void resultCollectionsAreImmutable() {
        OmrGradingResult result = grader.grade(
                controlledReading(),
                controlledAnswerKeyInDifferentOrder()
        );

        expectUnsupportedOperation(() ->
                result.getQuestionGrades().clear()
        );

        expectUnsupportedOperation(() ->
                result.getCountByStatus().put(
                        OmrQuestionGrade.Status.CORRECT,
                        100
                )
        );
    }

    @Test
    public void resultPreservesSourcesAndSupportsLookup() {
        OmrReadingResult reading = controlledReading();
        OmrAnswerKeyDefinition answerKey =
                controlledAnswerKeyInDifferentOrder();

        OmrGradingResult result = grader.grade(
                reading,
                answerKey
        );

        assertSame(reading, result.getReadingResult());
        assertSame(answerKey, result.getAnswerKeyDefinition());

        assertEquals(
                "Q04",
                result.findByQuestionId(" Q04 ")
                        .getQuestionId()
        );

        assertNull(result.findByQuestionId("Q99"));
        assertNull(result.findByQuestionId(null));
        assertNull(result.getQuestionAtPosition(0));
        assertNull(result.getQuestionAtPosition(7));
    }

    private OmrReadingResult controlledReading() {
        return reading(
                "layout-controlled",
                1,
                Arrays.asList(
                        singleMark(1, "Q01", "A", 0.96),
                        singleMark(2, "Q02", "B", 0.92),
                        question(
                                3,
                                "Q03",
                                OmrQuestionResult.Status.BLANK,
                                0.98
                        ),
                        question(
                                4,
                                "Q04",
                                OmrQuestionResult.Status.MULTIPLE_MARKS,
                                0.70,
                                option("A"),
                                option("C")
                        ),
                        question(
                                5,
                                "Q05",
                                OmrQuestionResult.Status.AMBIGUOUS,
                                0.52,
                                option("D")
                        ),
                        question(
                                6,
                                "Q06",
                                OmrQuestionResult.Status.NOT_READY,
                                0.0
                        )
                )
        );
    }

    private OmrAnswerKeyDefinition
    controlledAnswerKeyInDifferentOrder() {
        return answerKey(
                "layout-controlled",
                1,
                Arrays.asList(
                        entry("Q04", 1.0, "A"),
                        entry("Q02", 1.0, "C"),
                        entry("Q06", 1.0, "B"),
                        entry("Q01", 2.0, "A"),
                        entry("Q05", 1.0, "D"),
                        entry("Q03", 1.0, "B")
                )
        );
    }

    private List<OmrAnswerKeyEntry>
    controlledAnswerKeyEntries() {
        return Arrays.asList(
                entry("Q01", 2.0, "A"),
                entry("Q02", 1.0, "C"),
                entry("Q03", 1.0, "B"),
                entry("Q04", 1.0, "A"),
                entry("Q05", 1.0, "D"),
                entry("Q06", 1.0, "B")
        );
    }

    private OmrReadingResult reading(
            String layoutId,
            int layoutVersion,
            List<OmrQuestionResult> questions
    ) {
        return new OmrReadingResult(
                "reading-controlled",
                1_800_000_000_000L,
                layoutId,
                layoutVersion,
                "Layout controlado",
                questions
        );
    }

    private OmrAnswerKeyDefinition answerKey(
            String layoutId,
            int layoutVersion,
            List<OmrAnswerKeyEntry> entries
    ) {
        return new OmrAnswerKeyDefinition(
                "answer-key-controlled",
                1,
                "Gabarito controlado",
                layoutId,
                layoutVersion,
                entries
        );
    }

    private OmrAnswerKeyEntry entry(
            String questionId,
            double weight,
            String... acceptedOptionIds
    ) {
        return new OmrAnswerKeyEntry(
                questionId,
                Arrays.asList(acceptedOptionIds),
                weight
        );
    }

    private OmrQuestionResult singleMark(
            int position,
            String questionId,
            String optionId,
            double confidence
    ) {
        return question(
                position,
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                confidence,
                option(optionId)
        );
    }

    private OmrQuestionResult question(
            int position,
            String questionId,
            OmrQuestionResult.Status status,
            double confidence,
            OmrQuestionResult.Option... options
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                status,
                options.length == 0
                        ? Collections.emptyList()
                        : Arrays.asList(options),
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

    private void expectUnsupportedOperation(
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
