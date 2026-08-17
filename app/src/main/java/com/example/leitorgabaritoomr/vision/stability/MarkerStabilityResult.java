package com.example.leitorgabaritoomr.vision.stability;

import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;

public final class MarkerStabilityResult {

    private final MarkerStabilityState state;

    private final ResolvedMarkerSet markerSet;

    private final int consistentFrames;
    private final int requiredFrames;
    private final int missedFrames;

    private final double normalizedShapeDistance;
    private final double regionAreaChangeRatio;

    public MarkerStabilityResult(
            MarkerStabilityState state,
            ResolvedMarkerSet markerSet,
            int consistentFrames,
            int requiredFrames,
            int missedFrames,
            double normalizedShapeDistance,
            double regionAreaChangeRatio
    ) {

        this.state = state;
        this.markerSet = markerSet;
        this.consistentFrames = consistentFrames;
        this.requiredFrames = requiredFrames;
        this.missedFrames = missedFrames;
        this.normalizedShapeDistance =
                normalizedShapeDistance;
        this.regionAreaChangeRatio =
                regionAreaChangeRatio;
    }

    public MarkerStabilityState getState() {
        return state;
    }

    public ResolvedMarkerSet getMarkerSet() {
        return markerSet;
    }

    public int getConsistentFrames() {
        return consistentFrames;
    }

    public int getRequiredFrames() {
        return requiredFrames;
    }

    public int getMissedFrames() {
        return missedFrames;
    }

    public double getNormalizedShapeDistance() {
        return normalizedShapeDistance;
    }

    public double getRegionAreaChangeRatio() {
        return regionAreaChangeRatio;
    }

    public boolean isStable() {

        return state == MarkerStabilityState.STABLE
                || state
                == MarkerStabilityState.HELD_STABLE;
    }
}