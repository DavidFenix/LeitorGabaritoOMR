package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Produz associações preliminares e exclusivas entre os alvos
 * esperados pelo layout e os candidatos observados.
 *
 * Esta classe ainda não aceita a grade nem corrige o layout.
 * Sua responsabilidade termina ao escolher propostas locais
 * plausíveis, sem reutilizar alvos ou candidatos.
 */
public final class BubbleCandidateMatcher {

    private static final double POSITION_WEIGHT = 0.70;
    private static final double SIZE_WEIGHT = 0.25;
    private static final double SHAPE_WEIGHT = 0.05;

    /*
     * Dois contornos quase concêntricos e fortemente
     * sobrepostos representam a mesma bolha física.
     */
    private static final double
            PHYSICAL_DUPLICATE_CENTER_SCALE = 0.35;

    private static final double
            MINIMUM_PHYSICAL_DUPLICATE_OVERLAP = 0.45;

    private static final double
            MINIMUM_PHYSICAL_DUPLICATE_DISTANCE = 2.0;

    private final BubbleGridRegistrarConfig config;

    public BubbleCandidateMatcher(
            BubbleGridRegistrarConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuração é obrigatória."
            );
        }

        this.config = config;
    }

    public BubbleCandidateMatchingResult match(
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult
    ) {
        String validationError =
                validateInput(
                        targets,
                        extractionResult
                );

        if (validationError != null) {
            return BubbleCandidateMatchingResult
                    .failure(validationError);
        }

        List<BubbleContourCandidate> candidates =
                extractionResult.getCandidates();

        if (candidates.isEmpty()) {
            return BubbleCandidateMatchingResult
                    .success(
                            targets,
                            candidates,
                            Collections.emptyList()
                    );
        }

        List<CandidatePair> proposals =
                createProposals(
                        targets,
                        candidates
                );

        sortBestFirst(proposals);

        List<BubbleCandidateMatch> matches =
                chooseExclusiveMatches(
                        proposals
                );

        return BubbleCandidateMatchingResult
                .success(
                        targets,
                        candidates,
                        matches
                );
    }

    private String validateInput(
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult
    ) {
        if (targets == null
                || targets.isEmpty()) {

            return "A lista de alvos esperados está vazia.";
        }

        for (ExpectedBubbleTarget target
                : targets) {

            if (target == null) {
                return "A lista possui alvo esperado nulo.";
            }
        }

        if (extractionResult == null) {
            return "O resultado da extração é obrigatório.";
        }

        if (!extractionResult.isSuccess()) {
            return "A extração de candidatos falhou: "
                    + extractionResult.getMessage();
        }

        return null;
    }

    private List<CandidatePair> createProposals(
            List<ExpectedBubbleTarget> targets,
            List<BubbleContourCandidate> candidates
    ) {
        List<CandidatePair> proposals =
                new ArrayList<>();

        for (ExpectedBubbleTarget target
                : targets) {

            double searchRadiusX =
                    target.getExpectedWidth()
                            * config
                            .getSearchRadiusXScale();

            double searchRadiusY =
                    target.getExpectedHeight()
                            * config
                            .getSearchRadiusYScale();

            for (BubbleContourCandidate candidate
                    : candidates) {

                CandidatePair proposal =
                        createProposal(
                                target,
                                candidate,
                                searchRadiusX,
                                searchRadiusY
                        );

                if (proposal != null) {
                    proposals.add(proposal);
                }
            }
        }

        return proposals;
    }

    private CandidatePair createProposal(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate,
            double searchRadiusX,
            double searchRadiusY
    ) {
        double offsetX =
                candidate.getCenterX()
                        - target.getExpectedCenterX();

        double offsetY =
                candidate.getCenterY()
                        - target.getExpectedCenterY();

        double normalizedX =
                Math.abs(offsetX)
                        / searchRadiusX;

        double normalizedY =
                Math.abs(offsetY)
                        / searchRadiusY;

        double normalizedDistance =
                Math.hypot(
                        normalizedX,
                        normalizedY
                );

        /*
         * Janela elíptica. Um candidato fora dela não participa
         * da ordenação nem pode ser associado por acidente.
         */
        if (normalizedDistance > 1.0) {
            return null;
        }

        double positionScore =
                clamp01(
                        1.0 - normalizedDistance
                );

        double sizeScore =
                calculateSizeScore(
                        target,
                        candidate
                );

        double shapeScore =
                calculateShapeScore(
                        target,
                        candidate
                );

        double totalScore =
                clamp01(
                        positionScore
                                * POSITION_WEIGHT
                                + sizeScore
                                * SIZE_WEIGHT
                                + shapeScore
                                * SHAPE_WEIGHT
                );

        if (totalScore
                < config.getMinimumCandidateScore()) {

            return null;
        }

        BubbleCandidateMatch match =
                new BubbleCandidateMatch(
                        target,
                        candidate,
                        positionScore,
                        sizeScore,
                        shapeScore,
                        totalScore
                );

        return new CandidatePair(match);
    }

    private double calculateSizeScore(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate
    ) {
        PixelRectangle candidateBounds =
                candidate.getBounds();

        double widthSimilarity =
                ratioSimilarity(
                        target.getExpectedWidth(),
                        candidateBounds.getWidth()
                );

        double heightSimilarity =
                ratioSimilarity(
                        target.getExpectedHeight(),
                        candidateBounds.getHeight()
                );

        /*
         * A média geométrica pune quando somente uma das
         * dimensões é compatível.
         */
        return clamp01(
                Math.sqrt(
                        widthSimilarity
                                * heightSimilarity
                )
        );
    }

    private double calculateShapeScore(
            ExpectedBubbleTarget target,
            BubbleContourCandidate candidate
    ) {
        double expectedAspectRatio =
                target.getExpectedWidth()
                        / target.getExpectedHeight();

        double aspectSimilarity =
                ratioSimilarity(
                        expectedAspectRatio,
                        candidate.getAspectRatio()
                );

        /*
         * A retangularidade tem peso pequeno porque uma bolha
         * preenchida, incompleta ou impressa em outra cor pode
         * alterar muito sua área de contorno.
         */
        return clamp01(
                aspectSimilarity * 0.75
                        + candidate.getRectangularity()
                        * 0.25
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

    private void sortBestFirst(
            List<CandidatePair> proposals
    ) {
        Collections.sort(
                proposals,
                new Comparator<CandidatePair>() {
                    @Override
                    public int compare(
                            CandidatePair first,
                            CandidatePair second
                    ) {
                        BubbleCandidateMatch firstMatch =
                                first.match;

                        BubbleCandidateMatch secondMatch =
                                second.match;

                        int comparison =
                                Double.compare(
                                        secondMatch
                                                .getTotalScore(),
                                        firstMatch
                                                .getTotalScore()
                                );

                        if (comparison != 0) {
                            return comparison;
                        }

                        comparison =
                                Double.compare(
                                        secondMatch
                                                .getPositionScore(),
                                        firstMatch
                                                .getPositionScore()
                                );

                        if (comparison != 0) {
                            return comparison;
                        }

                        comparison =
                                Double.compare(
                                        secondMatch
                                                .getSizeScore(),
                                        firstMatch
                                                .getSizeScore()
                                );

                        if (comparison != 0) {
                            return comparison;
                        }

                        comparison =
                                firstMatch
                                .getTarget()
                                .getOptionId()
                                .compareTo(
                                        secondMatch
                                                .getTarget()
                                                .getOptionId()
                                );

                        if (comparison != 0) {
                            return comparison;
                        }

                        return Integer.compare(
                                firstMatch
                                        .getCandidate()
                                        .getCandidateId(),
                                secondMatch
                                        .getCandidate()
                                        .getCandidateId()
                        );
                    }
                }
        );
    }

    private List<BubbleCandidateMatch>
    chooseExclusiveMatches(
            List<CandidatePair> proposals
    ) {
        List<BubbleCandidateMatch> matches =
                new ArrayList<>();

        Set<String> usedOptionIds =
                new HashSet<>();

        Set<Integer> usedCandidateIds =
                new HashSet<>();

        List<BubbleContourCandidate>
                usedPhysicalCandidates =
                new ArrayList<>();

        for (CandidatePair proposal
                : proposals) {

            BubbleCandidateMatch match =
                    proposal.match;

            String optionId =
                    match.getTarget()
                            .getOptionId();

            int candidateId =
                    match.getCandidate()
                            .getCandidateId();

            if (usedOptionIds.contains(optionId)
                    || usedCandidateIds.contains(
                    candidateId
            )
                    || conflictsWithUsedPhysicalCandidate(
                    match.getCandidate(),
                    usedPhysicalCandidates
            )) {
                continue;
            }

            usedOptionIds.add(optionId);
            usedCandidateIds.add(candidateId);

            usedPhysicalCandidates.add(
                    match.getCandidate()
            );

            matches.add(match);
        }

        return matches;
    }

    private boolean conflictsWithUsedPhysicalCandidate(
            BubbleContourCandidate candidate,
            List<BubbleContourCandidate> usedCandidates
    ) {
        for (BubbleContourCandidate usedCandidate
                : usedCandidates) {

            if (representsSamePhysicalBubble(
                    candidate,
                    usedCandidate
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
        PixelRectangle firstBounds =
                first.getBounds();

        PixelRectangle secondBounds =
                second.getBounds();

        double referenceWidth =
                Math.min(
                        firstBounds.getWidth(),
                        secondBounds.getWidth()
                );

        double referenceHeight =
                Math.min(
                        firstBounds.getHeight(),
                        secondBounds.getHeight()
                );

        double maximumCenterOffsetX =
                Math.max(
                        MINIMUM_PHYSICAL_DUPLICATE_DISTANCE,
                        referenceWidth
                                * PHYSICAL_DUPLICATE_CENTER_SCALE
                );

        double maximumCenterOffsetY =
                Math.max(
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

        return calculateIntersectionOverSmallerArea(
                firstBounds,
                secondBounds
        ) >= MINIMUM_PHYSICAL_DUPLICATE_OVERLAP;
    }

    private double calculateIntersectionOverSmallerArea(
            PixelRectangle first,
            PixelRectangle second
    ) {
        int intersectionLeft =
                Math.max(
                        first.getLeft(),
                        second.getLeft()
                );

        int intersectionTop =
                Math.max(
                        first.getTop(),
                        second.getTop()
                );

        int intersectionRight =
                Math.min(
                        first.getRightExclusive(),
                        second.getRightExclusive()
                );

        int intersectionBottom =
                Math.min(
                        first.getBottomExclusive(),
                        second.getBottomExclusive()
                );

        int intersectionWidth =
                Math.max(
                        0,
                        intersectionRight
                                - intersectionLeft
                );

        int intersectionHeight =
                Math.max(
                        0,
                        intersectionBottom
                                - intersectionTop
                );

        double intersectionArea =
                intersectionWidth
                        * (double) intersectionHeight;

        double smallerArea =
                Math.min(
                        first.getArea(),
                        second.getArea()
                );

        if (smallerArea <= 0.0) {
            return 0.0;
        }

        return clamp01(
                intersectionArea
                        / smallerArea
        );
    }

    private double clamp01(
            double value
    ) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private static final class CandidatePair {

        private final BubbleCandidateMatch match;

        private CandidatePair(
                BubbleCandidateMatch match
        ) {
            this.match = match;
        }
    }
}
