package com.example.leitorgabaritoomr.vision.debug;

public enum VisionStage {

    ORIGINAL(
            "1/13 - Original"
    ),

    GRAYSCALE(
            "2/13 - Escala de cinza"
    ),

    BLURRED(
            "3/13 - Suavizacao"
    ),

    BINARY(
            "4/13 - Imagem binaria"
    ),

    ACCEPTED_CANDIDATES(
            "5/13 - Candidatos aceitos"
    ),

    RESOLVED_MARKERS(
            "6/13 - Quatro marcadores"
    ),

    STABLE_MARKERS(
            "7/13 - Estabilidade temporal"
    ),

    NORMALIZED_REGION(
            "8/13 - Regiao normalizada"
    ),

    LAYOUT_MAP(
            "9/13 - Mapa esperado das bolhas"
    ),

    BUBBLE_REGISTRATION(
            "10/13 - Registro das bolhas"
    ),

    BUBBLE_MEASUREMENTS(
            "11/13 - Medicao das bolhas"
    ),

    QUESTION_COMPARISON(
            "12/13 - Comparacao por questao"
    ),

    TEMPORAL_CONSENSUS(
            "13/13 - Consenso temporal"
    );

    private final String displayName;

    VisionStage(String displayName) {
        this.displayName =
                displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}