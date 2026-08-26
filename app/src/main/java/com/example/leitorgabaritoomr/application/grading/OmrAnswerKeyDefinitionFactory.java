package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Constrói um gabarito oficial a partir dos rótulos visíveis das
 * alternativas de um layout OMR.
 *
 * O dado de entrada pode usar valores humanos como A, B, C e D. A
 * fábrica resolve cada rótulo para o optionId técnico pertencente à
 * questão correta e entrega ao domínio somente identificadores
 * estáveis.
 *
 * As respostas são associadas às questões na ordem definida pelo
 * layout. Essa ordem serve apenas para a montagem: o gabarito criado
 * continua relacionando leitura e correção por questionId.
 */
public final class OmrAnswerKeyDefinitionFactory {

    private final OmrAnswerKeyLayoutValidator layoutValidator;

    public OmrAnswerKeyDefinitionFactory() {
        this(new OmrAnswerKeyLayoutValidator());
    }

    OmrAnswerKeyDefinitionFactory(
            OmrAnswerKeyLayoutValidator layoutValidator
    ) {
        if (layoutValidator == null) {
            throw new IllegalArgumentException(
                    "O validador de layout é obrigatório."
            );
        }

        this.layoutValidator = layoutValidator;
    }

    /**
     * Atalho para o caso convencional em que cada questão possui uma
     * única alternativa correta e todas têm o mesmo peso.
     */
    public OmrAnswerKeyDefinition createSingleAnswerKey(
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName,
            OmrLayoutDefinition layoutDefinition,
            List<String> acceptedOptionLabels,
            double uniformWeight
    ) {
        requireLayout(layoutDefinition);

        if (acceptedOptionLabels == null) {
            throw new IllegalArgumentException(
                    "A lista de respostas é obrigatória."
            );
        }

        List<Collection<String>> acceptedLabelsByQuestion =
                new ArrayList<>(acceptedOptionLabels.size());

        List<Double> questionWeights =
                new ArrayList<>(acceptedOptionLabels.size());

        for (String acceptedOptionLabel
                : acceptedOptionLabels) {

            acceptedLabelsByQuestion.add(
                    Collections.singleton(
                            acceptedOptionLabel
                    )
            );

            questionWeights.add(uniformWeight);
        }

        return create(
                answerKeyId,
                answerKeyVersion,
                answerKeyName,
                layoutDefinition,
                acceptedLabelsByQuestion,
                questionWeights
        );
    }

    /**
     * Cria um gabarito que pode possuir pesos diferentes e mais de uma
     * alternativa aceita em uma mesma questão.
     */
    public OmrAnswerKeyDefinition create(
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName,
            OmrLayoutDefinition layoutDefinition,
            List<? extends Collection<String>>
                    acceptedOptionLabelsByQuestion,
            List<Double> questionWeights
    ) {
        requireLayout(layoutDefinition);

        List<OmrQuestionDefinition> questions =
                layoutDefinition.getAllQuestions();

        validateInputCount(
                "respostas",
                acceptedOptionLabelsByQuestion,
                questions.size()
        );

        validateInputCount(
                "pesos",
                questionWeights,
                questions.size()
        );

        List<OmrAnswerKeyEntry> entries =
                new ArrayList<>(questions.size());

        for (int questionIndex = 0;
             questionIndex < questions.size();
             questionIndex++) {

            OmrQuestionDefinition question =
                    questions.get(questionIndex);

            Collection<String> acceptedLabels =
                    acceptedOptionLabelsByQuestion.get(
                            questionIndex
                    );

            Double weight = questionWeights.get(
                    questionIndex
            );

            if (weight == null) {
                throw new IllegalArgumentException(
                        "Peso nulo na questão "
                                + question.getId()
                                + "."
                );
            }

            entries.add(
                    new OmrAnswerKeyEntry(
                            question.getId(),
                            resolveAcceptedOptionIds(
                                    question,
                                    acceptedLabels
                            ),
                            weight
                    )
            );
        }

        OmrAnswerKeyDefinition answerKeyDefinition =
                new OmrAnswerKeyDefinition(
                        answerKeyId,
                        answerKeyVersion,
                        answerKeyName,
                        layoutDefinition.getId(),
                        layoutDefinition.getVersion(),
                        entries
                );

        layoutValidator.validateOrThrow(
                layoutDefinition,
                answerKeyDefinition
        );

        return answerKeyDefinition;
    }

    private List<String> resolveAcceptedOptionIds(
            OmrQuestionDefinition question,
            Collection<String> acceptedOptionLabels
    ) {
        if (acceptedOptionLabels == null
                || acceptedOptionLabels.isEmpty()) {

            throw new IllegalArgumentException(
                    "A questão "
                            + question.getId()
                            + " deve possuir pelo menos uma"
                            + " alternativa aceita."
            );
        }

        List<String> acceptedOptionIds =
                new ArrayList<>(
                        acceptedOptionLabels.size()
                );

        for (String acceptedOptionLabel
                : acceptedOptionLabels) {

            OmrOptionDefinition option =
                    findOptionByLabel(
                            question,
                            acceptedOptionLabel
                    );

            acceptedOptionIds.add(option.getId());
        }

        return acceptedOptionIds;
    }

    private OmrOptionDefinition findOptionByLabel(
            OmrQuestionDefinition question,
            String optionLabel
    ) {
        if (optionLabel == null
                || optionLabel.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Rótulo de alternativa vazio na questão "
                            + question.getId()
                            + "."
            );
        }

        String normalizedLabel = optionLabel.trim();
        OmrOptionDefinition matchingOption = null;

        for (OmrOptionDefinition option
                : question.getOptions()) {

            if (!option.getLabel().equalsIgnoreCase(
                    normalizedLabel
            )) {
                continue;
            }

            if (matchingOption != null) {
                throw new IllegalArgumentException(
                        "Rótulo de alternativa ambíguo na questão "
                                + question.getId()
                                + ": "
                                + normalizedLabel
                                + "."
                );
            }

            matchingOption = option;
        }

        if (matchingOption == null) {
            throw new IllegalArgumentException(
                    "Alternativa inexistente na questão "
                            + question.getId()
                            + ": "
                            + normalizedLabel
                            + "."
            );
        }

        return matchingOption;
    }

    private void validateInputCount(
            String inputName,
            List<?> values,
            int expectedCount
    ) {
        if (values == null) {
            throw new IllegalArgumentException(
                    "A lista de "
                            + inputName
                            + " é obrigatória."
            );
        }

        if (values.size() != expectedCount) {
            throw new IllegalArgumentException(
                    "A quantidade de "
                            + inputName
                            + " deve ser igual à quantidade de"
                            + " questões do layout: esperado="
                            + expectedCount
                            + ", recebido="
                            + values.size()
                            + "."
            );
        }
    }

    private void requireLayout(
            OmrLayoutDefinition layoutDefinition
    ) {
        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "A definição do layout é obrigatória."
            );
        }
    }
}
