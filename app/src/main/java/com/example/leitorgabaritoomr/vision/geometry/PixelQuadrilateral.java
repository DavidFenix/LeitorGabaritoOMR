package com.example.leitorgabaritoomr.vision.geometry;

import java.util.Locale;

/**
 * Quadrilatero convexo e imutavel em coordenadas de pixels.
 *
 * A ordem obrigatoria dos cantos e:
 *
 * 0. superior esquerdo;
 * 1. superior direito;
 * 2. inferior direito;
 * 3. inferior esquerdo.
 *
 * A classe nao depende do OpenCV. Ela pode ser usada pelo calculo,
 * pelos testes automatizados e pelo Laboratorio OMR.
 *
 * Alem de representar uma regiao, o quadrilatero pode gerar uma
 * versao reduzida ou ampliada em suas proprias coordenadas locais.
 * Isso permite criar o nucleo e o fundo de uma bolha mesmo quando
 * houver rotacao, cisalhamento ou pequena deformacao geometrica.
 */
public final class PixelQuadrilateral {

    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;

    private static final int CORNER_COUNT = 4;

    private static final double MINIMUM_AREA =
            1.0e-6;

    private static final double CONVEXITY_TOLERANCE =
            1.0e-9;

    private static final double CONTAINMENT_TOLERANCE =
            1.0e-7;

    private final int imageWidth;
    private final int imageHeight;

    private final double[] cornerX;
    private final double[] cornerY;

    private final double centerX;
    private final double centerY;

    private final double polygonArea;
    private final double nominalWidth;
    private final double nominalHeight;

    private final PixelRectangle pixelBounds;
    private final boolean clippedByImage;

    public PixelQuadrilateral(
            int imageWidth,
            int imageHeight,
            double topLeftX,
            double topLeftY,
            double topRightX,
            double topRightY,
            double bottomRightX,
            double bottomRightY,
            double bottomLeftX,
            double bottomLeftY
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "As dimensoes da imagem devem ser positivas."
            );
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        this.cornerX = new double[]{
                topLeftX,
                topRightX,
                bottomRightX,
                bottomLeftX
        };

        this.cornerY = new double[]{
                topLeftY,
                topRightY,
                bottomRightY,
                bottomLeftY
        };

        validateCorners();

        double signedTwiceArea =
                calculateSignedTwiceArea();

        if (!Double.isFinite(signedTwiceArea)
                || signedTwiceArea
                <= MINIMUM_AREA * 2.0) {

            throw new IllegalArgumentException(
                    "Os cantos nao formam um quadrilatero"
                            + " valido com orientacao positiva."
            );
        }

        validateStrictConvexity();

        this.polygonArea =
                signedTwiceArea / 2.0;

        this.centerX = interpolateX(0.5, 0.5);
        this.centerY = interpolateY(0.5, 0.5);

        this.nominalWidth =
                (
                        distance(TOP_LEFT, TOP_RIGHT)
                                + distance(
                                BOTTOM_LEFT,
                                BOTTOM_RIGHT
                        )
                ) / 2.0;

        this.nominalHeight =
                (
                        distance(TOP_LEFT, BOTTOM_LEFT)
                                + distance(
                                TOP_RIGHT,
                                BOTTOM_RIGHT
                        )
                ) / 2.0;

        PixelBoundsCalculation boundsCalculation =
                calculatePixelBounds();

        this.pixelBounds =
                boundsCalculation.bounds;

