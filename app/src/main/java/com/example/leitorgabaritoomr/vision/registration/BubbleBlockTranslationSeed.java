package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Estimativa robusta de translacao de um bloco de respostas.
 *
 * Esta translacao e apenas a semente do registro completo. Ela
 * corrige o grande deslocamento inicial antes que escala, inclinacao
 * e pequenos residuos sejam refinados pelo registrador da grade.
 */
public final class BubbleBlockTranslationSeed {

    private final int blockIndex;
    private final String blockId;

    private final double offsetX;
    private final double offsetY;

    private final int targetCount;

    private final List<BubbleTranslationSupport> supports;

    private final double supportRatio;
    private final double medianResidual;
    private final double confidence;

    private final boolean accepted;
    private final String message;

    public BubbleBlockTranslationSeed(
            int blockIndex,
            String blockId,
            double offsetX,
            double offsetY,
            int targetCount,
            List<BubbleTranslationSupport> supports,
            double medianResidual,
            double confidence,
            boolean accepted,
            String message
    ) {
        if (blockIndex < 0) {
            throw new IllegalArgumentException(
                    "blockIndex nao pode ser negativo."
            );
        }

        if (blockId == null
                || blockId.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "blockId nao pode ser vazio."
            );
        }

        validateFinite("offsetX", offsetX);
        validateFinite("offsetY", offsetY);

        if (targetCount <= 0) {
            throw new IllegalArgumentException(
                    "targetCount deve ser positivo."
            );
        }

        if (supports == null) {
            throw new IllegalArgumentException(
                    "A lista de apoios e obrigatoria."
            );
        }

        if (!Double.isFinite(medianResidual)
                || medianResidual < 0.0) {

            throw new IllegalArgumentException(
                    "medianResidual deve ser finito e nao negativo."
            );
        }

        validateRatio("confidence", confidence);

        this.blockIndex = blockIndex;
        this.blockId = blockId.trim();

        this.offsetX = offsetX;
        this.offsetY = offsetY;

        this.targetCount = targetCount;

        this.supports = validateAndCopySupports(
                supports,
                this.blockIndex,
                this.blockId,
                targetCount
        );

        this.supportRatio =
                this.supports.size()
                        / (double) targetCount;

        this.medianResidual = medianResidual;
        this.confidence = confidence;

        this.accepted = accepted;
        this.message =
                message == null
                        ? ""
                        : message.trim();
    }

    private List<BubbleTranslationSupport>
    validateAndCopySupports(
            List<BubbleTranslationSupport> source,
            int expectedBlockIndex,
            String expectedBlockId,
            int expectedTargetCount
    ) {
        if (source.size() > expectedTargetCount) {
            throw new IllegalArgumentException(
                    "Ha mais apoios que alvos no bloco."
            );
        }

        List<BubbleTranslationSupport> copy =
                new ArrayList<>();

        Set<String> optionIds = new HashSet<>();
        Set<Integer> candidateIds = new HashSet<>();

        for (BubbleTranslationSupport support : source) {
            if (support == null) {
                throw new IllegalArgumentException(
                        "A lista possui apoio nulo."
                );
            }

            ExpectedBubbleTarget target =
                    support.getTarget();

            if (target.getBlockIndex()
                    != expectedBlockIndex
                    || !target.getBlockId().equals(
                    expectedBlockId
            )) {
                throw new IllegalArgumentException(
                        "O apoio pertence a outro bloco: "
                                + target.getOptionId()
                );
            }

            if (!optionIds.add(target.getOptionId())) {
                throw new IllegalArgumentException(
                        "Alvo repetido nos apoios: "
                                + target.getOptionId()
                );
            }

            int candidateId =
                    support.getCandidate()
                            .getCandidateId();

            if (!candidateIds.add(candidateId)) {
                throw new IllegalArgumentException(
                        "Candidato repetido nos apoios: "
                                + candidateId
                );
            }

            copy.add(support);
        }

        return Collections.unmodifiableList(copy);
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

    public int getBlockIndex() {
        return blockIndex;
    }

    public String getBlockId() {
        return blockId;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public List<BubbleTranslationSupport> getSupports() {
        return supports;
    }

    public int getSupportCount() {
        return supports.size();
    }

    public double getSupportRatio() {
        return supportRatio;
    }

    public double getMedianResidual() {
        return medianResidual;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    public double predictCenterX(
            ExpectedBubbleTarget target
    ) {
        validateTargetBelongsToBlock(target);

        return target.getExpectedCenterX()
                + offsetX;
    }

    public double predictCenterY(
            ExpectedBubbleTarget target
    ) {
        validateTargetBelongsToBlock(target);

        return target.getExpectedCenterY()
                + offsetY;
    }

    private void validateTargetBelongsToBlock(
            ExpectedBubbleTarget target
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "O alvo e obrigatorio."
            );
        }

        if (target.getBlockIndex() != blockIndex
                || !target.getBlockId().equals(blockId)) {

            throw new IllegalArgumentException(
                    "O alvo nao pertence a " + blockId + "."
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s translation=(%.2f, %.2f) supports=%d/%d"
                        + " residual=%.2f confidence=%.3f accepted=%s",
                blockId,
                offsetX,
                offsetY,
                getSupportCount(),
                targetCount,
                medianResidual,
                confidence,
                accepted
        );
    }
}
