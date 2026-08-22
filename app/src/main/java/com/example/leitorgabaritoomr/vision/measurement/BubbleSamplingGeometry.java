package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.geometry.PixelQuadrilateral;
import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegion;

import java.util.Locale;

/**
 * Geometria imutavel das areas usadas para medir uma bolha.
 *
 * A mesma instancia deve ser entregue ao renderizador do
 * Laboratorio OMR e ao medidor. Dessa forma, o que aparece na tela
 * e exatamente o que participa dos calculos.
 *
 * A geometria possui tres regioes concentricas:
 *
 * - backgroundPolygon: envoltorio ampliado do fundo local;
 * - bubblePolygon: regiao externa registrada da bolha;
 * - corePolygon: nucleo interno usado para detectar preenchimento.
 *
 * A borda e a parte da bolha externa ao nucleo. O fundo local e a
 * parte do envoltorio ampliado externa a toda a bolha.
 */
public final class BubbleSamplingGeometry {

    private static final double CENTER_TOLERANCE =
            1.0e-6;

    public enum Zone {
        OUTSIDE,
        LOCAL_BACKGROUND,
        BORDER,
        CORE
    }

    private final RegisteredBubbleRegion registeredRegion;
    private final BubbleMeasurementConfig config;

    private final PixelQuadrilateral bubblePolygon;
    private final PixelQuadrilateral corePolygon;
    private final PixelQuadrilateral backgroundPolygon;

