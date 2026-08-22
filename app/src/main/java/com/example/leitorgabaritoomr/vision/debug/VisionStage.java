package com.example.leitorgabaritoomr.vision.debug;

/**
 * Etapas visuais do Laboratorio OMR.
 *
 * A ordem das constantes tambem define a ordem de navegacao
 * utilizada pelo VisionDebugController.
 */
public enum VisionStage {

    ORIGINAL(
            "1/17 - Original"
    ),

    GRAYSCALE(
            "2/17 - Escala de cinza"
    ),

    BLURRED(
            "3/17 - Suavizacao"
    ),

    BINARY(
            "4/17 - Imagem binaria"
    ),

    ACCEPTED_CANDIDATES(
            "5/17 - Candidatos aceitos"
    ),

    RESOLVED_MARKERS(
            "6/17 - Quatro marcadores"
    ),

    STABLE_MARKERS(
            "7/17 - Estabilidade temporal"
    ),

    NORMALIZED_REGION(
            "8/17 - Regiao normalizada"
    ),

    LAYOUT_MAP(
            "9/17 - Mapa esperado das bolhas"
    ),

    BUBBLE_REGISTRATION(
            "10/17 - Associacao preliminar"
    ),

    BUBBLE_TRANSLATION_SEED(
            "11/17 - Translacao inicial dos blocos"
    ),

    BUBBLE_GRID_REGISTRATION(
            "12/17 - Registro geometrico dos blocos"
    ),

    REGISTERED_BUBBLE_REGIONS(
            "13/17 - Regioes finais registradas"
    ),

    BUBBLE_SAMPLING_GEOMETRY(
            "14/17 - Areas exatas de amostragem"
    ),

    BUBBLE_MEASUREMENTS(
            "15/17 - Medicao das bolhas"
    ),

    QUESTION_COMPARISON(
            "16/17 - Comparacao por questao"
    ),

    TEMPORAL_CONSENSUS(
            "17/17 - Consenso temporal"
    );

    private final String displayName;

    VisionStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
