package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.List;

/**
 * Ponto único da camada de aplicação para corrigir uma leitura OMR.
 *
 * Antes de delegar a pontuação ao domínio, valida em conjunto o layout,
 * o gabarito oficial e a leitura. Assim, câmera, Activity e telas de
 * resultado não precisam coordenar validadores nem conhecer detalhes da
 * estrutura interna da correção.
 */
public final class OmrGradingService {

    private final OmrAnswerKeyLayoutValidator answerKeyLayoutValidator;
    private final OmrReadingGrader readingGrader;

    public OmrGradingService() {
        this(
                new OmrAnswerKeyLayoutValidator(),
                new OmrReadingGrader()
        );
    }

    OmrGradingService(
            OmrAnswerKeyLayoutValidator answerKeyLayoutValidator,
            OmrReadingGrader readingGrader
    ) {
        if (answerKeyLayoutValidator == null) {
            throw new IllegalArgumentException(
                    "O validador de layout e gabarito é obrigatório."
            );
        }

        if (readingGrader == null) {
            throw new IllegalArgumentException(
                    "O corretor da leitura é obrigatório."
            );
        }

        this.answerKeyLayoutValidator =
                answerKeyLayoutValidator;

        this.readingGrader = readingGrader;
    }

    public OmrGradingResult grade(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition,
            OmrReadingResult readingResult
    ) {
        requireInputs(
                layoutDefinition,
                answerKeyDefinition,
                readingResult
        );

        validateReadingAgainstLayout(
                layoutDefinition,
                readingResult
        );

        answerKeyLayoutValidator.validateOrThrow(
                layoutDefinition,
                answerKeyDefinition
        );

        return readingGrader.grade(
                readingResult,
                answerKeyDefinition
        );
    }

    private void validateReadingAgainstLayout(
            OmrLayoutDefinition layoutDefinition,
            OmrReadingResult readingResult
    ) {
        validateReadingLayoutIdentity(
                layoutDefinition,
                readingResult
        );

        List<OmrQuestionDefinition> layoutQuestions =
                layoutDefinition.getAllQuestions();

        List<OmrQuestionResult> readingQuestions =
                readingResult.getQuestionResults();

        if (layoutQuestions.size() != readingQuestions.size()) {
            throw new IllegalArgumentException(
                    "O layout e a leitura possuem quantidades"
                            + " diferentes de questões: layout="
                            + layoutQuestions.size()
                            + ", leitura="
                            + readingQuestions.size()
                            + "."
            );
        }

        for (int index = 0;
             index < layoutQuestions.size();
             index++) {

            validateQuestionAtPosition(
                    layoutQuestions.get(index),
                    readingQuestions.get(index),
                    index + 1
            );
        }
    }

    private void validateReadingLayoutIdentity(
            OmrLayoutDefinition layoutDefinition,
            OmrReadingResult readingResult
    ) {
        boolean sameLayoutId = layoutDefinition.getId().equals(
                readingResult.getLayoutId()
        );

        boolean sameLayoutVersion =
                layoutDefinition.getVersion()
                        == readingResult.getLayoutVersion();

        if (!sameLayoutId || !sameLayoutVersion) {
            throw new IllegalArgumentException(
                    "A leitura não pertence ao layout informado:"
                            + " layout="
                            + layoutDefinition.getId()
                            + "@v"
                            + layoutDefinition.getVersion()
                            + ", leitura="
                            + readingResult.getLayoutId()
                            + "@v"
                            + readingResult.getLayoutVersion()
                            + "."
            );
        }
    }

    private void validateQuestionAtPosition(
            OmrQuestionDefinition layoutQuestion,
            OmrQuestionResult readingQuestion,
            int expectedPosition
    ) {
        if (!layoutQuestion.getId().equals(
                readingQuestion.getQuestionId()
        )) {
            throw new IllegalArgumentException(
                    "A leitura contém uma questão inesperada"
                            + " na posição "
                            + expectedPosition
                            + ": esperado="
                            + layoutQuestion.getId()
                            + ", recebido="
                            + readingQuestion.getQuestionId()
                            + "."
            );
        }

        for (OmrQuestionResult.Option readingOption
                : readingQuestion.getRelevantOptions()) {

            validateRelevantOption(
                    layoutQuestion,
                    readingOption
            );
        }
    }

    private void validateRelevantOption(
            OmrQuestionDefinition layoutQuestion,
            OmrQuestionResult.Option readingOption
    ) {
        OmrOptionDefinition layoutOption =
                layoutQuestion.findOptionById(
                        readingOption.getId()
                );

        if (layoutOption == null) {
            throw new IllegalArgumentException(
                    "Alternativa da leitura ausente no layout:"
                            + " questionId="
                            + layoutQuestion.getId()
                            + ", optionId="
                            + readingOption.getId()
                            + "."
            );
        }

        if (!layoutOption.getLabel().equals(
                readingOption.getLabel()
        )) {
            throw new IllegalArgumentException(
                    "Rótulo da alternativa da leitura não corresponde"
                            + " ao layout: questionId="
                            + layoutQuestion.getId()
                            + ", optionId="
                            + readingOption.getId()
                            + ", esperado="
                            + layoutOption.getLabel()
                            + ", recebido="
                            + readingOption.getLabel()
                            + "."
            );
        }
    }

    private void requireInputs(
            OmrLayoutDefinition layoutDefinition,
            OmrAnswerKeyDefinition answerKeyDefinition,
            OmrReadingResult readingResult
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

        if (readingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da leitura é obrigatório."
            );
        }
    }
}
