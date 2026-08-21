package com.example.leitorgabaritoomr.vision.registration;

/**
 * Configuração genérica do registrador de grades OMR.
 *
 * As dimensões de procura e filtragem são expressas como
 * proporções do tamanho previsto da bolha.
 *
 * Portanto, não dependem de:
 *
 * - quantidade de blocos;
 * - quantidade de questões;
 * - quantidade de alternativas;
 * - resolução fixa;
 * - medidas em milímetros.
 */
public final class BubbleGridRegistrarConfig {

    /*
     * Pré-processamento.
     */
    private final int blurKernelSize;

    private final double adaptiveBlockSizeScale;
    private final int minimumAdaptiveBlockSize;
    private final int maximumAdaptiveBlockSize;
    private final double adaptiveThresholdConstant;

    /*
     * Tamanho aceitável do candidato em relação ao tamanho
     * previsto da bolha.
     */
    private final double minimumCandidateWidthScale;
    private final double maximumCandidateWidthScale;

    private final double minimumCandidateHeightScale;
    private final double maximumCandidateHeightScale;

    /*
     * Critérios geométricos do contorno.
     */
    private final double minimumRectangularity;
    private final double minimumAspectRatio;
    private final double maximumAspectRatio;

    /*
     * Distância máxima de procura em múltiplos da largura
     * e da altura previstas.
     */
    private final double searchRadiusXScale;
    private final double searchRadiusYScale;

    /*
     * Pontuação mínima para associar um contorno a uma
     * alternativa esperada.
     */
    private final double minimumCandidateScore;

    /*
     * Quantidade mínima de correspondências diretas para
     * aceitar o modelo de um bloco.
     */
    private final int minimumDirectMatchesPerBlock;
    private final double minimumDirectMatchRatio;

    /*
     * Erro residual máximo em relação à diagonal prevista
     * da bolha.
     */
    private final double maximumResidualDiagonalScale;

    /*
     * Variação máxima de escala permitida pelo registro.
     *
     * Exemplo: 0.20 permite escalas entre 0.80 e 1.20.
     */
    private final double maximumScaleDeviation;

    private final double minimumBlockConfidence;
    private final double minimumSheetConfidence;

    public BubbleGridRegistrarConfig(
            int blurKernelSize,
            double adaptiveBlockSizeScale,
            int minimumAdaptiveBlockSize,
            int maximumAdaptiveBlockSize,
            double adaptiveThresholdConstant,
            double minimumCandidateWidthScale,
            double maximumCandidateWidthScale,
            double minimumCandidateHeightScale,
            double maximumCandidateHeightScale,
            double minimumRectangularity,
            double minimumAspectRatio,
            double maximumAspectRatio,
            double searchRadiusXScale,
            double searchRadiusYScale,
            double minimumCandidateScore,
            int minimumDirectMatchesPerBlock,
            double minimumDirectMatchRatio,
            double maximumResidualDiagonalScale,
            double maximumScaleDeviation,
            double minimumBlockConfidence,
            double minimumSheetConfidence
    ) {
        validateOddKernel(
                "blurKernelSize",
                blurKernelSize,
                1
        );

        validatePositive(
                "adaptiveBlockSizeScale",
                adaptiveBlockSizeScale
        );

        validateOddKernel(
                "minimumAdaptiveBlockSize",
                minimumAdaptiveBlockSize,
                3
        );

        validateOddKernel(
                "maximumAdaptiveBlockSize",
                maximumAdaptiveBlockSize,
                minimumAdaptiveBlockSize
        );

        if (maximumAdaptiveBlockSize
                < minimumAdaptiveBlockSize) {

            throw new IllegalArgumentException(
                    "O bloco adaptativo máximo não pode"
                            + " ser menor que o mínimo."
            );
        }

        if (!Double.isFinite(
                adaptiveThresholdConstant
        )) {
            throw new IllegalArgumentException(
                    "adaptiveThresholdConstant deve ser finito."
            );
        }

        validateScaleRange(
                "largura",
                minimumCandidateWidthScale,
                maximumCandidateWidthScale
        );

        validateScaleRange(
                "altura",
                minimumCandidateHeightScale,
                maximumCandidateHeightScale
        );

        validateRatio(
                "minimumRectangularity",
                minimumRectangularity
        );

        validatePositive(
                "minimumAspectRatio",
                minimumAspectRatio
        );

        validatePositive(
                "maximumAspectRatio",
                maximumAspectRatio
        );

        if (maximumAspectRatio
                < minimumAspectRatio) {

            throw new IllegalArgumentException(
                    "maximumAspectRatio não pode ser menor"
                            + " que minimumAspectRatio."
            );
        }

        validatePositive(
                "searchRadiusXScale",
                searchRadiusXScale
        );

        validatePositive(
                "searchRadiusYScale",
                searchRadiusYScale
        );

        validateRatio(
                "minimumCandidateScore",
                minimumCandidateScore
        );

        if (minimumDirectMatchesPerBlock < 2) {
            throw new IllegalArgumentException(
                    "minimumDirectMatchesPerBlock deve ser"
                            + " maior ou igual a 2."
            );
        }

        validateRatio(
                "minimumDirectMatchRatio",
                minimumDirectMatchRatio
        );

        validatePositive(
                "maximumResidualDiagonalScale",
                maximumResidualDiagonalScale
        );

        if (!Double.isFinite(maximumScaleDeviation)
                || maximumScaleDeviation < 0.0
                || maximumScaleDeviation >= 1.0) {

            throw new IllegalArgumentException(
                    "maximumScaleDeviation deve estar"
                            + " entre 0.0 e 1.0."
            );
        }

        validateRatio(
                "minimumBlockConfidence",
                minimumBlockConfidence
        );

        validateRatio(
                "minimumSheetConfidence",
                minimumSheetConfidence
        );

        this.blurKernelSize = blurKernelSize;

        this.adaptiveBlockSizeScale =
                adaptiveBlockSizeScale;

        this.minimumAdaptiveBlockSize =
                minimumAdaptiveBlockSize;

        this.maximumAdaptiveBlockSize =
                maximumAdaptiveBlockSize;

        this.adaptiveThresholdConstant =
                adaptiveThresholdConstant;

        this.minimumCandidateWidthScale =
                minimumCandidateWidthScale;

        this.maximumCandidateWidthScale =
                maximumCandidateWidthScale;

        this.minimumCandidateHeightScale =
                minimumCandidateHeightScale;

        this.maximumCandidateHeightScale =
                maximumCandidateHeightScale;

        this.minimumRectangularity =
                minimumRectangularity;

        this.minimumAspectRatio =
                minimumAspectRatio;

        this.maximumAspectRatio =
                maximumAspectRatio;

        this.searchRadiusXScale =
                searchRadiusXScale;

        this.searchRadiusYScale =
                searchRadiusYScale;

        this.minimumCandidateScore =
                minimumCandidateScore;

        this.minimumDirectMatchesPerBlock =
                minimumDirectMatchesPerBlock;

        this.minimumDirectMatchRatio =
                minimumDirectMatchRatio;

        this.maximumResidualDiagonalScale =
                maximumResidualDiagonalScale;

        this.maximumScaleDeviation =
                maximumScaleDeviation;

        this.minimumBlockConfidence =
                minimumBlockConfidence;

        this.minimumSheetConfidence =
                minimumSheetConfidence;
    }

