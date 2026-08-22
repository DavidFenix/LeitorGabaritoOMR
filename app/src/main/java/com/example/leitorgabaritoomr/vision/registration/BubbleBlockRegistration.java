package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resultado imutavel do registro geometrico de um bloco.
 *
 * Reune a transformacao estimada e exatamente os apoios que
 * permaneceram como evidencias validas depois do refinamento.
 * As estatisticas sao calculadas internamente a partir desses
 * apoios, evitando divergencia entre dados armazenados, calculos
 * e desenho do Laboratorio OMR.
 */
public final class BubbleBlockRegistration {

    private final BubbleBlockTransform transform;

    private final int targetCount;
    private final int sourceSupportCount;

    private final List<BubbleGridSupport> supports;

    private final Map<String, BubbleGridSupport>
            supportByOptionId;

    private final Map<Integer, BubbleGridSupport>
            supportByCandidateId;

    private final double supportRatio;
    private final double medianResidual;
    private final double medianNormalizedResidual;
    private final double rootMeanSquareResidual;
    private final double maximumResidual;
    private final double meanQuality;

    private final double confidence;
    private final boolean accepted;
    private final String message;

    public BubbleBlockRegistration(
            BubbleBlockTransform transform,
            int targetCount,
            int sourceSupportCount,
            List<BubbleGridSupport> supports,
            double confidence,
            boolean accepted,
            String message
    ) {
        if (transform == null) {
            throw new IllegalArgumentException(
                    "A transformacao do bloco e obrigatoria."
            );
        }

        if (targetCount <= 0) {
            throw new IllegalArgumentException(
                    "targetCount deve ser positivo."
            );
        }

        if (sourceSupportCount < 0
                || sourceSupportCount > targetCount) {

            throw new IllegalArgumentException(
                    "sourceSupportCount deve estar entre zero"
                            + " e targetCount."
            );
        }

        if (supports == null) {
            throw new IllegalArgumentException(
                    "A lista de apoios e obrigatoria."
            );
        }

        if (supports.size() > sourceSupportCount) {
            throw new IllegalArgumentException(
                    "Ha mais apoios finais que apoios de origem."
            );
        }

        validateRatio("confidence", confidence);

        if (accepted && supports.isEmpty()) {
            throw new IllegalArgumentException(
                    "Um bloco aceito deve possuir apoios."
            );
        }

        this.transform = transform;
        this.targetCount = targetCount;
        this.sourceSupportCount = sourceSupportCount;

        ValidatedSupports validated =
                validateAndCopySupports(
                        supports,
                        transform
                );

        this.supports = validated.supports;
        this.supportByOptionId =
                validated.supportByOptionId;
        this.supportByCandidateId =
                validated.supportByCandidateId;

        this.supportRatio =
                this.supports.size()
                        / (double) targetCount;

        Statistics statistics =
                calculateStatistics(this.supports);

        this.medianResidual =
                statistics.medianResidual;

        this.medianNormalizedResidual =
                statistics.medianNormalizedResidual;

        this.rootMeanSquareResidual =
                statistics.rootMeanSquareResidual;

        this.maximumResidual =
                statistics.maximumResidual;

        this.meanQuality =
                statistics.meanQuality;

        this.confidence = confidence;
        this.accepted = accepted;

        this.message =
                message == null
                        ? ""
                        : message.trim();
    }

    private ValidatedSupports validateAndCopySupports(
            List<BubbleGridSupport> source,
            BubbleBlockTransform expectedTransform
    ) {
        List<BubbleGridSupport> copy =
                new ArrayList<>();

        Map<String, BubbleGridSupport> byOptionId =
                new HashMap<>();

        Map<Integer, BubbleGridSupport> byCandidateId =
                new HashMap<>();

        Set<String> optionIds = new HashSet<>();
        Set<Integer> candidateIds = new HashSet<>();

        for (BubbleGridSupport support : source) {
            if (support == null) {
                throw new IllegalArgumentException(
                        "A lista possui apoio nulo."
                );
            }

            if (support.getBlockTransform()
                    != expectedTransform) {

                throw new IllegalArgumentException(
                        "Todos os apoios devem referenciar"
                                + " exatamente a transformacao"
                                + " armazenada pelo bloco."
                );
            }

            ExpectedBubbleTarget target =
                    support.getTarget();

            if (target.getBlockIndex()
                    != expectedTransform.getBlockIndex()
                    || !target.getBlockId().equals(
                    expectedTransform.getBlockId()
            )) {

                throw new IllegalArgumentException(
                        "O apoio pertence a outro bloco: "
                                + target.getOptionId()
                );
            }

            String optionId = target.getOptionId();

            int candidateId =
                    support.getCandidate()
                            .getCandidateId();

            if (!optionIds.add(optionId)) {
                throw new IllegalArgumentException(
                        "Alvo repetido nos apoios: "
                                + optionId
                );
            }

            if (!candidateIds.add(candidateId)) {
                throw new IllegalArgumentException(
                        "Candidato repetido nos apoios: "
                                + candidateId
                );
            }

            copy.add(support);
            byOptionId.put(optionId, support);
            byCandidateId.put(candidateId, support);
        }

        return new ValidatedSupports(
                Collections.unmodifiableList(copy),
                Collections.unmodifiableMap(byOptionId),
                Collections.unmodifiableMap(byCandidateId)
        );
    }

