package com.example.leitorgabaritoomr.domain.grading;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado imutável da correção de uma leitura OMR completa.
 *
 * Preserva a leitura, o gabarito oficial e cada correção individual.
 * Os pontos de questões em revisão ou ainda não prontas permanecem
 * provisoriamente em zero até que exista uma decisão explícita.
 */
public final class OmrGradingResult
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final double WEIGHT_TOLERANCE = 0.000000001;

    private final OmrReadingResult readingResult;
    private final OmrAnswerKeyDefinition answerKeyDefinition;

    private final List<OmrQuestionGrade> questionGrades;
    private final Map<OmrQuestionGrade.Status, Integer>
            countByStatus;

    private final double possiblePoints;
    private final double awardedPoints;

    public OmrGradingResult(
            OmrReadingResult readingResult,
            OmrAnswerKeyDefinition answerKeyDefinition,
            List<OmrQuestionGrade> questionGrades
    ) {
        if (readingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da leitura é obrigatório."
            );
        }

        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito oficial é obrigatório."
            );
        }

        validateLayoutCompatibility(
                readingResult,
                answerKeyDefinition
        );

        if (questionGrades == null
                || questionGrades.isEmpty()) {

            throw new IllegalArgumentException(
                    "A correção deve possuir pelo menos uma questão."
            );
        }

        int readingQuestionCount =
                readingResult.getQuestionCount();

        int answerKeyQuestionCount =
                answerKeyDefinition.getQuestionCount();

        if (readingQuestionCount != answerKeyQuestionCount) {
            throw new IllegalArgumentException(
                    "A leitura e o gabarito possuem quantidades"
                            + " diferentes de questões: leitura="
                            + readingQuestionCount
                            + ", gabarito="
                            + answerKeyQuestionCount
                            + "."
            );
        }

        if (questionGrades.size() != readingQuestionCount) {
            throw new IllegalArgumentException(
                    "A quantidade de correções deve ser igual"
                            + " à quantidade de questões da leitura:"
                            + " correções="
                            + questionGrades.size()
                            + ", leitura="
                            + readingQuestionCount
                            + "."
            );
        }

        List<OmrQuestionGrade> gradeCopy =
                new ArrayList<>(questionGrades.size());

        EnumMap<OmrQuestionGrade.Status, Integer> counts =
                createEmptyCounts();

        Set<String> correctedQuestionIds =
                new HashSet<>();

        double accumulatedPossiblePoints = 0.0;
        double accumulatedAwardedPoints = 0.0;

        for (int index = 0;
             index < questionGrades.size();
             index++) {

            OmrQuestionGrade grade =
                    questionGrades.get(index);

            if (grade == null) {
                throw new IllegalArgumentException(
                        "A lista de correções não pode"
                                + " conter valores nulos."
                );
            }

            validateGradeAtPosition(
                    readingResult,
                    answerKeyDefinition,
                    grade,
                    index + 1
            );

            if (!correctedQuestionIds.add(
                    grade.getQuestionId()
            )) {
                throw new IllegalArgumentException(
                        "Questão repetida na correção: "
                                + grade.getQuestionId()
                );
            }

            gradeCopy.add(grade);

            OmrQuestionGrade.Status status =
                    grade.getStatus();

            counts.put(
                    status,
                    counts.get(status) + 1
            );

            accumulatedPossiblePoints +=
                    grade.getPossiblePoints();

            accumulatedAwardedPoints +=
                    grade.getAwardedPoints();
        }

        validateTotalWeight(
                accumulatedPossiblePoints,
                answerKeyDefinition.getTotalWeight()
        );

        this.readingResult = readingResult;
        this.answerKeyDefinition = answerKeyDefinition;

        this.questionGrades =
                Collections.unmodifiableList(gradeCopy);

        this.countByStatus =
                Collections.unmodifiableMap(counts);

        this.possiblePoints = accumulatedPossiblePoints;
        this.awardedPoints = accumulatedAwardedPoints;
    }

    private static void validateLayoutCompatibility(
            OmrReadingResult readingResult,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        boolean sameLayoutId =
                readingResult.getLayoutId().equals(
                        answerKeyDefinition.getLayoutId()
                );

        boolean sameLayoutVersion =
                readingResult.getLayoutVersion()
                        == answerKeyDefinition.getLayoutVersion();

        if (!sameLayoutId || !sameLayoutVersion) {
            throw new IllegalArgumentException(
                    "O gabarito não pertence ao layout da leitura:"
                            + " leitura="
                            + readingResult.getLayoutId()
                            + "@v"
                            + readingResult.getLayoutVersion()
                            + ", gabarito="
                            + answerKeyDefinition.getLayoutId()
                            + "@v"
                            + answerKeyDefinition.getLayoutVersion()
                            + "."
            );
        }
    }

    private static EnumMap<OmrQuestionGrade.Status, Integer>
    createEmptyCounts() {
        EnumMap<OmrQuestionGrade.Status, Integer> counts =
                new EnumMap<>(OmrQuestionGrade.Status.class);

        for (OmrQuestionGrade.Status status
                : OmrQuestionGrade.Status.values()) {
            counts.put(status, 0);
        }

        return counts;
    }

    private static void validateGradeAtPosition(
            OmrReadingResult readingResult,
            OmrAnswerKeyDefinition answerKeyDefinition,
            OmrQuestionGrade grade,
            int expectedPosition
    ) {
        if (grade.getPosition() != expectedPosition) {
            throw new IllegalArgumentException(
                    "A posição da correção deve acompanhar"
                            + " a ordem da leitura: esperado="
                            + expectedPosition
                            + ", recebido="
                            + grade.getPosition()
                            + "."
            );
        }

        OmrQuestionResult expectedReading =
                readingResult.getQuestionAtPosition(
                        expectedPosition
                );

        if (!grade.getReadingResult().equals(
                expectedReading
        )) {
            throw new IllegalArgumentException(
                    "A correção não contém a leitura esperada"
                            + " na posição "
                            + expectedPosition
                            + "."
            );
        }

        OmrAnswerKeyEntry expectedAnswerKey =
                answerKeyDefinition.findEntryByQuestionId(
                        grade.getQuestionId()
                );

        if (expectedAnswerKey == null) {
            throw new IllegalArgumentException(
                    "Questão ausente no gabarito: "
                            + grade.getQuestionId()
            );
        }

        if (!grade.getAnswerKeyEntry().equals(
                expectedAnswerKey
        )) {
            throw new IllegalArgumentException(
                    "A correção não contém a regra esperada"
                            + " para a questão "
                            + grade.getQuestionId()
                            + "."
            );
        }
    }

    private static void validateTotalWeight(
            double calculatedWeight,
            double declaredWeight
    ) {
        double scale = Math.max(
                1.0,
                Math.abs(declaredWeight)
        );

        if (Math.abs(calculatedWeight - declaredWeight)
                > WEIGHT_TOLERANCE * scale) {

            throw new IllegalArgumentException(
                    "O peso total das correções não corresponde"
                            + " ao peso declarado pelo gabarito:"
                            + " correções="
                            + calculatedWeight
                            + ", gabarito="
                            + declaredWeight
                            + "."
            );
        }
    }

    public OmrReadingResult getReadingResult() {
        return readingResult;
    }

    public OmrAnswerKeyDefinition getAnswerKeyDefinition() {
        return answerKeyDefinition;
    }

    public List<OmrQuestionGrade> getQuestionGrades() {
        return questionGrades;
    }

    public int getQuestionCount() {
        return questionGrades.size();
    }

    public OmrQuestionGrade getQuestionAtPosition(
            int position
    ) {
        if (position <= 0
                || position > questionGrades.size()) {
            return null;
        }

        return questionGrades.get(position - 1);
    }

    public OmrQuestionGrade findByQuestionId(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        String normalizedId = questionId.trim();

        for (OmrQuestionGrade grade : questionGrades) {
            if (grade.getQuestionId().equals(normalizedId)) {
                return grade;
            }
        }

        return null;
    }

    public int getCount(
            OmrQuestionGrade.Status status
    ) {
        if (status == null) {
            return 0;
        }

        Integer count = countByStatus.get(status);
        return count == null ? 0 : count;
    }

    public Map<OmrQuestionGrade.Status, Integer>
    getCountByStatus() {
        return countByStatus;
    }

    public int getCorrectCount() {
        return getCount(OmrQuestionGrade.Status.CORRECT);
    }

    public int getIncorrectCount() {
        return getCount(OmrQuestionGrade.Status.INCORRECT);
    }

    public int getBlankCount() {
        return getCount(OmrQuestionGrade.Status.BLANK);
    }

    public int getMultipleMarkCount() {
        return getCount(
                OmrQuestionGrade.Status.MULTIPLE_MARKS
        );
    }

    public int getAmbiguousCount() {
        return getCount(OmrQuestionGrade.Status.AMBIGUOUS);
    }

    public int getNotReadyCount() {
        return getCount(OmrQuestionGrade.Status.NOT_READY);
    }

    public int getReviewRequiredCount() {
        return getMultipleMarkCount()
                + getAmbiguousCount();
    }

    public int getFinalQuestionCount() {
        return getCorrectCount()
                + getIncorrectCount()
                + getBlankCount();
    }

    public int getUnresolvedCount() {
        return getReviewRequiredCount()
                + getNotReadyCount();
    }

    public double getPossiblePoints() {
        return possiblePoints;
    }

    public double getAwardedPoints() {
        return awardedPoints;
    }

    public double getAwardedFraction() {
        return awardedPoints / possiblePoints;
    }

    public double getAwardedPercentage() {
        return getAwardedFraction() * 100.0;
    }

    public boolean isComplete() {
        return getNotReadyCount() == 0;
    }

    public boolean requiresReview() {
        return getReviewRequiredCount() > 0;
    }

    public boolean isFinal() {
        return isComplete() && !requiresReview();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrGradingResult)) {
            return false;
        }

        OmrGradingResult that =
                (OmrGradingResult) other;

        return readingResult.equals(that.readingResult)
                && answerKeyDefinition.equals(
                that.answerKeyDefinition
        )
                && questionGrades.equals(
                that.questionGrades
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                readingResult,
                answerKeyDefinition,
                questionGrades
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "reading=%s answerKey=%s@v%d"
                        + " correct=%d incorrect=%d blank=%d"
                        + " review=%d pending=%d"
                        + " points=%.3f/%.3f percentage=%.2f",
                readingResult.getReadingId(),
                answerKeyDefinition.getId(),
                answerKeyDefinition.getVersion(),
                getCorrectCount(),
                getIncorrectCount(),
                getBlankCount(),
                getReviewRequiredCount(),
                getNotReadyCount(),
                awardedPoints,
                possiblePoints,
                getAwardedPercentage()
        );
    }
}
