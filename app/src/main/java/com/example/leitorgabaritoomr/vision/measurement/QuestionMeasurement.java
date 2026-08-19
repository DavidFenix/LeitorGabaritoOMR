package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reúne e compara as alternativas de uma única questão.
 *
 * Ainda não classifica a questão como marcada, vazia ou múltipla.
 */
public final class QuestionMeasurement {

    private final OmrQuestionDefinition question;

    private final List<BubbleMeasurement> measurements;

    private final Map<String, BubbleMeasurement>
            measurementsByOptionId;

    private final Map<String, Double>
            evidenceByOptionId;

    private final BubbleMeasurement bestMeasurement;
    private final BubbleMeasurement secondBestMeasurement;

    private final double bestEvidence;
    private final double secondBestEvidence;
    private final double evidenceGap;

    private final double minimumEvidence;
    private final double averageEvidence;

    public QuestionMeasurement(
            OmrQuestionDefinition question,
            List<BubbleMeasurement> measurements,
            BubbleEvidenceScorer evidenceScorer
    ) {
        if (question == null) {
            throw new IllegalArgumentException(
                    "A questão é obrigatória."
            );
        }

        if (measurements == null
                || evidenceScorer == null) {

            throw new IllegalArgumentException(
                    "As medições e o scorer são obrigatórios."
            );
        }

        if (measurements.size()
                != question.getOptionCount()) {

            throw new IllegalArgumentException(
                    "A quantidade de medições não corresponde"
                            + " às alternativas da questão "
                            + question.getId()
                            + "."
            );
        }

        this.question = question;

        List<BubbleMeasurement> measurementCopy =
                new ArrayList<>(measurements);

        Map<String, BubbleMeasurement>
                measurementIndex = new HashMap<>();

        Map<String, Double>
                evidenceIndex = new HashMap<>();

        BubbleMeasurement currentBest = null;
        BubbleMeasurement currentSecond = null;

        double currentBestEvidence =
                Double.NEGATIVE_INFINITY;

        double currentSecondEvidence =
                Double.NEGATIVE_INFINITY;

        double currentMinimumEvidence =
                Double.POSITIVE_INFINITY;

        double evidenceSum = 0.0;

        for (BubbleMeasurement measurement
                : measurementCopy) {

            if (measurement == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter medições nulas."
                );
            }

            String optionId =
                    measurement
                            .getOption()
                            .getId();

            if (question.findOptionById(optionId)
                    == null) {

                throw new IllegalArgumentException(
                        "A alternativa "
                                + optionId
                                + " não pertence à questão "
                                + question.getId()
                                + "."
                );
            }

            if (measurementIndex.put(
                    optionId,
                    measurement
            ) != null) {

                throw new IllegalArgumentException(
                        "Medição repetida para "
                                + optionId
                                + "."
                );
            }

            double evidence =
                    evidenceScorer.score(
                            measurement
                    );

            evidenceIndex.put(
                    optionId,
                    evidence
            );

            evidenceSum += evidence;

            currentMinimumEvidence =
                    Math.min(
                            currentMinimumEvidence,
                            evidence
                    );

            if (evidence
                    > currentBestEvidence) {

                currentSecond =
                        currentBest;

                currentSecondEvidence =
                        currentBestEvidence;

                currentBest =
                        measurement;

                currentBestEvidence =
                        evidence;

            } else if (evidence
                    > currentSecondEvidence) {

                currentSecond =
                        measurement;

                currentSecondEvidence =
                        evidence;
            }
        }

        if (currentBest == null
                || currentSecond == null) {

            throw new IllegalArgumentException(
                    "Não foi possível determinar as duas"
                            + " melhores alternativas."
            );
        }

        this.measurements =
                Collections.unmodifiableList(
                        measurementCopy
                );

        this.measurementsByOptionId =
                Collections.unmodifiableMap(
                        measurementIndex
                );

        this.evidenceByOptionId =
                Collections.unmodifiableMap(
                        evidenceIndex
                );

        this.bestMeasurement =
                currentBest;

        this.secondBestMeasurement =
                currentSecond;

        this.bestEvidence =
                currentBestEvidence;

        this.secondBestEvidence =
                currentSecondEvidence;

        this.evidenceGap =
                Math.max(
                        0.0,
                        currentBestEvidence
                                - currentSecondEvidence
                );

        this.minimumEvidence =
                currentMinimumEvidence;

        this.averageEvidence =
                evidenceSum
                        / measurementCopy.size();
    }

    public OmrQuestionDefinition getQuestion() {
        return question;
    }

    public List<BubbleMeasurement>
    getMeasurements() {

        return measurements;
    }

    public BubbleMeasurement getBestMeasurement() {
        return bestMeasurement;
    }

    public BubbleMeasurement
    getSecondBestMeasurement() {

        return secondBestMeasurement;
    }

    public OmrOptionDefinition getBestOption() {
        return bestMeasurement.getOption();
    }

    public OmrOptionDefinition getSecondBestOption() {
        return secondBestMeasurement.getOption();
    }

    public double getBestEvidence() {
        return bestEvidence;
    }

    public double getSecondBestEvidence() {
        return secondBestEvidence;
    }

    public double getEvidenceGap() {
        return evidenceGap;
    }

    public double getMinimumEvidence() {
        return minimumEvidence;
    }

    public double getAverageEvidence() {
        return averageEvidence;
    }

    public double getEvidence(
            String optionId
    ) {
        Double value =
                evidenceByOptionId.get(optionId);

        return value == null
                ? 0.0
                : value;
    }

    /**
     * Retorna a posição relativa da alternativa dentro
     * da própria questão.
     *
     * 0.0 = menor evidência da questão
     * 1.0 = maior evidência da questão
     */
    public double getRelativeEvidence(
            String optionId
    ) {
        double evidence =
                getEvidence(optionId);

        double range =
                bestEvidence
                        - minimumEvidence;

        if (range <= 0.000001) {
            return 0.0;
        }

        return clamp01(
                (evidence - minimumEvidence)
                        / range
        );
    }

    public boolean isBestOption(
            String optionId
    ) {
        return optionId != null
                && bestMeasurement
                .getOption()
                .getId()
                .equals(optionId);
    }

    public boolean isSecondBestOption(
            String optionId
    ) {
        return optionId != null
                && secondBestMeasurement
                .getOption()
                .getId()
                .equals(optionId);
    }

    public BubbleMeasurement findMeasurement(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return measurementsByOptionId.get(
                optionId
        );
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}