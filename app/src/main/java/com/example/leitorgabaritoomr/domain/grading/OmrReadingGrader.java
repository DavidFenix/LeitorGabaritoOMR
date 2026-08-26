package com.example.leitorgabaritoomr.domain.grading;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Corrige uma leitura OMR completa usando um gabarito oficial.
 *
 * As questões são relacionadas exclusivamente pelo questionId.
 * A ordem da leitura é preservada no resultado final, mas não é usada
 * para descobrir qual entrada do gabarito pertence a cada questão.
 */
public final class OmrReadingGrader {

    private final OmrQuestionGrader questionGrader;

    public OmrReadingGrader() {
        this.questionGrader = new OmrQuestionGrader();
    }

    public OmrGradingResult grade(
            OmrReadingResult readingResult,
            OmrAnswerKeyDefinition answerKeyDefinition
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

        validateQuestionCount(
                readingResult,
                answerKeyDefinition
        );

        List<OmrQuestionGrade> questionGrades =
                new ArrayList<>(
                        readingResult.getQuestionCount()
                );

        for (OmrQuestionResult questionResult
                : readingResult.getQuestionResults()) {

            OmrAnswerKeyEntry answerKeyEntry =
                    answerKeyDefinition.findEntryByQuestionId(
                            questionResult.getQuestionId()
                    );

            if (answerKeyEntry == null) {
                throw new IllegalArgumentException(
                        "Questão da leitura ausente no gabarito: "
                                + questionResult.getQuestionId()
                );
            }

            questionGrades.add(
                    questionGrader.grade(
                            questionResult,
                            answerKeyEntry
                    )
            );
        }

        return new OmrGradingResult(
                readingResult,
                answerKeyDefinition,
                questionGrades
        );
    }

    private void validateLayoutCompatibility(
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

    private void validateQuestionCount(
            OmrReadingResult readingResult,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
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
    }
}
