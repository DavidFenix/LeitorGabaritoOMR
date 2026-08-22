package com.example.leitorgabaritoomr.vision.interpretation;

import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resultado imutavel da interpretacao semantica de uma folha.
 *
 * Mantem o consenso temporal original, a interpretacao de cada
 * questao, indices por questionId e contadores por estado.
 */
public final class SheetInterpretationResult {

    private final SheetEvidenceAggregate
            evidenceAggregate;

    private final List<QuestionInterpretation>
            questionInterpretations;

    private final Map<String, QuestionInterpretation>
            interpretationByQuestionId;

    private final Map<QuestionMarkState, Integer>
            countByState;

    private final int reviewRequiredCount;

    public SheetInterpretationResult(
            SheetEvidenceAggregate evidenceAggregate,
            List<QuestionInterpretation>
                    questionInterpretations
    ) {
        if (evidenceAggregate == null) {
            throw new IllegalArgumentException(
                    "O consenso temporal da folha e obrigatorio."
            );
        }

        if (questionInterpretations == null) {
            throw new IllegalArgumentException(
                    "A lista de interpretacoes e obrigatoria."
            );
        }

        int expectedQuestionCount =
                evidenceAggregate
                .getLayout()
                .getQuestionCount();

        if (questionInterpretations.size()
                != expectedQuestionCount) {

            throw new IllegalArgumentException(
                    "A quantidade de interpretacoes nao"
                            + " corresponde ao layout: esperado="
                            + expectedQuestionCount
                            + ", recebido="
                            + questionInterpretations.size()
                            + "."
            );
        }

        List<QuestionInterpretation> copy =
                new ArrayList<>(
                        questionInterpretations.size()
                );

        Map<String, QuestionInterpretation> byQuestionId =
                new HashMap<>();

        EnumMap<QuestionMarkState, Integer> mutableCounts =
                new EnumMap<>(QuestionMarkState.class);

        for (QuestionMarkState state
                : QuestionMarkState.values()) {

            mutableCounts.put(state, 0);
        }

        int mutableReviewRequiredCount = 0;

        for (QuestionInterpretation interpretation
                : questionInterpretations) {

            if (interpretation == null) {
                throw new IllegalArgumentException(
                        "A lista nao pode conter"
                                + " interpretacoes nulas."
                );
            }

            String questionId =
                    interpretation
                    .getQuestion()
                    .getId();

            QuestionEvidenceAggregate expectedAggregate =
                    evidenceAggregate.findByQuestionId(
                            questionId
                    );

            if (expectedAggregate == null) {
                throw new IllegalArgumentException(
                        "A questao "
                                + questionId
                                + " nao pertence ao consenso"
                                + " desta folha."
                );
            }

            if (interpretation.getEvidenceAggregate()
                    != expectedAggregate) {

                throw new IllegalArgumentException(
                        "A interpretacao de "
                                + questionId
                                + " nao preservou a instancia"
                                + " do agregado da folha."
                );
            }

            if (byQuestionId.put(
                    questionId,
                    interpretation
            ) != null) {

                throw new IllegalArgumentException(
                        "Questao interpretada mais de uma vez: "
                                + questionId
                );
            }

            copy.add(interpretation);

            QuestionMarkState state =
                    interpretation.getState();

            mutableCounts.put(
                    state,
                    mutableCounts.get(state) + 1
            );

            if (interpretation.requiresReview()) {
                mutableReviewRequiredCount++;
            }
        }

        validateOrder(
                evidenceAggregate,
                copy
        );

        this.evidenceAggregate =
                evidenceAggregate;

        this.questionInterpretations =
                Collections.unmodifiableList(copy);

        this.interpretationByQuestionId =
                Collections.unmodifiableMap(
                        byQuestionId
                );

        this.countByState =
                Collections.unmodifiableMap(
                        mutableCounts
                );

        this.reviewRequiredCount =
                mutableReviewRequiredCount;
    }

    private void validateOrder(
            SheetEvidenceAggregate sheet,
            List<QuestionInterpretation> interpretations
    ) {
        List<QuestionEvidenceAggregate> aggregates =
                sheet.getQuestionAggregates();

        for (int index = 0;
             index < aggregates.size();
             index++) {

            if (interpretations
                    .get(index)
                    .getEvidenceAggregate()
                    != aggregates.get(index)) {

                throw new IllegalArgumentException(
                        "A ordem das interpretacoes divergiu"
                                + " do consenso na posicao "
                                + index
                                + "."
                );
            }
        }
    }

    public SheetEvidenceAggregate getEvidenceAggregate() {
        return evidenceAggregate;
    }

    public List<QuestionInterpretation>
    getQuestionInterpretations() {
        return questionInterpretations;
    }

    public QuestionInterpretation findByQuestionId(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        return interpretationByQuestionId.get(
                questionId
        );
    }

    public int getQuestionCount() {
        return questionInterpretations.size();
    }

    public int getCount(QuestionMarkState state) {
        if (state == null) {
            return 0;
        }

        Integer count = countByState.get(state);

        return count == null ? 0 : count;
    }

    public Map<QuestionMarkState, Integer>
    getCountByState() {
        return countByState;
    }

    public int getSingleMarkCount() {
        return getCount(
                QuestionMarkState.SINGLE_MARK
        );
    }

    public int getBlankCount() {
        return getCount(
                QuestionMarkState.BLANK
        );
    }

    public int getMultipleMarkCount() {
        return getCount(
                QuestionMarkState.MULTIPLE_MARKS
        );
    }

    public int getAmbiguousCount() {
        return getCount(
                QuestionMarkState.AMBIGUOUS
        );
    }

    public int getNotReadyCount() {
        return getCount(
                QuestionMarkState.NOT_READY
        );
    }

    public int getReviewRequiredCount() {
        return reviewRequiredCount;
    }

    /**
     * Completo significa que todas as questoes receberam uma
     * classificacao. Uma folha completa ainda pode conter questoes
     * ambiguas ou multiplas que exigem revisao.
     */
    public boolean isComplete() {
        return evidenceAggregate.isReady()
                && getNotReadyCount() == 0;
    }

    public boolean requiresReview() {
        return reviewRequiredCount > 0;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "questions=%d single=%d blank=%d multiple=%d"
                        + " ambiguous=%d notReady=%d review=%d"
                        + " complete=%s",
                getQuestionCount(),
                getSingleMarkCount(),
                getBlankCount(),
                getMultipleMarkCount(),
                getAmbiguousCount(),
                getNotReadyCount(),
                getReviewRequiredCount(),
                isComplete()
        );
    }
}
