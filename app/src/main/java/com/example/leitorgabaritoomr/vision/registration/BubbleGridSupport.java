package com.example.leitorgabaritoomr.vision.registration;

import java.util.Locale;

/**
 * Correspondencia concreta usada pelo registro geometrico de uma
 * bolha.
 *
 * Esta classe e a fonte unica dos valores apresentados no
 * Laboratorio OMR e avaliados pelo registrador. Ela guarda:
 *
 * - o alvo definido pelo layout;
 * - o contorno realmente observado;
 * - a transformacao aplicada ao bloco;
 * - o centro e o tamanho previstos;
 * - o erro residual em pixels e de forma normalizada;
 * - a qualidade geometrica do contorno.
 *
 * Como todos os objetos referenciados sao imutaveis, uma instancia
 * representa uma fotografia exata da evidencia usada no calculo.
 */
public final class BubbleGridSupport {

    private final ExpectedBubbleTarget target;
    private final BubbleContourCandidate candidate;
    private final BubbleBlockTransform blockTransform;

    private final double predictedCenterX;
    private final double predictedCenterY;

    private final double predictedWidth;
    private final double predictedHeight;
    private final double predictedDiagonal;

    private final double residualX;
    private final double residualY;
    private final double residualDistance;
    private final double normalizedResidual;

    private final double quality;

    public BubbleGridSupport(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate,
            BubbleBlockTransform blockTransform,
            double quality
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "O alvo esperado e obrigatorio."
            );
        }

        if (candidate == null) {
            throw new IllegalArgumentException(
                    "O candidato observado e obrigatorio."
            );
        }

        if (blockTransform == null) {
            throw new IllegalArgumentException(
                    "A transformacao do bloco e obrigatoria."
            );
        }

        if (target.getBlockIndex()
                != blockTransform.getBlockIndex()
                || !target.getBlockId().equals(
                blockTransform.getBlockId()
        )) {

            throw new IllegalArgumentException(
                    "O alvo e a transformacao pertencem"
                            + " a blocos diferentes."
            );
        }

        validateRatio("quality", quality);

        this.target = target;
        this.candidate = candidate;
        this.blockTransform = blockTransform;

        this.predictedCenterX =
                blockTransform.predictCenterX(target);

        this.predictedCenterY =
                blockTransform.predictCenterY(target);

        this.predictedWidth =
                blockTransform.predictBoundingWidth(target);

        this.predictedHeight =
                blockTransform.predictBoundingHeight(target);

        validatePositive(
                "predictedWidth",
                predictedWidth
        );

        validatePositive(
                "predictedHeight",
                predictedHeight
        );

        this.predictedDiagonal =
                Math.hypot(
                        predictedWidth,
                        predictedHeight
                );

        this.residualX =
                candidate.getCenterX()
                        - predictedCenterX;

        this.residualY =
                candidate.getCenterY()
                        - predictedCenterY;

        this.residualDistance =
                Math.hypot(
                        residualX,
                        residualY
                );

        this.normalizedResidual =
                residualDistance
                        / predictedDiagonal;

        this.quality = quality;
    }

    private void validatePositive(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0) {

            throw new IllegalArgumentException(
                    fieldName + " deve ser positivo."
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

    public ExpectedBubbleTarget getTarget() {
        return target;
    }

    public BubbleContourCandidate getCandidate() {
        return candidate;
    }

    public BubbleBlockTransform getBlockTransform() {
        return blockTransform;
    }

    public double getPredictedCenterX() {
        return predictedCenterX;
    }

    public double getPredictedCenterY() {
        return predictedCenterY;
    }

    public double getPredictedWidth() {
        return predictedWidth;
    }

    public double getPredictedHeight() {
        return predictedHeight;
    }

    public double getPredictedDiagonal() {
        return predictedDiagonal;
    }

    public double getObservedCenterX() {
        return candidate.getCenterX();
    }

    public double getObservedCenterY() {
        return candidate.getCenterY();
    }

    public double getResidualX() {
        return residualX;
    }

    public double getResidualY() {
        return residualY;
    }

    public double getResidualDistance() {
        return residualDistance;
    }

    /**
     * Erro dividido pela diagonal da regiao transformada.
     *
     * Isso permite comparar o alinhamento em imagens com
     * resolucoes e tamanhos de bolha diferentes.
     */
    public double getNormalizedResidual() {
        return normalizedResidual;
    }

    public double getQuality() {
        return quality;
    }

    public boolean isWithinNormalizedResidual(
            double maximumNormalizedResidual
    ) {
        if (!Double.isFinite(maximumNormalizedResidual)
                || maximumNormalizedResidual < 0.0) {

            throw new IllegalArgumentException(
                    "maximumNormalizedResidual deve ser"
                            + " finito e nao negativo."
            );
        }

        return normalizedResidual
                <= maximumNormalizedResidual;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s -> candidate-%d"
                        + " predicted=(%.2f, %.2f)"
                        + " observed=(%.2f, %.2f)"
                        + " residual=(%.2f, %.2f)"
                        + " distance=%.3f"
                        + " normalized=%.4f"
                        + " quality=%.3f",
                target.getOptionId(),
                candidate.getCandidateId(),
                predictedCenterX,
                predictedCenterY,
                candidate.getCenterX(),
                candidate.getCenterY(),
                residualX,
                residualY,
                residualDistance,
                normalizedResidual,
                quality
        );
    }
}
