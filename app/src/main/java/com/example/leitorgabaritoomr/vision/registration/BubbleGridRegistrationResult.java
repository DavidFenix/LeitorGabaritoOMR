package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resultado imutavel do registro geometrico da folha inteira.
 *
 * Agrega os registros dos blocos e calcula os indicadores globais
 * exclusivamente a partir das evidencias armazenadas neles.
 * Tambem garante que nenhum alvo ou candidato seja reutilizado
 * entre blocos diferentes.
 */
public final class BubbleGridRegistrationResult {

    private final boolean success;

    private final int targetCount;
    private final int candidateCount;

    private final List<BubbleBlockRegistration>
            blockRegistrations;

    private final Map<Integer, BubbleBlockRegistration>
            registrationByBlockIndex;

    private final Map<String, BubbleBlockRegistration>
            registrationByBlockId;

    private final Map<String, BubbleGridSupport>
            supportByOptionId;

    private final Map<Integer, BubbleGridSupport>
            supportByCandidateId;

    private final int acceptedBlockCount;
    private final int sourceSupportCount;
    private final int registeredSupportCount;
    private final int discardedSupportCount;

    private final double registeredTargetRatio;
    private final double sheetConfidence;
    private final double medianResidual;
    private final double medianNormalizedResidual;
    private final double maximumResidual;

    private final String message;

