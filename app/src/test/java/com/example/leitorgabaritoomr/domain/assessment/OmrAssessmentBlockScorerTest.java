package com.example.leitorgabaritoomr.domain.assessment;

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
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentBlockScorerTest {

    private final OmrAssessmentBlockScorer scorer =
            new OmrAssessmentBlockScorer();

    @Test
    public void calculatesCountsAndScoresForEveryBlock() {
        OmrAssessmentBlockBreakdown breakdown = scorer.calculate(
                multidisciplinaryStructure(),
                gradingResult()
        );

        assertEquals(2, breakdown.getBlockCount());

        OmrAssessmentBlockResult chemistry =
                breakdown.findResultByBlockId("chemistry");

        assertEquals(3, chemistry.getQuestionCount());
        assertEquals(1, chemistry.getCorrectCount());
        assertEquals(1, chemistry.getIncorrectCount());
        assertEquals(1, chemistry.getBlankCount());
        assertEquals(0, chemistry.getUnresolvedCount());
        assertEquals(2.0, chemistry.getAwardedPoints(), 0.000001);
        assertEquals(4.0, chemistry.getPossiblePoints(), 0.000001);
        assertEquals(
                new BigDecimal("3.3"),
                chemistry.getCalculatedScore()
        );
        assertTrue(chemistry.isFinal());

        OmrAssessmentBlockResult physics =
                breakdown.findResultByBlockId("physics");

        assertEquals(1, physics.getCorrectCount());
        assertEquals(1, physics.getMultipleMarkCount());
        assertEquals(1, physics.getNotReadyCount());
        assertEquals(1, physics.getReviewRequiredCount());
        assertEquals(2, physics.getUnresolvedCount());
        assertEquals(
                new BigDecimal("3.3"),
                physics.getCalculatedScore()
        );
        assertTrue(physics.requiresReview());
        assertFalse(physics.isComplete());
        assertFalse(physics.isFinal());
    }

    @Test
    public void calculatedScoreRemainsMarkedProvisionalWhenUnresolved() {
        OmrAssessmentBlockResult physics = scorer.calculate(
                multidisciplinaryStructure(),
                gradingResult()
        ).findResultByBlockId("physics");

        assertEquals(
                new BigDecimal("3.3"),
                physics.getCalculatedScore()
        );
        assertFalse(physics.isFinal());
        assertEquals(2, physics.getUnresolvedCount());
    }

    @Test
    public void legacyBlockMatchesExistingWeightedOverallResult() {
        OmrGradingResult grading = gradingResult();
        OmrAssessmentStructure legacy =
                OmrAssessmentStructure.legacySingleBlock(
                        "assessment-1",
                        1,
                        grading.getQuestionCount()
                );

        OmrAssessmentBlockResult result = scorer.calculate(
                legacy,
                grading
        ).getBlockResults().get(0);

        assertEquals(
                grading.getAwardedPoints(),
                result.getAwardedPoints(),
                0.000001
        );
        assertEquals(
                grading.getPossiblePoints(),
                result.getPossiblePoints(),
                0.000001
        );
        assertEquals(new BigDecimal("3.8"), result.getCalculatedScore());
        assertEquals(
                grading.getAwardedPercentage(),
                result.getCalculatedScoreAsDouble() * 10.0,
                0.5
        );
    }

    @Test
    public void lookupByQuestionPositionUsesBlockBoundaries() {
        OmrAssessmentBlockBreakdown breakdown = scorer.calculate(
                multidisciplinaryStructure(),
                gradingResult()
        );

        assertEquals(
                "chemistry",
                breakdown.findResultByQuestionPosition(1)
                        .getBlock().getBlockId()
        );
        assertEquals(
                "chemistry",
                breakdown.findResultByQuestionPosition(3)
                        .getBlock().getBlockId()
        );
        assertEquals(
                "physics",
                breakdown.findResultByQuestionPosition(4)
                        .getBlock().getBlockId()
        );
        assertEquals(
                "physics",
                breakdown.findResultByQuestionPosition(6)
                        .getBlock().getBlockId()
        );
        assertNull(breakdown.findResultByQuestionPosition(0));
        assertNull(breakdown.findResultByQuestionPosition(7));
    }

    @Test
    public void breakdownPreservesExactStructureAndGradingSources() {
        OmrAssessmentStructure structure =
                multidisciplinaryStructure();
        OmrGradingResult grading = gradingResult();

        OmrAssessmentBlockBreakdown breakdown =
                scorer.calculate(structure, grading);

        assertSame(structure, breakdown.getStructure());
        assertSame(grading, breakdown.getGradingResult());
        assertFalse(breakdown.isComplete());
        assertTrue(breakdown.requiresReview());
        assertFalse(breakdown.isFinal());
    }

    @Test
    public void resultCollectionsAreImmutable() {
        OmrAssessmentBlockBreakdown breakdown = scorer.calculate(
                multidisciplinaryStructure(),
                gradingResult()
        );

        expectUnsupportedOperation(() ->
                breakdown.getBlockResults().clear()
        );
    }

    @Test
    public void rejectsQuestionCountMismatch() {
        OmrAssessmentStructure fiveQuestionStructure =
                new OmrAssessmentStructure(
                        "assessment-1",
                        1,
                        5,
                        Collections.singletonList(
                                new OmrAssessmentBlock(
                                        "general",
                                        1,
                                        null,
                                        null,
                                        "Geral",
                                        1,
                                        5,
                                        policy()
                                )
                        )
                );

        expectIllegalArgument(() -> scorer.calculate(
                fiveQuestionStructure,
                gradingResult()
        ));
    }

    @Test
    public void rejectsMissingArguments() {
        expectIllegalArgument(() -> scorer.calculate(
                null,
                gradingResult()
        ));
        expectIllegalArgument(() -> scorer.calculate(
                multidisciplinaryStructure(),
                null
        ));
    }

    @Test
    public void blockResultRejectsDifferentQuestionCount() {
        OmrAssessmentBlock block = new OmrAssessmentBlock(
                "chemistry",
                1,
                null,
                null,
                "Química",
                1,
                3,
                policy()
        );

        OmrAssessmentScoreInput twoQuestions =
                new OmrAssessmentScoreInput(
                        2,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        1.0,
                        2.0
                );

        expectIllegalArgument(() ->
                new OmrAssessmentBlockResult(
                        block,
                        twoQuestions
                )
        );
    }

    @Test
    public void breakdownSurvivesJavaSerialization()
            throws Exception {
        OmrAssessmentBlockBreakdown original = scorer.calculate(
                multidisciplinaryStructure(),
                gradingResult()
        );

        OmrAssessmentBlockBreakdown restored = roundTrip(original);

        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertEquals(
                new BigDecimal("3.3"),
                restored.findResultByBlockId("chemistry")
                        .getCalculatedScore()
        );
    }

    private OmrAssessmentStructure multidisciplinaryStructure() {
        return new OmrAssessmentStructure(
                "assessment-1",
                1,
                6,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                "chemistry",
                                1,
                                "Ciências da Natureza",
                                "QUI",
                                "Química",
                                1,
                                3,
                                policy()
                        ),
                        new OmrAssessmentBlock(
                                "physics",
                                2,
                                "Ciências da Natureza",
                                "FIS",
                                "Física",
                                4,
                                3,
                                policy()
                        )
                )
        );
    }

    private OmrProportionalScoringPolicy policy() {
        return OmrProportionalScoringPolicy
                .correctQuestionsToTenV1();
    }

    private OmrGradingResult gradingResult() {
        List<OmrQuestionResult> questions = Arrays.asList(
                singleMark(1, "Q01", "A"),
                singleMark(2, "Q02", "B"),
                question(
                        3,
                        "Q03",
                        OmrQuestionResult.Status.BLANK,
                        Collections.emptyList()
                ),
                singleMark(4, "Q04", "D"),
                question(
                        5,
                        "Q05",
                        OmrQuestionResult.Status.MULTIPLE_MARKS,
                        Arrays.asList(
                                option("Q05", "A"),
                                option("Q05", "B")
                        )
                ),
                question(
                        6,
                        "Q06",
                        OmrQuestionResult.Status.NOT_READY,
                        Collections.emptyList()
                )
        );

        OmrReadingResult reading = new OmrReadingResult(
                "reading-1",
                1_000L,
                "layout-1",
                1,
                "Layout",
                questions
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-1",
                        1,
                        "Gabarito",
                        "layout-1",
                        1,
                        Arrays.asList(
                                entry("Q01", "A", 2.0),
                                entry("Q02", "C", 1.0),
                                entry("Q03", "A", 1.0),
                                entry("Q04", "D", 1.0),
                                entry("Q05", "A", 1.0),
                                entry("Q06", "B", 2.0)
                        )
                );

        return new OmrReadingGrader().grade(reading, answerKey);
    }

    private OmrQuestionResult singleMark(
            int position,
            String questionId,
            String optionLabel
    ) {
        return question(
                position,
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        option(questionId, optionLabel)
                )
        );
    }

    private OmrQuestionResult question(
            int position,
            String questionId,
            OmrQuestionResult.Status status,
            List<OmrQuestionResult.Option> options
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                status,
                options,
                status == OmrQuestionResult.Status.NOT_READY
                        ? 0.0
                        : 0.95
        );
    }

    private OmrQuestionResult.Option option(
            String questionId,
            String label
    ) {
        return new OmrQuestionResult.Option(
                questionId + "-" + label,
                label
        );
    }

    private OmrAnswerKeyEntry entry(
            String questionId,
            String acceptedLabel,
            double weight
    ) {
        return OmrAnswerKeyEntry.singleAnswer(
                questionId,
                questionId + "-" + acceptedLabel,
                weight
        );
    }

    private OmrAssessmentBlockBreakdown roundTrip(
            OmrAssessmentBlockBreakdown source
    ) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(bytes)) {
            output.writeObject(source);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            return (OmrAssessmentBlockBreakdown)
                    input.readObject();
        }
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private void expectUnsupportedOperation(Runnable action) {
        try {
            action.run();
            fail("Era esperada UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }
}
