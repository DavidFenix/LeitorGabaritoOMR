package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refina a translacao inicial de cada bloco para um registro com
 * translacao e escalas horizontal e vertical independentes.
 *
 * O algoritmo nao conhece quantidade fixa de blocos, questoes ou
 * alternativas. Ele utiliza somente a geometria dos alvos e as
 * correspondencias robustas produzidas pela etapa de translacao.
 *
 * Estrategia por bloco:
 *
 * 1. calcula scaleX por pares da mesma questao;
 * 2. calcula scaleY por pares da mesma alternativa;
 * 3. usa a mediana das razoes de distancia;
 * 4. calcula ancoras esperada e observada por medianas;
 * 5. remove residuos discrepantes de forma robusta;
 * 6. refaz o modelo com os apoios restantes;
 * 7. calcula cobertura, erro e confianca finais.
 *
 * Nenhum objeto Mat e mantido. O resultado pode ser usado pelo
 * calculo, pelo Laboratorio OMR e pelos testes sem release().
 */
public final class BubbleGridRegistrar {

    private static final int REFINEMENT_ITERATIONS = 3;

    private static final double
            MINIMUM_NUMERICAL_SCALE = 0.25;

    private static final double
            MAXIMUM_NUMERICAL_SCALE = 4.00;

    private static final double
            MINIMUM_ROBUST_RESIDUAL_SCALE = 0.10;

    private static final double
            MAD_TO_SIGMA = 1.4826;

    private static final double
            ROBUST_SIGMA_MULTIPLIER = 3.0;

    private final BubbleGridRegistrarConfig config;

