package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

/**
 * Fonte única da geometria usada para medir uma alternativa.
 *
 * O cálculo e o Laboratório OMR obrigatoriamente receberão
 * esta mesma instância.
 */
public final class BubbleMeasurementGeometry {

    private final OmrOptionDefinition option;

    /*
     * Limite completo da bolha.
     *
     * Este é o retângulo que deverá ficar tangente à borda
     * externa da bolha impressa.
     */
    private final PixelRectangle bubbleRegion;

    /*
     * Região central usada para identificar o preenchimento.
     */
    private final PixelRectangle coreRegion;

    /*
     * Região externa usada para calcular o fundo local.
     *
     * O fundo efetivo corresponde à área desta região,
     * excluindo bubbleRegion.
     */
    private final PixelRectangle backgroundRegion;

    public BubbleMeasurementGeometry(
            OmrOptionDefinition option,
            PixelRectangle bubbleRegion,
            PixelRectangle coreRegion,
            PixelRectangle backgroundRegion
    ) {
        if (option == null) {
            throw new IllegalArgumentException(
                    "A alternativa é obrigatória."
            );
        }

        if (bubbleRegion == null
                || coreRegion == null
                || backgroundRegion == null) {

            throw new IllegalArgumentException(
                    "Todas as regiões são obrigatórias."
            );
        }

        if (!bubbleRegion.contains(coreRegion)) {
            throw new IllegalArgumentException(
                    "O núcleo deve permanecer dentro"
                            + " da região da bolha."
            );
        }

        if (!backgroundRegion.contains(bubbleRegion)) {
            throw new IllegalArgumentException(
                    "A região da bolha deve permanecer dentro"
                            + " da região de fundo."
            );
        }

        this.option = option;
        this.bubbleRegion = bubbleRegion;
        this.coreRegion = coreRegion;
        this.backgroundRegion = backgroundRegion;
    }

    public OmrOptionDefinition getOption() {
        return option;
    }

    public PixelRectangle getBubbleRegion() {
        return bubbleRegion;
    }

    public PixelRectangle getCoreRegion() {
        return coreRegion;
    }

    public PixelRectangle getBackgroundRegion() {
        return backgroundRegion;
    }

    public int getBackgroundPixelCount() {
        return backgroundRegion.getArea()
                - bubbleRegion.getArea();
    }

    @Override
    public String toString() {
        return "BubbleMeasurementGeometry{"
                + "option=" + option.getId()
                + ", bubble=" + bubbleRegion
                + ", core=" + coreRegion
                + ", background=" + backgroundRegion
                + '}';
    }
}