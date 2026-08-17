package com.example.leitorgabaritoomr.vision.geometry;

import com.example.leitorgabaritoomr.vision.model.DetectedMarker;

import org.opencv.core.Point;

public final class ResolvedMarker {

    private final CornerRole role;
    private final DetectedMarker marker;

    public ResolvedMarker(
            CornerRole role,
            DetectedMarker marker
    ) {

        if (role == null) {
            throw new IllegalArgumentException(
                    "O papel geométrico é obrigatório."
            );
        }

        if (marker == null) {
            throw new IllegalArgumentException(
                    "O marcador é obrigatório."
            );
        }

        this.role = role;
        this.marker = marker;
    }

    public CornerRole getRole() {
        return role;
    }

    public DetectedMarker getMarker() {
        return marker;
    }

    public Point getCenter() {
        return marker.getCenter();
    }
}