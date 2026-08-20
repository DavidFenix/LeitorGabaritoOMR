package com.example.leitorgabaritoomr.vision.aggregation;

import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.Locale;

/**
 * Resultado temporal acumulado de uma alternativa.
 *
 * É imutável e representa uma fotografia do acumulador
 * em determinado instante.
 */
public final class OptionEvidenceAggregate {

    private final OmrOptionDefinition option;

    private final int sampleCount;
    private final int winCount;

    /*
     * Soma ponderada das vitórias da alternativa.
     *
     * Uma vitória clara terá peso maior que uma vitória
     * ocorrida em um frame fraco ou ambíguo.
     */
    private final double weightedWinScore;

    /*
     * Proporção das vitórias ponderadas da alternativa
     * em relação ao peso total observado na questão.
     */
    private final double weightedWinRatio;

    private final double averageEvidence;
    private final double maximumEvidence;

    /*
     * Média das duas melhores evidências observadas.
     *
     * É mais robusta que usar somente o máximo, pois um
     * único frame defeituoso não deve dominar o resultado.
     */
    private final double robustEvidence;

    private final double averageRelativeEvidence;

    /*
     * Média da vantagem sobre a segunda colocada nos frames
     * em que esta alternativa ficou em primeiro lugar.
     */
    private final double averageWinningGap;

    /*
     * Pontuação final usada para ordenar as alternativas
     * no consenso temporal.
     */
    private final double consensusScore;

    /**
     * Construtor mantido para compatibilidade com o código
     * anterior.
     */
    public OptionEvidenceAggregate(
            OmrOptionDefinition option,
            int sampleCount,
            int winCount,
            double averageEvidence,
            double maximumEvidence,
            double averageRelativeEvidence,
            double averageWinningGap,
            double consensusScore
    ) {
        this(
                option,
                sampleCount,
                winCount,
                winCount,
                sampleCount <= 0
                        ? 0.0
                        : winCount / (double) sampleCount,
                averageEvidence,
                maximumEvidence,
                averageEvidence,
                averageRelativeEvidence,
                averageWinningGap,
                consensusScore
        );
    }

    /**
     * Construtor completo usado pelo novo consenso ponderado.
     */
    public OptionEvidenceAggregate(
            OmrOptionDefinition option,
            int sampleCount,
            int winCount,
            double weightedWinScore,
            double weightedWinRatio,
            double averageEvidence,
            double maximumEvidence,
            double robustEvidence,
            double averageRelativeEvidence,
            double averageWinningGap,
            double consensusScore
    ) {
        if (option == null) {
            throw new IllegalArgumentException(
                    "A alternativa é obrigatória."
            );
        }

        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "sampleCount não pode ser negativo."
            );
        }

        if (winCount < 0 || winCount > sampleCount) {
            throw new IllegalArgumentException(
                    "winCount deve estar entre zero e sampleCount."
            );
        }

        if (!Double.isFinite(weightedWinScore)
                || weightedWinScore < 0.0) {

            throw new IllegalArgumentException(
                    "weightedWinScore deve ser finito"
                            + " e não negativo."
            );
        }

        validateRatio(
                "weightedWinRatio",
                weightedWinRatio
        );

        validateRatio(
                "averageEvidence",
                averageEvidence
        );

        validateRatio(
                "maximumEvidence",
                maximumEvidence
        );

        validateRatio(
                "robustEvidence",
                robustEvidence
        );

        validateRatio(
                "averageRelativeEvidence",
                averageRelativeEvidence
        );

        validateRatio(
                "averageWinningGap",
                averageWinningGap
        );

        validateRatio(
                "consensusScore",
                consensusScore
        );

        this.option = option;

        this.sampleCount = sampleCount;
        this.winCount = winCount;

        this.weightedWinScore =
                weightedWinScore;

        this.weightedWinRatio =
                weightedWinRatio;

        this.averageEvidence =
                averageEvidence;

        this.maximumEvidence =
                maximumEvidence;

        this.robustEvidence =
                robustEvidence;

        this.averageRelativeEvidence =
                averageRelativeEvidence;

        this.averageWinningGap =
                averageWinningGap;

        this.consensusScore =
                consensusScore;
    }

    private void validateRatio(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve estar entre 0.0 e 1.0."
            );
        }
    }

    public OmrOptionDefinition getOption() {
        return option;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public int getWinCount() {
        return winCount;
    }

    /**
     * Frequência simples de vitórias.
     *
     * Mantida para diagnóstico e compatibilidade.
     */
    public double getWinRatio() {
        if (sampleCount <= 0) {
            return 0.0;
        }

        return winCount
                / (double) sampleCount;
    }

    public double getWeightedWinScore() {
        return weightedWinScore;
    }

    public double getWeightedWinRatio() {
        return weightedWinRatio;
    }

    public double getAverageEvidence() {
        return averageEvidence;
    }

    public double getMaximumEvidence() {
        return maximumEvidence;
    }

    public double getRobustEvidence() {
        return robustEvidence;
    }

    public double getAverageRelativeEvidence() {
        return averageRelativeEvidence;
    }

    public double getAverageWinningGap() {
        return averageWinningGap;
    }

    public double getConsensusScore() {
        return consensusScore;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s amostras=%d vitorias=%d taxa=%.3f"
                        + " votoPonderado=%.3f"
                        + " taxaPonderada=%.3f"
                        + " evidenciaMedia=%.3f"
                        + " evidenciaRobusta=%.3f"
                        + " relativa=%.3f"
                        + " gap=%.3f"
                        + " consenso=%.3f",
                option.getId(),
                sampleCount,
                winCount,
                getWinRatio(),
                weightedWinScore,
                weightedWinRatio,
                averageEvidence,
                robustEvidence,
                averageRelativeEvidence,
                averageWinningGap,
                consensusScore
        );
    }
}