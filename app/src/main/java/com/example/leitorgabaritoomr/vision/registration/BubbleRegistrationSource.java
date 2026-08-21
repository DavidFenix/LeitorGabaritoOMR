package com.example.leitorgabaritoomr.vision.registration;

/**
 * Informa como a posição final de uma bolha foi obtida.
 */
public enum BubbleRegistrationSource {

    /*
     * O contorno da própria bolha foi localizado.
     */
    DIRECT_DETECTION(false),

    /*
     * A posição foi inferida pelo modelo geométrico
     * calculado para o bloco.
     */
    BLOCK_MODEL(true),

    /*
     * A posição foi inferida pelo modelo global da folha.
     */
    SHEET_MODEL(true);

    private final boolean inferred;

    BubbleRegistrationSource(
            boolean inferred
    ) {
        this.inferred = inferred;
    }

    public boolean isInferred() {
        return inferred;
    }

    public boolean isDirectDetection() {
        return this == DIRECT_DETECTION;
    }
}