package com.example.leitorgabaritoomr.domain.grading;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado imutável da correção de uma única questão.
 *
 * Mantém a leitura original e a regra do gabarito que realmente foram
 * usadas na correção. Assim, telas, relatórios e diagnósticos podem
 * explicar o resultado sem reconstruir informações.
 */
public final class OmrQuestionGrade
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        CORRECT,
        INCORRECT,
        BLANK,
        MULTIPLE_MARKS,
        AMBIGUOUS,
        NOT_READY;

        public boolean isFinal() {
            return this == CORRECT
                    || this == INCORRECT
                    || this == BLANK;
        }

        public boolean isCorrect() {
            return this == CORRECT;
        }

        public boolean requiresReview() {
            return this == MULTIPLE_MARKS
                    || this == AMBIGUOUS;
        }
    }

    private final OmrQuestionResult readingResult;
    private final OmrAnswerKeyEntry answerKeyEntry;
    private final Status status;

    public OmrQuestionGrade(
            OmrQuestionResult readingResult,
            OmrAnswerKeyEntry answerKeyEntry,
            Status status
    ) {
        if (readingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado de leitura da questão é obrigatório."
            );
        }

        if (answerKeyEntry == null) {
            throw new IllegalArgumentException(
                    "A regra do gabarito da questão é obrigatória."
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "O status da correção é obrigatório."
            );
        }

        if (!readingResult.getQuestionId().equals(
                answerKeyEntry.getQuestionId()
        )) {
            throw new IllegalArgumentException(
                    "A leitura e o gabarito pertencem a questões"
                            + " diferentes: leitura="
                            + readingResult.getQuestionId()
                            + ", gabarito="
                            + answerKeyEntry.getQuestionId()
                            + "."
            );
        }

        validateStatusConsistency(
                readingResult,
                answerKeyEntry,
                status
        );

        this.readingResult = readingResult;
        this.answerKeyEntry = answerKeyEntry;
        this.status = status;
    }

    private static void validateStatusConsistency(
            OmrQuestionResult readingResult,
            OmrAnswerKeyEntry answerKeyEntry,
            Status status
    ) {
        OmrQuestionResult.Status readingStatus =
                readingResult.getStatus();

        if (status == Status.CORRECT
                || status == Status.INCORRECT) {

            if (readingStatus
                    != OmrQuestionResult.Status.SINGLE_MARK) {

                throw inconsistentStatus(
                        status,
                        readingStatus
                );
            }

            OmrQuestionResult.Option selectedOption =
                    readingResult.getSelectedOption();

            boolean accepted =
                    selectedOption != null
                            && answerKeyEntry.acceptsOption(
                            selectedOption.getId()
                    );

            if (status == Status.CORRECT && !accepted) {
                throw new IllegalArgumentException(
                        "CORRECT exige uma alternativa aceita"
                                + " pelo gabarito."
                );
            }

            if (status == Status.INCORRECT && accepted) {
                throw new IllegalArgumentException(
                        "INCORRECT não pode usar uma alternativa"
                                + " aceita pelo gabarito."
                );
            }

            return;
        }

        OmrQuestionResult.Status expectedReadingStatus;

        switch (status) {
            case BLANK:
                expectedReadingStatus =
                        OmrQuestionResult.Status.BLANK;
                break;

            case MULTIPLE_MARKS:
                expectedReadingStatus =
                        OmrQuestionResult.Status.MULTIPLE_MARKS;
                break;

            case AMBIGUOUS:
                expectedReadingStatus =
                        OmrQuestionResult.Status.AMBIGUOUS;
                break;

            case NOT_READY:
                expectedReadingStatus =
                        OmrQuestionResult.Status.NOT_READY;
                break;

            default:
                throw new IllegalArgumentException(
                        "Status de correção não suportado: "
                                + status
                );
        }

        if (readingStatus != expectedReadingStatus) {
            throw inconsistentStatus(
                    status,
                    readingStatus
            );
        }
    }

    private static IllegalArgumentException inconsistentStatus(
            Status gradingStatus,
            OmrQuestionResult.Status readingStatus
    ) {
        return new IllegalArgumentException(
                "Status de correção incompatível com a leitura:"
                        + " correção="
                        + gradingStatus
                        + ", leitura="
                        + readingStatus
                        + "."
        );
    }

    public int getPosition() {
        return readingResult.getPosition();
    }

    public String getQuestionId() {
        return readingResult.getQuestionId();
    }

    public OmrQuestionResult getReadingResult() {
        return readingResult;
    }

    public OmrAnswerKeyEntry getAnswerKeyEntry() {
        return answerKeyEntry;
    }

    public Status getStatus() {
        return status;
    }

    public List<OmrQuestionResult.Option> getRelevantOptions() {
        return readingResult.getRelevantOptions();
    }

    public OmrQuestionResult.Option getSelectedOption() {
        return readingResult.getSelectedOption();
    }

    public Set<String> getAcceptedOptionIds() {
        return answerKeyEntry.getAcceptedOptionIds();
    }

    public double getConfidence() {
        return readingResult.getConfidence();
    }

    public double getPossiblePoints() {
        return answerKeyEntry.getWeight();
    }

    public double getAwardedPoints() {
        return status.isCorrect()
                ? getPossiblePoints()
                : 0.0;
    }

    public boolean isFinal() {
        return status.isFinal();
    }

    public boolean isCorrect() {
        return status.isCorrect();
    }

    public boolean requiresReview() {
        return status.requiresReview();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrQuestionGrade)) {
            return false;
        }

        OmrQuestionGrade that =
                (OmrQuestionGrade) other;

        return readingResult.equals(that.readingResult)
                && answerKeyEntry.equals(that.answerKeyEntry)
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                readingResult,
                answerKeyEntry,
                status
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "question=%s status=%s points=%.3f/%.3f"
                        + " confidence=%.3f",
                getQuestionId(),
                status,
                getAwardedPoints(),
                getPossiblePoints(),
                getConfidence()
        );
    }
}
