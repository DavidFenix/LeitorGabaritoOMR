package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;

/**
 * Decide se dois contornos representam a mesma bolha fisica.
 *
 * Uma bolha frequentemente produz um contorno interno e outro
 * externo. Esses contornos podem ter IDs diferentes, mas nao podem
 * sustentar duas alternativas distintas.
 */
public final class BubblePhysicalCandidateEquivalence {

    private static final double CENTER_SCALE = 0.35;
    private static final double MINIMUM_OVERLAP = 0.45;
    private static final double MINIMUM_CENTER_DISTANCE = 2.0;

    public boolean representsSamePhysicalBubble(
            BubbleContourCandidate first,
            BubbleContourCandidate second
    ) {
        if (first == null || second == null) {
            return false;
        }

        if (first.getCandidateId()
                == second.getCandidateId()) {
            return true;
        }

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
                        MINIMUM_CENTER_DISTANCE,
                        referenceWidth * CENTER_SCALE
                );

        double maximumCenterOffsetY =
                Math.max(
                        MINIMUM_CENTER_DISTANCE,
                        referenceHeight * CENTER_SCALE
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
        ) >= MINIMUM_OVERLAP;
    }

    public boolean conflictsWithAny(
            BubbleContourCandidate candidate,
            Iterable<BubbleContourCandidate> usedCandidates
    ) {
        if (candidate == null
                || usedCandidates == null) {
            return false;
        }

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
                intersectionArea / smallerArea
        );
    }

    private double clamp01(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}
