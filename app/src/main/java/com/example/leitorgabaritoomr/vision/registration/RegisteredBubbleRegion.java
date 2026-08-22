package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.Locale;

/**
 * Regiao final e imutavel de uma bolha depois do registro
 * geometrico do bloco.
 *
 * Esta classe e o contrato comum entre:
 *
 * 1. o registro geometrico;
 * 2. o desenho do Laboratorio OMR;
 * 3. a medicao dos pixels.
 *
 * Nenhum consumidor deve recalcular, deslocar ou redimensionar
 * esta regiao. O que for desenhado a partir deste objeto sera
 * exatamente a geometria entregue ao medidor.
 *
 * A regiao conserva os quatro cantos transformados, e nao apenas
 * um retangulo alinhado aos eixos. Isso permite que uma futura
 * versao do registrador utilize pequena rotacao ou cisalhamento
 * sem obrigar a substituicao desta classe.
 *
 * A classe nao depende do OpenCV. Portanto, pode ser validada por
 * testes Java puros e reutilizada em outros ambientes.
 */
public final class RegisteredBubbleRegion {

    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;

    private static final int CORNER_COUNT = 4;

    private static final double MINIMUM_POLYGON_AREA =
            1.0e-6;

    private static final double CONTAINMENT_TOLERANCE =
            1.0e-7;

    private final ExpectedBubbleTarget target;
    private final BubbleBlockRegistration registration;
    private final BubbleBlockTransform transform;

    private final int imageWidth;
    private final int imageHeight;

    private final double[] cornerX;
    private final double[] cornerY;

    private final double centerX;
    private final double centerY;

    /*
     * Janela inteira minima que contem o poligono dentro da
     * imagem. Ela delimita o trabalho do medidor, mas nao substitui
     * o poligono: quando houver rotacao, somente os pixels contidos
     * no poligono deverao participar da medicao da bolha.
     */
    private final PixelRectangle pixelBounds;

    private final double polygonArea;
    private final double nominalWidth;
    private final double nominalHeight;

    private final boolean clippedByImage;