    private Statistics calculateStatistics(
            List<BubbleGridSupport> source
    ) {
        if (source.isEmpty()) {
            return Statistics.empty();
        }

        List<Double> residuals =
                new ArrayList<>();

        List<Double> normalizedResiduals =
                new ArrayList<>();

        double squaredResidualSum = 0.0;
        double maximum = 0.0;
        double qualitySum = 0.0;

        for (BubbleGridSupport support : source) {
            double residual =
                    support.getResidualDistance();

            residuals.add(residual);

            normalizedResiduals.add(
                    support.getNormalizedResidual()
            );

            squaredResidualSum +=
                    residual * residual;

            maximum = Math.max(maximum, residual);
            qualitySum += support.getQuality();
        }

        return new Statistics(
                median(residuals),
                median(normalizedResiduals),
                Math.sqrt(
                        squaredResidualSum
                                / source.size()
                ),
                maximum,
                qualitySum / source.size()
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
        return transform.getBlockIndex();
    }

    public String getBlockId() {
        return transform.getBlockId();
    }

    public BubbleBlockTransform getTransform() {
        return transform;
    }

    public int getTargetCount() {
        return targetCount;
    }

    /**
     * Quantidade de pares recebidos da etapa de translacao antes
     * da remocao robusta de residuos discrepantes.
     */
    public int getSourceSupportCount() {
        return sourceSupportCount;
    }

    public List<BubbleGridSupport> getSupports() {
        return supports;
    }

    public int getSupportCount() {
        return supports.size();
    }

    public int getDiscardedSupportCount() {
        return sourceSupportCount
                - supports.size();
    }

    public double getSupportRatio() {
        return supportRatio;
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

    public double getMedianResidual() {
        return medianResidual;
    }

    public double getMedianNormalizedResidual() {
        return medianNormalizedResidual;
    }

    public double getRootMeanSquareResidual() {
        return rootMeanSquareResidual;
    }

    public double getMaximumResidual() {
        return maximumResidual;
    }

    public double getMeanQuality() {
        return meanQuality;
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
        return transform.predictCenterX(target);
    }

    public double predictCenterY(
            ExpectedBubbleTarget target
    ) {
        return transform.predictCenterY(target);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s scale=(%.5f, %.5f)"
                        + " supports=%d/%d"
                        + " discarded=%d"
                        + " medianResidual=%.3f"
                        + " normalized=%.4f"
                        + " rms=%.3f"
                        + " confidence=%.3f"
                        + " accepted=%s",
                getBlockId(),
                transform.getScaleX(),
                transform.getScaleY(),
                getSupportCount(),
                targetCount,
                getDiscardedSupportCount(),
                medianResidual,
                medianNormalizedResidual,
                rootMeanSquareResidual,
                confidence,
                accepted
        );
    }

    private static final class ValidatedSupports {

        private final List<BubbleGridSupport> supports;

        private final Map<String, BubbleGridSupport>
                supportByOptionId;

        private final Map<Integer, BubbleGridSupport>
                supportByCandidateId;

        private ValidatedSupports(
                List<BubbleGridSupport> supports,
                Map<String, BubbleGridSupport>
                        supportByOptionId,
                Map<Integer, BubbleGridSupport>
                        supportByCandidateId
        ) {
            this.supports = supports;
            this.supportByOptionId =
                    supportByOptionId;
            this.supportByCandidateId =
                    supportByCandidateId;
        }
    }

    private static final class Statistics {

        private final double medianResidual;
        private final double medianNormalizedResidual;
        private final double rootMeanSquareResidual;
        private final double maximumResidual;
        private final double meanQuality;

        private Statistics(
                double medianResidual,
                double medianNormalizedResidual,
                double rootMeanSquareResidual,
                double maximumResidual,
                double meanQuality
        ) {
            this.medianResidual = medianResidual;
            this.medianNormalizedResidual =
                    medianNormalizedResidual;
            this.rootMeanSquareResidual =
                    rootMeanSquareResidual;
            this.maximumResidual = maximumResidual;
            this.meanQuality = meanQuality;
        }

        private static Statistics empty() {
            return new Statistics(
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }
}
