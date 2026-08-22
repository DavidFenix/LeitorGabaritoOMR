package com.example.leitorgabaritoomr.vision.debug;

/**
 * Etapas visuais do Laboratorio OMR.
 *
 * A ordem das constantes tambem define a ordem de navegacao
 * utilizada pelo VisionDebugController.
 */
public enum VisionStage {

    ORIGINAL(
            "1/18 - Original"
    ),

    GRAYSCALE(
            "2/18 - Escala de cinza"
    ),

    BLURRED(
            "3/18 - Suavizacao"
    ),

    BINARY(
            "4/18 - Imagem binaria"
    ),

    ACCEPTED_CANDIDATES(
            "5/18 - Candidatos aceitos"
    ),

    RESOLVED_MARKERS(
            "6/18 - Quatro marcadores"
    ),

    STABLE_MARKERS(
            "7/18 - Estabilidade temporal"
    ),

    NORMALIZED_REGION(
            "8/18 - Regiao normalizada"
    ),

    LAYOUT_MAP(
            "9/18 - Mapa esperado das bolhas"
    ),

    BUBBLE_REGISTRATION(
            "10/18 - Associacao preliminar"
    ),

    BUBBLE_TRANSLATION_SEED(
            "11/18 - Translacao inicial dos blocos"
    ),

    BUBBLE_GRID_REGISTRATION(
            "12/18 - Registro geometrico dos blocos"
    ),

    REGISTERED_BUBBLE_REGIONS(
            "13/18 - Regioes finais registradas"
    ),

    BUBBLE_SAMPLING_GEOMETRY(
            "14/18 - Areas exatas de amostragem"
    ),

    BUBBLE_MEASUREMENTS(
            "15/18 - Medicao das bolhas"
    ),

    QUESTION_COMPARISON(
            "16/18 - Comparacao por questao"
    ),

    TEMPORAL_CONSENSUS(
            "17/18 - Consenso temporal"
    ),

    FINAL_INTERPRETATION(
            "18/18 - Interpretacao final"
    );

    private final String displayName;

    VisionStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
