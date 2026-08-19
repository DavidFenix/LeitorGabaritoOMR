package com.example.leitorgabaritoomr.vision.layout;

import java.util.Locale;
import java.util.Objects;

/**
 * Define uma alternativa dentro de uma questão.
 *
 * A alternativa não conhece pixels, milímetros, tamanho de papel
 * ou resolução de câmera. Sua posição e região de amostragem são
 * expressas em coordenadas normalizadas.
 */
public final class OmrOptionDefinition {

    private final String id;
    private final String label;

    private final NormalizedCoordinate center;

    /*
     * Metade da largura e da altura da região que futuramente
     * será analisada para decidir se a bolha está preenchida.
     */
    private final double samplingRadiusX;
    private final double samplingRadiusY;

    public OmrOptionDefinition(
            String id,
            String label,
            NormalizedCoordinate center,
            double samplingRadiusX,
            double samplingRadiusY
    ) {
        this.id = requireText("id", id);
        this.label = requireText("label", label);

        if (center == null) {
            throw new IllegalArgumentException(
                    "O centro da alternativa é obrigatório."
            );
        }

        validateRadius(
                "samplingRadiusX",
                samplingRadiusX
        );

        validateRadius(
                "samplingRadiusY",
                samplingRadiusY
        );

        validateSamplingRegion(
                center,
                samplingRadiusX,
                samplingRadiusY
        );

        this.center = center;
        this.samplingRadiusX = samplingRadiusX;
        this.samplingRadiusY = samplingRadiusY;
    }

    public static OmrOptionDefinition circular(
            String id,
            String label,
            NormalizedCoordinate center,
            double samplingRadius
    ) {
        return new OmrOptionDefinition(
                id,
                label,
                center,
                samplingRadius,
                samplingRadius
        );
    }

    private String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    private void validateRadius(
            String fieldName,
            double radius
    ) {
        if (!Double.isFinite(radius)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser um número finito."
            );
        }

        if (radius <= 0.0 || radius > 0.5) {
            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser maior que 0.0"
                            + " e menor ou igual a 0.5."
            );
        }
    }

    private void validateSamplingRegion(
            NormalizedCoordinate center,
            double radiusX,
            double radiusY
    ) {
        double left = center.getX() - radiusX;
        double right = center.getX() + radiusX;

        double top = center.getY() - radiusY;
        double bottom = center.getY() + radiusY;

        if (left < 0.0
                || right > 1.0
                || top < 0.0
                || bottom > 1.0) {

            throw new IllegalArgumentException(
                    "A região de amostragem da alternativa"
                            + " deve permanecer dentro do layout."
            );
        }
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public NormalizedCoordinate getCenter() {
        return center;
    }

    public double getSamplingRadiusX() {
        return samplingRadiusX;
    }

    public double getSamplingRadiusY() {
        return samplingRadiusY;
    }

    public double getLeft() {
        return center.getX() - samplingRadiusX;
    }

    public double getTop() {
        return center.getY() - samplingRadiusY;
    }

    public double getRight() {
        return center.getX() + samplingRadiusX;
    }

    public double getBottom() {
        return center.getY() + samplingRadiusY;
    }

    public double getNormalizedWidth() {
        return samplingRadiusX * 2.0;
    }

    public double getNormalizedHeight() {
        return samplingRadiusY * 2.0;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof OmrOptionDefinition)) {
            return false;
        }

        OmrOptionDefinition other =
                (OmrOptionDefinition) object;

        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[%s] centro=%s raio=(%.4f, %.4f)",
                id,
                label,
                center,
                samplingRadiusX,
                samplingRadiusY
        );
    }
}