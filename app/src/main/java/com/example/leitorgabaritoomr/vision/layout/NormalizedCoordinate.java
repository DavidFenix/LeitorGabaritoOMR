package com.example.leitorgabaritoomr.vision.layout;

import java.util.Locale;
import java.util.Objects;

/**
 * Representa uma posição independente da resolução da imagem.
 *
 * Os valores de X e Y ficam sempre no intervalo de 0.0 a 1.0.
 *
 * Exemplos:
 *
 * (0.0, 0.0) = canto superior esquerdo
 * (1.0, 0.0) = canto superior direito
 * (1.0, 1.0) = canto inferior direito
 * (0.0, 1.0) = canto inferior esquerdo
 * (0.5, 0.5) = centro
 */
public final class NormalizedCoordinate {

    private final double x;
    private final double y;

    public NormalizedCoordinate(
            double x,
            double y
    ) {
        validateValue("x", x);
        validateValue("y", y);

        this.x = x;
        this.y = y;
    }

    public static NormalizedCoordinate of(
            double x,
            double y
    ) {
        return new NormalizedCoordinate(x, y);
    }

    private void validateValue(
            String name,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " deve ser um número finito."
            );
        }

        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " deve estar entre 0.0 e 1.0."
            );
        }
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    /**
     * Converte X normalizado para a coordenada correspondente
     * em uma imagem.
     */
    public double toPixelX(int imageWidth) {
        if (imageWidth <= 0) {
            throw new IllegalArgumentException(
                    "A largura da imagem deve ser positiva."
            );
        }

        return x * (imageWidth - 1.0);
    }

    /**
     * Converte Y normalizado para a coordenada correspondente
     * em uma imagem.
     */
    public double toPixelY(int imageHeight) {
        if (imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "A altura da imagem deve ser positiva."
            );
        }

        return y * (imageHeight - 1.0);
    }

    public double distanceTo(
            NormalizedCoordinate other
    ) {
        if (other == null) {
            throw new IllegalArgumentException(
                    "A outra coordenada não pode ser nula."
            );
        }

        double deltaX = other.x - x;
        double deltaY = other.y - y;

        return Math.sqrt(
                deltaX * deltaX
                        + deltaY * deltaY
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof NormalizedCoordinate)) {
            return false;
        }

        NormalizedCoordinate other =
                (NormalizedCoordinate) object;

        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "(%.4f, %.4f)",
                x,
                y
        );
    }
}