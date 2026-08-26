package com.example.leitorgabaritoomr.domain.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class OmrQuestionGraderTest {

    private static final double DELTA = 0.000001;

    private final OmrQuestionGrader grader =
            new OmrQuestionGrader();

    @Test
    public void acceptedSingleMarkIsCorrectAndAwardsFullWeight() {
        OmrQuestionResult reading = singleMark(
                "Q01",
                "B",
                0.94
        );

        OmrAnswerKeyEntry answerKey = answerKey(
                "Q01",
                2.5,
                "B"
        );

        OmrQuestionGrade grade = grader.grade(
                reading,
                answerKey
        );

        assertEquals(
                OmrQuestionGrade.Status.CORRECT,
                grade.getStatus()
        );

        assertTrue(grade.isCorrect());
        assertTrue(grade.isFinal());
        assertFalse(grade.requiresReview());
        assertEquals(2.5, grade.getPossiblePoints(), DELTA);
        assertEquals(2.5, grade.getAwardedPoints(), DELTA);
    }

    @Test
    public void unacceptedSingleMarkIsIncorrectAndAwardsZero() {
        OmrQuestionGrade grade = grader.grade(
                singleMark("Q01", "A", 0.91),
                answerKey("Q01", 3.0, "C")
        );

        assertEquals(
                OmrQuestionGrade.Status.INCORRECT,
                grade.getStatus()
        );

        assertFalse(grade.isCorrect());
        assertTrue(grade.isFinal());
        assertEquals(3.0, grade.getPossiblePoints(), DELTA);
        assertEquals(0.0, grade.getAwardedPoints(), DELTA);
    }

    @Test
    public void anyConfiguredAlternativeCanBeAccepted() {
        OmrQuestionGrade grade = grader.grade(
                singleMark("Q01", "D", 0.88),
                answerKey("Q01", 1.0, "B", "D")
        );

        assertEquals(
                OmrQuestionGrade.Status.CORRECT,
                grade.getStatus()
        );

        assertEquals(1.0, grade.getAwardedPoints(), DELTA);
    }

    @Test
    public void blankReadingRemainsBlank() {
        OmrQuestionGrade grade = grader.grade(
                question(
                        "Q01",
                        OmrQuestionResult.Status.BLANK,
                        0.97
                ),
                answerKey("Q01", 1.0, "A")
        );

        assertEquals(
                OmrQuestionGrade.Status.BLANK,
                grade.getStatus()
        );

        assertTrue(grade.isFinal());
        assertFalse(grade.requiresReview());
        assertEquals(0.0, grade.getAwardedPoints(), DELTA);
    }

    @Test
    public void multipleMarksRemainAvailableForReview() {
        OmrQuestionGrade grade = grader.grade(
                question(
                        "Q01",
                        OmrQuestionResult.Status.MULTIPLE_MARKS,
                        0.72,
                        option("B"),
                        option("D")
                ),
                answerKey("Q01", 1.0, "B")
        );

        assertEquals(
                OmrQuestionGrade.Status.MULTIPLE_MARKS,
                grade.getStatus()
        );

        assertFalse(grade.isFinal());
        assertTrue(grade.requiresReview());
        assertEquals(2, grade.getRelevantOptions().size());
        assertEquals(0.0, grade.getAwardedPoints(), DELTA);
    }

    @Test
    public void ambiguousAndNotReadyStatesArePreserved() {
        OmrQuestionGrade ambiguous = grader.grade(
                question(
                        "Q01",
                        OmrQuestionResult.Status.AMBIGUOUS,
                        0.51,
                        option("C")
                ),
                answerKey("Q01", 1.0, "C")
        );

        OmrQuestionGrade notReady = grader.grade(
                question(
                        "Q02",
                        OmrQuestionResult.Status.NOT_READY,
                        0.0
                ),
                answerKey("Q02", 1.0, "A")
        );

        assertEquals(
                OmrQuestionGrade.Status.AMBIGUOUS,
                ambiguous.getStatus()
        );
        assertTrue(ambiguous.requiresReview());
        assertFalse(ambiguous.isFinal());

        assertEquals(
                OmrQuestionGrade.Status.NOT_READY,
                notReady.getStatus()
        );
        assertFalse(notReady.requiresReview());
        assertFalse(notReady.isFinal());
    }

    @Test
    public void gradeKeepsTheExactReadingAndAnswerKeyUsed() {
        OmrQuestionResult reading = singleMark(
                "Q01",
                "A",
                0.83
        );

        OmrAnswerKeyEntry answerKey = answerKey(
                "Q01",
                1.5,
                "A"
        );

        OmrQuestionGrade grade = grader.grade(
                reading,
                answerKey
        );

        assertSame(reading, grade.getReadingResult());
        assertSame(answerKey, grade.getAnswerKeyEntry());
        assertSame(
                reading.getSelectedOption(),
                grade.getSelectedOption()
        );
        assertEquals(0.83, grade.getConfidence(), DELTA);
        assertEquals("Q01", grade.getQuestionId());
        assertEquals(1, grade.getPosition());
    }

    @Test
    public void differentQuestionIdsAreRejected() {
        expectIllegalArgument(() ->
                grader.grade(
                        singleMark("Q01", "A", 0.9),
                        answerKey("Q02", 1.0, "A")
                )
        );
    }

    @Test
    public void gradeRejectsAStatusThatContradictsTheAnswerKey() {
        OmrQuestionResult reading = singleMark(
                "Q01",
                "A",
                0.9
        );

        OmrAnswerKeyEntry answerKey = answerKey(
                "Q01",
                1.0,
                "A"
        );

        expectIllegalArgument(() ->
                new OmrQuestionGrade(
                        reading,
                        answerKey,
                        OmrQuestionGrade.Status.INCORRECT
                )
        );
    }

    @Test
    public void graderRejectsNullArguments() {
        OmrQuestionResult reading = singleMark(
                "Q01",
                "A",
                0.9
        );

        OmrAnswerKeyEntry answerKey = answerKey(
                "Q01",
                1.0,
                "A"
        );

        expectIllegalArgument(() ->
                grader.grade(null, answerKey)
        );

        expectIllegalArgument(() ->
                grader.grade(reading, null)
        );
    }

    private OmrQuestionResult singleMark(
            String questionId,
            String optionId,
            double confidence
    ) {
        return question(
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                confidence,
                option(optionId)
        );
    }

    private OmrQuestionResult question(
            String questionId,
            OmrQuestionResult.Status status,
            double confidence,
            OmrQuestionResult.Option... options
    ) {
        return new OmrQuestionResult(
                1,
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
        return new OmrQuestionResult.Option(
                id,
                id
        );
    }

    private OmrAnswerKeyEntry answerKey(
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
