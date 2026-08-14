package com.example.leitorgabaritoomr.vision.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarkerDetectionResult {

    private final String detectorName;
    private final List<DetectedMarker> markers;
    private final int rejectedCandidates;
    private final long processingTimeNanos;

    public MarkerDetectionResult(
            String detectorName,
            List<DetectedMarker> markers,
            int rejectedCandidates,
            long processingTimeNanos
    ) {

        this.detectorName = detectorName;

        this.markers = Collections.unmodifiableList(
                new ArrayList<>(markers)
        );

        this.rejectedCandidates = rejectedCandidates;
        this.processingTimeNanos = processingTimeNanos;
    }

    public String getDetectorName() {
        return detectorName;
    }

    public List<DetectedMarker> getMarkers() {
        return markers;
    }

    public int getRejectedCandidates() {
        return rejectedCandidates;
    }

    public long getProcessingTimeNanos() {
        return processingTimeNanos;
    }

    public double getProcessingTimeMillis() {
        return processingTimeNanos / 1_000_000.0;
    }

    public boolean hasMarkers() {
        return !markers.isEmpty();
    }

    public int getMarkerCount() {
        return markers.size();
    }
}