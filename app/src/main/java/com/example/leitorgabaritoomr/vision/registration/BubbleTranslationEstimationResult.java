package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado da estimativa de translacao de todos os blocos.
 *
 * Nao possui objetos Mat e pode ser mantido pelo Laboratorio OMR,
 * pelos testes e pelas camadas posteriores sem exigir release().
 */
public final class BubbleTranslationEstimationResult {

    private final boolean success;

    private final int targetCount;
    private final int candidateCount;

    private final List<BubbleBlockTranslationSeed> blockSeeds;

    private final Map<Integer, BubbleBlockTranslationSeed>
            seedByBlockIndex;

    private final Map<String, BubbleBlockTranslationSeed>
            seedByBlockId;

    private final int acceptedBlockCount;
    private final int supportedTargetCount;

    private final double supportedTargetRatio;
    private final double sheetConfidence;

    private final String message;

    private BubbleTranslationEstimationResult(
            boolean success,
            int targetCount,
            int candidateCount,
            List<BubbleBlockTranslationSeed> blockSeeds,
            String message
    ) {
        if (targetCount < 0 || candidateCount < 0) {
            throw new IllegalArgumentException(
                    "As quantidades nao podem ser negativas."
            );
        }

        if (blockSeeds == null) {
            throw new IllegalArgumentException(
                    "A lista de sementes e obrigatoria."
            );
        }

        this.success = success;
        this.targetCount = targetCount;
        this.candidateCount = candidateCount;

        this.blockSeeds =
                Collections.unmodifiableList(
                        new ArrayList<>(blockSeeds)
                );

        Map<Integer, BubbleBlockTranslationSeed>
                mutableByIndex = new HashMap<>();

        Map<String, BubbleBlockTranslationSeed>
                mutableById = new HashMap<>();

        int mutableAcceptedBlockCount = 0;
        int mutableSupportedTargetCount = 0;
        int representedTargetCount = 0;

        double weightedConfidenceSum = 0.0;

        for (BubbleBlockTranslationSeed seed
                : this.blockSeeds) {

            if (seed == null) {
                throw new IllegalArgumentException(
                        "A lista possui semente nula."
                );
            }

            if (mutableByIndex.put(
                    seed.getBlockIndex(),
                    seed
            ) != null) {
                throw new IllegalArgumentException(
                        "blockIndex repetido: "
                                + seed.getBlockIndex()
                );
            }

            if (mutableById.put(
                    seed.getBlockId(),
                    seed
            ) != null) {
                throw new IllegalArgumentException(
                        "blockId repetido: "
                                + seed.getBlockId()
                );
            }

            if (seed.isAccepted()) {
                mutableAcceptedBlockCount++;
            }

            mutableSupportedTargetCount +=
                    seed.getSupportCount();

            representedTargetCount +=
                    seed.getTargetCount();

            weightedConfidenceSum +=
                    seed.getConfidence()
                            * seed.getTargetCount();
        }

        if (success
                && representedTargetCount != targetCount) {

            throw new IllegalArgumentException(
                    "A soma dos alvos dos blocos difere"
                            + " de targetCount."
            );
        }

        this.seedByBlockIndex =
                Collections.unmodifiableMap(
                        mutableByIndex
                );

        this.seedByBlockId =
                Collections.unmodifiableMap(
                        mutableById
                );

        this.acceptedBlockCount =
                mutableAcceptedBlockCount;

        this.supportedTargetCount =
                mutableSupportedTargetCount;

        this.supportedTargetRatio =
                targetCount == 0
                        ? 0.0
                        : mutableSupportedTargetCount
                        / (double) targetCount;

        this.sheetConfidence =
                representedTargetCount == 0
                        ? 0.0
                        : weightedConfidenceSum
                        / representedTargetCount;

        this.message =
                message == null
                        ? ""
                        : message.trim();
    }

    public static BubbleTranslationEstimationResult success(
            int targetCount,
            int candidateCount,
            List<BubbleBlockTranslationSeed> blockSeeds
    ) {
        return new BubbleTranslationEstimationResult(
                true,
                targetCount,
                candidateCount,
                blockSeeds,
                "Estimativa de translacao concluida."
        );
    }

    public static BubbleTranslationEstimationResult failure(
            String message
    ) {
        return new BubbleTranslationEstimationResult(
                false,
                0,
                0,
                Collections.<BubbleBlockTranslationSeed>
                        emptyList(),
                message
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public List<BubbleBlockTranslationSeed>
    getBlockSeeds() {

        return blockSeeds;
    }

    public int getBlockCount() {
        return blockSeeds.size();
    }

    public int getAcceptedBlockCount() {
        return acceptedBlockCount;
    }

    public int getSupportedTargetCount() {
        return supportedTargetCount;
    }

    public double getSupportedTargetRatio() {
        return supportedTargetRatio;
    }

    public double getSheetConfidence() {
        return sheetConfidence;
    }

    public boolean areAllBlocksAccepted() {
        return success
                && !blockSeeds.isEmpty()
                && acceptedBlockCount
                == blockSeeds.size();
    }

    public BubbleBlockTranslationSeed findByBlockIndex(
            int blockIndex
    ) {
        return seedByBlockIndex.get(blockIndex);
    }

    public BubbleBlockTranslationSeed findByBlockId(
            String blockId
    ) {
        if (blockId == null) {
            return null;
        }

        return seedByBlockId.get(blockId);
    }

    public String getMessage() {
        return message;
    }
}
