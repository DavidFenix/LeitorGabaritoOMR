package com.example.leitorgabaritoomr.domain.reading;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Instantaneo imutavel e transportavel do resultado de uma questao.
 *
 * Nao depende de Android, OpenCV, Mat, layout geometrico ou objetos
 * internos do pipeline. Pode ser usado por telas, persistencia,
 * exportacao e pela futura camada de correcao.
 */
public final class OmrQuestionResult
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        NOT_READY,
        SINGLE_MARK,
        BLANK,
        MULTIPLE_MARKS,
        AMBIGUOUS;

        public boolean isReady() {
            return this != NOT_READY;
        }

        public boolean hasSingleMark() {
            return this == SINGLE_MARK;
        }

        public boolean requiresReview() {
            return this == MULTIPLE_MARKS
                    || this == AMBIGUOUS;
        }
    }

    /**
     * Identidade leve de uma alternativa relevante.
     */
    public static final class Option
            implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String id;
        private final String label;

        public Option(
                String id,
                String label
        ) {
            this.id = requireText("id", id);
            this.label = requireText("label", label);
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof Option)) {
                return false;
            }

            Option that = (Option) other;

            return id.equals(that.id)
                    && label.equals(that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, label);
        }

        @Override
        public String toString() {
            return label + "[" + id + "]";
        }
    }

    /**
     * Posicao visual e logica da questao, iniciando em 1.
     * Nao precisa ser igual ao ID nem pressupoe numeracao sequencial
     * impressa no gabarito.
     */
    private final int position;

    private final String questionId;
    private final Status status;

    private final List<Option> relevantOptions;

    private final double confidence;

    public OmrQuestionResult(
            int position,
            String questionId,
            Status status,
            List<Option> relevantOptions,
            double confidence
    ) {
        if (position <= 0) {
            throw new IllegalArgumentException(
                    "position deve iniciar em 1."
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "O status da questao e obrigatorio."
            );
        }

        if (relevantOptions == null) {
            throw new IllegalArgumentException(
                    "A lista de alternativas relevantes e obrigatoria."
            );
        }

        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {

            throw new IllegalArgumentException(
                    "confidence deve estar entre 0.0 e 1.0."
            );
        }

        List<Option> optionsCopy =
                copyOptions(relevantOptions);

        validateCardinality(
                status,
                optionsCopy.size()
        );

        this.position = position;
        this.questionId =
                requireText("questionId", questionId);
        this.status = status;
        this.relevantOptions =
                Collections.unmodifiableList(
                        optionsCopy
                );
        this.confidence = confidence;
    }

    private static List<Option> copyOptions(
            List<Option> relevantOptions
    ) {
        List<Option> copy =
                new ArrayList<>(
                        relevantOptions.size()
                );

        Set<String> optionIds =
                new HashSet<>();

        for (Option option : relevantOptions) {
            if (option == null) {
                throw new IllegalArgumentException(
                        "A lista nao pode conter alternativas nulas."
                );
            }

            if (!optionIds.add(option.getId())) {
                throw new IllegalArgumentException(
                        "Alternativa relevante repetida: "
                                + option.getId()
                );
            }

            copy.add(option);
        }

        return copy;
    }

    private static void validateCardinality(
            Status status,
            int relevantOptionCount
    ) {
        if (status == Status.SINGLE_MARK
                && relevantOptionCount != 1) {

            throw new IllegalArgumentException(
                    "SINGLE_MARK exige exatamente uma alternativa."
            );
        }

        if (status == Status.MULTIPLE_MARKS
                && relevantOptionCount < 2) {

            throw new IllegalArgumentException(
                    "MULTIPLE_MARKS exige pelo menos duas alternativas."
            );
        }

        if ((status == Status.BLANK
                || status == Status.NOT_READY)
                && relevantOptionCount != 0) {

            throw new IllegalArgumentException(
                    status
                            + " nao pode possuir alternativas relevantes."
            );
        }
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value.trim();
    }

    public int getPosition() {
        return position;
    }

    public String getQuestionId() {
        return questionId;
    }

    public Status getStatus() {
        return status;
    }

    public List<Option> getRelevantOptions() {
        return relevantOptions;
    }

    public Option getSelectedOption() {
        if (!status.hasSingleMark()) {
            return null;
        }

        return relevantOptions.get(0);
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isReady() {
        return status.isReady();
    }

    public boolean requiresReview() {
        return status.requiresReview();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrQuestionResult)) {
            return false;
        }

        OmrQuestionResult that =
                (OmrQuestionResult) other;

        return position == that.position
                && Double.compare(
                confidence,
                that.confidence
        ) == 0
                && questionId.equals(that.questionId)
                && status == that.status
                && relevantOptions.equals(
                that.relevantOptions
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                position,
                questionId,
                status,
                relevantOptions,
                confidence
        );
    }

    @Override
    public String toString() {
        String selectedLabel =
                getSelectedOption() == null
                        ? "-"
                        : getSelectedOption().getLabel();

        return String.format(
                Locale.US,
                "position=%d id=%s status=%s selected=%s"
                        + " relevant=%d confidence=%.3f",
                position,
                questionId,
                status,
                selectedLabel,
                relevantOptions.size(),
                confidence
        );
    }
}
