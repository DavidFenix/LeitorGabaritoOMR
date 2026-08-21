package com.example.leitorgabaritoomr.vision.registration;

import java.util.Locale;
import java.util.Objects;

/**
 * Transformação afim imutável em coordenadas de pixels.
 *
 * x' = m00 * x + m01 * y + m02
 * y' = m10 * x + m11 * y + m12
 *
 * Poderá representar:
 *
 * - translação;
 * - escala;
 * - rotação;
 * - pequena inclinação residual.
 */
public final class PixelAffineTransform {

    private static final double MINIMUM_DETERMINANT =
            0.000000001;

    private final double m00;
    private final double m01;
    private final double m02;

    private final double m10;
    private final double m11;
    private final double m12;

    public PixelAffineTransform(
            double m00,
            double m01,
            double m02,
            double m10,
            double m11,
            double m12
    ) {
        validateFinite("m00", m00);
        validateFinite("m01", m01);
        validateFinite("m02", m02);
        validateFinite("m10", m10);
        validateFinite("m11", m11);
        validateFinite("m12", m12);

        double determinant =
                m00 * m11
                        - m01 * m10;

        if (Math.abs(determinant)
                < MINIMUM_DETERMINANT) {

            throw new IllegalArgumentException(
                    "A transformação afim é degenerada."
            );
        }

        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;

        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
    }

    public static PixelAffineTransform identity() {
        return new PixelAffineTransform(
                1.0,
                0.0,
                0.0,
                0.0,
                1.0,
                0.0
        );
    }

    public static PixelAffineTransform translation(
            double deltaX,
            double deltaY
    ) {
        return new PixelAffineTransform(
                1.0,
                0.0,
                deltaX,
                0.0,
                1.0,
                deltaY
        );
    }

    public static PixelAffineTransform
    scaleAndTranslation(
            double scaleX,
            double scaleY,
            double translationX,
            double translationY
    ) {
        if (scaleX <= 0.0 || scaleY <= 0.0) {
            throw new IllegalArgumentException(
                    "As escalas devem ser positivas."
            );
        }

        return new PixelAffineTransform(
                scaleX,
                0.0,
                translationX,
                0.0,
                scaleY,
                translationY
        );
    }

    public static PixelAffineTransform
    scaleRotationAndTranslation(
            double scale,
            double rotationRadians,
            double translationX,
            double translationY
    ) {
        if (!Double.isFinite(scale)
                || scale <= 0.0) {

            throw new IllegalArgumentException(
                    "A escala deve ser positiva."
            );
        }

        validateStaticFinite(
                "rotationRadians",
                rotationRadians
        );

        double cosine =
                Math.cos(rotationRadians);

        double sine =
                Math.sin(rotationRadians);

        return new PixelAffineTransform(
                scale * cosine,
                -scale * sine,
                translationX,
                scale * sine,
                scale * cosine,
                translationY
        );
    }

    public double transformX(
            double x,
            double y
    ) {
        return m00 * x
                + m01 * y
                + m02;
    }

    public double transformY(
            double x,
            double y
    ) {
        return m10 * x
                + m11 * y
                + m12;
    }

    /**
     * Retorna uma transformação que aplica esta transformação
     * primeiro e a transformação next em seguida.
     */
    public PixelAffineTransform then(
            PixelAffineTransform next
    ) {
        if (next == null) {
            throw new IllegalArgumentException(
                    "A próxima transformação é obrigatória."
            );
        }

        return new PixelAffineTransform(
                next.m00 * m00
                        + next.m01 * m10,

                next.m00 * m01
                        + next.m01 * m11,

                next.m00 * m02
                        + next.m01 * m12
                        + next.m02,

                next.m10 * m00
                        + next.m11 * m10,

                next.m10 * m01
                        + next.m11 * m11,

                next.m10 * m02
                        + next.m11 * m12
                        + next.m12
        );
    }

    public PixelAffineTransform inverse() {
        double determinant =
                getDeterminant();

        if (Math.abs(determinant)
                < MINIMUM_DETERMINANT) {

            throw new IllegalStateException(
                    "A transformação não pode ser invertida."
            );
        }

        double inverse00 =
                m11 / determinant;

        double inverse01 =
                -m01 / determinant;

        double inverse10 =
                -m10 / determinant;

        double inverse11 =
                m00 / determinant;

        double inverse02 =
                -(
                        inverse00 * m02
                                + inverse01 * m12
                );

        double inverse12 =
                -(
                        inverse10 * m02
                                + inverse11 * m12
                );

        return new PixelAffineTransform(
                inverse00,
                inverse01,
                inverse02,
                inverse10,
                inverse11,
                inverse12
        );
    }

    public double getDeterminant() {
        return m00 * m11
                - m01 * m10;
    }

    public double getApproximateScaleX() {
        return Math.hypot(
                m00,
                m10
        );
    }

    public double getApproximateScaleY() {
        return Math.hypot(
                m01,
                m11
        );
    }

    public double getApproximateRotationRadians() {
        return Math.atan2(
                m10,
                m00
        );
    }

    public double getM00() {
        return m00;
    }

    public double getM01() {
        return m01;
    }

    public double getM02() {
        return m02;
    }

    public double getM10() {
        return m10;
    }

    public double getM11() {
        return m11;
    }

    public double getM12() {
        return m12;
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        validateStaticFinite(
                fieldName,
                value
        );
    }

    private static void validateStaticFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser um número finito."
            );
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object
                instanceof PixelAffineTransform)) {

            return false;
        }

        PixelAffineTransform other =
                (PixelAffineTransform) object;

        return Double.compare(m00, other.m00) == 0
                && Double.compare(m01, other.m01) == 0
                && Double.compare(m02, other.m02) == 0
                && Double.compare(m10, other.m10) == 0
                && Double.compare(m11, other.m11) == 0
                && Double.compare(m12, other.m12) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                m00,
                m01,
                m02,
                m10,
                m11,
                m12
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "[[%.6f, %.6f, %.3f],"
                        + " [%.6f, %.6f, %.3f]]",
                m00,
                m01,
                m02,
                m10,
                m11,
                m12
        );
    }
}