    private BubbleGridRegistrationResult(
            boolean success,
            int targetCount,
            int candidateCount,
            List<BubbleBlockRegistration>
                    blockRegistrations,
            String message
    ) {
        if (targetCount < 0
                || candidateCount < 0) {

            throw new IllegalArgumentException(
                    "As quantidades nao podem ser negativas."
            );
        }

        if (blockRegistrations == null) {
            throw new IllegalArgumentException(
                    "A lista de registros e obrigatoria."
            );
        }

        this.success = success;
        this.targetCount = targetCount;
        this.candidateCount = candidateCount;

        this.blockRegistrations =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                blockRegistrations
                        )
                );

        Aggregation aggregation =
                aggregate(this.blockRegistrations);

        if (success
                && aggregation.representedTargetCount
                != targetCount) {

            throw new IllegalArgumentException(
                    "A soma dos alvos dos blocos difere"
                            + " de targetCount."
            );
        }

        this.registrationByBlockIndex =
                aggregation.registrationByBlockIndex;

        this.registrationByBlockId =
                aggregation.registrationByBlockId;

        this.supportByOptionId =
                aggregation.supportByOptionId;

        this.supportByCandidateId =
                aggregation.supportByCandidateId;

        this.acceptedBlockCount =
                aggregation.acceptedBlockCount;

        this.sourceSupportCount =
                aggregation.sourceSupportCount;

        this.registeredSupportCount =
                aggregation.registeredSupportCount;

        this.discardedSupportCount =
                sourceSupportCount
                        - registeredSupportCount;

        this.registeredTargetRatio =
                targetCount == 0
                        ? 0.0
                        : registeredSupportCount
                        / (double) targetCount;

        this.sheetConfidence =
                aggregation.representedTargetCount == 0
                        ? 0.0
                        : aggregation
                        .weightedConfidenceSum
                        / aggregation
                        .representedTargetCount;

        this.medianResidual =
                median(
                        aggregation.residuals
                );

        this.medianNormalizedResidual =
                median(
                        aggregation.normalizedResiduals
                );

        this.maximumResidual =
                aggregation.maximumResidual;

        this.message =
                message == null
                        ? ""
                        : message.trim();
    }

    public static BubbleGridRegistrationResult success(
            int targetCount,
            int candidateCount,
            List<BubbleBlockRegistration>
                    blockRegistrations
    ) {
        return new BubbleGridRegistrationResult(
                true,
                targetCount,
                candidateCount,
                blockRegistrations,
                "Registro geometrico concluido."
        );
    }

    public static BubbleGridRegistrationResult failure(
            String message
    ) {
        return failure(0, 0, message);
    }

    public static BubbleGridRegistrationResult failure(
            int targetCount,
            int candidateCount,
            String message
    ) {
        return new BubbleGridRegistrationResult(
                false,
                targetCount,
                candidateCount,
                Collections
                        .<BubbleBlockRegistration>
                                emptyList(),
                message
        );
    }

    private Aggregation aggregate(
            List<BubbleBlockRegistration> source
    ) {
        Map<Integer, BubbleBlockRegistration>
                byBlockIndex = new HashMap<>();

        Map<String, BubbleBlockRegistration>
                byBlockId = new HashMap<>();

        Map<String, BubbleGridSupport>
                byOptionId = new HashMap<>();

        Map<Integer, BubbleGridSupport>
                byCandidateId = new HashMap<>();

        Set<String> optionIds = new HashSet<>();
        Set<Integer> candidateIds = new HashSet<>();

        List<Double> residuals =
                new ArrayList<>();

        List<Double> normalizedResiduals =
                new ArrayList<>();

        int acceptedBlocks = 0;
        int representedTargets = 0;
        int sourceSupports = 0;
        int registeredSupports = 0;

        double weightedConfidence = 0.0;
        double maximum = 0.0;

        for (BubbleBlockRegistration registration
                : source) {

            if (registration == null) {
                throw new IllegalArgumentException(
                        "A lista possui registro nulo."
                );
            }

            if (byBlockIndex.put(
                    registration.getBlockIndex(),
                    registration
            ) != null) {

                throw new IllegalArgumentException(
                        "blockIndex repetido: "
                                + registration
                                .getBlockIndex()
                );
            }

            if (byBlockId.put(
                    registration.getBlockId(),
                    registration
            ) != null) {

                throw new IllegalArgumentException(
                        "blockId repetido: "
                                + registration
                                .getBlockId()
                );
            }

            if (registration.isAccepted()) {
                acceptedBlocks++;
            }

            representedTargets +=
                    registration.getTargetCount();

            sourceSupports +=
                    registration.getSourceSupportCount();

            registeredSupports +=
                    registration.getSupportCount();

            weightedConfidence +=
                    registration.getConfidence()
                            * registration
                            .getTargetCount();

            for (BubbleGridSupport support
                    : registration.getSupports()) {

                String optionId =
                        support.getTarget()
                                .getOptionId();

                int candidateId =
                        support.getCandidate()
                                .getCandidateId();

                if (!optionIds.add(optionId)) {
                    throw new IllegalArgumentException(
                            "Alvo reutilizado entre blocos: "
                                    + optionId
                    );
                }

                if (!candidateIds.add(candidateId)) {
                    throw new IllegalArgumentException(
                            "Candidato reutilizado entre blocos: "
                                    + candidateId
                    );
                }

                byOptionId.put(optionId, support);
                byCandidateId.put(candidateId, support);

                residuals.add(
                        support.getResidualDistance()
                );

                normalizedResiduals.add(
                        support.getNormalizedResidual()
                );

                maximum = Math.max(
                        maximum,
                        support.getResidualDistance()
                );
            }
        }

        return new Aggregation(
                Collections.unmodifiableMap(
                        byBlockIndex
                ),
                Collections.unmodifiableMap(
                        byBlockId
                ),
                Collections.unmodifiableMap(
                        byOptionId
                ),
                Collections.unmodifiableMap(
                        byCandidateId
                ),
                residuals,
                normalizedResiduals,
                acceptedBlocks,
                representedTargets,
                sourceSupports,
                registeredSupports,
                weightedConfidence,
                maximum
        );
    }

    private double median(
            List<Double> source
    ) {
        if (source.isEmpty()) {
            return 0.0;
        }

        List<Double> sorted =
                new ArrayList<>(source);

        Collections.sort(sorted);

        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return (
                sorted.get(middle - 1)
                        + sorted.get(middle)
        ) / 2.0;
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

    public List<BubbleBlockRegistration>
    getBlockRegistrations() {
        return blockRegistrations;
    }

    public int getBlockCount() {
        return blockRegistrations.size();
    }

    public int getAcceptedBlockCount() {
        return acceptedBlockCount;
    }

    public int getSourceSupportCount() {
        return sourceSupportCount;
    }

    public int getRegisteredSupportCount() {
        return registeredSupportCount;
    }

    public int getDiscardedSupportCount() {
        return discardedSupportCount;
    }

    public double getRegisteredTargetRatio() {
        return registeredTargetRatio;
    }

    public double getSheetConfidence() {
        return sheetConfidence;
    }

    public double getMedianResidual() {
        return medianResidual;
    }

    public double getMedianNormalizedResidual() {
        return medianNormalizedResidual;
    }

    public double getMaximumResidual() {
        return maximumResidual;
    }

    public boolean areAllBlocksAccepted() {
        return success
                && !blockRegistrations.isEmpty()
                && acceptedBlockCount
                == blockRegistrations.size();
    }

    public BubbleBlockRegistration findByBlockIndex(
            int blockIndex
    ) {
        return registrationByBlockIndex.get(blockIndex);
    }

    public BubbleBlockRegistration findByBlockId(
            String blockId
    ) {
        if (blockId == null) {
            return null;
        }

        return registrationByBlockId.get(blockId);
    }

    public BubbleGridSupport findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return supportByOptionId.get(optionId);
    }

    public BubbleGridSupport findByCandidateId(
            int candidateId
    ) {
        return supportByCandidateId.get(candidateId);
    }

    public String getMessage() {
        return message;
    }

    private static final class Aggregation {

        private final Map<Integer, BubbleBlockRegistration>
                registrationByBlockIndex;

        private final Map<String, BubbleBlockRegistration>
                registrationByBlockId;

        private final Map<String, BubbleGridSupport>
                supportByOptionId;

        private final Map<Integer, BubbleGridSupport>
                supportByCandidateId;

        private final List<Double> residuals;
        private final List<Double> normalizedResiduals;

        private final int acceptedBlockCount;
        private final int representedTargetCount;
        private final int sourceSupportCount;
        private final int registeredSupportCount;

        private final double weightedConfidenceSum;
        private final double maximumResidual;

        private Aggregation(
                Map<Integer, BubbleBlockRegistration>
                        registrationByBlockIndex,
                Map<String, BubbleBlockRegistration>
                        registrationByBlockId,
                Map<String, BubbleGridSupport>
                        supportByOptionId,
                Map<Integer, BubbleGridSupport>
                        supportByCandidateId,
                List<Double> residuals,
                List<Double> normalizedResiduals,
                int acceptedBlockCount,
                int representedTargetCount,
                int sourceSupportCount,
                int registeredSupportCount,
                double weightedConfidenceSum,
                double maximumResidual
        ) {
            this.registrationByBlockIndex =
                    registrationByBlockIndex;
            this.registrationByBlockId =
                    registrationByBlockId;
            this.supportByOptionId =
                    supportByOptionId;
            this.supportByCandidateId =
                    supportByCandidateId;
            this.residuals = residuals;
            this.normalizedResiduals =
                    normalizedResiduals;
            this.acceptedBlockCount =
                    acceptedBlockCount;
            this.representedTargetCount =
                    representedTargetCount;
            this.sourceSupportCount =
                    sourceSupportCount;
            this.registeredSupportCount =
                    registeredSupportCount;
            this.weightedConfidenceSum =
                    weightedConfidenceSum;
            this.maximumResidual =
                    maximumResidual;
        }
    }
}
