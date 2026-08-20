package com.example.leitorgabaritoomr.vision.aggregation;

/**
 * Configura o consenso temporal das respostas.
 *
 * Uma vitória deixa de valer automaticamente um voto inteiro.
 * O peso dependerá da evidência da alternativa vencedora e
 * da vantagem sobre a segunda colocada.
 */
public final class QuestionEvidenceAccumulatorConfig {

    private final int requiredFrames;

    private final double winRatioWeight;
    private final double relativeEvidenceWeight;
    private final double absoluteEvidenceWeight;

    /*
     * Evidência mínima para que uma vitória possa participar
     * da votação ponderada.
     */
    private final double minimumBestEvidenceForVote;

    /*
     * Gap que passa a representar separação máxima entre
     * a primeira e a segunda colocadas.
     *
     * Exemplo:
     * gap 0.10 com saturation 0.20 produz qualidade 0.50.
     */
    private final double winningGapSaturation;

    /*
     * Votos com peso inferior a este valor são ignorados.
     */
    private final double minimumVoteWeight;

    /**
     * Construtor anterior mantido para compatibilidade.
     */
    public QuestionEvidenceAccumulatorConfig(
            int requiredFrames,
            double winRatioWeight,
            double relativeEvidenceWeight,
            double absoluteEvidenceWeight
    ) {
        this(
                requiredFrames,
                winRatioWeight,
                relativeEvidenceWeight,
                absoluteEvidenceWeight,
                0.08,
                0.20,
                0.03
        );
    }

    /**
     * Construtor completo do consenso ponderado.
     */
    public QuestionEvidenceAccumulatorConfig(
            int requiredFrames,
            double winRatioWeight,
            double relativeEvidenceWeight,
            double absoluteEvidenceWeight,
            double minimumBestEvidenceForVote,
            double winningGapSaturation,
            double minimumVoteWeight
    ) {
        if (requiredFrames < 2) {
            throw new IllegalArgumentException(
                    "requiredFrames deve ser maior ou igual a 2."
            );
        }

        validateWeight(
                "winRatioWeight",
                winRatioWeight
        );

        validateWeight(
                "relativeEvidenceWeight",
                relativeEvidenceWeight
        );

        validateWeight(
                "absoluteEvidenceWeight",
                absoluteEvidenceWeight
        );

        if (winRatioWeight
                + relativeEvidenceWeight
                + absoluteEvidenceWeight
                <= 0.0) {

            throw new IllegalArgumentException(
                    "Pelo menos um peso deve ser positivo."
            );
        }

        validateRatio(
                "minimumBestEvidenceForVote",
                minimumBestEvidenceForVote
        );

        if (!Double.isFinite(winningGapSaturation)
                || winningGapSaturation <= 0.0
                || winningGapSaturation > 1.0) {

            throw new IllegalArgumentException(
                    "winningGapSaturation deve estar"
                            + " entre 0.0 exclusivo e 1.0."
            );
        }

        validateRatio(
                "minimumVoteWeight",
                minimumVoteWeight
        );

        this.requiredFrames =
                requiredFrames;

        this.winRatioWeight =
                winRatioWeight;

        this.relativeEvidenceWeight =
                relativeEvidenceWeight;

        this.absoluteEvidenceWeight =
                absoluteEvidenceWeight;

        this.minimumBestEvidenceForVote =
                minimumBestEvidenceForVote;

        this.winningGapSaturation =
                winningGapSaturation;

        this.minimumVoteWeight =
                minimumVoteWeight;
    }

    public static QuestionEvidenceAccumulatorConfig
    developmentDefaults() {

        return new QuestionEvidenceAccumulatorConfig(
                /*
                 * Sete observações completas antes de concluir.
                 */
                7,

                /*
                 * O voto ponderado continua sendo o sinal
                 * principal, mas deixa de dominar sozinho.
                 */
                0.55,

                /*
                 * A posição relativa dentro da questão ajuda
                 * a confirmar a consistência temporal.
                 */
                0.15,

                /*
                 * Este peso será aplicado à evidência robusta:
                 * média das duas melhores observações.
                 */
                0.30,

                /*
                 * Uma alternativa com evidência inferior a 8%
                 * não produz voto ponderado.
                 */
                0.08,

                /*
                 * Um gap de 20% ou mais já representa
                 * separação máxima para o peso do voto.
                 */
                0.20,

                /*
                 * Descarta vitórias praticamente sem separação.
                 */
                0.03
        );
    }

    private void validateWeight(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser finito e não negativo."
            );
        }
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

    public int getRequiredFrames() {
        return requiredFrames;
    }

    /**
     * No novo acumulador, representa o peso da proporção
     * ponderada de vitórias.
     */
    public double getWinRatioWeight() {
        return winRatioWeight;
    }

    public double getRelativeEvidenceWeight() {
        return relativeEvidenceWeight;
    }

    /**
     * No novo acumulador, este peso será aplicado à
     * evidência robusta, não apenas à média absoluta.
     */
    public double getAbsoluteEvidenceWeight() {
        return absoluteEvidenceWeight;
    }

    public double getMinimumBestEvidenceForVote() {
        return minimumBestEvidenceForVote;
    }

    public double getWinningGapSaturation() {
        return winningGapSaturation;
    }

    public double getMinimumVoteWeight() {
        return minimumVoteWeight;
    }

    public double getTotalWeight() {
        return winRatioWeight
                + relativeEvidenceWeight
                + absoluteEvidenceWeight;
    }
}