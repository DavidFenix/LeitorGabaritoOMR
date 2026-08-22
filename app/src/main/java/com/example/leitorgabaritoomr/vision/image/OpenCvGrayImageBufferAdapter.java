package com.example.leitorgabaritoomr.vision.image;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Converte uma Mat OpenCV de um canal e oito bits em um
 * GrayImageBuffer puro e imutavel.
 *
 * Esta e a fronteira unica entre o OpenCV e a nova medicao OMR.
 * Depois da conversao, nenhum calculo de bolha precisa chamar
 * Mat.get() ou depender de tipos nativos.
 *
 * A matriz normal produzida pelo cvtColor e continua. Nesse caso,
 * todos os pixels sao transferidos em uma unica chamada Mat.get().
 * Submatrizes nao continuas tambem sao aceitas por meio de um
 * fallback seguro que copia uma linha por chamada.
 */
public final class OpenCvGrayImageBufferAdapter {

    public GrayImageBuffer copyFrom(Mat grayImage) {
        validateInput(grayImage);

        int width = grayImage.cols();
        int height = grayImage.rows();

        int pixelCount = calculatePixelCount(
                width,
                height
        );

        byte[] pixels = new byte[pixelCount];

        if (grayImage.isContinuous()) {
            copyContinuous(
                    grayImage,
                    pixels
            );
        } else {
            copyRows(
                    grayImage,
                    pixels,
                    width,
                    height
            );
        }

        /*
         * O vetor acabou de ser criado neste metodo e nao sera mais
         * alterado. Transferimos sua propriedade para evitar uma
         * segunda copia defensiva.
         */
        return GrayImageBuffer.fromOwnedPixels(
                width,
                height,
                pixels
        );
    }

    private void validateInput(Mat grayImage) {
        if (grayImage == null
                || grayImage.empty()) {

            throw new IllegalArgumentException(
                    "A Mat em escala de cinza e obrigatoria."
            );
        }

        if (grayImage.channels() != 1) {
            throw new IllegalArgumentException(
                    "A Mat deve possuir exatamente um canal,"
                            + " mas possui "
                            + grayImage.channels()
                            + "."
            );
        }

        if (grayImage.depth() != CvType.CV_8U) {
            throw new IllegalArgumentException(
                    "A Mat deve possuir profundidade CV_8U,"
                            + " mas possui "
                            + grayImage.depth()
                            + "."
            );
        }
    }

    private int calculatePixelCount(
            int width,
            int height
    ) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "As dimensoes da Mat devem ser positivas."
            );
        }

        long count =
                (long) width * (long) height;

        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "A Mat possui pixels demais: "
                            + count
            );
        }

        return (int) count;
    }

    private void copyContinuous(
            Mat grayImage,
            byte[] destination
    ) {
        int copied = grayImage.get(
                0,
                0,
                destination
        );

        validateCopiedCount(
                copied,
                destination.length,
                "imagem continua"
        );
    }

    private void copyRows(
            Mat grayImage,
            byte[] destination,
            int width,
            int height
    ) {
        byte[] rowPixels = new byte[width];

        for (int row = 0;
             row < height;
             row++) {

            int copied = grayImage.get(
                    row,
                    0,
                    rowPixels
            );

            validateCopiedCount(
                    copied,
                    width,
                    "linha " + row
            );

            System.arraycopy(
                    rowPixels,
                    0,
                    destination,
                    row * width,
                    width
            );
        }
    }

    private void validateCopiedCount(
            int copied,
            int expected,
            String sourceDescription
    ) {
        if (copied != expected) {
            throw new IllegalStateException(
                    "O OpenCV transferiu "
                            + copied
                            + " de "
                            + expected
                            + " pixels da "
                            + sourceDescription
                            + "."
            );
        }
    }
}
