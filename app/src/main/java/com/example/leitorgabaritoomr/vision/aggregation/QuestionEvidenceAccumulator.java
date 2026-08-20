package com.example.leitorgabaritoomr.vision.aggregation;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
import com.example.leitorgabaritoomr.vision.measurement.QuestionMeasurement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acumula evidências de várias observações da mesma folha.
 *
 * Cada questão é avaliada separadamente. Uma vitória clara
 * recebe peso maior que uma vitória fraca ou ambígua.
 *
 * Depois de alcançar requiredFrames, novas atualizações são
 * ignoradas até que reset() seja chamado.
 */
public final class QuestionEvidenceAccumulator {

    private final QuestionEvidenceAccumulatorConfig config;
    private final OmrLayoutDefinition layout;

    private final Map<String, MutableQuestionAggregate>
            mutableQuestions;

    private int accumulatedFrames = 0;

    public QuestionEvidenceAccumulator(
            QuestionEvidenceAccumulatorConfig config,
            OmrLayoutDefinition layout
    ) {
        if (config == null || layout == null) {
            throw new IllegalArgumentException(
                    "Configuração e layout são obrigatórios."
            );
        }

        this.config = config;
        this.layout = layout;

        this.mutableQuestions =
                createMutableQuestions(layout);
    }

    /**
     * Adiciona uma observação completa da folha.
     */
    public synchronized SheetEvidenceAggregate update(
            List<QuestionMeasurement> frameQuestions
    ) {
        if (isReady()) {
            return createSnapshot();
        }

        Map<String, QuestionMeasurement> frameIndex =
                validateAndIndexFrame(frameQuestions);

        /*
         * Toda a validação ocorre antes de alterar o estado.
         * Um frame inválido nunca fica parcialmente acumulado.
         */
        for (OmrBlockDefinition block : layout.getBlocks()) {
            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                QuestionMeasurement frameQuestion =
                        frameIndex.get(question.getId());

                MutableQuestionAggregate mutable =
                        mutableQuestions.get(question.getId());

                mutable.addFrame(
                        frameQuestion,
                        config
                );
            }
        }

        accumulatedFrames++;

