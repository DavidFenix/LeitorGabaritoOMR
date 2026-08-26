package com.example.leitorgabaritoomr.domain.grading;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Regra imutável de correção de uma questão do gabarito oficial.
 *
 * A ligação acontece pelo questionId, nunca pela posição visual ou
 * pelo número impresso. Isso permite blocos, temas e numerações não
 * sequenciais sem acoplar a correção ao desenho da folha.
 *
 * Mais de uma alternativa aceita significa que uma marcação única em
 * qualquer uma delas pode ser considerada correta. Isso não transforma
 * uma resposta com múltiplas bolhas marcadas em resposta válida.
 */
public final class OmrAnswerKeyEntry
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String questionId;
    private final Set<String> acceptedOptionIds;
    private final double weight;

    public OmrAnswerKeyEntry(
            String questionId,
            Collection<String> acceptedOptionIds,
            double weight
    ) {
        this.questionId = requireText(
                "questionId",
                questionId
        );

        this.acceptedOptionIds =
                Collections.unmodifiableSet(
                        copyAndValidateOptionIds(
                                acceptedOptionIds
                        )
                );

        if (!Double.isFinite(weight)
                || weight <= 0.0) {

            throw new IllegalArgumentException(
                    "weight deve ser finito e maior que zero."
            );
        }

        this.weight = weight;
    }

    /**
     * Atalho para o caso convencional de uma única resposta correta.
     */
    public static OmrAnswerKeyEntry singleAnswer(
            String questionId,
            String acceptedOptionId,
            double weight
    ) {
        return new OmrAnswerKeyEntry(
                questionId,
                Collections.singleton(
                        acceptedOptionId
                ),
                weight
        );
    }

    private static Set<String> copyAndValidateOptionIds(
            Collection<String> acceptedOptionIds
    ) {
        if (acceptedOptionIds == null
                || acceptedOptionIds.isEmpty()) {

            throw new IllegalArgumentException(
                    "A questão deve possuir pelo menos uma"
                            + " alternativa aceita."
            );
        }

        Set<String> copy =
                new LinkedHashSet<>(
                        acceptedOptionIds.size()
                );

        for (String optionId : acceptedOptionIds) {
            String normalizedOptionId = requireText(
                    "acceptedOptionId",
                    optionId
            );

            if (!copy.add(
                    normalizedOptionId
            )) {
                throw new IllegalArgumentException(
                        "Alternativa aceita repetida: "
                                + normalizedOptionId
                );
            }

        }

        return copy;
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getQuestionId() {
        return questionId;
    }

    public Set<String> getAcceptedOptionIds() {
        return acceptedOptionIds;
    }

    public int getAcceptedOptionCount() {
        return acceptedOptionIds.size();
    }

    public double getWeight() {
        return weight;
    }

    public boolean acceptsOption(
            String optionId
    ) {
        if (optionId == null) {
            return false;
        }

        return acceptedOptionIds.contains(
                optionId.trim()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAnswerKeyEntry)) {
            return false;
        }

        OmrAnswerKeyEntry that =
                (OmrAnswerKeyEntry) other;

        return Double.compare(
                weight,
                that.weight
        ) == 0
                && questionId.equals(that.questionId)
                && acceptedOptionIds.equals(
                that.acceptedOptionIds
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                questionId,
                acceptedOptionIds,
                weight
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "question=%s accepted=%s weight=%.3f",
                questionId,
                acceptedOptionIds,
                weight
        );
    }
}