    public RegisteredBubbleRegion(
            ExpectedBubbleTarget target,
            BubbleBlockRegistration registration,
            int imageWidth,
            int imageHeight
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "O alvo esperado e obrigatorio."
            );
        }

        if (registration == null) {
            throw new IllegalArgumentException(
                    "O registro do bloco e obrigatorio."
            );
        }

        if (!registration.isAccepted()) {
            throw new IllegalArgumentException(
                    "A regiao final exige um bloco aceito: "
                            + registration.getBlockId()
            );
        }

        if (target.getBlockIndex()
                != registration.getBlockIndex()
                || !target.getBlockId().equals(
                registration.getBlockId()
        )) {

            throw new IllegalArgumentException(
                    "O alvo "
                            + target.getOptionId()
                            + " nao pertence ao registro "
                            + registration.getBlockId()
            );
        }

        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "As dimensoes da imagem devem ser positivas."
            );
        }

        this.target = target;
        this.registration = registration;
        this.transform = registration.getTransform();

        if (transform == null) {
            throw new IllegalArgumentException(
                    "O registro nao possui transformacao."
            );
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        this.cornerX = new double[CORNER_COUNT];
        this.cornerY = new double[CORNER_COUNT];

        PixelRectangle expectedBounds =
                target.getExpectedBounds();

        transformCorner(
                TOP_LEFT,
                expectedBounds.getLeft(),
                expectedBounds.getTop()
        );

        transformCorner(
                TOP_RIGHT,
                expectedBounds.getRightInclusive(),
                expectedBounds.getTop()
        );

        transformCorner(
                BOTTOM_RIGHT,
                expectedBounds.getRightInclusive(),
                expectedBounds.getBottomInclusive()
        );

        transformCorner(
                BOTTOM_LEFT,
                expectedBounds.getLeft(),
                expectedBounds.getBottomInclusive()
        );

        this.centerX =
                transform.predictCenterX(target);

        this.centerY =
                transform.predictCenterY(target);

        validateFinite("centerX", centerX);
        validateFinite("centerY", centerY);

        double signedTwiceArea =
                calculateSignedTwiceArea();

        if (!Double.isFinite(signedTwiceArea)
                || signedTwiceArea
                <= MINIMUM_POLYGON_AREA * 2.0) {

            throw new IllegalArgumentException(
                    "A transformacao produziu uma regiao"
                            + " degenerada para "
                            + target.getOptionId()
            );
        }

        this.polygonArea =
                signedTwiceArea / 2.0;

        this.nominalWidth =
                calculateAverageOppositeSideLength(
                        TOP_LEFT,
                        TOP_RIGHT,
                        BOTTOM_LEFT,
                        BOTTOM_RIGHT
                );

        this.nominalHeight =
                calculateAverageOppositeSideLength(
                        TOP_LEFT,
                        BOTTOM_LEFT,
                        TOP_RIGHT,
                        BOTTOM_RIGHT
                );

        PixelBoundsCalculation boundsCalculation =
                calculatePixelBounds(
                        imageWidth,
                        imageHeight
                );

        this.pixelBounds =
                boundsCalculation.bounds;

        this.clippedByImage =
                boundsCalculation.clipped;
    }

    private void transformCorner(
            int cornerIndex,
            double expectedX,
            double expectedY
    ) {
        double transformedX =
                transform.transformX(
                        expectedX,
                        expectedY
                );

        double transformedY =
                transform.transformY(
                        expectedX,
                        expectedY
                );

        validateFinite(
                "cornerX[" + cornerIndex + "]",
                transformedX
        );

        validateFinite(
                "cornerY[" + cornerIndex + "]",
                transformedY
        );

        cornerX[cornerIndex] = transformedX;
        cornerY[cornerIndex] = transformedY;
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

    private double calculateAverageOppositeSideLength(
            int firstStart,
            int firstEnd,
            int secondStart,
            int secondEnd
    ) {
        return (
                distance(firstStart, firstEnd)
                        + distance(secondStart, secondEnd)
        ) / 2.0;
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

    private PixelBoundsCalculation calculatePixelBounds(
            int width,
            int height
    ) {
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
                || rawLeft >= width
                || rawTop >= height) {

            throw new IllegalArgumentException(
                    "A regiao registrada ficou fora da imagem: "
                            + target.getOptionId()
            );
        }

        int clippedLeft = clamp(
                rawLeft,
                0,
                width - 1
        );

        int clippedRight = clamp(
                rawRight,
                clippedLeft,
                width - 1
        );

        int clippedTop = clamp(
                rawTop,
                0,
                height - 1
        );

        int clippedBottom = clamp(
                rawBottom,
                clippedTop,
                height - 1
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

    public ExpectedBubbleTarget getTarget() {
        return target;
    }

    public OmrOptionDefinition getOption() {
        return target.getOption();
    }

    public String getOptionId() {
        return target.getOptionId();
    }

    public String getQuestionId() {
        return target.getQuestionId();
    }

    public String getBlockId() {
        return target.getBlockId();
    }

    public int getBlockIndex() {
        return target.getBlockIndex();
    }

    public int getQuestionIndex() {
        return target.getQuestionIndex();
    }

    public int getOptionIndex() {
        return target.getOptionIndex();
    }

    public BubbleBlockRegistration getRegistration() {
        return registration;
    }

    public BubbleBlockTransform getTransform() {
        return transform;
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

    public PixelRectangle getPixelBounds() {
        return pixelBounds;
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

    public boolean isClippedByImage() {
        return clippedByImage;
    }

    /**
     * Informa se o centro de um pixel pertence ao poligono final.
     *
     * O metodo sera usado pelo medidor para impedir que pixels dos
     * cantos do retangulo envolvente sejam incluidos quando uma
     * futura transformacao possuir rotacao ou cisalhamento.
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

            double cross =
                    (cornerX[next] - cornerX[index])
                            * (pixelCenterY - cornerY[index])
                            - (cornerY[next] - cornerY[index])
                            * (pixelCenterX - cornerX[index]);

            if (cross < -CONTAINMENT_TOLERANCE) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s center=(%.2f, %.2f) size=(%.2f, %.2f)"
                        + " bounds=%s area=%.2f clipped=%s",
                getOptionId(),
                centerX,
                centerY,
                nominalWidth,
                nominalHeight,
                pixelBounds,
                polygonArea,
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