        this.clippedByImage =
                boundsCalculation.clipped;
    }

    private void validateCorners() {
        for (int index = 0;
             index < CORNER_COUNT;
             index++) {

            validateFinite(
                    "cornerX[" + index + "]",
                    cornerX[index]
            );

            validateFinite(
                    "cornerY[" + index + "]",
                    cornerY[index]
            );
        }
    }

    private void validateStrictConvexity() {
        for (int index = 0;
             index < CORNER_COUNT;
             index++) {

            int next =
                    (index + 1) % CORNER_COUNT;

            int afterNext =
                    (index + 2) % CORNER_COUNT;

            double cross = cross(
                    cornerX[index],
                    cornerY[index],
                    cornerX[next],
                    cornerY[next],
                    cornerX[afterNext],
                    cornerY[afterNext]
            );

            if (!Double.isFinite(cross)
                    || cross <= CONVEXITY_TOLERANCE) {

                throw new IllegalArgumentException(
                        "Os cantos devem formar um"
                                + " quadrilatero estritamente convexo"
                                + " na ordem TL, TR, BR, BL."
                );
            }
        }
    }

    private double calculateSignedTwiceArea() {
        double sum = 0.0;

        for (int index = 0;
             index < CORNER_COUNT;
             index++) {

            int next =
                    (index + 1) % CORNER_COUNT;

            sum += cornerX[index]
                    * cornerY[next]
                    - cornerX[next]
                    * cornerY[index];
        }

        return sum;
    }

    private double distance(
            int firstCorner,
            int secondCorner
    ) {
        return Math.hypot(
                cornerX[secondCorner]
                        - cornerX[firstCorner],
                cornerY[secondCorner]
                        - cornerY[firstCorner]
        );
    }

    private double cross(
            double startX,
            double startY,
            double endX,
            double endY,
            double pointX,
            double pointY
    ) {
        return (endX - startX)
                * (pointY - startY)
                - (endY - startY)
                * (pointX - startX);
    }

    private PixelBoundsCalculation calculatePixelBounds() {
        double minimumX = cornerX[0];
        double maximumX = cornerX[0];
        double minimumY = cornerY[0];
        double maximumY = cornerY[0];

        for (int index = 1;
             index < CORNER_COUNT;
             index++) {

            minimumX = Math.min(
                    minimumX,
                    cornerX[index]
            );

            maximumX = Math.max(
                    maximumX,
                    cornerX[index]
            );

            minimumY = Math.min(
                    minimumY,
                    cornerY[index]
            );

            maximumY = Math.max(
                    maximumY,
                    cornerY[index]
            );
        }

        int rawLeft = roundCoordinate(minimumX);
        int rawRight = roundCoordinate(maximumX);
        int rawTop = roundCoordinate(minimumY);
        int rawBottom = roundCoordinate(maximumY);

        if (rawRight < 0
                || rawBottom < 0
                || rawLeft >= imageWidth
                || rawTop >= imageHeight) {

            throw new IllegalArgumentException(
                    "O quadrilatero ficou totalmente"
                            + " fora da imagem."
            );
        }

        int clippedLeft = clamp(
                rawLeft,
                0,
                imageWidth - 1
        );

        int clippedRight = clamp(
                rawRight,
                clippedLeft,
                imageWidth - 1
        );

        int clippedTop = clamp(
                rawTop,
                0,
                imageHeight - 1
        );

        int clippedBottom = clamp(
                rawBottom,
                clippedTop,
                imageHeight - 1
        );

        PixelRectangle bounds =
                new PixelRectangle(
                        clippedLeft,
                        clippedTop,
                        clippedRight
                                - clippedLeft
                                + 1,
                        clippedBottom
                                - clippedTop
                                + 1
                );

        boolean clipped =
                clippedLeft != rawLeft
                        || clippedRight != rawRight
                        || clippedTop != rawTop
                        || clippedBottom != rawBottom;

        return new PixelBoundsCalculation(
                bounds,
                clipped
        );
    }

    private int roundCoordinate(double value) {
        validateFinite("coordinate", value);

        long rounded = Math.round(value);

        if (rounded < Integer.MIN_VALUE
                || rounded > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                    "Coordenada fora do intervalo inteiro: "
                            + value
            );
        }

        return (int) rounded;
    }

    private int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(value, maximum)
        );
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " deve ser finito."
            );
        }
    }

    private void validateCornerIndex(int cornerIndex) {
        if (cornerIndex < 0
                || cornerIndex >= CORNER_COUNT) {

            throw new IllegalArgumentException(
                    "cornerIndex deve estar entre 0 e 3."
            );
        }
    }

    private void validateScale(
            String fieldName,
            double scale
    ) {
        if (!Double.isFinite(scale)
                || scale <= 0.0) {

            throw new IllegalArgumentException(
                    fieldName + " deve ser positiva."
            );
        }
    }

    /**
     * Interpolacao bilinear na coordenada local horizontal e
     * vertical do quadrilatero.
     *
     * u=0 representa o lado esquerdo e u=1 o lado direito.
     * v=0 representa o lado superior e v=1 o lado inferior.
     * Valores externos ao intervalo permitem ampliacao do fundo.
     */
    public double interpolateX(
            double u,
            double v
    ) {
        validateFinite("u", u);
        validateFinite("v", v);

        double top =
                cornerX[TOP_LEFT]
                        + u * (
                        cornerX[TOP_RIGHT]
                                - cornerX[TOP_LEFT]
                );

        double bottom =
                cornerX[BOTTOM_LEFT]
                        + u * (
                        cornerX[BOTTOM_RIGHT]
                                - cornerX[BOTTOM_LEFT]
                );

        return top + v * (bottom - top);
    }

    public double interpolateY(
            double u,
            double v
    ) {
        validateFinite("u", u);
        validateFinite("v", v);

        double top =
                cornerY[TOP_LEFT]
                        + u * (
                        cornerY[TOP_RIGHT]
                                - cornerY[TOP_LEFT]
                );

        double bottom =
                cornerY[BOTTOM_LEFT]
                        + u * (
                        cornerY[BOTTOM_RIGHT]
                                - cornerY[BOTTOM_LEFT]
                );

        return top + v * (bottom - top);
    }

    /**
     * Cria outro quadrilatero centralizado neste, com escalas
     * aplicadas nas coordenadas locais u e v.
     *
     * Escalas menores que 1 criam uma regiao interna.
     * Escalas maiores que 1 criam uma regiao externa.
     */
    public PixelQuadrilateral scaled(
            double horizontalScale,
            double verticalScale
    ) {
        validateScale(
                "horizontalScale",
                horizontalScale
        );

        validateScale(
                "verticalScale",
                verticalScale
        );

        double minimumU =
                (1.0 - horizontalScale) / 2.0;

        double maximumU =
                (1.0 + horizontalScale) / 2.0;

        double minimumV =
                (1.0 - verticalScale) / 2.0;

        double maximumV =
                (1.0 + verticalScale) / 2.0;

        return new PixelQuadrilateral(
                imageWidth,
                imageHeight,
                interpolateX(minimumU, minimumV),
                interpolateY(minimumU, minimumV),
                interpolateX(maximumU, minimumV),
                interpolateY(maximumU, minimumV),
                interpolateX(maximumU, maximumV),
                interpolateY(maximumU, maximumV),
                interpolateX(minimumU, maximumV),
                interpolateY(minimumU, maximumV)
        );
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getCornerCount() {
        return CORNER_COUNT;
    }

    public double getCornerX(int cornerIndex) {
        validateCornerIndex(cornerIndex);

        return cornerX[cornerIndex];
    }

    public double getCornerY(int cornerIndex) {
        validateCornerIndex(cornerIndex);

        return cornerY[cornerIndex];
    }

    public double[] copyCornerX() {
        return cornerX.clone();
    }

    public double[] copyCornerY() {
        return cornerY.clone();
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getPolygonArea() {
        return polygonArea;
    }

    public double getNominalWidth() {
        return nominalWidth;
    }

    public double getNominalHeight() {
        return nominalHeight;
    }

    public PixelRectangle getPixelBounds() {
        return pixelBounds;
    }

    public boolean isClippedByImage() {
        return clippedByImage;
    }

    /**
     * Testa o centro de um pixel contra os quatro lados exatos.
     */
    public boolean containsPixelCenter(
            double pixelCenterX,
            double pixelCenterY
    ) {
        if (!Double.isFinite(pixelCenterX)
                || !Double.isFinite(pixelCenterY)) {

            return false;
        }

        for (int index = 0;
             index < CORNER_COUNT;
             index++) {

            int next =
                    (index + 1) % CORNER_COUNT;

            double side = cross(
                    cornerX[index],
                    cornerY[index],
                    cornerX[next],
                    cornerY[next],
                    pixelCenterX,
                    pixelCenterY
            );

            if (side < -CONTAINMENT_TOLERANCE) {
                return false;
            }
        }

        return true;
    }

    public boolean containsPixel(
            int pixelX,
            int pixelY
    ) {
        return containsPixelCenter(
                pixelX,
                pixelY
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "PixelQuadrilateral{center=(%.2f, %.2f),"
                        + " size=(%.2f, %.2f), area=%.2f,"
                        + " bounds=%s, clipped=%s}",
                centerX,
                centerY,
                nominalWidth,
                nominalHeight,
                polygonArea,
                pixelBounds,
                clippedByImage
        );
    }

    private static final class PixelBoundsCalculation {

        private final PixelRectangle bounds;
        private final boolean clipped;

        private PixelBoundsCalculation(
                PixelRectangle bounds,
                boolean clipped
        ) {
            this.bounds = bounds;
            this.clipped = clipped;
        }
    }
}
