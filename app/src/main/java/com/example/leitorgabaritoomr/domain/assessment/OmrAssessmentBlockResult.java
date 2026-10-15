package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** Resultado calculado e auditavel de um bloco da prova. */
public final class OmrAssessmentBlockResult
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final OmrAssessmentBlock block;
    private final OmrAssessmentScoreInput scoreInput;
    private final BigDecimal calculatedScore;

    public OmrAssessmentBlockResult(
            OmrAssessmentBlock block,
            OmrAssessmentScoreInput scoreInput
    ) {
        if (block == null) {
            throw new IllegalArgumentException(
                    "O bloco da prova e obrigatorio."
            );
        }

        if (scoreInput == null) {
            throw new IllegalArgumentException(
                    "Os dados do resultado do bloco sao obrigatorios."
            );
        }

        if (block.getQuestionCount()
                != scoreInput.getQuestionCount()) {
            throw new IllegalArgumentException(
                    "O resultado deve possuir a quantidade"
                            + " de questoes definida pelo bloco."
            );
        }

        BigDecimal score = block
                .getScoringPolicy()
                .calculate(scoreInput);

        if (score == null
                || score.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "A politica produziu uma nota invalida."
            );
        }

        this.block = block;
        this.scoreInput = scoreInput;
        this.calculatedScore = score;
    }

    public OmrAssessmentBlock getBlock() {
        return block;
    }

    public OmrAssessmentScoreInput getScoreInput() {
        return scoreInput;
    }

    public BigDecimal getCalculatedScore() {
        return calculatedScore;
    }

    public double getCalculatedScoreAsDouble() {
        return calculatedScore.doubleValue();
    }

    public BigDecimal getMaximumScore() {
        return block.getScoringPolicy().getMaximumScore();
    }

    public String getScoringPolicyId() {
        return block.getScoringPolicy().getPolicyId();
    }

    public int getScoringPolicyVersion() {
        return block.getScoringPolicy().getVersion();
    }

    public int getQuestionCount() {
        return scoreInput.getQuestionCount();
    }

    public int getCorrectCount() {
        return scoreInput.getCorrectCount();
    }

    public int getIncorrectCount() {
        return scoreInput.getIncorrectCount();
    }

    public int getBlankCount() {
        return scoreInput.getBlankCount();
    }

    public int getMultipleMarkCount() {
        return scoreInput.getMultipleMarkCount();
    }

    public int getAmbiguousCount() {
        return scoreInput.getAmbiguousCount();
    }

    public int getNotReadyCount() {
        return scoreInput.getNotReadyCount();
    }

    public int getReviewRequiredCount() {
        return scoreInput.getReviewRequiredCount();
    }

    public int getUnresolvedCount() {
        return scoreInput.getUnresolvedCount();
    }

    public double getAwardedPoints() {
        return scoreInput.getAwardedPointsAsDouble();
    }

    public double getPossiblePoints() {
        return scoreInput.getPossiblePointsAsDouble();
    }

    public boolean requiresReview() {
        return scoreInput.requiresReview();
    }

    public boolean isComplete() {
        return scoreInput.isComplete();
    }

    public boolean isFinal() {
        return scoreInput.isFinal();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentBlockResult)) {
            return false;
        }

        OmrAssessmentBlockResult that =
                (OmrAssessmentBlockResult) other;

        return block.equals(that.block)
                && scoreInput.equals(that.scoreInput)
                && calculatedScore.compareTo(
                that.calculatedScore
        ) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                block,
                scoreInput,
                calculatedScore.stripTrailingZeros()
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[correct=%d/%d, score=%s, final=%s]",
                block.getBlockId(),
                getCorrectCount(),
                getQuestionCount(),
                calculatedScore.toPlainString(),
                isFinal()
        );
    }
}
