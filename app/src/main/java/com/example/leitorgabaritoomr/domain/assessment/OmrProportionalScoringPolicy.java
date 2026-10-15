package com.example.leitorgabaritoomr.domain.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Politica proporcional configuravel e versionada.
 *
 * A base por quantidade reproduz a regra de tres usada pela escola. A base
 * por pontos preserva o comportamento historico quando o gabarito possui
 * pesos diferentes entre as questoes.
 */
public final class OmrProportionalScoringPolicy
        implements OmrAssessmentScoringPolicy {

    private static final long serialVersionUID = 1L;

    public static final String CORRECT_QUESTIONS_POLICY_ID =
            "proportional-correct-questions";

    public static final String WEIGHTED_POINTS_POLICY_ID =
            "proportional-weighted-points";

    public enum Basis {
        CORRECT_QUESTION_COUNT,
        WEIGHTED_POINTS
    }

    private final String policyId;
    private final int version;
    private final Basis basis;
    private final BigDecimal maximumScore;
    private final int decimalPlaces;
    private final RoundingMode roundingMode;

    public OmrProportionalScoringPolicy(
            String policyId,
            int version,
            Basis basis,
            BigDecimal maximumScore,
            int decimalPlaces,
            RoundingMode roundingMode
    ) {
        this.policyId = requireText("policyId", policyId);

        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versao da politica deve ser positiva."
            );
        }

        if (basis == null) {
            throw new IllegalArgumentException(
                    "A base de calculo e obrigatoria."
            );
        }

        if (maximumScore == null
                || maximumScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "A nota maxima deve ser maior que zero."
            );
        }

        if (decimalPlaces < 0 || decimalPlaces > 6) {
            throw new IllegalArgumentException(
                    "As casas decimais devem estar entre zero e seis."
            );
        }

        if (roundingMode == null
                || roundingMode == RoundingMode.UNNECESSARY) {
            throw new IllegalArgumentException(
                    "Um modo de arredondamento aplicavel e obrigatorio."
            );
        }

        this.version = version;
        this.basis = basis;
        this.maximumScore = normalizedDecimal(maximumScore);
        this.decimalPlaces = decimalPlaces;
        this.roundingMode = roundingMode;
    }

    public static OmrProportionalScoringPolicy
    correctQuestionsToTenV1() {
        return new OmrProportionalScoringPolicy(
                CORRECT_QUESTIONS_POLICY_ID,
                1,
                Basis.CORRECT_QUESTION_COUNT,
                BigDecimal.TEN,
                1,
                RoundingMode.HALF_UP
        );
    }

    public static OmrProportionalScoringPolicy
    weightedPointsToTenV1() {
        return new OmrProportionalScoringPolicy(
                WEIGHTED_POINTS_POLICY_ID,
                1,
                Basis.WEIGHTED_POINTS,
                BigDecimal.TEN,
                1,
                RoundingMode.HALF_UP
        );
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value.trim();
    }

    private static BigDecimal normalizedDecimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();

        return normalized.scale() < 0
                ? normalized.setScale(0)
                : normalized;
    }

    @Override
    public String getPolicyId() {
        return policyId;
    }

    @Override
    public int getVersion() {
        return version;
    }

    public Basis getBasis() {
        return basis;
    }

    @Override
    public BigDecimal getMaximumScore() {
        return maximumScore;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    @Override
    public BigDecimal calculate(
            OmrAssessmentScoreInput input
    ) {
        if (input == null) {
            throw new IllegalArgumentException(
                    "Os dados para calculo da nota sao obrigatorios."
            );
        }

        BigDecimal numerator;
        BigDecimal denominator;

        if (basis == Basis.CORRECT_QUESTION_COUNT) {
            numerator = BigDecimal.valueOf(
                    input.getCorrectCount()
            );
            denominator = BigDecimal.valueOf(
                    input.getQuestionCount()
            );
        } else {
            numerator = input.getAwardedPoints();
            denominator = input.getPossiblePoints();
        }

        return numerator
                .multiply(maximumScore)
                .divide(
                        denominator,
                        decimalPlaces,
                        roundingMode
                );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrProportionalScoringPolicy)) {
            return false;
        }

        OmrProportionalScoringPolicy that =
                (OmrProportionalScoringPolicy) other;

        return version == that.version
                && decimalPlaces == that.decimalPlaces
                && policyId.equals(that.policyId)
                && basis == that.basis
                && maximumScore.compareTo(that.maximumScore) == 0
                && roundingMode == that.roundingMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                policyId,
                version,
                basis,
                normalizedDecimal(maximumScore),
                decimalPlaces,
                roundingMode
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s@v%d[basis=%s, max=%s, decimals=%d, rounding=%s]",
                policyId,
                version,
                basis,
                maximumScore.toPlainString(),
                decimalPlaces,
                roundingMode
        );
    }
}
