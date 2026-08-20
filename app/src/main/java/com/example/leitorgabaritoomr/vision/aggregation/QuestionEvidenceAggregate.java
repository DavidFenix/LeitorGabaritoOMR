package com.example.leitorgabaritoomr.vision.aggregation;

import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado temporal agregado de uma questão.
 *
 * Ainda não declara se a questão está marcada, vazia,
 * múltipla ou ambígua. Apenas ordena as alternativas.
 */
public final class QuestionEvidenceAggregate {

    private final OmrQuestionDefinition question;

    private final List<OptionEvidenceAggregate>
            optionAggregates;

    private final Map<String, OptionEvidenceAggregate>
            aggregatesByOptionId;

    private final OptionEvidenceAggregate winner;
    private final OptionEvidenceAggregate runnerUp;

    private final int accumulatedFrames;

    private final double consensusGap;

    public QuestionEvidenceAggregate(
            OmrQuestionDefinition question,
            List<OptionEvidenceAggregate> optionAggregates
    ) {
        if (question == null) {
            throw new IllegalArgumentException(
                    "A questão é obrigatória."
            );
        }

        if (optionAggregates == null
                || optionAggregates.size()
                != question.getOptionCount()) {

            throw new IllegalArgumentException(
                    "A quantidade de agregados deve corresponder"
                            + " às alternativas da questão."
            );
        }

        this.question = question;

        List<OptionEvidenceAggregate> copy =
                new ArrayList<>(optionAggregates);

        Map<String, OptionEvidenceAggregate> index =
                new HashMap<>();

        OptionEvidenceAggregate currentWinner = null;
        OptionEvidenceAggregate currentRunnerUp = null;

        int expectedSampleCount = -1;

        for (OptionEvidenceAggregate aggregate
                : copy) {

            if (aggregate == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter agregados nulos."
                );
            }

            String optionId =
                    aggregate
                            .getOption()
                            .getId();

            OmrOptionDefinition expectedOption =
                    question.findOptionById(optionId);

            if (expectedOption == null) {
                throw new IllegalArgumentException(
                        "A alternativa "
                                + optionId
                                + " não pertence à questão "
                                + question.getId()
                                + "."
                );
            }

            if (index.put(optionId, aggregate)
                    != null) {

                throw new IllegalArgumentException(
                        "Alternativa agregada mais de uma vez: "
                                + optionId
                );
            }

            if (expectedSampleCount < 0) {
                expectedSampleCount =
                        aggregate.getSampleCount();

            } else if (aggregate.getSampleCount()
                    != expectedSampleCount) {

                throw new IllegalArgumentException(
                        "As alternativas da questão possuem"
                                + " quantidades diferentes de amostras."
                );
            }

            if (isBetter(
                    aggregate,
                    currentWinner
            )) {
                currentRunnerUp =
                        currentWinner;

                currentWinner =
                        aggregate;

            } else if (isBetter(
                    aggregate,
                    currentRunnerUp
            )) {
                currentRunnerUp =
                        aggregate;
            }
        }

        if (currentWinner == null
                || currentRunnerUp == null) {

            throw new IllegalArgumentException(
                    "Não foi possível determinar primeira"
                            + " e segunda colocadas."
            );
        }

        this.optionAggregates =
                Collections.unmodifiableList(copy);

        this.aggregatesByOptionId =
                Collections.unmodifiableMap(index);

        this.winner = currentWinner;
        this.runnerUp = currentRunnerUp;

        this.accumulatedFrames =
                Math.max(0, expectedSampleCount);

        this.consensusGap =
                Math.max(
                        0.0,
                        winner.getConsensusScore()
                                - runnerUp
                                .getConsensusScore()
                );
    }

    private boolean isBetter(
            OptionEvidenceAggregate candidate,
            OptionEvidenceAggregate current
    ) {
        if (current == null) {
            return true;
        }

        int scoreComparison =
                Double.compare(
                        candidate.getConsensusScore(),
                        current.getConsensusScore()
                );

        if (scoreComparison != 0) {
            return scoreComparison > 0;
        }

        /*
         * Critérios de desempate determinísticos.
         */
        int winsComparison =
                Integer.compare(
                        candidate.getWinCount(),
                        current.getWinCount()
                );

        if (winsComparison != 0) {
            return winsComparison > 0;
        }

        int evidenceComparison =
                Double.compare(
                        candidate.getAverageEvidence(),
                        current.getAverageEvidence()
                );

        if (evidenceComparison != 0) {
            return evidenceComparison > 0;
        }

        return candidate
                .getOption()
                .getId()
                .compareTo(
                        current
                                .getOption()
                                .getId()
                ) < 0;
    }

    public OmrQuestionDefinition getQuestion() {
        return question;
    }

    public List<OptionEvidenceAggregate>
    getOptionAggregates() {

        return optionAggregates;
    }

    public OptionEvidenceAggregate getWinner() {
        return winner;
    }

    public OptionEvidenceAggregate getRunnerUp() {
        return runnerUp;
    }

    public int getAccumulatedFrames() {
        return accumulatedFrames;
    }

    public double getConsensusGap() {
        return consensusGap;
    }

    public double getWinnerVoteRatio() {
        return winner.getWinRatio();
    }

    public int getWinnerVoteCount() {
        return winner.getWinCount();
    }

    public OptionEvidenceAggregate findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return aggregatesByOptionId.get(
                optionId
        );
    }

    public boolean isWinner(String optionId) {
        return optionId != null
                && winner
                .getOption()
                .getId()
                .equals(optionId);
    }

    public boolean isRunnerUp(String optionId) {
        return optionId != null
                && runnerUp
                .getOption()
                .getId()
                .equals(optionId);
    }
}