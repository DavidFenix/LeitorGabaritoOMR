package com.example.leitorgabaritoomr.domain.grading;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;

/**
 * Corrige uma única questão sem depender de Android, câmera,
 * OpenCV ou detalhes geométricos da folha.
 *
 * Estados que exigem revisão são preservados. O corretor não tenta
 * converter marcações múltiplas ou ambíguas em uma resposta escolhida.
 */
public final class OmrQuestionGrader {

    public OmrQuestionGrade grade(
            OmrQuestionResult readingResult,
            OmrAnswerKeyEntry answerKeyEntry
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

        OmrQuestionGrade.Status gradingStatus =
                resolveStatus(
                        readingResult,
                        answerKeyEntry
                );

        return new OmrQuestionGrade(
                readingResult,
                answerKeyEntry,
                gradingStatus
        );
    }

    private OmrQuestionGrade.Status resolveStatus(
            OmrQuestionResult readingResult,
            OmrAnswerKeyEntry answerKeyEntry
    ) {
        switch (readingResult.getStatus()) {
            case SINGLE_MARK:
                return resolveSingleMark(
                        readingResult,
                        answerKeyEntry
                );

            case BLANK:
                return OmrQuestionGrade.Status.BLANK;

            case MULTIPLE_MARKS:
                return OmrQuestionGrade.Status.MULTIPLE_MARKS;

            case AMBIGUOUS:
                return OmrQuestionGrade.Status.AMBIGUOUS;

            case NOT_READY:
                return OmrQuestionGrade.Status.NOT_READY;

            default:
                throw new IllegalStateException(
                        "Status de leitura não suportado: "
                                + readingResult.getStatus()
                );
        }
    }

    private OmrQuestionGrade.Status resolveSingleMark(
            OmrQuestionResult readingResult,
            OmrAnswerKeyEntry answerKeyEntry
    ) {
        OmrQuestionResult.Option selectedOption =
                readingResult.getSelectedOption();

        if (selectedOption == null) {
            throw new IllegalStateException(
                    "SINGLE_MARK sem alternativa selecionada."
            );
        }

        if (answerKeyEntry.acceptsOption(
                selectedOption.getId()
        )) {
            return OmrQuestionGrade.Status.CORRECT;
        }

        return OmrQuestionGrade.Status.INCORRECT;
    }
}
