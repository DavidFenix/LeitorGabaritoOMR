package com.example.leitorgabaritoomr.vision.interpretation;

import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Interpretacao imutavel de uma questao depois do consenso
 * temporal.
 *
 * O agregado original e preservado integralmente. Assim, nenhuma
 * evidencia e descartada e o Laboratorio pode explicar por que a
 * questao foi classificada como unica, vazia, multipla ou ambigua.
 */
public final class QuestionInterpretation {

    private final QuestionEvidenceAggregate
            evidenceAggregate;

    private final QuestionMarkState state;

    /**
     * Alternativas relevantes para o estado encontrado.
     *
     * SINGLE_MARK: exatamente uma alternativa.
     * BLANK e NOT_READY: nenhuma alternativa.
     * MULTIPLE_MARKS: duas ou mais alternativas.
     * AMBIGUOUS: zero ou mais alternativas suspeitas.
     */
    private final List<OmrOptionDefinition>
            relevantOptions;

    private final double confidence;

    public QuestionInterpretation(
            QuestionEvidenceAggregate evidenceAggregate,
            QuestionMarkState state,
            List<OmrOptionDefinition> relevantOptions,
            double confidence
    ) {
        if (evidenceAggregate == null) {
            throw new IllegalArgumentException(
                    "O agregado de evidencias e obrigatorio."
            );
        }

        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado da questao e obrigatorio."
            );
        }

        if (relevantOptions == null) {
            throw new IllegalArgumentException(
                    "A lista de alternativas relevantes"
                            + " e obrigatoria."
            );
        }

        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {

            throw new IllegalArgumentException(
                    "confidence deve estar entre 0.0 e 1.0."
            );
        }

        OmrQuestionDefinition question =
                evidenceAggregate.getQuestion();

        List<OmrOptionDefinition> optionsCopy =
                new ArrayList<>(relevantOptions.size());

        Set<String> optionIds =
                new HashSet<>();

        for (OmrOptionDefinition option
                : relevantOptions) {

            if (option == null) {
                throw new IllegalArgumentException(
                        "A lista nao pode conter"
                                + " alternativas nulas."
                );
            }

            String optionId = option.getId();

            OmrOptionDefinition expectedOption =
                    question.findOptionById(optionId);

            if (expectedOption == null) {
                throw new IllegalArgumentException(
                        "A alternativa "
                                + optionId
                                + " nao pertence a questao "
                                + question.getId()
                                + "."
                );
            }

            if (!optionIds.add(optionId)) {
                throw new IllegalArgumentException(
                        "Alternativa relevante repetida: "
                                + optionId
                );
            }

            /*
             * Conserva a instancia canonica pertencente a questao.
             */
            optionsCopy.add(expectedOption);
        }

        validateStateCardinality(
                state,
                optionsCopy.size()
        );

        this.evidenceAggregate =
                evidenceAggregate;

        this.state = state;

        this.relevantOptions =
                Collections.unmodifiableList(
                        optionsCopy
                );

        this.confidence = confidence;
    }

    private void validateStateCardinality(
            QuestionMarkState state,
            int relevantOptionCount
    ) {
        if (state == QuestionMarkState.SINGLE_MARK
                && relevantOptionCount != 1) {

            throw new IllegalArgumentException(
                    "SINGLE_MARK exige exatamente"
                            + " uma alternativa relevante."
            );
        }

        if (state == QuestionMarkState.MULTIPLE_MARKS
                && relevantOptionCount < 2) {

            throw new IllegalArgumentException(
                    "MULTIPLE_MARKS exige pelo menos"
                            + " duas alternativas relevantes."
            );
        }

        if ((state == QuestionMarkState.BLANK
                || state == QuestionMarkState.NOT_READY)
                && relevantOptionCount != 0) {

            throw new IllegalArgumentException(
                    state
                            + " nao pode possuir"
                            + " alternativas relevantes."
            );
        }
    }

    public QuestionEvidenceAggregate
    getEvidenceAggregate() {
        return evidenceAggregate;
    }

    public OmrQuestionDefinition getQuestion() {
        return evidenceAggregate.getQuestion();
    }

    public QuestionMarkState getState() {
        return state;
    }

    public List<OmrOptionDefinition>
    getRelevantOptions() {
        return relevantOptions;
    }

    /**
     * Retorna a resposta somente quando existe uma unica marcacao.
     */
    public OmrOptionDefinition getSelectedOption() {
        if (!state.hasSingleMark()) {
            return null;
        }

        return relevantOptions.get(0);
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isReady() {
        return state.isReady();
    }

    public boolean requiresReview() {
        return state.requiresReview();
    }

    @Override
    public String toString() {
        String selectedOptionId =
                getSelectedOption() == null
                        ? "-"
                        : getSelectedOption().getId();

        return String.format(
                Locale.US,
                "%s state=%s selected=%s relevant=%d conf=%.3f",
                getQuestion().getId(),
                state,
                selectedOptionId,
                relevantOptions.size(),
                confidence
        );
    }
}
