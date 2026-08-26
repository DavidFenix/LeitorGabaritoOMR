package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

/**
 * Valida a compatibilidade estrutural entre um layout OMR e um
 * gabarito oficial.
 *
 * Esta classe faz a ponte entre a geometria configurada da folha e o
 * domínio de correção. Não depende de câmera, OpenCV nem de imagem.
 */
public final class OmrAnswerKeyLayoutValidator {

    public void validateOrThrow(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "A definição do layout é obrigatória."
            );
        }

        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "A definição do gabarito é obrigatória."
            );
        }

        validateIdentity(
                layoutDefinition,
                answerKeyDefinition
        );

        validateQuestionCount(
                layoutDefinition,
                answerKeyDefinition
        );

        validateEveryLayoutQuestionHasAnswer(
                layoutDefinition,
                answerKeyDefinition
        );

        validateEveryAnswerExistsInLayout(
                layoutDefinition,
                answerKeyDefinition
        );
    }

    private void validateIdentity(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        boolean sameId = layoutDefinition.getId().equals(
                answerKeyDefinition.getLayoutId()
        );

        boolean sameVersion =
                layoutDefinition.getVersion()
                        == answerKeyDefinition.getLayoutVersion();

        if (!sameId || !sameVersion) {
            throw new IllegalArgumentException(
                    "O gabarito não pertence ao layout informado:"
                            + " layout="
                            + layoutDefinition.getId()
                            + "@v"
                            + layoutDefinition.getVersion()
                            + ", gabarito="
                            + answerKeyDefinition.getLayoutId()
                            + "@v"
                            + answerKeyDefinition.getLayoutVersion()
                            + "."
            );
        }
    }

    private void validateQuestionCount(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        int layoutQuestionCount =
                layoutDefinition.getQuestionCount();

        int answerKeyQuestionCount =
                answerKeyDefinition.getQuestionCount();

        if (layoutQuestionCount != answerKeyQuestionCount) {
            throw new IllegalArgumentException(
                    "O layout e o gabarito possuem quantidades"
                            + " diferentes de questões: layout="
                            + layoutQuestionCount
                            + ", gabarito="
                            + answerKeyQuestionCount
                            + "."
            );
        }
    }

    private void validateEveryLayoutQuestionHasAnswer(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        for (OmrQuestionDefinition question
                : layoutDefinition.getAllQuestions()) {

            if (!answerKeyDefinition.containsQuestion(
                    question.getId()
            )) {
                throw new IllegalArgumentException(
                        "Questão do layout ausente no gabarito: "
                                + question.getId()
                );
            }
        }
    }

    private void validateEveryAnswerExistsInLayout(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        for (OmrAnswerKeyEntry entry
                : answerKeyDefinition.getEntries()) {

            OmrQuestionDefinition question =
                    layoutDefinition.findQuestionById(
                            entry.getQuestionId()
                    );

            if (question == null) {
                throw new IllegalArgumentException(
                        "Questão do gabarito ausente no layout: "
                                + entry.getQuestionId()
                );
            }

            for (String acceptedOptionId
                    : entry.getAcceptedOptionIds()) {

                if (question.findOptionById(
                        acceptedOptionId
                ) == null) {
                    throw new IllegalArgumentException(
                            "Alternativa aceita ausente na questão:"
                                    + " questionId="
                                    + entry.getQuestionId()
                                    + ", optionId="
                                    + acceptedOptionId
                                    + "."
                    );
                }
            }
        }
    }
}