    public BubbleSamplingGeometry(
            RegisteredBubbleRegion registeredRegion,
            BubbleMeasurementConfig config
    ) {
        if (registeredRegion == null) {
            throw new IllegalArgumentException(
                    "A regiao registrada e obrigatoria."
            );
        }

        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuracao de medicao e obrigatoria."
            );
        }

        this.registeredRegion = registeredRegion;
        this.config = config;

        this.bubblePolygon =
                copyRegisteredPolygon(
                        registeredRegion
                );

        validateExactRegisteredGeometry();

        this.corePolygon =
                bubblePolygon.scaled(
                        config.getCoreWidthScale(),
                        config.getCoreHeightScale()
                );

        this.backgroundPolygon =
                bubblePolygon.scaled(
                        config.getBackgroundWidthScale(),
                        config.getBackgroundHeightScale()
                );

        validateNesting();
        validateMinimumRegionSize();
    }

    private PixelQuadrilateral copyRegisteredPolygon(
            RegisteredBubbleRegion region
    ) {
        return new PixelQuadrilateral(
                region.getImageWidth(),
                region.getImageHeight(),
                region.getCornerX(
                        RegisteredBubbleRegion.TOP_LEFT
                ),
                region.getCornerY(
                        RegisteredBubbleRegion.TOP_LEFT
                ),
                region.getCornerX(
                        RegisteredBubbleRegion.TOP_RIGHT
                ),
                region.getCornerY(
                        RegisteredBubbleRegion.TOP_RIGHT
                ),
                region.getCornerX(
                        RegisteredBubbleRegion.BOTTOM_RIGHT
                ),
                region.getCornerY(
                        RegisteredBubbleRegion.BOTTOM_RIGHT
                ),
                region.getCornerX(
                        RegisteredBubbleRegion.BOTTOM_LEFT
                ),
                region.getCornerY(
                        RegisteredBubbleRegion.BOTTOM_LEFT
                )
        );
    }

    /**
     * Garante que a conversao para PixelQuadrilateral nao alterou
     * centro nem janela inteira de pixels.
     */
    private void validateExactRegisteredGeometry() {
        if (Math.abs(
                bubblePolygon.getCenterX()
                        - registeredRegion.getCenterX()
        ) > CENTER_TOLERANCE
                || Math.abs(
                bubblePolygon.getCenterY()
                        - registeredRegion.getCenterY()
        ) > CENTER_TOLERANCE) {

            throw new IllegalArgumentException(
                    "O centro do poligono difere da regiao"
                            + " registrada: "
                            + registeredRegion.getOptionId()
            );
        }

        if (!sameBounds(
                bubblePolygon.getPixelBounds(),
                registeredRegion.getPixelBounds()
        )) {

            throw new IllegalArgumentException(
                    "A janela do poligono difere da regiao"
                            + " registrada: "
                            + registeredRegion.getOptionId()
            );
        }

        if (bubblePolygon.isClippedByImage()
                != registeredRegion.isClippedByImage()) {

            throw new IllegalArgumentException(
                    "A informacao de recorte divergiu para "
                            + registeredRegion.getOptionId()
            );
        }
    }

    private boolean sameBounds(
            PixelRectangle first,
            PixelRectangle second
    ) {
        return first.getLeft() == second.getLeft()
                && first.getTop() == second.getTop()
                && first.getWidth() == second.getWidth()
                && first.getHeight() == second.getHeight();
    }

    private void validateNesting() {
        validateCornersContained(
                corePolygon,
                bubblePolygon,
                "O nucleo saiu da regiao da bolha."
        );

        validateCornersContained(
                bubblePolygon,
                backgroundPolygon,
                "A bolha saiu da regiao de fundo."
        );
    }

    private void validateCornersContained(
            PixelQuadrilateral inner,
            PixelQuadrilateral outer,
            String errorMessage
    ) {
        for (int cornerIndex = 0;
             cornerIndex < inner.getCornerCount();
             cornerIndex++) {

            if (!outer.containsPixelCenter(
                    inner.getCornerX(cornerIndex),
                    inner.getCornerY(cornerIndex)
            )) {

                throw new IllegalArgumentException(
                        errorMessage
                                + " Alternativa: "
                                + registeredRegion.getOptionId()
                );
            }
        }
    }

    private void validateMinimumRegionSize() {
        PixelRectangle bounds =
                bubblePolygon.getPixelBounds();

        if (bounds.getWidth()
                < config.getMinimumRegionWidth()
                || bounds.getHeight()
                < config.getMinimumRegionHeight()) {

            throw new IllegalArgumentException(
                    "A regiao registrada ficou pequena demais: "
                            + bounds.getWidth()
                            + "x"
                            + bounds.getHeight()
                            + " para "
                            + registeredRegion.getOptionId()
            );
        }
    }

    public RegisteredBubbleRegion getRegisteredRegion() {
        return registeredRegion;
    }

    public BubbleMeasurementConfig getConfig() {
        return config;
    }

    public OmrOptionDefinition getOption() {
        return registeredRegion.getOption();
    }

    public String getOptionId() {
        return registeredRegion.getOptionId();
    }

    public String getQuestionId() {
        return registeredRegion.getQuestionId();
    }

    public String getBlockId() {
        return registeredRegion.getBlockId();
    }

    public int getBlockIndex() {
        return registeredRegion.getBlockIndex();
    }

    public int getQuestionIndex() {
        return registeredRegion.getQuestionIndex();
    }

    public int getOptionIndex() {
        return registeredRegion.getOptionIndex();
    }

    public PixelQuadrilateral getBackgroundPolygon() {
        return backgroundPolygon;
    }

    public PixelQuadrilateral getBubblePolygon() {
        return bubblePolygon;
    }

    public PixelQuadrilateral getCorePolygon() {
        return corePolygon;
    }

    /**
     * Menor janela inteira que contem toda a amostragem.
     */
    public PixelRectangle getSamplingBounds() {
        return backgroundPolygon.getPixelBounds();
    }

    public boolean isBackgroundClippedByImage() {
        return backgroundPolygon.isClippedByImage();
    }

    /**
     * Classificacao unica usada posteriormente tanto pelo desenho
     * de diagnostico quanto pelo calculo dos indicadores.
     */
    public Zone classifyPixel(
            int pixelX,
            int pixelY
    ) {
        if (pixelX < 0
                || pixelY < 0
                || pixelX
                >= registeredRegion.getImageWidth()
                || pixelY
                >= registeredRegion.getImageHeight()) {

            return Zone.OUTSIDE;
        }

        if (corePolygon.containsPixel(
                pixelX,
                pixelY
        )) {

            return Zone.CORE;
        }

        if (bubblePolygon.containsPixel(
                pixelX,
                pixelY
        )) {

            return Zone.BORDER;
        }

        if (backgroundPolygon.containsPixel(
                pixelX,
                pixelY
        )) {

            return Zone.LOCAL_BACKGROUND;
        }

        return Zone.OUTSIDE;
    }

    public boolean isCorePixel(
            int pixelX,
            int pixelY
    ) {
        return classifyPixel(pixelX, pixelY)
                == Zone.CORE;
    }

    public boolean isBorderPixel(
            int pixelX,
            int pixelY
    ) {
        return classifyPixel(pixelX, pixelY)
                == Zone.BORDER;
    }

    public boolean isBubblePixel(
            int pixelX,
            int pixelY
    ) {
        Zone zone = classifyPixel(pixelX, pixelY);

        return zone == Zone.CORE
                || zone == Zone.BORDER;
    }

    public boolean isLocalBackgroundPixel(
            int pixelX,
            int pixelY
    ) {
        return classifyPixel(pixelX, pixelY)
                == Zone.LOCAL_BACKGROUND;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s sampling{background=%.1fx%.1f,"
                        + " bubble=%.1fx%.1f, core=%.1fx%.1f,"
                        + " clippedBackground=%s}",
                getOptionId(),
                backgroundPolygon.getNominalWidth(),
                backgroundPolygon.getNominalHeight(),
                bubblePolygon.getNominalWidth(),
                bubblePolygon.getNominalHeight(),
                corePolygon.getNominalWidth(),
                corePolygon.getNominalHeight(),
                backgroundPolygon.isClippedByImage()
        );
    }
}
