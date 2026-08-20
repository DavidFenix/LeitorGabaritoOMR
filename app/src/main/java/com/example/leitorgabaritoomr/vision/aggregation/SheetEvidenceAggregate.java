package com.example.leitorgabaritoomr.vision.aggregation;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fotografia imutável do consenso temporal da folha inteira.
 */
public final class SheetEvidenceAggregate {

    private final OmrLayoutDefinition layout;

    private final List<QuestionEvidenceAggregate>
            questionAggregates;

    private final Map<String, QuestionEvidenceAggregate>
            aggregatesByQuestionId;

    private final int accumulatedFrames;
    private final int requiredFrames;

    public SheetEvidenceAggregate(
            OmrLayoutDefinition layout,
            List<QuestionEvidenceAggregate> questionAggregates,
            int accumulatedFrames,
            int requiredFrames
    ) {
        if (layout == null) {
            throw new IllegalArgumentException(
                    "O layout é obrigatório."
            );
        }

        if (questionAggregates == null
                || questionAggregates.size()
                != layout.getQuestionCount()) {

            throw new IllegalArgumentException(
                    "A quantidade de questões agregadas"
                            + " não corresponde ao layout."
            );
        }

        if (accumulatedFrames < 0) {
            throw new IllegalArgumentException(
                    "accumulatedFrames não pode ser negativo."
            );
        }

        if (requiredFrames < 2) {
            throw new IllegalArgumentException(
                    "requiredFrames deve ser maior ou igual a 2."
            );
        }

        this.layout = layout;
        this.accumulatedFrames = accumulatedFrames;
        this.requiredFrames = requiredFrames;

        List<QuestionEvidenceAggregate> copy =
                new ArrayList<>(questionAggregates);

        Map<String, QuestionEvidenceAggregate> index =
                new HashMap<>();

        for (QuestionEvidenceAggregate aggregate
                : copy) {

            if (aggregate == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter questões nulas."
                );
            }

            String questionId =
                    aggregate
                            .getQuestion()
                            .getId();

            if (layout.findQuestionById(questionId)
                    == null) {

                throw new IllegalArgumentException(
                        "A questão "
                                + questionId
                                + " não pertence ao layout."
                );
            }

            if (aggregate.getAccumulatedFrames()
                    != accumulatedFrames) {

                throw new IllegalArgumentException(
                        "A questão "
                                + questionId
                                + " possui quantidade de frames"
                                + " diferente da folha."
                );
            }

            if (index.put(questionId, aggregate)
                    != null) {

                throw new IllegalArgumentException(
                        "Questão agregada mais de uma vez: "
                                + questionId
                );
            }
        }

        this.questionAggregates =
                Collections.unmodifiableList(copy);

        this.aggregatesByQuestionId =
                Collections.unmodifiableMap(index);
    }

    public OmrLayoutDefinition getLayout() {
        return layout;
    }

    public List<QuestionEvidenceAggregate>
    getQuestionAggregates() {

        return questionAggregates;
    }

    public QuestionEvidenceAggregate findByQuestionId(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        return aggregatesByQuestionId.get(
                questionId
        );
    }

    public int getAccumulatedFrames() {
        return accumulatedFrames;
    }

    public int getRequiredFrames() {
        return requiredFrames;
    }

    public boolean isReady() {
        return accumulatedFrames
                >= requiredFrames;
    }

    public double getProgressRatio() {
        return Math.min(
                1.0,
                accumulatedFrames
                        / (double) requiredFrames
        );
    }
}