package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agrupa as medições da folha por questão.
 */
public final class OmrQuestionMeasurer {

    private final BubbleEvidenceScorer evidenceScorer;

    public OmrQuestionMeasurer(
            BubbleEvidenceScorer evidenceScorer
    ) {
        if (evidenceScorer == null) {
            throw new IllegalArgumentException(
                    "BubbleEvidenceScorer é obrigatório."
            );
        }

        this.evidenceScorer =
                evidenceScorer;
    }

    public List<QuestionMeasurement> measure(
            OmrSheetMeasurementResult sheetResult
    ) {
        if (sheetResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da folha é obrigatório."
            );
        }

        if (!sheetResult.isComplete()) {
            throw new IllegalArgumentException(
                    "A folha precisa estar completamente medida."
            );
        }

        List<QuestionMeasurement>
                questionMeasurements =
                new ArrayList<>();

        for (OmrBlockDefinition block
                : sheetResult
                .getLayout()
                .getBlocks()) {

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                questionMeasurements.add(
                        measureQuestion(
                                sheetResult,
                                question
                        )
                );
            }
        }

        return Collections.unmodifiableList(
                questionMeasurements
        );
    }

    private QuestionMeasurement measureQuestion(
            OmrSheetMeasurementResult sheetResult,
            OmrQuestionDefinition question
    ) {
        List<BubbleMeasurement> measurements =
                new ArrayList<>();

        for (OmrOptionDefinition option
                : question.getOptions()) {

            BubbleMeasurement measurement =
                    sheetResult.findByOptionId(
                            option.getId()
                    );

            if (measurement == null) {
                throw new IllegalStateException(
                        "Medição ausente para "
                                + option.getId()
                );
            }

            measurements.add(measurement);
        }

        return new QuestionMeasurement(
                question,
                measurements,
                evidenceScorer
        );
    }
}