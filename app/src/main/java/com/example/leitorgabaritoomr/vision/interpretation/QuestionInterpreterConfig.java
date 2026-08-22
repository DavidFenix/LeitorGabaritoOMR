package com.example.leitorgabaritoomr.vision.interpretation;

import java.util.Locale;

/**
 * Limiares explicitos da interpretacao semantica de uma questao.
 *
 * Todos os valores pertencem ao intervalo normalizado de 0.0 a
 * 1.0. A configuracao nao conhece camera, OpenCV, layout ou
 * quantidade de alternativas.
 */
public final class QuestionInterpreterConfig {

    private final double blankMaximumRobustEvidence;
    private final double markedMinimumRobustEvidence;

    private final double singleMinimumWinnerVoteRatio;
    private final double singleMinimumWeightedVoteRatio;
    private final double singleMinimumConsensusGap;
    private final double singleMinimumConfidence;

    public QuestionInterpreterConfig(
            double blankMaximumRobustEvidence,
            double markedMinimumRobustEvidence,
            double singleMinimumWinnerVoteRatio,
            double singleMinimumWeightedVoteRatio,
            double singleMinimumConsensusGap,
            double singleMinimumConfidence
    ) {
        validateRatio(
                "blankMaximumRobustEvidence",
                blankMaximumRobustEvidence
        );

        validateRatio(
                "markedMinimumRobustEvidence",
                markedMinimumRobustEvidence
        );

        validateRatio(
                "singleMinimumWinnerVoteRatio",
                singleMinimumWinnerVoteRatio
        );

        validateRatio(
                "singleMinimumWeightedVoteRatio",
                singleMinimumWeightedVoteRatio
        );

        validateRatio(
                "singleMinimumConsensusGap",
                singleMinimumConsensusGap
        );

        validateRatio(
                "singleMinimumConfidence",
                singleMinimumConfidence
        );

        if (blankMaximumRobustEvidence
                >= markedMinimumRobustEvidence) {

            throw new IllegalArgumentException(
                    "O limite de branco deve ser menor que"
                            + " o limite de marcacao."
            );
        }

        this.blankMaximumRobustEvidence =
                blankMaximumRobustEvidence;

        this.markedMinimumRobustEvidence =
                markedMinimumRobustEvidence;

        this.singleMinimumWinnerVoteRatio =
                singleMinimumWinnerVoteRatio;

        this.singleMinimumWeightedVoteRatio =
                singleMinimumWeightedVoteRatio;

        this.singleMinimumConsensusGap =
                singleMinimumConsensusGap;

        this.singleMinimumConfidence =
                singleMinimumConfidence;
    }

    /**
     * Valores deliberadamente conservadores para o Laboratorio.
     *
     * Eles existem para permitir o primeiro diagnostico completo,
     * mas somente devem ser promovidos para producao depois de
     * testes com questoes vazias, multiplas, fracas e rasuradas.
     */
    public static QuestionInterpreterConfig
    developmentDefaults() {
        return new QuestionInterpreterConfig(
                0.20,
                0.45,
                0.60,
                0.55,
                0.08,
                0.55
        );
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

    public double getBlankMaximumRobustEvidence() {
        return blankMaximumRobustEvidence;
    }

    public double getMarkedMinimumRobustEvidence() {
        return markedMinimumRobustEvidence;
    }

    public double getSingleMinimumWinnerVoteRatio() {
        return singleMinimumWinnerVoteRatio;
    }

    public double getSingleMinimumWeightedVoteRatio() {
        return singleMinimumWeightedVoteRatio;
    }

    public double getSingleMinimumConsensusGap() {
        return singleMinimumConsensusGap;
    }

    public double getSingleMinimumConfidence() {
        return singleMinimumConfidence;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "blank<=%.3f marked>=%.3f"
                        + " vote>=%.3f weightedVote>=%.3f"
                        + " gap>=%.3f confidence>=%.3f",
                blankMaximumRobustEvidence,
                markedMinimumRobustEvidence,
                singleMinimumWinnerVoteRatio,
                singleMinimumWeightedVoteRatio,
                singleMinimumConsensusGap,
                singleMinimumConfidence
        );
    }
}
