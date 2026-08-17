package com.example.leitorgabaritoomr.vision.stability;

import com.example.leitorgabaritoomr.vision.geometry.CornerRole;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;

import org.opencv.core.Point;

public final class MarkerSetStabilizer {

    private final MarkerSetStabilizerConfig config;

    private ResolvedMarkerSet referenceSet;

    private int consistentFrames = 0;
    private int missedFrames = 0;

    private boolean stable = false;

    public MarkerSetStabilizer() {

        this(
                MarkerSetStabilizerConfig
                        .developmentDefaults()
        );
    }

    public MarkerSetStabilizer(
            MarkerSetStabilizerConfig config
    ) {

        if (config == null) {

            throw new IllegalArgumentException(
                    "A configuração do estabilizador é obrigatória."
            );
        }

        this.config = config;
    }

    public MarkerStabilityResult update(
            MarkerSetResolutionResult resolutionResult
    ) {

        if (resolutionResult == null
                || !resolutionResult.isAccepted()
                || resolutionResult.getMarkerSet() == null) {

            return processMissedFrame();
        }

        ResolvedMarkerSet currentSet =
                resolutionResult.getMarkerSet();

        if (referenceSet == null) {

            referenceSet = currentSet;
            consistentFrames = 1;
            missedFrames = 0;
            stable = false;

            return createResult(
                    MarkerStabilityState.ACCUMULATING,
                    0,
                    0
            );
        }

        double shapeDistance =
                calculateNormalizedShapeDistance(
                        referenceSet,
                        currentSet
                );

        double areaChangeRatio =
                calculateAreaChangeRatio(
                        referenceSet.getRegionAreaRatio(),
                        currentSet.getRegionAreaRatio()
                );

        boolean compatible =
                shapeDistance
                        <= config
                        .getMaximumNormalizedShapeDistance()
                        && areaChangeRatio
                        <= config
                        .getMaximumRegionAreaChangeRatio();

        if (!compatible) {

            referenceSet = currentSet;
            consistentFrames = 1;
            missedFrames = 0;
            stable = false;

            return createResult(
                    MarkerStabilityState.ACCUMULATING,
                    shapeDistance,
                    areaChangeRatio
            );
        }

        referenceSet = currentSet;

        consistentFrames++;
        missedFrames = 0;

        if (consistentFrames
                >= config.getRequiredConsistentFrames()) {

            stable = true;

            return createResult(
                    MarkerStabilityState.STABLE,
                    shapeDistance,
                    areaChangeRatio
            );
        }

        return createResult(
                MarkerStabilityState.ACCUMULATING,
                shapeDistance,
                areaChangeRatio
        );
    }

    private MarkerStabilityResult processMissedFrame() {

        if (referenceSet == null) {

            return createResult(
                    MarkerStabilityState.SEARCHING,
                    0,
                    0
            );
        }

        missedFrames++;

        if (missedFrames
                <= config.getMaximumMissedFrames()) {

            if (stable) {

                return createResult(
                        MarkerStabilityState.HELD_STABLE,
                        0,
                        0
                );
            }

            return createResult(
                    MarkerStabilityState.ACCUMULATING,
                    0,
                    0
            );
        }

        reset();

        return createResult(
                MarkerStabilityState.LOST,
                0,
                0
        );
    }

    private double calculateNormalizedShapeDistance(
            ResolvedMarkerSet first,
            ResolvedMarkerSet second
    ) {

        NormalizedPoints firstPoints =
                normalize(first);

        NormalizedPoints secondPoints =
                normalize(second);

        if (!firstPoints.valid
                || !secondPoints.valid) {

            return Double.MAX_VALUE;
        }

        double totalDistance = 0;

        for (CornerRole role
                : CornerRole.values()) {

            Point firstPoint =
                    firstPoints.get(role);

            Point secondPoint =
                    secondPoints.get(role);

            totalDistance +=
                    Math.hypot(
                            firstPoint.x
                                    - secondPoint.x,
                            firstPoint.y
                                    - secondPoint.y
                    );
        }

        return totalDistance
                / CornerRole.values().length;
    }

    private NormalizedPoints normalize(
            ResolvedMarkerSet markerSet
    ) {

        double centerX = 0;
        double centerY = 0;

        for (CornerRole role
                : CornerRole.values()) {

            Point point =
                    markerSet
                            .get(role)
                            .getCenter();

            centerX += point.x;
            centerY += point.y;
        }

        centerX /= CornerRole.values().length;
        centerY /= CornerRole.values().length;

        double scale = 0;

        for (CornerRole role
                : CornerRole.values()) {

            Point point =
                    markerSet
                            .get(role)
                            .getCenter();

            scale +=
                    Math.hypot(
                            point.x - centerX,
                            point.y - centerY
                    );
        }

        scale /= CornerRole.values().length;

        if (scale <= 0) {
            return NormalizedPoints.invalid();
        }

        NormalizedPoints result =
                new NormalizedPoints(true);

        for (CornerRole role
                : CornerRole.values()) {

            Point point =
                    markerSet
                            .get(role)
                            .getCenter();

            result.set(
                    role,
                    new Point(
                            (point.x - centerX)
                                    / scale,
                            (point.y - centerY)
                                    / scale
                    )
            );
        }

        return result;
    }

    private double calculateAreaChangeRatio(
            double first,
            double second
    ) {

        double maximum =
                Math.max(first, second);

        if (maximum <= 0) {
            return 0;
        }

        return Math.abs(first - second)
                / maximum;
    }

    private MarkerStabilityResult createResult(
            MarkerStabilityState state,
            double shapeDistance,
            double areaChangeRatio
    ) {

        return new MarkerStabilityResult(
                state,
                referenceSet,
                consistentFrames,
                config.getRequiredConsistentFrames(),
                missedFrames,
                shapeDistance,
                areaChangeRatio
        );
    }

    public void reset() {

        referenceSet = null;
        consistentFrames = 0;
        missedFrames = 0;
        stable = false;
    }

    private static final class NormalizedPoints {

        private final Point[] points =
                new Point[
                        CornerRole.values().length
                        ];

        private final boolean valid;

        private NormalizedPoints(
                boolean valid
        ) {

            this.valid = valid;
        }

        private static NormalizedPoints invalid() {
            return new NormalizedPoints(false);
        }

        private void set(
                CornerRole role,
                Point point
        ) {

            points[role.ordinal()] = point;
        }

        private Point get(
                CornerRole role
        ) {

            return points[role.ordinal()];
        }
    }
}