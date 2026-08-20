package com.example.leitorgabaritoomr.vision.geometry;

import java.util.Locale;
import java.util.Objects;

/**
 * Retângulo imutável em coordenadas inteiras de pixels.
 *
 * Não depende do OpenCV para que possa ser usado pelo cálculo,
 * pelos testes e pelo Laboratório OMR.
 */
public final class PixelRectangle {

    private final int left;
    private final int top;
    private final int width;
    private final int height;

    public PixelRectangle(
            int left,
            int top,
            int width,
            int height
    ) {
        if (left < 0 || top < 0) {
            throw new IllegalArgumentException(
                    "left e top não podem ser negativos."
            );
        }

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "width e height devem ser positivos."
            );
        }

        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRightExclusive() {
        return left + width;
    }

    public int getBottomExclusive() {
        return top + height;
    }

    public int getRightInclusive() {
        return getRightExclusive() - 1;
    }

    public int getBottomInclusive() {
        return getBottomExclusive() - 1;
    }

    public int getCenterX() {
        return left + width / 2;
    }

    public int getCenterY() {
        return top + height / 2;
    }

    public int getArea() {
        return width * height;
    }

    public boolean contains(
            PixelRectangle other
    ) {
        if (other == null) {
            return false;
        }

        return other.getLeft() >= getLeft()
                && other.getTop() >= getTop()
                && other.getRightExclusive()
                <= getRightExclusive()
                && other.getBottomExclusive()
                <= getBottomExclusive();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof PixelRectangle)) {
            return false;
        }

        PixelRectangle other =
                (PixelRectangle) object;

        return left == other.left
                && top == other.top
                && width == other.width
                && height == other.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                left,
                top,
                width,
                height
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "PixelRectangle{x=%d, y=%d, width=%d, height=%d}",
                left,
                top,
                width,
                height
        );
    }
}