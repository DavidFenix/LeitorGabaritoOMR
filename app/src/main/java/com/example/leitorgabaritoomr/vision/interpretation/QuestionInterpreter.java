package com.example.leitorgabaritoomr.vision.interpretation;

import com.example.leitorgabaritoomr.vision.aggregation.OptionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converte o ranking temporal de uma questao em uma interpretacao
 * semantica segura.
 *
 * O interpretador pode recusar uma resposta. Ter uma alternativa em
 * primeiro lugar nao significa, por si so, que exista uma marcacao
 * unica valida.
 */
public final class QuestionInterpreter {

    private static final double STRONG_GAP_REFERENCE = 0.25;

    private final QuestionInterpreterConfig config;

    public QuestionInterpreter(
            QuestionInterpreterConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "QuestionInterpreterConfig e obrigatorio."
            );
        }

        this.config = config;
    }

    public QuestionInterpretation interpret(
            QuestionEvidenceAggregate aggregate,
            boolean consensusReady
    ) {
        if (aggregate == null) {
            throw new IllegalArgumentException(
                    "O agregado da questao e obrigatorio."
            );
        }

        if (!consensusReady) {
            return new QuestionInterpretation(
                    aggregate,
                    QuestionMarkState.NOT_READY,
                    Collections
                            .<OmrOptionDefinition>emptyList(),
                    0.0
            );
        }

        List<OptionEvidenceAggregate> strongOptions =
                findStrongOptions(aggregate);

        double highestRobustEvidence =
                findHighestRobustEvidence(aggregate);

        if (strongOptions.size() >= 2) {
            return createMultipleInterpretation(
                    aggregate,
                    strongOptions
            );
        }

        if (highestRobustEvidence
                <= config
                .getBlankMaximumRobustEvidence()) {

            return createBlankInterpretation(
                    aggregate,
                    highestRobustEvidence
            );
        }

        if (strongOptions.size() == 1) {
            QuestionInterpretation single =
                    tryCreateSingleInterpretation(
                            aggregate,
                            strongOptions.get(0)
                    );

            if (single != null) {
                return single;
            }
        }

        return createAmbiguousInterpretation(
                aggregate
        );
    }

    private List<OptionEvidenceAggregate>
    findStrongOptions(
            QuestionEvidenceAggregate aggregate
    ) {
        List<OptionEvidenceAggregate> result =
                new ArrayList<>();

        for (OptionEvidenceAggregate option
                : aggregate.getOptionAggregates()) {

            if (option.getRobustEvidence()
                    >= config
                    .getMarkedMinimumRobustEvidence()) {

                result.add(option);
            }
        }

        return result;
    }

    private double findHighestRobustEvidence(
            QuestionEvidenceAggregate aggregate
    ) {
        double highest = 0.0;

        for (OptionEvidenceAggregate option
                : aggregate.getOptionAggregates()) {

            highest = Math.max(
                    highest,
                    option.getRobustEvidence()
            );
        }

        return highest;
    }

    private QuestionInterpretation
    createMultipleInterpretation(
            QuestionEvidenceAggregate aggregate,
            List<OptionEvidenceAggregate> strongOptions
    ) {
        List<OmrOptionDefinition> markedOptions =
                new ArrayList<>(strongOptions.size());

        double evidenceSum = 0.0;

        for (OptionEvidenceAggregate option
                : strongOptions) {

            markedOptions.add(option.getOption());

            evidenceSum +=
                    option.getRobustEvidence();
        }

        double confidence =
                clamp01(
                        evidenceSum
                                / strongOptions.size()
                );

        return new QuestionInterpretation(
                aggregate,
                QuestionMarkState.MULTIPLE_MARKS,
                markedOptions,
                confidence
        );
    }

    private QuestionInterpretation
    createBlankInterpretation(
            QuestionEvidenceAggregate aggregate,
            double highestRobustEvidence
    ) {
        double blankLimit =
                config
                .getBlankMaximumRobustEvidence();

        double confidence;

        if (blankLimit <= 0.0) {
            confidence = highestRobustEvidence <= 0.0
                    ? 1.0
                    : 0.0;
        } else {
            confidence =
                    clamp01(
                            1.0
                                    - highestRobustEvidence
                                    / blankLimit
                    );
        }

        return new QuestionInterpretation(
                aggregate,
                QuestionMarkState.BLANK,
                Collections
                        .<OmrOptionDefinition>emptyList(),
                confidence
        );
    }

    private QuestionInterpretation
    tryCreateSingleInterpretation(
            QuestionEvidenceAggregate aggregate,
            OptionEvidenceAggregate strongOption
    ) {
        OptionEvidenceAggregate winner =
                aggregate.getWinner();

        String winnerId =
                winner.getOption().getId();

        if (!winnerId.equals(
                strongOption.getOption().getId()
        )) {
            return null;
        }

        double voteRatio =
                aggregate.getWinnerVoteRatio();

        double weightedVoteRatio =
                winner.getWeightedWinRatio();

        double consensusGap =
                aggregate.getConsensusGap();

        if (voteRatio
                < config
                .getSingleMinimumWinnerVoteRatio()) {

            return null;
        }

        if (weightedVoteRatio
                < config
                .getSingleMinimumWeightedVoteRatio()) {

            return null;
        }

        if (consensusGap
                < config
                .getSingleMinimumConsensusGap()) {

            return null;
        }

        double confidence =
                calculateSingleConfidence(
                        winner,
                        voteRatio,
                        weightedVoteRatio,
                        consensusGap
                );

        if (confidence
                < config.getSingleMinimumConfidence()) {

            return null;
        }

        return new QuestionInterpretation(
                aggregate,
                QuestionMarkState.SINGLE_MARK,
                Collections.singletonList(
                        winner.getOption()
                ),
                confidence
        );
    }

    private double calculateSingleConfidence(
            OptionEvidenceAggregate winner,
            double voteRatio,
            double weightedVoteRatio,
            double consensusGap
    ) {
        double gapStrength =
                clamp01(
                        consensusGap
                                / STRONG_GAP_REFERENCE
                );

        return clamp01(
                0.35 * winner.getRobustEvidence()
                        + 0.25 * voteRatio
                        + 0.20 * weightedVoteRatio
                        + 0.20 * gapStrength
        );
    }

    private QuestionInterpretation
    createAmbiguousInterpretation(
            QuestionEvidenceAggregate aggregate
    ) {
        List<OmrOptionDefinition> relevantOptions =
                new ArrayList<>();

        double blankLimit =
                config
                .getBlankMaximumRobustEvidence();

        for (OptionEvidenceAggregate option
                : aggregate.getOptionAggregates()) {

            if (option.getRobustEvidence()
                    > blankLimit) {

                relevantOptions.add(
                        option.getOption()
                );
            }
        }

        if (relevantOptions.isEmpty()) {
            relevantOptions.add(
                    aggregate
                    .getWinner()
                    .getOption()
            );
        }

        return new QuestionInterpretation(
                aggregate,
                QuestionMarkState.AMBIGUOUS,
                relevantOptions,
                0.0
        );
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    public QuestionInterpreterConfig getConfig() {
        return config;
    }
}
