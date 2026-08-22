package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;

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
 * Encontra uma translacao inicial para cada bloco por votacao
 * geometrica e validacao de apoio global.
 *
 * O estimador nao depende das associacoes gulosas preliminares.
 * Cada deslocamento alvo -> candidato vira um voto. A hipotese
 * correta tende a alinhar todas as alternativas; hipoteses
 * deslocadas uma coluna perdem as alternativas da extremidade.
 */
public final class BubbleBlockTranslationEstimator {

    private static final double VOTE_BIN_SIZE_SCALE = 0.30;
    private static final double MINIMUM_VOTE_BIN_SIZE = 2.0;

    private static final int MAXIMUM_HYPOTHESES_PER_BLOCK = 48;
    private static final int REFINEMENT_ITERATIONS = 2;

    private static final double RESIDUAL_SCORE_WEIGHT = 0.75;
    private static final double SIZE_SCORE_WEIGHT = 0.20;
    private static final double SHAPE_SCORE_WEIGHT = 0.05;

    private static final double
            PHYSICAL_DUPLICATE_CENTER_SCALE = 0.35;

    private static final double
            MINIMUM_PHYSICAL_DUPLICATE_OVERLAP = 0.45;

    private static final double
            MINIMUM_PHYSICAL_DUPLICATE_DISTANCE = 2.0;

    private final BubbleGridRegistrarConfig config;