        return createSnapshot();
    }

    public synchronized SheetEvidenceAggregate
    getCurrentSnapshot() {

        return createSnapshot();
    }

    public synchronized int getAccumulatedFrames() {
        return accumulatedFrames;
    }

    public synchronized boolean isReady() {
        return accumulatedFrames
                >= config.getRequiredFrames();
    }

    public synchronized void reset() {
        accumulatedFrames = 0;

        for (MutableQuestionAggregate question
                : mutableQuestions.values()) {

            question.reset();
        }
    }

    private Map<String, MutableQuestionAggregate>
    createMutableQuestions(
            OmrLayoutDefinition layout
    ) {
        Map<String, MutableQuestionAggregate> result =
                new LinkedHashMap<>();

        for (OmrBlockDefinition block : layout.getBlocks()) {
            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                result.put(
                        question.getId(),
                        new MutableQuestionAggregate(question)
                );
            }
        }

        return result;
    }

    private Map<String, QuestionMeasurement>
    validateAndIndexFrame(
            List<QuestionMeasurement> frameQuestions
    ) {
        if (frameQuestions == null
                || frameQuestions.size()
                != layout.getQuestionCount()) {

            throw new IllegalArgumentException(
                    "O frame deve conter exatamente "
                            + layout.getQuestionCount()
                            + " questões."
            );
        }

        Map<String, QuestionMeasurement> index =
                new HashMap<>();

        for (QuestionMeasurement questionMeasurement
                : frameQuestions) {

            if (questionMeasurement == null) {
                throw new IllegalArgumentException(
                        "O frame não pode conter questões nulas."
                );
            }

            String questionId =
                    questionMeasurement
                            .getQuestion()
                            .getId();

            OmrQuestionDefinition expectedQuestion =
                    layout.findQuestionById(questionId);

            if (expectedQuestion == null) {
                throw new IllegalArgumentException(
                        "Questão desconhecida: "
                                + questionId
                );
            }

            if (questionMeasurement
                    .getMeasurements()
                    .size()
                    != expectedQuestion
                    .getOptionCount()) {

                throw new IllegalArgumentException(
                        "Quantidade de alternativas inválida em "
                                + questionId
                );
            }

            if (index.put(
                    questionId,
                    questionMeasurement
            ) != null) {

                throw new IllegalArgumentException(
                        "Questão repetida no frame: "
                                + questionId
                );
            }
        }

        for (OmrBlockDefinition block : layout.getBlocks()) {
            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                if (!index.containsKey(question.getId())) {
                    throw new IllegalArgumentException(
                            "Questão ausente no frame: "
                                    + question.getId()
                    );
                }
            }
        }

        return index;
    }

    private SheetEvidenceAggregate createSnapshot() {
        List<QuestionEvidenceAggregate> questionSnapshots =
                new ArrayList<>();

        for (OmrBlockDefinition block : layout.getBlocks()) {
            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                MutableQuestionAggregate mutable =
                        mutableQuestions.get(question.getId());

                questionSnapshots.add(
                        mutable.createSnapshot(
                                accumulatedFrames,
                                config
                        )
                );
            }
        }

        return new SheetEvidenceAggregate(
                layout,
                questionSnapshots,
                accumulatedFrames,
                config.getRequiredFrames()
        );
    }

    /**
     * Estado temporal de uma questão.
     */
    private static final class MutableQuestionAggregate {

        private final OmrQuestionDefinition question;

        private final Map<String, MutableOptionAggregate>
                options;

        /*
         * Soma do peso de todos os votos considerados
         * suficientemente confiáveis nesta questão.
         */
        private double totalVoteWeight = 0.0;

        private MutableQuestionAggregate(
                OmrQuestionDefinition question
        ) {
            this.question = question;
            this.options = new LinkedHashMap<>();

            for (OmrOptionDefinition option
                    : question.getOptions()) {

                options.put(
                        option.getId(),
                        new MutableOptionAggregate(option)
                );
            }
        }

        private void addFrame(
                QuestionMeasurement frameQuestion,
                QuestionEvidenceAccumulatorConfig config
        ) {
            double voteWeight =
                    calculateVoteWeight(
                            frameQuestion,
                            config
                    );

            totalVoteWeight += voteWeight;

            for (BubbleMeasurement measurement
                    : frameQuestion.getMeasurements()) {

                String optionId =
                        measurement
                                .getOption()
                                .getId();

                MutableOptionAggregate mutableOption =
                        options.get(optionId);

                if (mutableOption == null) {
                    throw new IllegalArgumentException(
                            "Alternativa desconhecida: "
                                    + optionId
                    );
                }

                double evidence =
                        frameQuestion.getEvidence(optionId);

                double relativeEvidence =
                        frameQuestion.getRelativeEvidence(
                                optionId
                        );

                boolean winner =
                        frameQuestion.isBestOption(optionId);

                double winningGap =
                        winner
                                ? frameQuestion.getEvidenceGap()
                                : 0.0;

                double optionVoteWeight =
                        winner
                                ? voteWeight
                                : 0.0;

                mutableOption.add(
                        evidence,
                        relativeEvidence,
                        winner,
                        winningGap,
                        optionVoteWeight
                );
            }
        }

        /**
         * Calcula quanto a vitória desta questão vale no frame.
         *
         * O peso depende de:
         *
         * 1. evidência absoluta da vencedora;
         * 2. separação entre primeira e segunda colocadas.
         *
         * Uma vitória sem separação recebe peso próximo de zero.
         */
        private double calculateVoteWeight(
                QuestionMeasurement frameQuestion,
                QuestionEvidenceAccumulatorConfig config
        ) {
            double bestEvidence =
                    clamp01(
                            frameQuestion.getBestEvidence()
                    );

            if (bestEvidence
                    < config.getMinimumBestEvidenceForVote()) {

                return 0.0;
            }

            double minimumEvidence =
                    config.getMinimumBestEvidenceForVote();

            double evidenceRange =
                    1.0 - minimumEvidence;

            double evidenceQuality;

            if (evidenceRange <= 0.000001) {
                evidenceQuality = 1.0;

            } else {
                evidenceQuality =
                        clamp01(
                                (bestEvidence - minimumEvidence)
                                        / evidenceRange
                        );
            }

            double gapQuality =
                    clamp01(
                            frameQuestion.getEvidenceGap()
                                    / config
                                    .getWinningGapSaturation()
                    );

            /*
             * A raiz quadrada evita que um valor apenas
             * moderado seja reduzido excessivamente pelo
             * produto das duas qualidades.
             */
            double voteWeight =
                    Math.sqrt(
                            evidenceQuality
                                    * gapQuality
                    );

            if (voteWeight
                    < config.getMinimumVoteWeight()) {

                return 0.0;
            }

            return clamp01(voteWeight);
        }

        private QuestionEvidenceAggregate
        createSnapshot(
                int sampleCount,
                QuestionEvidenceAccumulatorConfig config
        ) {
            List<OptionEvidenceAggregate> optionSnapshots =
                    new ArrayList<>();

            for (OmrOptionDefinition option
                    : question.getOptions()) {

                MutableOptionAggregate mutable =
                        options.get(option.getId());

                optionSnapshots.add(
                        mutable.createSnapshot(
                                sampleCount,
                                totalVoteWeight,
                                config
                        )
                );
            }

            return new QuestionEvidenceAggregate(
                    question,
                    optionSnapshots
            );
        }

        private void reset() {
            totalVoteWeight = 0.0;

            for (MutableOptionAggregate option
                    : options.values()) {

                option.reset();
            }
        }
    }

    /**
     * Estado temporal de uma alternativa.
     */
    private static final class MutableOptionAggregate {

        private final OmrOptionDefinition option;

        /*
         * Quantidade simples de vezes que a opção venceu.
         * Continua disponível para diagnóstico.
         */
        private int winCount = 0;

        /*
         * Soma ponderada apenas das vitórias confiáveis.
         */
        private double weightedWinScore = 0.0;

        private double evidenceSum = 0.0;

        /*
         * Duas melhores evidências observadas.
         */
        private double highestEvidence = 0.0;
        private double secondHighestEvidence = 0.0;

        private double relativeEvidenceSum = 0.0;
        private double winningGapSum = 0.0;

        private MutableOptionAggregate(
                OmrOptionDefinition option
        ) {
            this.option = option;
        }

        private void add(
                double evidence,
                double relativeEvidence,
                boolean winner,
                double winningGap,
                double optionVoteWeight
        ) {
            double safeEvidence =
                    clamp01(evidence);

            evidenceSum += safeEvidence;

            updateHighestEvidence(
                    safeEvidence
            );

            relativeEvidenceSum +=
                    clamp01(relativeEvidence);

            if (winner) {
                winCount++;

                winningGapSum +=
                        clamp01(winningGap);

                weightedWinScore +=
                        clamp01(optionVoteWeight);
            }
        }

        private void updateHighestEvidence(
                double evidence
        ) {
            if (evidence >= highestEvidence) {
                secondHighestEvidence =
                        highestEvidence;

                highestEvidence =
                        evidence;

            } else if (evidence
                    > secondHighestEvidence) {

                secondHighestEvidence =
                        evidence;
            }
        }

        private OptionEvidenceAggregate
        createSnapshot(
                int sampleCount,
                double totalVoteWeight,
                QuestionEvidenceAccumulatorConfig config
        ) {
            double averageEvidence =
                    sampleCount <= 0
                            ? 0.0
                            : evidenceSum / sampleCount;

            double robustEvidence;

            if (sampleCount <= 0) {
                robustEvidence = 0.0;

            } else if (sampleCount == 1) {
                robustEvidence =
                        highestEvidence;

            } else {
                /*
                 * Exige que a alternativa tenha aparecido
                 * forte em pelo menos duas observações.
                 */
                robustEvidence =
                        (
                                highestEvidence
                                        + secondHighestEvidence
                        ) / 2.0;
            }

            double averageRelativeEvidence =
                    sampleCount <= 0
                            ? 0.0
                            : relativeEvidenceSum
                            / sampleCount;

            double averageWinningGap =
                    winCount <= 0
                            ? 0.0
                            : winningGapSum
                            / winCount;

            double weightedWinRatio =
                    totalVoteWeight <= 0.000001
                            ? 0.0
                            : weightedWinScore
                            / totalVoteWeight;

            double consensusScore =
                    (
                            weightedWinRatio
                                    * config
                                    .getWinRatioWeight()

                                    + averageRelativeEvidence
                                    * config
                                    .getRelativeEvidenceWeight()

                                    + robustEvidence
                                    * config
                                    .getAbsoluteEvidenceWeight()
                    ) / config.getTotalWeight();

            return new OptionEvidenceAggregate(
                    option,
                    sampleCount,
                    winCount,
                    Math.max(
                            0.0,
                            weightedWinScore
                    ),
                    clamp01(weightedWinRatio),
                    clamp01(averageEvidence),
                    clamp01(highestEvidence),
                    clamp01(robustEvidence),
                    clamp01(averageRelativeEvidence),
                    clamp01(averageWinningGap),
                    clamp01(consensusScore)
            );
        }

        private void reset() {
            winCount = 0;

            weightedWinScore = 0.0;

            evidenceSum = 0.0;

            highestEvidence = 0.0;
            secondHighestEvidence = 0.0;

            relativeEvidenceSum = 0.0;
            winningGapSum = 0.0;
        }
    }

    private static double clamp01(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}