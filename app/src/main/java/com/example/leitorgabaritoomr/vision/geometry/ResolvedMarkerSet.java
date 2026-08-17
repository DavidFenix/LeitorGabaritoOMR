package com.example.leitorgabaritoomr.vision.geometry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ResolvedMarkerSet {

    private final Map<CornerRole, ResolvedMarker> markers;

    private final double confidence;
    private final double regionAreaRatio;
    private final double sizeSimilarity;
    private final double containmentRatio;

    public ResolvedMarkerSet(
            Map<CornerRole, ResolvedMarker> markers,
            double confidence,
            double regionAreaRatio,
            double sizeSimilarity,
            double containmentRatio
    ) {

        if (markers == null
                || markers.size() != 4) {

            throw new IllegalArgumentException(
                    "O conjunto deve possuir quatro marcadores."
            );
        }

        EnumMap<CornerRole, ResolvedMarker> copy =
                new EnumMap<>(CornerRole.class);

        copy.putAll(markers);

        for (CornerRole role : CornerRole.values()) {

            if (!copy.containsKey(role)) {

                throw new IllegalArgumentException(
                        "Marcador ausente: " + role
                );
            }
        }

        this.markers =
                Collections.unmodifiableMap(copy);

        this.confidence = confidence;
        this.regionAreaRatio = regionAreaRatio;
        this.sizeSimilarity = sizeSimilarity;
        this.containmentRatio = containmentRatio;
    }

    public ResolvedMarker get(
            CornerRole role
    ) {

        return markers.get(role);
    }

    public Map<CornerRole, ResolvedMarker> getMarkers() {
        return markers;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getRegionAreaRatio() {
        return regionAreaRatio;
    }

    public double getSizeSimilarity() {
        return sizeSimilarity;
    }

    public double getContainmentRatio() {
        return containmentRatio;
    }
}