    public static BubbleGridRegistrarConfig
    developmentDefaults() {

        return new BubbleGridRegistrarConfig(
                /*
                 * Suavização leve.
                 */
                3,

                /*
                 * Tamanho do bloco do threshold adaptativo,
                 * proporcional à maior dimensão da bolha.
                 */
                3.0,
                15,
                61,
                7.0,

                /*
                 * Intervalos relativos de tamanho.
                 */
                0.55,
                1.65,
                0.55,
                1.65,

                /*
                 * Forma mínima.
                 */
                0.20,
                0.55,
                1.80,

                /*
                 * Janela de procura local.
                 */
                1.60,
                1.60,

                /*
                 * Associação mínima candidato ↔ alternativa.
                 */
                0.42,

                /*
                 * Evidência mínima por bloco.
                 */
                4,
                0.25,

                /*
                 * Erro residual máximo.
                 */
                0.65,

                /*
                 * Escala permitida: 0.80 até 1.20.
                 */
                0.20,

                /*
                 * Confianças mínimas.
                 */
                0.55,
                0.55
        );
    }

    private void validateOddKernel(
            String fieldName,
            int value,
            int minimum
    ) {
        if (value < minimum
                || value % 2 == 0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser ímpar e maior"
                            + " ou igual a "
                            + minimum
                            + "."
            );
        }
    }

    private void validateScaleRange(
            String name,
            double minimum,
            double maximum
    ) {
        validatePositive(
                "minimumCandidate"
                        + name,
                minimum
        );

        validatePositive(
                "maximumCandidate"
                        + name,
                maximum
        );

        if (maximum < minimum) {
            throw new IllegalArgumentException(
                    "A escala máxima de "
                            + name
                            + " não pode ser menor"
                            + " que a mínima."
            );
        }
    }

    private void validatePositive(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser positivo."
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

    public int getBlurKernelSize() {
        return blurKernelSize;
    }

    public double getAdaptiveBlockSizeScale() {
        return adaptiveBlockSizeScale;
    }

    public int getMinimumAdaptiveBlockSize() {
        return minimumAdaptiveBlockSize;
    }

    public int getMaximumAdaptiveBlockSize() {
        return maximumAdaptiveBlockSize;
    }

    public double getAdaptiveThresholdConstant() {
        return adaptiveThresholdConstant;
    }

    public double getMinimumCandidateWidthScale() {
        return minimumCandidateWidthScale;
    }

    public double getMaximumCandidateWidthScale() {
        return maximumCandidateWidthScale;
    }

    public double getMinimumCandidateHeightScale() {
        return minimumCandidateHeightScale;
    }

    public double getMaximumCandidateHeightScale() {
        return maximumCandidateHeightScale;
    }

    public double getMinimumRectangularity() {
        return minimumRectangularity;
    }

    public double getMinimumAspectRatio() {
        return minimumAspectRatio;
    }

    public double getMaximumAspectRatio() {
        return maximumAspectRatio;
    }

    public double getSearchRadiusXScale() {
        return searchRadiusXScale;
    }

    public double getSearchRadiusYScale() {
        return searchRadiusYScale;
    }

    public double getMinimumCandidateScore() {
        return minimumCandidateScore;
    }

    public int getMinimumDirectMatchesPerBlock() {
        return minimumDirectMatchesPerBlock;
    }

    public double getMinimumDirectMatchRatio() {
        return minimumDirectMatchRatio;
    }

    public double getMaximumResidualDiagonalScale() {
        return maximumResidualDiagonalScale;
    }

    public double getMaximumScaleDeviation() {
        return maximumScaleDeviation;
    }

    public double getMinimumBlockConfidence() {
        return minimumBlockConfidence;
    }

    public double getMinimumSheetConfidence() {
        return minimumSheetConfidence;
    }
}