package com.example.leitorgabaritoomr.domain.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class OmrProportionalScoringPolicyTest {

    @Test
    public void correctQuestionPolicyAppliesSchoolRuleOfThree() {
        OmrProportionalScoringPolicy policy =
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1();

        BigDecimal score = policy.calculate(input(
                15,
                11,
                4,
                11.0,
                15.0
        ));

        assertEquals(new BigDecimal("7.3"), score);
        assertEquals(
                OmrProportionalScoringPolicy
                        .CORRECT_QUESTIONS_POLICY_ID,
                policy.getPolicyId()
        );
        assertEquals(1, policy.getVersion());
        assertEquals(
                OmrProportionalScoringPolicy.Basis
                        .CORRECT_QUESTION_COUNT,
                policy.getBasis()
        );
    }

    @Test
    public void weightedPolicyPreservesExistingWeightedCalculation() {
        OmrProportionalScoringPolicy policy =
                OmrProportionalScoringPolicy
                        .weightedPointsToTenV1();

        BigDecimal score = policy.calculate(input(
                6,
                1,
                5,
                2.0,
                7.0
        ));

        assertEquals(new BigDecimal("2.9"), score);
        assertEquals(
                OmrProportionalScoringPolicy.Basis.WEIGHTED_POINTS,
                policy.getBasis()
        );
    }

    @Test
    public void basesCanProduceDifferentScoresForWeightedQuestions() {
        OmrAssessmentScoreInput input = input(
                2,
                1,
                1,
                1.0,
                4.0
        );

        assertEquals(
                new BigDecimal("5.0"),
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1()
                        .calculate(input)
        );
        assertEquals(
                new BigDecimal("2.5"),
                OmrProportionalScoringPolicy
                        .weightedPointsToTenV1()
                        .calculate(input)
        );
    }

    @Test
    public void factoryUsesOneDecimalAndHalfUpRounding() {
        OmrProportionalScoringPolicy policy =
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1();

        assertEquals(BigDecimal.TEN, policy.getMaximumScore());
        assertEquals(1, policy.getDecimalPlaces());
        assertEquals(RoundingMode.HALF_UP, policy.getRoundingMode());
        assertEquals(
                new BigDecimal("6.7"),
                policy.calculate(input(
                        3,
                        2,
                        1,
                        2.0,
                        3.0
                ))
        );
    }

    @Test
    public void customScaleAndPrecisionRemainPossible() {
        OmrProportionalScoringPolicy policy =
                new OmrProportionalScoringPolicy(
                        "percentage-v1",
                        1,
                        OmrProportionalScoringPolicy.Basis
                                .CORRECT_QUESTION_COUNT,
                        BigDecimal.valueOf(100),
                        2,
                        RoundingMode.HALF_UP
                );

        assertEquals(
                new BigDecimal("33.33"),
                policy.calculate(input(
                        3,
                        1,
                        2,
                        1.0,
                        3.0
                ))
        );
    }

    @Test
    public void inputDistinguishesReviewPendingAndFinalStates() {
        OmrAssessmentScoreInput review = new OmrAssessmentScoreInput(
                4,
                1,
                1,
                0,
                1,
                0,
                1,
                1.0,
                4.0
        );

        assertEquals(1, review.getReviewRequiredCount());
        assertEquals(2, review.getUnresolvedCount());
        assertTrue(review.requiresReview());
        assertFalse(review.isComplete());
        assertFalse(review.isFinal());

        OmrAssessmentScoreInput finalResult =
                new OmrAssessmentScoreInput(
                        4,
                        2,
                        1,
                        1,
                        0,
                        0,
                        0,
                        2.0,
                        4.0
                );

        assertFalse(finalResult.requiresReview());
        assertTrue(finalResult.isComplete());
        assertTrue(finalResult.isFinal());
    }

    @Test
    public void inputRejectsStatusSumDifferentFromQuestionCount() {
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                4,
                1,
                1,
                0,
                0,
                0,
                0,
                1.0,
                4.0
        ));
    }

    @Test
    public void inputRejectsInvalidCountsAndPoints() {
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                0, 0, 0, 0, 0, 0, 0, 0.0, 1.0
        ));
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                1, -1, 2, 0, 0, 0, 0, 0.0, 1.0
        ));
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                1, 1, 0, 0, 0, 0, 0, -1.0, 1.0
        ));
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                1, 1, 0, 0, 0, 0, 0, 2.0, 1.0
        ));
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                1, 1, 0, 0, 0, 0, 0, 1.0, 0.0
        ));
        expectIllegalArgument(() -> new OmrAssessmentScoreInput(
                1, 1, 0, 0, 0, 0, 0,
                Double.NaN, 1.0
        ));
    }

    @Test
    public void policyRejectsInvalidConfiguration() {
        expectIllegalArgument(() -> policy(
                " ", 1, BigDecimal.TEN, 1,
                RoundingMode.HALF_UP
        ));
        expectIllegalArgument(() -> policy(
                "policy", 0, BigDecimal.TEN, 1,
                RoundingMode.HALF_UP
        ));
        expectIllegalArgument(() -> new OmrProportionalScoringPolicy(
                "policy", 1, null, BigDecimal.TEN, 1,
                RoundingMode.HALF_UP
        ));
        expectIllegalArgument(() -> policy(
                "policy", 1, BigDecimal.ZERO, 1,
                RoundingMode.HALF_UP
        ));
        expectIllegalArgument(() -> policy(
                "policy", 1, BigDecimal.TEN, -1,
                RoundingMode.HALF_UP
        ));
        expectIllegalArgument(() -> policy(
                "policy", 1, BigDecimal.TEN, 7,
                RoundingMode.HALF_UP
        ));
        expectIllegalArgument(() -> policy(
                "policy", 1, BigDecimal.TEN, 1, null
        ));
        expectIllegalArgument(() -> policy(
                "policy", 1, BigDecimal.TEN, 1,
                RoundingMode.UNNECESSARY
        ));
    }

    @Test
    public void policyRejectsMissingInput() {
        expectIllegalArgument(() ->
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1()
                        .calculate(null)
        );
    }

    @Test
    public void equalityIncludesFullVersionedConfiguration() {
        OmrProportionalScoringPolicy original =
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1();

        assertEquals(
                original,
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1()
        );
        assertEquals(
                original.hashCode(),
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1()
                        .hashCode()
        );
        assertNotEquals(
                original,
                OmrProportionalScoringPolicy
                        .weightedPointsToTenV1()
        );
        assertNotEquals(
                original,
                new OmrProportionalScoringPolicy(
                        original.getPolicyId(),
                        2,
                        original.getBasis(),
                        original.getMaximumScore(),
                        original.getDecimalPlaces(),
                        original.getRoundingMode()
                )
        );
    }

    @Test
    public void policyAndInputSurviveJavaSerialization()
            throws Exception {
        OmrProportionalScoringPolicy policy =
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1();
        OmrAssessmentScoreInput input = input(
                15, 11, 4, 11.0, 15.0
        );

        OmrProportionalScoringPolicy restoredPolicy =
                roundTrip(policy);
        OmrAssessmentScoreInput restoredInput =
                roundTrip(input);

        assertEquals(policy, restoredPolicy);
        assertEquals(input, restoredInput);
        assertEquals(
                new BigDecimal("7.3"),
                restoredPolicy.calculate(restoredInput)
        );
    }

    private OmrAssessmentScoreInput input(
            int questionCount,
            int correctCount,
            int incorrectCount,
            double awardedPoints,
            double possiblePoints
    ) {
        return new OmrAssessmentScoreInput(
                questionCount,
                correctCount,
                incorrectCount,
                0,
                0,
                0,
                0,
                awardedPoints,
                possiblePoints
        );
    }

    private OmrProportionalScoringPolicy policy(
            String id,
            int version,
            BigDecimal maximumScore,
            int decimalPlaces,
            RoundingMode roundingMode
    ) {
        return new OmrProportionalScoringPolicy(
                id,
                version,
                OmrProportionalScoringPolicy.Basis
                        .CORRECT_QUESTION_COUNT,
                maximumScore,
                decimalPlaces,
                roundingMode
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T source) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(bytes)) {
            output.writeObject(source);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            return (T) input.readObject();
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
}
