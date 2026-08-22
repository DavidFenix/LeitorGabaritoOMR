package com.example.leitorgabaritoomr.vision.image;

import java.util.Locale;

/**
 * Fotografia imutavel de uma imagem de oito bits em escala de
 * cinza.
 *
 * A classe nao depende de Android, camera ou OpenCV. O medidor OMR
 * podera, portanto, ser testado com vetores de pixels artificiais e
 * resultados conhecidos.
 *
 * Os pixels sao armazenados em ordem de linhas:
 *
 * index = y * width + x
 *
 * Embora o armazenamento use byte para consumir um byte por pixel,
 * todas as leituras publicas retornam valores inteiros sem sinal no
 * intervalo de 0 a 255.
 */
public final class GrayImageBuffer {

    private final int width;
    private final int height;
    private final int pixelCount;

    private final byte[] pixels;

    private final int minimumIntensity;
    private final int maximumIntensity;
    private final double meanIntensity;

    /**
     * Cria uma imagem defensivamente imutavel.
     *
     * O vetor recebido e copiado. Alteracoes posteriores no vetor
     * original nao modificam esta instancia.
     */
    public GrayImageBuffer(
            int width,
            int height,
            byte[] pixels
    ) {
        this(
                width,
                height,
                pixels,
                true
        );
    }

    /**
     * Recebe internamente a propriedade de um vetor recem-criado.
     *
     * Este metodo possui acesso de pacote de proposito. O adaptador
     * OpenCV o usara depois de preencher um vetor que nao sera mais
     * exposto nem alterado. Dessa forma, a conversao do Mat exige
     * somente uma transferencia dos pixels.
     */
    static GrayImageBuffer fromOwnedPixels(
            int width,
            int height,
            byte[] ownedPixels
    ) {
        return new GrayImageBuffer(
                width,
                height,
                ownedPixels,
                false
        );
    }

    private GrayImageBuffer(
            int width,
            int height,
            byte[] sourcePixels,
            boolean copyPixels
    ) {
        int expectedPixelCount =
                calculatePixelCount(
                        width,
                        height
                );

        if (sourcePixels == null) {
            throw new IllegalArgumentException(
                    "O vetor de pixels e obrigatorio."
            );
        }

        if (sourcePixels.length
                != expectedPixelCount) {

            throw new IllegalArgumentException(
                    "A imagem "
                            + width
                            + "x"
                            + height
                            + " exige "
                            + expectedPixelCount
                            + " pixels, mas recebeu "
                            + sourcePixels.length
                            + "."
            );
        }

        this.width = width;
        this.height = height;
        this.pixelCount = expectedPixelCount;

        this.pixels = copyPixels
                ? sourcePixels.clone()
                : sourcePixels;

        Statistics statistics =
                calculateStatistics(this.pixels);

        this.minimumIntensity =
                statistics.minimumIntensity;

        this.maximumIntensity =
                statistics.maximumIntensity;

        this.meanIntensity =
                statistics.meanIntensity;
    }

    private int calculatePixelCount(
            int width,
            int height
    ) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "As dimensoes da imagem devem ser positivas."
            );
        }

        long count =
                (long) width * (long) height;

        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "A imagem possui pixels demais: "
                            + count
            );
        }

        return (int) count;
    }

    private Statistics calculateStatistics(
            byte[] source
    ) {
        int minimum = 255;
        int maximum = 0;
        long sum = 0L;

        for (byte pixel : source) {
            int intensity =
                    toUnsignedIntensity(pixel);

            minimum = Math.min(
                    minimum,
                    intensity
            );

            maximum = Math.max(
                    maximum,
                    intensity
            );

            sum += intensity;
        }

        return new Statistics(
                minimum,
                maximum,
                sum / (double) source.length
        );
    }

    private int toUnsignedIntensity(byte pixel) {
        return pixel & 0xFF;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPixelCount() {
        return pixelCount;
    }

    public boolean hasDimensions(
            int expectedWidth,
            int expectedHeight
    ) {
        return width == expectedWidth
                && height == expectedHeight;
    }

    public boolean isInside(
            int pixelX,
            int pixelY
    ) {
        return pixelX >= 0
                && pixelY >= 0
                && pixelX < width
                && pixelY < height;
    }

    public int getRowOffset(int pixelY) {
        validateRow(pixelY);

        return pixelY * width;
    }

    public int indexOf(
            int pixelX,
            int pixelY
    ) {
        validateCoordinates(
                pixelX,
                pixelY
        );

        return pixelY * width + pixelX;
    }

    public int getIntensity(
            int pixelX,
            int pixelY
    ) {
        return getIntensityAtIndex(
                indexOf(pixelX, pixelY)
        );
    }

    /**
     * Leitura linear eficiente para os lacos internos do medidor.
     */
    public int getIntensityAtIndex(int pixelIndex) {
        validatePixelIndex(pixelIndex);

        return toUnsignedIntensity(
                pixels[pixelIndex]
        );
    }

    public byte[] copyPixels() {
        return pixels.clone();
    }

    public int getMinimumIntensity() {
        return minimumIntensity;
    }

    public int getMaximumIntensity() {
        return maximumIntensity;
    }

    public double getMeanIntensity() {
        return meanIntensity;
    }

    private void validateRow(int pixelY) {
        if (pixelY < 0 || pixelY >= height) {
            throw new IndexOutOfBoundsException(
                    "pixelY fora da imagem: "
                            + pixelY
            );
        }
    }

    private void validateCoordinates(
            int pixelX,
            int pixelY
    ) {
        if (!isInside(pixelX, pixelY)) {
            throw new IndexOutOfBoundsException(
                    "Pixel fora da imagem: ("
                            + pixelX
                            + ", "
                            + pixelY
                            + ") em "
                            + width
                            + "x"
                            + height
            );
        }
    }

    private void validatePixelIndex(int pixelIndex) {
        if (pixelIndex < 0
                || pixelIndex >= pixelCount) {

            throw new IndexOutOfBoundsException(
                    "Indice de pixel fora da imagem: "
                            + pixelIndex
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "GrayImageBuffer{%dx%d pixels=%d"
                        + " min=%d max=%d mean=%.2f}",
                width,
                height,
                pixelCount,
                minimumIntensity,
                maximumIntensity,
                meanIntensity
        );
    }

    private static final class Statistics {

        private final int minimumIntensity;
        private final int maximumIntensity;
        private final double meanIntensity;

        private Statistics(
                int minimumIntensity,
                int maximumIntensity,
                double meanIntensity
        ) {
            this.minimumIntensity =
                    minimumIntensity;

            this.maximumIntensity =
                    maximumIntensity;

            this.meanIntensity =
                    meanIntensity;
        }
    }
}
