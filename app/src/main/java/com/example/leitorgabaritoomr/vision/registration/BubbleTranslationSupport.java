package com.example.leitorgabaritoomr.vision.registration;

import java.util.Locale;

/**
 * Evidencia concreta utilizada para sustentar a estimativa de
 * deslocamento de um bloco.
 *
 * O Laboratorio OMR desenhara exatamente estes dados. Portanto,
 * o alvo, o candidato, a posicao prevista pelo modelo e o erro
 * residual nao sao informacoes decorativas: sao os mesmos valores
 * que participam da validacao geometrica.
 */
public final class BubbleTranslationSupport {

    private final ExpectedBubbleTarget target;
    private final BubbleContourCandidate candidate;

    private final double predictedCenterX;
    private final double predictedCenterY;

    private final double residualX;
    private final double residualY;
    private final double residualDistance;

    private final double quality;

    public BubbleTranslationSupport(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate,
            double predictedCenterX,
            double predictedCenterY,
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

        validateFinite(
                "predictedCenterX",
                predictedCenterX
        );

        validateFinite(
                "predictedCenterY",
                predictedCenterY
        );

        if (!Double.isFinite(quality)
                || quality < 0.0
                || quality > 1.0) {

            throw new IllegalArgumentException(
                    "quality deve estar entre 0.0 e 1.0."
            );
        }

        this.target = target;
        this.candidate = candidate;

        this.predictedCenterX = predictedCenterX;
        this.predictedCenterY = predictedCenterY;

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

        this.quality = quality;
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " deve ser finito."
            );
        }
    }

    public ExpectedBubbleTarget getTarget() {
        return target;
    }

    public BubbleContourCandidate getCandidate() {
        return candidate;
    }

    public double getPredictedCenterX() {
        return predictedCenterX;
    }

    public double getPredictedCenterY() {
        return predictedCenterY;
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

    public double getQuality() {
        return quality;
    }

    public double getObservedOffsetX() {
        return candidate.getCenterX()
                - target.getExpectedCenterX();
    }

    public double getObservedOffsetY() {
        return candidate.getCenterY()
                - target.getExpectedCenterY();
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s -> candidate-%d residual=%.2f quality=%.3f",
                target.getOptionId(),
                candidate.getCandidateId(),
                residualDistance,
                quality
        );
    }
}