    public BubbleGridRegistrar(
            BubbleGridRegistrarConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuracao e obrigatoria."
            );
        }

        this.config = config;
    }

    public BubbleGridRegistrationResult register(
            List<ExpectedBubbleTarget> targets,
            BubbleTranslationEstimationResult
                    translationResult
    ) {
        String inputError = validateInput(
                targets,
                translationResult
        );

        if (inputError != null) {
            return BubbleGridRegistrationResult.failure(
                    targets == null ? 0 : targets.size(),
                    translationResult == null
                            ? 0
                            : translationResult
                            .getCandidateCount(),
                    inputError
            );
        }

        Map<Integer, List<ExpectedBubbleTarget>>
                targetsByBlock = groupTargetsByBlock(
                targets
        );

        List<BubbleBlockRegistration>
                registrations = new ArrayList<>();

        for (Map.Entry<Integer,
                List<ExpectedBubbleTarget>> entry
                : targetsByBlock.entrySet()) {

            int blockIndex = entry.getKey();

            List<ExpectedBubbleTarget> blockTargets =
                    entry.getValue();

            BubbleBlockTranslationSeed seed =
                    translationResult
                            .findByBlockIndex(blockIndex);

            registrations.add(
                    registerBlock(
                            blockTargets,
                            seed
                    )
            );
        }

        return BubbleGridRegistrationResult.success(
                targets.size(),
                translationResult.getCandidateCount(),
                registrations
        );
    }

    private String validateInput(
            List<ExpectedBubbleTarget> targets,
            BubbleTranslationEstimationResult
                    translationResult
    ) {
        if (targets == null || targets.isEmpty()) {
            return "Nao ha alvos para registrar.";
        }

        if (translationResult == null) {
            return "O resultado de translacao e obrigatorio.";
        }

        if (!translationResult.isSuccess()) {
            return "A translacao inicial nao foi concluida: "
                    + translationResult.getMessage();
        }

        if (translationResult.getTargetCount()
                != targets.size()) {

            return "A quantidade de alvos difere da etapa"
                    + " de translacao.";
        }

        Set<String> optionIds = new HashSet<>();
        Map<Integer, String> blockIdByIndex =
                new HashMap<>();

        for (ExpectedBubbleTarget target : targets) {
            if (target == null) {
                return "A lista possui alvo nulo.";
            }

            if (!optionIds.add(target.getOptionId())) {
                return "Alvo repetido: "
                        + target.getOptionId();
            }

            String previousBlockId =
                    blockIdByIndex.put(
                            target.getBlockIndex(),
                            target.getBlockId()
                    );

            if (previousBlockId != null
                    && !previousBlockId.equals(
                    target.getBlockId()
            )) {

                return "Um blockIndex esta associado"
                        + " a mais de um blockId.";
            }
        }

        return null;
    }

    private Map<Integer, List<ExpectedBubbleTarget>>
    groupTargetsByBlock(
            List<ExpectedBubbleTarget> targets
    ) {
        Map<Integer, List<ExpectedBubbleTarget>> grouped =
                new LinkedHashMap<>();

        for (ExpectedBubbleTarget target : targets) {
            List<ExpectedBubbleTarget> blockTargets =
                    grouped.get(target.getBlockIndex());

            if (blockTargets == null) {
                blockTargets = new ArrayList<>();

                grouped.put(
                        target.getBlockIndex(),
                        blockTargets
                );
            }

            blockTargets.add(target);
        }

        return grouped;
    }

    private BubbleBlockRegistration registerBlock(
            List<ExpectedBubbleTarget> blockTargets,
            BubbleBlockTranslationSeed seed
    ) {
        ExpectedBubbleTarget firstTarget =
                blockTargets.get(0);

        if (seed == null) {
            BubbleBlockTransform fallbackTransform =
                    BubbleBlockTransform.translation(
                            firstTarget.getBlockIndex(),
                            firstTarget.getBlockId(),
                            0.0,
                            0.0
                    );

            return new BubbleBlockRegistration(
                    fallbackTransform,
                    blockTargets.size(),
                    0,
                    Collections.<BubbleGridSupport>
                            emptyList(),
                    0.0,
                    false,
                    "O bloco nao possui semente de translacao."
            );
        }

        if (!seed.getBlockId().equals(
                firstTarget.getBlockId()
        )) {

            BubbleBlockTransform fallbackTransform =
                    BubbleBlockTransform.translation(
                            firstTarget.getBlockIndex(),
                            firstTarget.getBlockId(),
                            0.0,
                            0.0
                    );

            return new BubbleBlockRegistration(
                    fallbackTransform,
                    blockTargets.size(),
                    0,
                    Collections.<BubbleGridSupport>
                            emptyList(),
                    0.0,
                    false,
                    "A semente pertence a outro bloco."
            );
        }

        List<BubbleTranslationSupport> sourceSupports =
                seed.getSupports();

        if (sourceSupports.isEmpty()) {
            BubbleBlockTransform fallbackTransform =
                    createTranslationFallback(
                            blockTargets,
                            seed
                    );

            return new BubbleBlockRegistration(
                    fallbackTransform,
                    blockTargets.size(),
                    0,
                    Collections.<BubbleGridSupport>
                            emptyList(),
                    0.0,
                    false,
                    "A semente nao possui apoios."
            );
        }

        List<BubbleTranslationSupport> workingSupports =
                new ArrayList<>(sourceSupports);

        ModelFit fit = fitModel(
                blockTargets,
                workingSupports,
                seed
        );

        for (int iteration = 0;
                iteration < REFINEMENT_ITERATIONS;
                iteration++) {

            InlierSelection selection =
                    selectInliers(
                            sourceSupports,
                            fit.transform
                    );

            if (selection.translationSupports.size()
                    < minimumModelSupportCount()
                    || sameSupportSet(
                    workingSupports,
                    selection.translationSupports
            )) {

                workingSupports =
                        selection.translationSupports;
                break;
            }

            workingSupports =
                    selection.translationSupports;

            fit = fitModel(
                    blockTargets,
                    workingSupports,
                    seed
            );
        }

        if (!workingSupports.isEmpty()) {
            fit = fitModel(
                    blockTargets,
                    workingSupports,
                    seed
            );
        }

        InlierSelection finalSelection =
                selectInliers(
                        sourceSupports,
                        fit.transform
                );

        List<BubbleGridSupport> finalSupports =
                finalSelection.gridSupports;

        BlockDecision decision = evaluateBlock(
                blockTargets.size(),
                sourceSupports.size(),
                finalSupports,
                fit
        );

        return new BubbleBlockRegistration(
                fit.transform,
                blockTargets.size(),
                sourceSupports.size(),
                finalSupports,
                decision.confidence,
                decision.accepted,
                decision.message
        );
    }

    private BubbleBlockTransform createTranslationFallback(
            List<ExpectedBubbleTarget> blockTargets,
            BubbleBlockTranslationSeed seed
    ) {
        ExpectedBubbleTarget first = blockTargets.get(0);

        double expectedAnchorX = medianTargetX(
                blockTargets
        );

        double expectedAnchorY = medianTargetY(
                blockTargets
        );

        return BubbleBlockTransform.translationAndScale(
                first.getBlockIndex(),
                first.getBlockId(),
                expectedAnchorX,
                expectedAnchorY,
                expectedAnchorX + seed.getOffsetX(),
                expectedAnchorY + seed.getOffsetY(),
                1.0,
                1.0
        );
    }

    private ModelFit fitModel(
            List<ExpectedBubbleTarget> blockTargets,
            List<BubbleTranslationSupport> supports,
            BubbleBlockTranslationSeed seed
    ) {
        ExpectedBubbleTarget first = blockTargets.get(0);

        if (supports.isEmpty()) {
            return ModelFit.fallback(
                    createTranslationFallback(
                            blockTargets,
                            seed
                    )
            );
        }

        AxisScaleEstimate xEstimate =
                estimateScaleX(supports);

        AxisScaleEstimate yEstimate =
                estimateScaleY(supports);

        double scaleX =
                xEstimate.valid
                        ? xEstimate.scale
                        : 1.0;

        double scaleY =
                yEstimate.valid
                        ? yEstimate.scale
                        : 1.0;

        double expectedAnchorX =
                medianExpectedX(supports);

        double expectedAnchorY =
                medianExpectedY(supports);

        double observedAnchorX =
                medianObservedAnchorX(
                        supports,
                        expectedAnchorX,
                        scaleX
                );

        double observedAnchorY =
                medianObservedAnchorY(
                        supports,
                        expectedAnchorY,
                        scaleY
                );

        BubbleBlockTransform transform =
                BubbleBlockTransform.translationAndScale(
                        first.getBlockIndex(),
                        first.getBlockId(),
                        expectedAnchorX,
                        expectedAnchorY,
                        observedAnchorX,
                        observedAnchorY,
                        scaleX,
                        scaleY
                );

        return new ModelFit(
                transform,
                xEstimate,
                yEstimate
        );
    }

    private AxisScaleEstimate estimateScaleX(
            List<BubbleTranslationSupport> supports
    ) {
        List<Double> slopes = new ArrayList<>();

        double minimumSeparation =
                0.25 * medianExpectedWidth(supports);

        for (int firstIndex = 0;
                firstIndex < supports.size();
                firstIndex++) {

            BubbleTranslationSupport first =
                    supports.get(firstIndex);

            for (int secondIndex = firstIndex + 1;
                    secondIndex < supports.size();
                    secondIndex++) {

                BubbleTranslationSupport second =
                        supports.get(secondIndex);

                if (first.getTarget().getQuestionIndex()
                        != second.getTarget()
                        .getQuestionIndex()) {

                    continue;
                }

                addSlopeIfValid(
                        slopes,
                        first.getTarget()
                                .getExpectedCenterX(),
                        second.getTarget()
                                .getExpectedCenterX(),
                        first.getCandidate().getCenterX(),
                        second.getCandidate().getCenterX(),
                        minimumSeparation
                );
            }
        }

        return createAxisEstimate(slopes);
    }

    private AxisScaleEstimate estimateScaleY(
            List<BubbleTranslationSupport> supports
    ) {
        List<Double> slopes = new ArrayList<>();

        double minimumSeparation =
                0.25 * medianExpectedHeight(supports);

        for (int firstIndex = 0;
                firstIndex < supports.size();
                firstIndex++) {

            BubbleTranslationSupport first =
                    supports.get(firstIndex);

            for (int secondIndex = firstIndex + 1;
                    secondIndex < supports.size();
                    secondIndex++) {

                BubbleTranslationSupport second =
                        supports.get(secondIndex);

                if (first.getTarget().getOptionIndex()
                        != second.getTarget()
                        .getOptionIndex()) {

                    continue;
                }

                addSlopeIfValid(
                        slopes,
                        first.getTarget()
                                .getExpectedCenterY(),
                        second.getTarget()
                                .getExpectedCenterY(),
                        first.getCandidate().getCenterY(),
                        second.getCandidate().getCenterY(),
                        minimumSeparation
                );
            }
        }

        return createAxisEstimate(slopes);
    }

    private void addSlopeIfValid(
            List<Double> slopes,
            double firstExpected,
            double secondExpected,
            double firstObserved,
            double secondObserved,
            double minimumSeparation
    ) {
        double expectedDifference =
                secondExpected - firstExpected;

        if (Math.abs(expectedDifference)
                < minimumSeparation) {

            return;
        }

        double observedDifference =
                secondObserved - firstObserved;

        double slope =
                observedDifference
                        / expectedDifference;

        if (Double.isFinite(slope)
                && slope >= MINIMUM_NUMERICAL_SCALE
                && slope <= MAXIMUM_NUMERICAL_SCALE) {

            slopes.add(slope);
        }
    }

    private AxisScaleEstimate createAxisEstimate(
            List<Double> slopes
    ) {
        if (slopes.isEmpty()) {
            return AxisScaleEstimate.unobservable();
        }

        double scale = median(slopes);

        boolean valid =
                Double.isFinite(scale)
                        && scale
                        >= MINIMUM_NUMERICAL_SCALE
                        && scale
                        <= MAXIMUM_NUMERICAL_SCALE;

        return new AxisScaleEstimate(
                valid ? scale : 1.0,
                true,
                valid,
                slopes.size()
        );
    }

    private double medianObservedAnchorX(
            List<BubbleTranslationSupport> supports,
            double expectedAnchorX,
            double scaleX
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleTranslationSupport support : supports) {
            values.add(
                    support.getCandidate().getCenterX()
                            - scaleX * (
                            support.getTarget()
                                    .getExpectedCenterX()
                                    - expectedAnchorX
                    )
            );
        }

        return median(values);
    }

    private double medianObservedAnchorY(
            List<BubbleTranslationSupport> supports,
            double expectedAnchorY,
            double scaleY
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleTranslationSupport support : supports) {
            values.add(
                    support.getCandidate().getCenterY()
                            - scaleY * (
                            support.getTarget()
                                    .getExpectedCenterY()
                                    - expectedAnchorY
                    )
            );
        }

        return median(values);
    }

    private InlierSelection selectInliers(
            List<BubbleTranslationSupport> sourceSupports,
            BubbleBlockTransform transform
    ) {
        List<BubbleGridSupport> allGridSupports =
                createGridSupports(
                        sourceSupports,
                        transform
                );

        List<Double> normalizedResiduals =
                new ArrayList<>();

        for (BubbleGridSupport support
                : allGridSupports) {

            normalizedResiduals.add(
                    support.getNormalizedResidual()
            );
        }

        double medianResidual =
                median(normalizedResiduals);

        List<Double> absoluteDeviations =
                new ArrayList<>();

        for (Double residual : normalizedResiduals) {
            absoluteDeviations.add(
                    Math.abs(
                            residual - medianResidual
                    )
            );
        }

        double mad = median(absoluteDeviations);

        double robustThreshold =
                medianResidual
                        + ROBUST_SIGMA_MULTIPLIER
                        * MAD_TO_SIGMA
                        * mad;

        robustThreshold = Math.max(
                MINIMUM_ROBUST_RESIDUAL_SCALE,
                robustThreshold
        );

        robustThreshold = Math.min(
                config.getMaximumResidualDiagonalScale(),
                robustThreshold
        );

        List<BubbleTranslationSupport>
                translationInliers = new ArrayList<>();

        List<BubbleGridSupport> gridInliers =
                new ArrayList<>();

        for (int index = 0;
                index < allGridSupports.size();
                index++) {

            BubbleGridSupport gridSupport =
                    allGridSupports.get(index);

            if (gridSupport.getNormalizedResidual()
                    <= robustThreshold) {

                translationInliers.add(
                        sourceSupports.get(index)
                );

                gridInliers.add(gridSupport);
            }
        }

        return new InlierSelection(
                translationInliers,
                gridInliers,
                robustThreshold
        );
    }

    private List<BubbleGridSupport> createGridSupports(
            List<BubbleTranslationSupport>
                    translationSupports,
            BubbleBlockTransform transform
    ) {
        List<BubbleGridSupport> result =
                new ArrayList<>();

        for (BubbleTranslationSupport support
                : translationSupports) {

            result.add(
                    new BubbleGridSupport(
                            support.getTarget(),
                            support.getCandidate(),
                            transform,
                            support.getQuality()
                    )
            );
        }

        return result;
    }

    private boolean sameSupportSet(
            List<BubbleTranslationSupport> first,
            List<BubbleTranslationSupport> second
    ) {
        if (first.size() != second.size()) {
            return false;
        }

        Set<String> firstOptionIds = new HashSet<>();

        for (BubbleTranslationSupport support : first) {
            firstOptionIds.add(
                    support.getTarget().getOptionId()
            );
        }

        for (BubbleTranslationSupport support : second) {
            if (!firstOptionIds.contains(
                    support.getTarget().getOptionId()
            )) {
                return false;
            }
        }

        return true;
    }

    private int minimumModelSupportCount() {
        return Math.max(
                2,
                config.getMinimumDirectMatchesPerBlock()
        );
    }

    private BlockDecision evaluateBlock(
            int targetCount,
            int sourceSupportCount,
            List<BubbleGridSupport> supports,
            ModelFit fit
    ) {
        int supportCount = supports.size();

        double supportRatio =
                supportCount / (double) targetCount;

        double retentionRatio =
                sourceSupportCount == 0
                        ? 0.0
                        : supportCount
                        / (double) sourceSupportCount;

        double medianNormalizedResidual =
                medianNormalizedResidual(supports);

        double meanQuality = meanQuality(supports);

        double residualScore = clamp01(
                1.0
                        - medianNormalizedResidual
                        / config
                        .getMaximumResidualDiagonalScale()
        );

        double scaleScore =
                calculateScaleScore(fit);

        double confidence = clamp01(
                0.35 * supportRatio
                        + 0.25 * residualScore
                        + 0.15 * scaleScore
                        + 0.15 * meanQuality
                        + 0.10 * retentionRatio
        );

        boolean enoughSupports =
                supportCount
                        >= config
                        .getMinimumDirectMatchesPerBlock()
                        && supportRatio
                        >= config
                        .getMinimumDirectMatchRatio();

        boolean scaleXAccepted =
                isAxisScaleAccepted(fit.xEstimate);

        boolean scaleYAccepted =
                isAxisScaleAccepted(fit.yEstimate);

        boolean residualAccepted =
                supports.isEmpty()
                        ? false
                        : medianNormalizedResidual
                        <= config
                        .getMaximumResidualDiagonalScale();

        boolean accepted =
                enoughSupports
                        && scaleXAccepted
                        && scaleYAccepted
                        && residualAccepted
                        && confidence
                        >= config
                        .getMinimumBlockConfidence();

        String message;

        if (!enoughSupports) {
            message = "Evidencia insuficiente no bloco.";
        } else if (!scaleXAccepted) {
            message = "Escala horizontal fora do limite.";
        } else if (!scaleYAccepted) {
            message = "Escala vertical fora do limite.";
        } else if (!residualAccepted) {
            message = "Erro residual excessivo.";
        } else if (confidence
                < config.getMinimumBlockConfidence()) {

            message = "Confianca insuficiente no bloco.";
        } else {
            message = "Registro do bloco aceito.";
        }

        return new BlockDecision(
                accepted,
                confidence,
                message
        );
    }

    private boolean isAxisScaleAccepted(
            AxisScaleEstimate estimate
    ) {
        if (!estimate.valid) {
            return false;
        }

        if (!estimate.observable) {
            return true;
        }

        return Math.abs(estimate.scale - 1.0)
                <= config.getMaximumScaleDeviation();
    }

    private double calculateScaleScore(
            ModelFit fit
    ) {
        List<Double> scores = new ArrayList<>();

        if (fit.xEstimate.observable) {
            scores.add(
                    axisScaleScore(fit.xEstimate)
            );
        }

        if (fit.yEstimate.observable) {
            scores.add(
                    axisScaleScore(fit.yEstimate)
            );
        }

        if (scores.isEmpty()) {
            return 1.0;
        }

        double sum = 0.0;

        for (Double score : scores) {
            sum += score;
        }

        return sum / scores.size();
    }

    private double axisScaleScore(
            AxisScaleEstimate estimate
    ) {
        if (!estimate.valid) {
            return 0.0;
        }

        double maximumDeviation =
                config.getMaximumScaleDeviation();

        if (maximumDeviation <= 0.0) {
            return estimate.scale == 1.0
                    ? 1.0
                    : 0.0;
        }

        return clamp01(
                1.0
                        - Math.abs(estimate.scale - 1.0)
                        / maximumDeviation
        );
    }

    private double medianNormalizedResidual(
            List<BubbleGridSupport> supports
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleGridSupport support : supports) {
            values.add(
                    support.getNormalizedResidual()
            );
        }

        return median(values);
    }

    private double meanQuality(
            List<BubbleGridSupport> supports
    ) {
        if (supports.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;

        for (BubbleGridSupport support : supports) {
            sum += support.getQuality();
        }

        return sum / supports.size();
    }

    private double medianExpectedX(
            List<BubbleTranslationSupport> supports
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleTranslationSupport support : supports) {
            values.add(
                    support.getTarget()
                            .getExpectedCenterX()
            );
        }

        return median(values);
    }

    private double medianExpectedY(
            List<BubbleTranslationSupport> supports
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleTranslationSupport support : supports) {
            values.add(
                    support.getTarget()
                            .getExpectedCenterY()
            );
        }

        return median(values);
    }

    private double medianTargetX(
            List<ExpectedBubbleTarget> targets
    ) {
        List<Double> values = new ArrayList<>();

        for (ExpectedBubbleTarget target : targets) {
            values.add(target.getExpectedCenterX());
        }

        return median(values);
    }

    private double medianTargetY(
            List<ExpectedBubbleTarget> targets
    ) {
        List<Double> values = new ArrayList<>();

        for (ExpectedBubbleTarget target : targets) {
            values.add(target.getExpectedCenterY());
        }

        return median(values);
    }

    private double medianExpectedWidth(
            List<BubbleTranslationSupport> supports
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleTranslationSupport support : supports) {
            values.add(
                    support.getTarget()
                            .getExpectedWidth()
            );
        }

        return median(values);
    }

    private double medianExpectedHeight(
            List<BubbleTranslationSupport> supports
    ) {
        List<Double> values = new ArrayList<>();

        for (BubbleTranslationSupport support : supports) {
            values.add(
                    support.getTarget()
                            .getExpectedHeight()
            );
        }

        return median(values);
    }

    private double median(
            List<Double> source
    ) {
        if (source.isEmpty()) {
            return 0.0;
        }

        List<Double> sorted =
                new ArrayList<>(source);

        Collections.sort(
                sorted,
                new Comparator<Double>() {
                    @Override
                    public int compare(
                            Double first,
                            Double second
                    ) {
                        return Double.compare(
                                first,
                                second
                        );
                    }
                }
        );

        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return (
                sorted.get(middle - 1)
                        + sorted.get(middle)
        ) / 2.0;
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private static final class AxisScaleEstimate {

        private final double scale;
        private final boolean observable;
        private final boolean valid;
        private final int pairCount;

        private AxisScaleEstimate(
                double scale,
                boolean observable,
                boolean valid,
                int pairCount
        ) {
            this.scale = scale;
            this.observable = observable;
            this.valid = valid;
            this.pairCount = pairCount;
        }

        private static AxisScaleEstimate unobservable() {
            return new AxisScaleEstimate(
                    1.0,
                    false,
                    true,
                    0
            );
        }
    }

    private static final class ModelFit {

        private final BubbleBlockTransform transform;
        private final AxisScaleEstimate xEstimate;
        private final AxisScaleEstimate yEstimate;

        private ModelFit(
                BubbleBlockTransform transform,
                AxisScaleEstimate xEstimate,
                AxisScaleEstimate yEstimate
        ) {
            this.transform = transform;
            this.xEstimate = xEstimate;
            this.yEstimate = yEstimate;
        }

        private static ModelFit fallback(
                BubbleBlockTransform transform
        ) {
            return new ModelFit(
                    transform,
                    AxisScaleEstimate.unobservable(),
                    AxisScaleEstimate.unobservable()
            );
        }
    }

    private static final class InlierSelection {

        private final List<BubbleTranslationSupport>
                translationSupports;

        private final List<BubbleGridSupport>
                gridSupports;

        private final double threshold;

        private InlierSelection(
                List<BubbleTranslationSupport>
                        translationSupports,
                List<BubbleGridSupport> gridSupports,
                double threshold
        ) {
            this.translationSupports =
                    translationSupports;
            this.gridSupports = gridSupports;
            this.threshold = threshold;
        }
    }

    private static final class BlockDecision {

        private final boolean accepted;
        private final double confidence;
        private final String message;

        private BlockDecision(
                boolean accepted,
                double confidence,
                String message
        ) {
            this.accepted = accepted;
            this.confidence = confidence;
            this.message = message;
        }
    }
}
