package com.example.leitorgabaritoomr.vision.registration;

import java.util.Locale;

/**
 * Correspondência proposta entre uma posição esperada pelo
 * layout e um contorno observado na imagem.
 *
 * Todos os valores aqui armazenados serão os mesmos usados
 * posteriormente pelo cálculo e pelo Laboratório OMR.
 */
public final class BubbleCandidateMatch {

    private final ExpectedBubbleTarget target;
    private final BubbleContourCandidate candidate;

    private final double offsetX;
    private final double offsetY;
    private final double centerDistance;

    private final double positionScore;
    private final double sizeScore;
    private final double shapeScore;
    private final double totalScore;

    public BubbleCandidateMatch(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate,
            double positionScore,
            double sizeScore,
            double shapeScore,
            double totalScore
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "O alvo esperado é obrigatório."
            );
        }

        if (candidate == null) {
            throw new IllegalArgumentException(
                    "O candidato observado é obrigatório."
            );
        }

        validateScore(
                "positionScore",
                positionScore
        );

        validateScore(
                "sizeScore",
                sizeScore
        );

        validateScore(
                "shapeScore",
                shapeScore
        );

        validateScore(
                "totalScore",
                totalScore
        );

        this.target = target;
        this.candidate = candidate;

        this.offsetX =
                candidate.getCenterX()
                        - target.getExpectedCenterX();

        this.offsetY =
                candidate.getCenterY()
                        - target.getExpectedCenterY();

        this.centerDistance =
                Math.hypot(
                        offsetX,
                        offsetY
                );

        this.positionScore = positionScore;
        this.sizeScore = sizeScore;
        this.shapeScore = shapeScore;
        this.totalScore = totalScore;
    }

    private void validateScore(
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

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getCenterDistance() {
        return centerDistance;
    }

    public double getPositionScore() {
        return positionScore;
    }

    public double getSizeScore() {
        return sizeScore;
    }

    public double getShapeScore() {
        return shapeScore;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public boolean belongsToBlock(
            String blockId
    ) {
        return blockId != null
                && target
                .getBlockId()
                .equals(blockId);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s -> candidate-%d score=%.3f"
                        + " offset=(%.2f, %.2f)",
                target.getOptionId(),
                candidate.getCandidateId(),
                totalScore,
                offsetX,
                offsetY
        );
    }
}
