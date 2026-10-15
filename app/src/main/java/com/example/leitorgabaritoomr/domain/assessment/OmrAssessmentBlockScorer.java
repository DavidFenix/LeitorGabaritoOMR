package com.example.leitorgabaritoomr.domain.assessment;

import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrQuestionGrade;

import java.util.ArrayList;
import java.util.List;

/** Calcula acertos e nota de cada bloco sem depender de Android ou SQLite. */
public final class OmrAssessmentBlockScorer {

    public OmrAssessmentBlockBreakdown calculate(
            OmrAssessmentStructure structure,
            OmrGradingResult gradingResult
    ) {
        if (structure == null) {
            throw new IllegalArgumentException(
                    "A estrutura da prova e obrigatoria."
            );
        }

        if (gradingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da correcao e obrigatorio."
            );
        }

        if (structure.getQuestionCount()
                != gradingResult.getQuestionCount()) {
            throw new IllegalArgumentException(
                    "A estrutura e a correcao possuem quantidades"
                            + " diferentes de questoes: estrutura="
                            + structure.getQuestionCount()
                            + ", correcao="
                            + gradingResult.getQuestionCount()
                            + "."
            );
        }

        List<OmrAssessmentBlockResult> blockResults =
                new ArrayList<>(structure.getBlockCount());

        for (OmrAssessmentBlock block : structure.getBlocks()) {
            blockResults.add(calculateBlock(block, gradingResult));
        }

        return new OmrAssessmentBlockBreakdown(
                structure,
                gradingResult,
                blockResults
        );
    }

    private OmrAssessmentBlockResult calculateBlock(
            OmrAssessmentBlock block,
            OmrGradingResult gradingResult
    ) {
        int correctCount = 0;
        int incorrectCount = 0;
        int blankCount = 0;
        int multipleMarkCount = 0;
        int ambiguousCount = 0;
        int notReadyCount = 0;

        double awardedPoints = 0.0;
        double possiblePoints = 0.0;

        for (int position = block.getFirstQuestionPosition();
             position <= block.getLastQuestionPosition();
             position++) {

            OmrQuestionGrade grade =
                    gradingResult.getQuestionAtPosition(position);

            if (grade == null) {
                throw new IllegalStateException(
                        "A correcao nao possui a questao da posicao "
                                + position
                                + "."
                );
            }

            switch (grade.getStatus()) {
                case CORRECT:
                    correctCount++;
                    break;

                case INCORRECT:
                    incorrectCount++;
                    break;

                case BLANK:
                    blankCount++;
                    break;

                case MULTIPLE_MARKS:
                    multipleMarkCount++;
                    break;

                case AMBIGUOUS:
                    ambiguousCount++;
                    break;

                case NOT_READY:
                    notReadyCount++;
                    break;

                default:
                    throw new IllegalStateException(
                            "Estado de correcao nao suportado: "
                                    + grade.getStatus()
                    );
            }

            awardedPoints += grade.getAwardedPoints();
            possiblePoints += grade.getPossiblePoints();
        }

        OmrAssessmentScoreInput input =
                new OmrAssessmentScoreInput(
                        block.getQuestionCount(),
                        correctCount,
                        incorrectCount,
                        blankCount,
                        multipleMarkCount,
                        ambiguousCount,
                        notReadyCount,
                        awardedPoints,
                        possiblePoints
                );

        return new OmrAssessmentBlockResult(block, input);
    }
}
