package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Dados imutaveis usados por uma politica para calcular a nota do bloco. */
public final class OmrAssessmentScoreInput
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int questionCount;
    private final int correctCount;
    private final int incorrectCount;
    private final int blankCount;
    private final int multipleMarkCount;
    private final int ambiguousCount;
    private final int notReadyCount;

    private final BigDecimal awardedPoints;
    private final BigDecimal possiblePoints;

    public OmrAssessmentScoreInput(
            int questionCount,
            int correctCount,
            int incorrectCount,
            int blankCount,
            int multipleMarkCount,
            int ambiguousCount,
            int notReadyCount,
            double awardedPoints,
            double possiblePoints
    ) {
        if (questionCount <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade de questoes deve ser positiva."
            );
        }

        validateNonNegative("correctCount", correctCount);
        validateNonNegative("incorrectCount", incorrectCount);
        validateNonNegative("blankCount", blankCount);
        validateNonNegative(
                "multipleMarkCount",
                multipleMarkCount
        );
        validateNonNegative("ambiguousCount", ambiguousCount);
        validateNonNegative("notReadyCount", notReadyCount);

        long classifiedCount = (long) correctCount
                + incorrectCount
                + blankCount
                + multipleMarkCount
                + ambiguousCount
                + notReadyCount;

        if (classifiedCount != questionCount) {
            throw new IllegalArgumentException(
                    "A soma dos estados deve corresponder"
                            + " a quantidade de questoes."
            );
        }

        if (!Double.isFinite(possiblePoints)
                || possiblePoints <= 0.0) {
            throw new IllegalArgumentException(
                    "Os pontos possiveis devem ser finitos"
                            + " e maiores que zero."
            );
        }

        if (!Double.isFinite(awardedPoints)
                || awardedPoints < 0.0
                || awardedPoints > possiblePoints) {
            throw new IllegalArgumentException(
                    "Os pontos obtidos devem estar entre zero"
                            + " e os pontos possiveis."
            );
        }

        this.questionCount = questionCount;
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.blankCount = blankCount;
        this.multipleMarkCount = multipleMarkCount;
        this.ambiguousCount = ambiguousCount;
        this.notReadyCount = notReadyCount;
        this.awardedPoints = BigDecimal.valueOf(awardedPoints);
        this.possiblePoints = BigDecimal.valueOf(possiblePoints);
    }

    private static void validateNonNegative(
            String fieldName,
            int value
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + " nao pode ser negativo."
            );
        }
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getIncorrectCount() {
        return incorrectCount;
    }

    public int getBlankCount() {
        return blankCount;
    }

    public int getMultipleMarkCount() {
        return multipleMarkCount;
    }

    public int getAmbiguousCount() {
        return ambiguousCount;
    }

    public int getNotReadyCount() {
        return notReadyCount;
    }

    public int getReviewRequiredCount() {
        return multipleMarkCount + ambiguousCount;
    }

    public int getUnresolvedCount() {
        return getReviewRequiredCount() + notReadyCount;
    }

    public BigDecimal getAwardedPoints() {
        return awardedPoints;
    }

    public BigDecimal getPossiblePoints() {
        return possiblePoints;
    }

    public double getAwardedPointsAsDouble() {
        return awardedPoints.doubleValue();
    }

    public double getPossiblePointsAsDouble() {
        return possiblePoints.doubleValue();
    }

    public boolean requiresReview() {
        return getReviewRequiredCount() > 0;
    }

    public boolean isComplete() {
        return notReadyCount == 0;
    }

    public boolean isFinal() {
        return isComplete() && !requiresReview();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentScoreInput)) {
            return false;
        }

        OmrAssessmentScoreInput that =
                (OmrAssessmentScoreInput) other;

        return questionCount == that.questionCount
                && correctCount == that.correctCount
                && incorrectCount == that.incorrectCount
                && blankCount == that.blankCount
                && multipleMarkCount == that.multipleMarkCount
                && ambiguousCount == that.ambiguousCount
                && notReadyCount == that.notReadyCount
                && awardedPoints.compareTo(that.awardedPoints) == 0
                && possiblePoints.compareTo(that.possiblePoints) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                questionCount,
                correctCount,
                incorrectCount,
                blankCount,
                multipleMarkCount,
                ambiguousCount,
                notReadyCount,
                normalized(awardedPoints),
                normalized(possiblePoints)
        );
    }

    private static BigDecimal normalized(BigDecimal value) {
        return value.stripTrailingZeros();
    }
}
