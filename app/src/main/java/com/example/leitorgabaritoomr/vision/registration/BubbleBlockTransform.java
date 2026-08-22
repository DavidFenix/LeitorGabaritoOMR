package com.example.leitorgabaritoomr.vision.registration;

import java.util.Locale;

/**
 * Transformacao geometrica imutavel de um bloco de respostas.
 *
 * O modelo trabalha apenas com numeros e nao depende do OpenCV.
 * Assim, a mesma transformacao pode ser usada pelo calculo, pelos
 * testes automatizados e pelo Laboratorio OMR.
 *
 * A transformacao e representada ao redor de duas ancoras:
 *
 * expected = posicao prevista pelo layout;
 * observed = posicao correspondente observada na imagem.
 *
 * Para um ponto esperado (x, y):
 *
 * dx = x - expectedAnchorX
 * dy = y - expectedAnchorY
 *
 * x' = observedAnchorX + matrixXX * dx + matrixXY * dy
 * y' = observedAnchorY + matrixYX * dx + matrixYY * dy
 *
 * Nesta primeira fase, o registrador fornecera uma matriz sem
 * inclinacao:
 *
 * [ scaleX    0     ]
 * [    0    scaleY  ]
 *
 * Entretanto, o contrato ja admite uma transformacao afim completa.
 * Portanto, uma futura correcao de pequena rotacao ou cisalhamento
 * nao exigira descartar esta classe nem alterar seus consumidores.
 */
public final class BubbleBlockTransform {

    private static final double
            MINIMUM_POSITIVE_DETERMINANT = 1.0e-9;

    private final int blockIndex;
    private final String blockId;

    private final double expectedAnchorX;
    private final double expectedAnchorY;

    private final double observedAnchorX;
    private final double observedAnchorY;

    private final double matrixXX;
    private final double matrixXY;
    private final double matrixYX;
    private final double matrixYY;

    private final double determinant;

    /**
     * Cria uma transformacao afim completa.
     *
     * O determinante deve ser positivo. Isso impede modelos
     * degenerados e espelhamentos acidentais do bloco.
     */
    public BubbleBlockTransform(
            int blockIndex,
            String blockId,
            double expectedAnchorX,
            double expectedAnchorY,
            double observedAnchorX,
            double observedAnchorY,
            double matrixXX,
            double matrixXY,
            double matrixYX,
            double matrixYY
    ) {
        if (blockIndex < 0) {
            throw new IllegalArgumentException(
                    "blockIndex nao pode ser negativo."
            );
        }

        if (blockId == null
                || blockId.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "blockId nao pode ser vazio."
            );
        }

        validateFinite(
                "expectedAnchorX",
                expectedAnchorX
        );

        validateFinite(
                "expectedAnchorY",
                expectedAnchorY
        );

        validateFinite(
                "observedAnchorX",
                observedAnchorX
        );

        validateFinite(
                "observedAnchorY",
                observedAnchorY
        );

        validateFinite("matrixXX", matrixXX);
        validateFinite("matrixXY", matrixXY);
        validateFinite("matrixYX", matrixYX);
        validateFinite("matrixYY", matrixYY);

        double mutableDeterminant =
                matrixXX * matrixYY
                        - matrixXY * matrixYX;

        if (!Double.isFinite(mutableDeterminant)
                || mutableDeterminant
                <= MINIMUM_POSITIVE_DETERMINANT) {

            throw new IllegalArgumentException(
                    "A transformacao deve possuir determinante"
                            + " positivo e nao degenerado."
            );
        }

        this.blockIndex = blockIndex;
        this.blockId = blockId.trim();

        this.expectedAnchorX = expectedAnchorX;
        this.expectedAnchorY = expectedAnchorY;

        this.observedAnchorX = observedAnchorX;
        this.observedAnchorY = observedAnchorY;

