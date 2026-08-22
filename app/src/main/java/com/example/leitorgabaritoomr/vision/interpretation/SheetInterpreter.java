package com.example.leitorgabaritoomr.vision.interpretation;

import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplica o interpretador de questoes a uma fotografia imutavel do
 * consenso temporal da folha inteira.
 */
public final class SheetInterpreter {

    private final QuestionInterpreter questionInterpreter;

    public SheetInterpreter(
            QuestionInterpreter questionInterpreter
    ) {
        if (questionInterpreter == null) {
            throw new IllegalArgumentException(
                    "QuestionInterpreter e obrigatorio."
            );
        }

        this.questionInterpreter =
                questionInterpreter;
    }

    public SheetInterpretationResult interpret(
            SheetEvidenceAggregate sheetAggregate
    ) {
        if (sheetAggregate == null) {
            throw new IllegalArgumentException(
                    "O consenso temporal da folha e obrigatorio."
            );
        }

        boolean consensusReady =
                sheetAggregate.isReady();

        List<QuestionInterpretation> interpretations =
                new ArrayList<>(
                        sheetAggregate
                        .getQuestionAggregates()
                        .size()
                );

        for (QuestionEvidenceAggregate questionAggregate
                : sheetAggregate
                .getQuestionAggregates()) {

            interpretations.add(
                    questionInterpreter.interpret(
                            questionAggregate,
                            consensusReady
                    )
            );
        }

        return new SheetInterpretationResult(
                sheetAggregate,
                interpretations
        );
    }

    public QuestionInterpreter getQuestionInterpreter() {
        return questionInterpreter;
    }
}