    public BubbleBlockTranslationEstimator(
            BubbleGridRegistrarConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuracao e obrigatoria."
            );
        }

        this.config = config;
    }

    public BubbleTranslationEstimationResult estimate(
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult
    ) {
        String validationError =
                validateInput(
                        targets,
                        extractionResult
                );

        if (validationError != null) {
            return BubbleTranslationEstimationResult
                    .failure(validationError);
        }

        Map<Integer, List<ExpectedBubbleTarget>>
                targetsByBlock = groupTargetsByBlock(
                targets
        );

        List<BubbleBlockTranslationSeed> seeds =
                new ArrayList<>();

        List<BubbleContourCandidate> candidates =
                extractionResult.getCandidates();

        for (List<ExpectedBubbleTarget> blockTargets
                : targetsByBlock.values()) {

            seeds.add(
                    estimateBlock(
                            blockTargets,
                            candidates
                    )
            );
        }

        return BubbleTranslationEstimationResult.success(
                targets.size(),
                candidates.size(),
                seeds
        );
    }

    private String validateInput(
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult
    ) {
        if (targets == null || targets.isEmpty()) {
            return "A lista de alvos esperados esta vazia.";
        }

        Set<String> optionIds = new HashSet<>();

        for (ExpectedBubbleTarget target : targets) {
            if (target == null) {
                return "A lista possui alvo esperado nulo.";
            }

            if (!optionIds.add(target.getOptionId())) {
                return "Alvo esperado repetido: "
                        + target.getOptionId();
            }
        }

        if (extractionResult == null) {
            return "O resultado da extracao e obrigatorio.";
        }

        if (!extractionResult.isSuccess()) {
            return "A extracao de candidatos falhou: "
                    + extractionResult.getMessage();
        }

        return null;
    }

    private Map<Integer, List<ExpectedBubbleTarget>>
    groupTargetsByBlock(
            List<ExpectedBubbleTarget> targets
    ) {
        Map<Integer, List<ExpectedBubbleTarget>> result =
                new LinkedHashMap<>();

        Map<Integer, String> blockIdByIndex =
                new HashMap<>();

        for (ExpectedBubbleTarget target : targets) {
            int blockIndex = target.getBlockIndex();

            String previousBlockId =
                    blockIdByIndex.put(
                            blockIndex,
                            target.getBlockId()
                    );

            if (previousBlockId != null
                    && !previousBlockId.equals(
                    target.getBlockId()
            )) {
                throw new IllegalArgumentException(
                        "O mesmo blockIndex possui blockIds diferentes."
                );
            }

            List<ExpectedBubbleTarget> blockTargets =
                    result.get(blockIndex);

            if (blockTargets == null) {
                blockTargets = new ArrayList<>();
                result.put(blockIndex, blockTargets);
            }

            blockTargets.add(target);
        }

        return result;
    }

    private BubbleBlockTranslationSeed estimateBlock(
            List<ExpectedBubbleTarget> targets,
            List<BubbleContourCandidate> candidates
    ) {
        ExpectedBubbleTarget firstTarget = targets.get(0);

        if (candidates.isEmpty()) {
            return rejectedSeed(
                    firstTarget,
                    targets.size(),
                    "Nenhum candidato observado."
            );
        }

        List<VoteBucket> voteBuckets =
                createVoteBuckets(
                        targets,
                        candidates
                );

        if (voteBuckets.isEmpty()) {
            return rejectedSeed(
                    firstTarget,
                    targets.size(),
                    "Nenhuma hipotese de translacao plausivel."
            );
        }

        sortVoteBuckets(voteBuckets);

        int hypothesisCount =
                Math.min(
                        MAXIMUM_HYPOTHESES_PER_BLOCK,
                        voteBuckets.size()
                );

        TranslationEvaluation bestEvaluation = null;

        for (int index = 0;
             index < hypothesisCount;
             index++) {

            VoteBucket bucket = voteBuckets.get(index);

            double offsetX = bucket.medianOffsetX();
            double offsetY = bucket.medianOffsetY();

            TranslationEvaluation evaluation = null;

            for (int iteration = 0;
                 iteration < REFINEMENT_ITERATIONS;
                 iteration++) {

                evaluation = evaluateTranslation(
                        targets,
                        candidates,
                        offsetX,
                        offsetY
                );

                if (evaluation.supportPairs.isEmpty()) {
                    break;
                }

                offsetX = evaluation.refinedOffsetX;
                offsetY = evaluation.refinedOffsetY;
            }

            if (evaluation != null
                    && isBetterEvaluation(
                    evaluation,
                    bestEvaluation
            )) {
                bestEvaluation = evaluation;
            }
        }

        if (bestEvaluation == null
                || bestEvaluation.supportPairs.isEmpty()) {

            return rejectedSeed(
                    firstTarget,
                    targets.size(),
                    "As hipoteses nao produziram apoios exclusivos."
            );
        }

        /*
         * Uma ultima avaliacao garante que os apoios, residuos e
         * pontuacoes correspondam exatamente ao deslocamento final.
         */
        bestEvaluation = evaluateTranslation(
                targets,
                candidates,
                bestEvaluation.refinedOffsetX,
                bestEvaluation.refinedOffsetY
        );

        return createSeed(
                firstTarget,
                targets,
                bestEvaluation
        );
    }

    private List<VoteBucket> createVoteBuckets(
            List<ExpectedBubbleTarget> targets,
            List<BubbleContourCandidate> candidates
    ) {
        double binSizeX = Math.max(
                MINIMUM_VOTE_BIN_SIZE,
                medianExpectedWidth(targets)
                        * VOTE_BIN_SIZE_SCALE
        );

        double binSizeY = Math.max(
                MINIMUM_VOTE_BIN_SIZE,
                medianExpectedHeight(targets)
                        * VOTE_BIN_SIZE_SCALE
        );

        Map<String, VoteBucket> bucketByKey =
                new HashMap<>();

        for (ExpectedBubbleTarget target : targets) {
            double searchRadiusX =
                    target.getExpectedWidth()
                            * config.getSearchRadiusXScale();

            double searchRadiusY =
                    target.getExpectedHeight()
                            * config.getSearchRadiusYScale();

            for (BubbleContourCandidate candidate : candidates) {
                double offsetX =
                        candidate.getCenterX()
                                - target.getExpectedCenterX();

                double offsetY =
                        candidate.getCenterY()
                                - target.getExpectedCenterY();

                double normalizedDistance = Math.hypot(
                        offsetX / searchRadiusX,
                        offsetY / searchRadiusY
                );

                if (normalizedDistance > 1.0) {
                    continue;
                }

                long binX = Math.round(offsetX / binSizeX);
                long binY = Math.round(offsetY / binSizeY);

                String key = binX + ":" + binY;

                VoteBucket bucket = bucketByKey.get(key);

                if (bucket == null) {
                    bucket = new VoteBucket(binX, binY);
                    bucketByKey.put(key, bucket);
                }

                RawVote vote = new RawVote(
                        target,
                        candidate,
                        offsetX,
                        offsetY,
                        calculateGeometryScore(
                                target,
                                candidate
                        )
                );

                bucket.offer(vote);
            }
        }

        return new ArrayList<>(bucketByKey.values());
    }

    private void sortVoteBuckets(
            List<VoteBucket> buckets
    ) {
        Collections.sort(
                buckets,
                new Comparator<VoteBucket>() {
                    @Override
                    public int compare(
                            VoteBucket first,
                            VoteBucket second
                    ) {
                        int comparison = Integer.compare(
                                second.getVoteCount(),
                                first.getVoteCount()
                        );

                        if (comparison != 0) {
                            return comparison;
                        }

                        comparison = Double.compare(
                                second.getQualitySum(),
                                first.getQualitySum()
                        );

                        if (comparison != 0) {
                            return comparison;
                        }

                        double firstMagnitude = Math.hypot(
                                first.medianOffsetX(),
                                first.medianOffsetY()
                        );

                        double secondMagnitude = Math.hypot(
                                second.medianOffsetX(),
                                second.medianOffsetY()
                        );

                        comparison = Double.compare(
                                firstMagnitude,
                                secondMagnitude
                        );

                        if (comparison != 0) {
                            return comparison;
                        }

                        comparison = Long.compare(
                                first.binX,
                                second.binX
                        );

                        if (comparison != 0) {
                            return comparison;
                        }

                        return Long.compare(
                                first.binY,
                                second.binY
                        );
                    }
                }
        );
    }

    private TranslationEvaluation evaluateTranslation(
            List<ExpectedBubbleTarget> targets,
            List<BubbleContourCandidate> candidates,
            double offsetX,
            double offsetY
    ) {
        List<SupportPair> proposals = new ArrayList<>();

        for (ExpectedBubbleTarget target : targets) {
            double maximumResidual = Math.max(
                    MINIMUM_VOTE_BIN_SIZE,
                    target.getExpectedDiagonal()
                            * config
                            .getMaximumResidualDiagonalScale()
            );

            double predictedCenterX =
                    target.getExpectedCenterX() + offsetX;

            double predictedCenterY =
                    target.getExpectedCenterY() + offsetY;

            for (BubbleContourCandidate candidate : candidates) {
                double residualDistance =
                        candidate.distanceTo(
                                predictedCenterX,
                                predictedCenterY
                        );

                if (residualDistance > maximumResidual) {
                    continue;
                }

                double residualScore = clamp01(
                        1.0 - residualDistance
                                / maximumResidual
                );

                double geometryScore =
                        calculateGeometryScore(
                                target,
                                candidate
                        );

                double sizeScore =
                        calculateSizeScore(
                                target,
                                candidate
                        );

                double quality = clamp01(
                        residualScore
                                * RESIDUAL_SCORE_WEIGHT
                                + sizeScore
                                * SIZE_SCORE_WEIGHT
                                + geometryScore
                                * SHAPE_SCORE_WEIGHT
                );

                proposals.add(
                        new SupportPair(
                                target,
                                candidate,
                                quality
                        )
                );
            }
        }

        sortSupportPairs(proposals);

        List<SupportPair> selected =
                chooseExclusiveSupportPairs(proposals);

        if (selected.isEmpty()) {
            return TranslationEvaluation.empty(
                    offsetX,
                    offsetY
            );
        }

        List<Double> observedOffsetsX =
                new ArrayList<>();

        List<Double> observedOffsetsY =
                new ArrayList<>();

        for (SupportPair pair : selected) {
            observedOffsetsX.add(
                    pair.candidate.getCenterX()
                            - pair.target
                            .getExpectedCenterX()
            );

            observedOffsetsY.add(
                    pair.candidate.getCenterY()
                            - pair.target
                            .getExpectedCenterY()
            );
        }

        double refinedOffsetX = median(observedOffsetsX);
        double refinedOffsetY = median(observedOffsetsY);

        List<Double> residuals = new ArrayList<>();
        double qualitySum = 0.0;

        for (SupportPair pair : selected) {
            double predictedCenterX =
                    pair.target.getExpectedCenterX()
                            + refinedOffsetX;

            double predictedCenterY =
                    pair.target.getExpectedCenterY()
                            + refinedOffsetY;

            residuals.add(
                    pair.candidate.distanceTo(
                            predictedCenterX,
                            predictedCenterY
                    )
            );

            qualitySum += pair.quality;
        }

        return new TranslationEvaluation(
                refinedOffsetX,
                refinedOffsetY,
                selected,
                median(residuals),
                qualitySum / selected.size()
        );
    }

    private void sortSupportPairs(
            List<SupportPair> proposals
    ) {
        Collections.sort(
                proposals,
                new Comparator<SupportPair>() {
                    @Override
                    public int compare(
                            SupportPair first,
                            SupportPair second
                    ) {
                        int comparison = Double.compare(
                                second.quality,
                                first.quality
                        );

                        if (comparison != 0) {
                            return comparison;
                        }

                        comparison = first.target
                                .getOptionId()
                                .compareTo(
                                        second.target
                                                .getOptionId()
                                );

                        if (comparison != 0) {
                            return comparison;
                        }

                        return Integer.compare(
                                first.candidate
                                        .getCandidateId(),
                                second.candidate
                                        .getCandidateId()
                        );
                    }
                }
        );
    }

    private List<SupportPair> chooseExclusiveSupportPairs(
            List<SupportPair> proposals
    ) {
        List<SupportPair> result = new ArrayList<>();

        Set<String> usedOptionIds = new HashSet<>();
        Set<Integer> usedCandidateIds = new HashSet<>();

        List<BubbleContourCandidate> usedPhysicalCandidates =
                new ArrayList<>();

        for (SupportPair pair : proposals) {
            String optionId = pair.target.getOptionId();

            int candidateId =
                    pair.candidate.getCandidateId();

            if (usedOptionIds.contains(optionId)
                    || usedCandidateIds.contains(candidateId)
                    || conflictsWithPhysicalCandidate(
                    pair.candidate,
                    usedPhysicalCandidates
            )) {
                continue;
            }

            usedOptionIds.add(optionId);
            usedCandidateIds.add(candidateId);
            usedPhysicalCandidates.add(pair.candidate);

            result.add(pair);
        }

        return result;
    }

    private boolean isBetterEvaluation(
            TranslationEvaluation candidate,
            TranslationEvaluation currentBest
    ) {
        if (currentBest == null) {
            return true;
        }

        int candidateSupportCount =
                candidate.supportPairs.size();

        int currentSupportCount =
                currentBest.supportPairs.size();

        if (candidateSupportCount
                != currentSupportCount) {

            return candidateSupportCount
                    > currentSupportCount;
        }

        int comparison = Double.compare(
                candidate.medianResidual,
                currentBest.medianResidual
        );

        if (comparison != 0) {
            return comparison < 0;
        }

        comparison = Double.compare(
                candidate.averageQuality,
                currentBest.averageQuality
        );

        if (comparison != 0) {
            return comparison > 0;
        }

        double candidateMagnitude = Math.hypot(
                candidate.refinedOffsetX,
                candidate.refinedOffsetY
        );

        double currentMagnitude = Math.hypot(
                currentBest.refinedOffsetX,
                currentBest.refinedOffsetY
        );

        return candidateMagnitude < currentMagnitude;
    }

    private BubbleBlockTranslationSeed createSeed(
            ExpectedBubbleTarget firstTarget,
            List<ExpectedBubbleTarget> targets,
            TranslationEvaluation evaluation
    ) {
        List<BubbleTranslationSupport> supports =
                new ArrayList<>();

        for (SupportPair pair : evaluation.supportPairs) {
            supports.add(
                    new BubbleTranslationSupport(
                            pair.target,
                            pair.candidate,
                            pair.target
                                    .getExpectedCenterX()
                                    + evaluation
                                    .refinedOffsetX,
                            pair.target
                                    .getExpectedCenterY()
                                    + evaluation
                                    .refinedOffsetY,
                            pair.quality
                    )
            );
        }

        double supportRatio =
                supports.size()
                        / (double) targets.size();

        double referenceResidualLimit = Math.max(
                MINIMUM_VOTE_BIN_SIZE,
                medianExpectedDiagonal(targets)
                        * config
                        .getMaximumResidualDiagonalScale()
        );

        double residualQuality = clamp01(
                1.0 - evaluation.medianResidual
                        / referenceResidualLimit
        );

        double confidence = clamp01(
                supportRatio * 0.65
                        + residualQuality * 0.25
                        + evaluation.averageQuality * 0.10
        );

        boolean accepted =
                supports.size()
                        >= config
                        .getMinimumDirectMatchesPerBlock()
                        && supportRatio
                        >= config
                        .getMinimumDirectMatchRatio()
                        && confidence
                        >= config
                        .getMinimumBlockConfidence();

        String message = accepted
                ? "Semente de translacao aceita."
                : "Semente rejeitada por apoio"
                + " ou confianca insuficiente.";

        return new BubbleBlockTranslationSeed(
                firstTarget.getBlockIndex(),
                firstTarget.getBlockId(),
                evaluation.refinedOffsetX,
                evaluation.refinedOffsetY,
                targets.size(),
                supports,
                evaluation.medianResidual,
                confidence,
                accepted,
                message
        );
    }

    private BubbleBlockTranslationSeed rejectedSeed(
            ExpectedBubbleTarget firstTarget,
            int targetCount,
            String message
    ) {
        return new BubbleBlockTranslationSeed(
                firstTarget.getBlockIndex(),
                firstTarget.getBlockId(),
                0.0,
                0.0,
                targetCount,
                Collections
                        .<BubbleTranslationSupport>
                                emptyList(),
                0.0,
                0.0,
                false,
                message
        );
    }

    private double calculateGeometryScore(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate
    ) {
        double sizeScore = calculateSizeScore(
                target,
                candidate
        );

        double expectedAspect =
                target.getExpectedWidth()
                        / target.getExpectedHeight();

        double aspectScore = ratioSimilarity(
                expectedAspect,
                candidate.getAspectRatio()
        );

        return clamp01(
                sizeScore * 0.70
                        + aspectScore * 0.22
                        + candidate.getRectangularity()
                        * 0.08
        );
    }

    private double calculateSizeScore(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate
    ) {
        PixelRectangle bounds = candidate.getBounds();

        double widthScore = ratioSimilarity(
                target.getExpectedWidth(),
                bounds.getWidth()
        );

        double heightScore = ratioSimilarity(
                target.getExpectedHeight(),
                bounds.getHeight()
        );

        return clamp01(
                Math.sqrt(widthScore * heightScore)
        );
    }

    private double ratioSimilarity(
            double first,
            double second
    ) {
        if (!Double.isFinite(first)
                || !Double.isFinite(second)
                || first <= 0.0
                || second <= 0.0) {

            return 0.0;
        }

        return clamp01(
                Math.min(first, second)
                        / Math.max(first, second)
        );
    }

    private boolean conflictsWithPhysicalCandidate(
            BubbleContourCandidate candidate,
            List<BubbleContourCandidate> usedCandidates
    ) {
        for (BubbleContourCandidate used : usedCandidates) {
            if (representsSamePhysicalBubble(
                    candidate,
                    used
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean representsSamePhysicalBubble(
            BubbleContourCandidate first,
            BubbleContourCandidate second
    ) {
        PixelRectangle firstBounds = first.getBounds();
        PixelRectangle secondBounds = second.getBounds();

        double referenceWidth = Math.min(
                firstBounds.getWidth(),
                secondBounds.getWidth()
        );

        double referenceHeight = Math.min(
                firstBounds.getHeight(),
                secondBounds.getHeight()
        );

        double maximumCenterOffsetX = Math.max(
                MINIMUM_PHYSICAL_DUPLICATE_DISTANCE,
                referenceWidth
                        * PHYSICAL_DUPLICATE_CENTER_SCALE
        );

        double maximumCenterOffsetY = Math.max(
                MINIMUM_PHYSICAL_DUPLICATE_DISTANCE,
                referenceHeight
                        * PHYSICAL_DUPLICATE_CENTER_SCALE
        );

        if (Math.abs(
                first.getCenterX()
                        - second.getCenterX()
        ) > maximumCenterOffsetX
                || Math.abs(
                first.getCenterY()
                        - second.getCenterY()
        ) > maximumCenterOffsetY) {

            return false;
        }

        int intersectionLeft = Math.max(
                firstBounds.getLeft(),
                secondBounds.getLeft()
        );

        int intersectionTop = Math.max(
                firstBounds.getTop(),
                secondBounds.getTop()
        );

        int intersectionRight = Math.min(
                firstBounds.getRightExclusive(),
                secondBounds.getRightExclusive()
        );

        int intersectionBottom = Math.min(
                firstBounds.getBottomExclusive(),
                secondBounds.getBottomExclusive()
        );

        int intersectionWidth = Math.max(
                0,
                intersectionRight - intersectionLeft
        );

        int intersectionHeight = Math.max(
                0,
                intersectionBottom - intersectionTop
        );

        double intersectionArea =
                intersectionWidth
                        * (double) intersectionHeight;

        double smallerArea = Math.min(
                firstBounds.getArea(),
                secondBounds.getArea()
        );

        if (smallerArea <= 0.0) {
            return false;
        }

        return intersectionArea / smallerArea
                >= MINIMUM_PHYSICAL_DUPLICATE_OVERLAP;
    }

    private double medianExpectedWidth(
            List<ExpectedBubbleTarget> targets
    ) {
        List<Double> values = new ArrayList<>();

        for (ExpectedBubbleTarget target : targets) {
            values.add(target.getExpectedWidth());
        }

        return median(values);
    }

    private double medianExpectedHeight(
            List<ExpectedBubbleTarget> targets
    ) {
        List<Double> values = new ArrayList<>();

        for (ExpectedBubbleTarget target : targets) {
            values.add(target.getExpectedHeight());
        }

        return median(values);
    }

    private double medianExpectedDiagonal(
            List<ExpectedBubbleTarget> targets
    ) {
        List<Double> values = new ArrayList<>();

        for (ExpectedBubbleTarget target : targets) {
            values.add(target.getExpectedDiagonal());
        }

        return median(values);
    }

    private double median(
            List<Double> source
    ) {
        if (source == null || source.isEmpty()) {
            return 0.0;
        }

        List<Double> values = new ArrayList<>(source);
        Collections.sort(values);

        int middle = values.size() / 2;

        if (values.size() % 2 == 1) {
            return values.get(middle);
        }

        return (
                values.get(middle - 1)
                        + values.get(middle)
        ) / 2.0;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class RawVote {

        private final ExpectedBubbleTarget target;
        private final BubbleContourCandidate candidate;

        private final double offsetX;
        private final double offsetY;
        private final double quality;

        private RawVote(
                ExpectedBubbleTarget target,
                BubbleContourCandidate candidate,
                double offsetX,
                double offsetY,
                double quality
        ) {
            this.target = target;
            this.candidate = candidate;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.quality = quality;
        }
    }

    private static final class VoteBucket {

        private final long binX;
        private final long binY;

        private final Map<String, RawVote> bestVoteByOptionId =
                new HashMap<>();

        private VoteBucket(long binX, long binY) {
            this.binX = binX;
            this.binY = binY;
        }

        private void offer(RawVote vote) {
            String optionId = vote.target.getOptionId();

            RawVote previous =
                    bestVoteByOptionId.get(optionId);

            if (previous == null
                    || vote.quality > previous.quality
                    || vote.quality == previous.quality
                    && vote.candidate.getCandidateId()
                    < previous.candidate.getCandidateId()) {

                bestVoteByOptionId.put(optionId, vote);
            }
        }

        private int getVoteCount() {
            return bestVoteByOptionId.size();
        }

        private double getQualitySum() {
            double result = 0.0;

            for (RawVote vote
                    : bestVoteByOptionId.values()) {
                result += vote.quality;
            }

            return result;
        }

        private double medianOffsetX() {
            List<Double> values = new ArrayList<>();

            for (RawVote vote
                    : bestVoteByOptionId.values()) {
                values.add(vote.offsetX);
            }

            return medianStatic(values);
        }

        private double medianOffsetY() {
            List<Double> values = new ArrayList<>();

            for (RawVote vote
                    : bestVoteByOptionId.values()) {
                values.add(vote.offsetY);
            }

            return medianStatic(values);
        }

        private static double medianStatic(
                List<Double> source
        ) {
            if (source.isEmpty()) {
                return 0.0;
            }

            Collections.sort(source);

            int middle = source.size() / 2;

            if (source.size() % 2 == 1) {
                return source.get(middle);
            }

            return (
                    source.get(middle - 1)
                            + source.get(middle)
            ) / 2.0;
        }
    }

    private static final class SupportPair {

        private final ExpectedBubbleTarget target;
        private final BubbleContourCandidate candidate;
        private final double quality;

        private SupportPair(
                ExpectedBubbleTarget target,
                BubbleContourCandidate candidate,
                double quality
        ) {
            this.target = target;
            this.candidate = candidate;
            this.quality = quality;
        }
    }

    private static final class TranslationEvaluation {

        private final double refinedOffsetX;
        private final double refinedOffsetY;

        private final List<SupportPair> supportPairs;

        private final double medianResidual;
        private final double averageQuality;

        private TranslationEvaluation(
                double refinedOffsetX,
                double refinedOffsetY,
                List<SupportPair> supportPairs,
                double medianResidual,
                double averageQuality
        ) {
            this.refinedOffsetX = refinedOffsetX;
            this.refinedOffsetY = refinedOffsetY;
            this.supportPairs = supportPairs;
            this.medianResidual = medianResidual;
            this.averageQuality = averageQuality;
        }

        private static TranslationEvaluation empty(
                double offsetX,
                double offsetY
        ) {
            return new TranslationEvaluation(
                    offsetX,
                    offsetY,
                    Collections.<SupportPair>emptyList(),
                    0.0,
                    0.0
            );
        }
    }
}
