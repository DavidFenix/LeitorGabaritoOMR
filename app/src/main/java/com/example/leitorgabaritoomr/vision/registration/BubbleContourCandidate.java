package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;

import org.opencv.core.Point;

import java.util.Locale;

/**
 * Contorno candidato a bolha encontrado na imagem normalizada.
 *
 * Os pontos armazenados são os mesmos que poderão ser exibidos
 * pelo Laboratório OMR.
 */
public final class BubbleContourCandidate {

    private final int candidateId;

    private final PixelRectangle bounds;

    private final double centerX;
    private final double centerY;

    private final double contourArea;
    private final double perimeter;
    private final double rectangularity;
    private final double aspectRatio;

    /*
     * Cópia imutável dos pontos do contorno aproximado.
     */
    private final double[] contourX;
    private final double[] contourY;

    public BubbleContourCandidate(
            int candidateId,
            PixelRectangle bounds,
            double centerX,
            double centerY,
            double contourArea,
            double perimeter,
            double rectangularity,
            double aspectRatio,
            Point[] contourPoints
    ) {
        if (candidateId < 0) {
            throw new IllegalArgumentException(
                    "candidateId não pode ser negativo."
            );
        }

        if (bounds == null) {
            throw new IllegalArgumentException(
                    "Os limites do candidato são obrigatórios."
            );
        }

        validateFinite(
                "centerX",
                centerX
        );

        validateFinite(
                "centerY",
                centerY
        );

        if (contourArea <= 0.0
                || !Double.isFinite(contourArea)) {

            throw new IllegalArgumentException(
                    "contourArea deve ser positivo."
            );
        }

        if (perimeter <= 0.0
                || !Double.isFinite(perimeter)) {

            throw new IllegalArgumentException(
                    "perimeter deve ser positivo."
            );
        }

        if (!Double.isFinite(rectangularity)
                || rectangularity < 0.0
                || rectangularity > 1.0) {

            throw new IllegalArgumentException(
                    "rectangularity deve estar"
                            + " entre 0.0 e 1.0."
            );
        }

        if (!Double.isFinite(aspectRatio)
                || aspectRatio <= 0.0) {

            throw new IllegalArgumentException(
                    "aspectRatio deve ser positivo."
            );
        }

        if (contourPoints == null
                || contourPoints.length < 2) {

            throw new IllegalArgumentException(
                    "O contorno precisa de pelo menos"
                            + " dois pontos."
            );
        }

        this.candidateId = candidateId;
        this.bounds = bounds;

        this.centerX = centerX;
        this.centerY = centerY;

        this.contourArea = contourArea;
        this.perimeter = perimeter;
        this.rectangularity = rectangularity;
        this.aspectRatio = aspectRatio;

        this.contourX =
                new double[contourPoints.length];

        this.contourY =
                new double[contourPoints.length];

        for (int index = 0;
             index < contourPoints.length;
             index++) {

            Point point =
                    contourPoints[index];

            if (point == null
                    || !Double.isFinite(point.x)
                    || !Double.isFinite(point.y)) {

                throw new IllegalArgumentException(
                        "O contorno possui ponto inválido."
                );
            }

            contourX[index] = point.x;
            contourY[index] = point.y;
        }
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser finito."
            );
        }
    }

    public int getCandidateId() {
        return candidateId;
    }

    public PixelRectangle getBounds() {
        return bounds;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getContourArea() {
        return contourArea;
    }

    public double getPerimeter() {
        return perimeter;
    }

    public double getRectangularity() {
        return rectangularity;
    }

    public double getAspectRatio() {
        return aspectRatio;
    }

    public int getContourPointCount() {
        return contourX.length;
    }

    /**
     * Retorna uma cópia dos pontos.
     *
     * Alterações realizadas pelo chamador não modificam
     * o candidato armazenado.
     */
    public Point[] copyContourPoints() {
        Point[] copy =
                new Point[contourX.length];

        for (int index = 0;
             index < contourX.length;
             index++) {

            copy[index] =
                    new Point(
                            contourX[index],
                            contourY[index]
                    );
        }

        return copy;
    }

    public double distanceTo(
            double x,
            double y
    ) {
        return Math.hypot(
                centerX - x,
                centerY - y
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "candidate-%d center=(%.2f, %.2f)"
                        + " size=%dx%d rectangularity=%.3f"
                        + " aspect=%.3f",
                candidateId,
                centerX,
                centerY,
                bounds.getWidth(),
                bounds.getHeight(),
                rectangularity,
                aspectRatio
        );
    }
}