        this.matrixXX = matrixXX;
        this.matrixXY = matrixXY;
        this.matrixYX = matrixYX;
        this.matrixYY = matrixYY;

        this.determinant = mutableDeterminant;
    }

    /**
     * Fabrica o modelo usado pela primeira versao do registro:
     * translacao mais escalas horizontal e vertical independentes.
     */
    public static BubbleBlockTransform
    translationAndScale(
            int blockIndex,
            String blockId,
            double expectedAnchorX,
            double expectedAnchorY,
            double observedAnchorX,
            double observedAnchorY,
            double scaleX,
            double scaleY
    ) {
        validatePositiveStatic("scaleX", scaleX);
        validatePositiveStatic("scaleY", scaleY);

        return new BubbleBlockTransform(
                blockIndex,
                blockId,
                expectedAnchorX,
                expectedAnchorY,
                observedAnchorX,
                observedAnchorY,
                scaleX,
                0.0,
                0.0,
                scaleY
        );
    }

    /**
     * Fabrica um modelo que aplica apenas translacao.
     */
    public static BubbleBlockTransform translation(
            int blockIndex,
            String blockId,
            double offsetX,
            double offsetY
    ) {
        validateFiniteStatic("offsetX", offsetX);
        validateFiniteStatic("offsetY", offsetY);

        return translationAndScale(
                blockIndex,
                blockId,
                0.0,
                0.0,
                offsetX,
                offsetY,
                1.0,
                1.0
        );
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        validateFiniteStatic(fieldName, value);
    }

    private static void validateFiniteStatic(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + " deve ser finito."
            );
        }
    }

    private static void validatePositiveStatic(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0) {

            throw new IllegalArgumentException(
                    fieldName + " deve ser positivo."
            );
        }
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public String getBlockId() {
        return blockId;
    }

    public double getExpectedAnchorX() {
        return expectedAnchorX;
    }

    public double getExpectedAnchorY() {
        return expectedAnchorY;
    }

    public double getObservedAnchorX() {
        return observedAnchorX;
    }

    public double getObservedAnchorY() {
        return observedAnchorY;
    }

    public double getAnchorOffsetX() {
        return observedAnchorX - expectedAnchorX;
    }

    public double getAnchorOffsetY() {
        return observedAnchorY - expectedAnchorY;
    }

    public double getMatrixXX() {
        return matrixXX;
    }

    public double getMatrixXY() {
        return matrixXY;
    }

    public double getMatrixYX() {
        return matrixYX;
    }

    public double getMatrixYY() {
        return matrixYY;
    }

    public double getDeterminant() {
        return determinant;
    }

    /**
     * Comprimento do eixo horizontal transformado.
     */
    public double getScaleX() {
        return Math.hypot(
                matrixXX,
                matrixYX
        );
    }

    /**
     * Comprimento do eixo vertical transformado.
     */
    public double getScaleY() {
        return Math.hypot(
                matrixXY,
                matrixYY
        );
    }

    /**
     * Angulo, em graus, do eixo horizontal transformado.
     */
    public double getRotationDegrees() {
        return Math.toDegrees(
                Math.atan2(
                        matrixYX,
                        matrixXX
                )
        );
    }

    /**
     * Cosseno do angulo entre os dois eixos transformados.
     * Zero representa eixos perpendiculares, sem cisalhamento.
     */
    public double getAxisCosine() {
        double denominator =
                getScaleX() * getScaleY();

        if (denominator <= 0.0) {
            return 0.0;
        }

        return (
                matrixXX * matrixXY
                        + matrixYX * matrixYY
        ) / denominator;
    }

    public boolean isAxisAligned(
            double tolerance
    ) {
        if (!Double.isFinite(tolerance)
                || tolerance < 0.0) {

            throw new IllegalArgumentException(
                    "tolerance deve ser finita e nao negativa."
            );
        }

        return Math.abs(matrixXY) <= tolerance
                && Math.abs(matrixYX) <= tolerance;
    }

    public double transformX(
            double expectedX,
            double expectedY
    ) {
        validateInputPoint(
                expectedX,
                expectedY
        );

        double deltaX =
                expectedX - expectedAnchorX;

        double deltaY =
                expectedY - expectedAnchorY;

        return observedAnchorX
                + matrixXX * deltaX
                + matrixXY * deltaY;
    }

    public double transformY(
            double expectedX,
            double expectedY
    ) {
        validateInputPoint(
                expectedX,
                expectedY
        );

        double deltaX =
                expectedX - expectedAnchorX;

        double deltaY =
                expectedY - expectedAnchorY;

        return observedAnchorY
                + matrixYX * deltaX
                + matrixYY * deltaY;
    }

    public double inverseTransformX(
            double observedX,
            double observedY
    ) {
        validateInputPoint(
                observedX,
                observedY
        );

        double deltaX =
                observedX - observedAnchorX;

        double deltaY =
                observedY - observedAnchorY;

        return expectedAnchorX
                + (
                matrixYY * deltaX
                        - matrixXY * deltaY
        ) / determinant;
    }

    public double inverseTransformY(
            double observedX,
            double observedY
    ) {
        validateInputPoint(
                observedX,
                observedY
        );

        double deltaX =
                observedX - observedAnchorX;

        double deltaY =
                observedY - observedAnchorY;

        return expectedAnchorY
                + (
                -matrixYX * deltaX
                        + matrixXX * deltaY
        ) / determinant;
    }

    public double predictCenterX(
            ExpectedBubbleTarget target
    ) {
        validateTargetBelongsToBlock(target);

        return transformX(
                target.getExpectedCenterX(),
                target.getExpectedCenterY()
        );
    }

    public double predictCenterY(
            ExpectedBubbleTarget target
    ) {
        validateTargetBelongsToBlock(target);

        return transformY(
                target.getExpectedCenterX(),
                target.getExpectedCenterY()
        );
    }

    /**
     * Largura do retangulo alinhado aos eixos que envolve a
     * regiao esperada depois da transformacao.
     */
    public double predictBoundingWidth(
            ExpectedBubbleTarget target
    ) {
        validateTargetBelongsToBlock(target);

        return Math.abs(matrixXX)
                * target.getExpectedWidth()
                + Math.abs(matrixXY)
                * target.getExpectedHeight();
    }

    /**
     * Altura do retangulo alinhado aos eixos que envolve a
     * regiao esperada depois da transformacao.
     */
    public double predictBoundingHeight(
            ExpectedBubbleTarget target
    ) {
        validateTargetBelongsToBlock(target);

        return Math.abs(matrixYX)
                * target.getExpectedWidth()
                + Math.abs(matrixYY)
                * target.getExpectedHeight();
    }

    private void validateInputPoint(
            double x,
            double y
    ) {
        validateFinite("x", x);
        validateFinite("y", y);
    }

    private void validateTargetBelongsToBlock(
            ExpectedBubbleTarget target
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "O alvo e obrigatorio."
            );
        }

        if (target.getBlockIndex() != blockIndex
                || !target.getBlockId().equals(blockId)) {

            throw new IllegalArgumentException(
                    "O alvo nao pertence a "
                            + blockId
                            + ": "
                            + target.getOptionId()
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s anchor=(%.2f, %.2f)->(%.2f, %.2f)"
                        + " matrix=[[%.5f, %.5f],"
                        + " [%.5f, %.5f]]"
                        + " scale=(%.5f, %.5f)"
                        + " rotation=%.3f determinant=%.6f",
                blockId,
                expectedAnchorX,
                expectedAnchorY,
                observedAnchorX,
                observedAnchorY,
                matrixXX,
                matrixXY,
                matrixYX,
                matrixYY,
                getScaleX(),
                getScaleY(),
                getRotationDegrees(),
                determinant
        );
    }
}
