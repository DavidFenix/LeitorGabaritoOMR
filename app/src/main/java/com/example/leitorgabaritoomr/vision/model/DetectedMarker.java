package com.example.leitorgabaritoomr.vision.model;

import org.opencv.core.Point;

import java.util.Arrays;

public final class DetectedMarker {

    private final MarkerType type;
    private final Integer code;
    private final Point[] corners;
    private final Point center;
    private final double confidence;

    public DetectedMarker(
            MarkerType type,
            Integer code,
            Point[] corners,
            double confidence
    ) {

        if (type == null) {
            throw new IllegalArgumentException(
                    "O tipo do marcador é obrigatório."
            );
        }

        if (corners == null || corners.length != 4) {
            throw new IllegalArgumentException(
                    "O marcador deve possuir exatamente quatro cantos."
            );
        }

        this.type = type;
        this.code = code;
        this.corners = copiarPontos(corners);
        this.center = calcularCentro(this.corners);
        this.confidence = confidence;
    }

    public MarkerType getType() {
        return type;
    }

    public Integer getCode() {
        return code;
    }

    public Point[] getCorners() {
        return copiarPontos(corners);
    }

    public Point getCenter() {
        return new Point(center.x, center.y);
    }

    public double getConfidence() {
        return confidence;
    }

    private static Point calcularCentro(Point[] corners) {

        double somaX = 0;
        double somaY = 0;

        for (Point point : corners) {
            somaX += point.x;
            somaY += point.y;
        }

        return new Point(
                somaX / corners.length,
                somaY / corners.length
        );
    }

    private static Point[] copiarPontos(Point[] source) {

        return Arrays.stream(source)
                .map(point -> new Point(point.x, point.y))
                .toArray(Point[]::new);
    }
}