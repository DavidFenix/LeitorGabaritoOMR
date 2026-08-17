package com.example.leitorgabaritoomr.vision.debug;

public enum VisionStage {

    ORIGINAL("1/8 - Original"),

    GRAYSCALE("2/8 - Escala de cinza"),

    BLURRED("3/8 - Suavizacao"),

    BINARY("4/8 - Imagem binaria"),

    ACCEPTED_CANDIDATES(
            "5/8 - Candidatos aceitos"
    ),

    RESOLVED_MARKERS(
            "6/8 - Quatro marcadores"
    ),

    STABLE_MARKERS(
            "7/8 - Estabilidade temporal"
    ),

    NORMALIZED_REGION(
            "8/8 - Regiao normalizada"
    );

    private final String displayName;

    VisionStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
//package com.example.leitorgabaritoomr.vision.debug;
//
//public enum VisionStage {
//
//    ORIGINAL("1/7 - Original"),
//    GRAYSCALE("2/7 - Escala de cinza"),
//    BLURRED("3/7 - Suavizacao"),
//    BINARY("4/7 - Imagem binaria"),
//    ACCEPTED_CANDIDATES("5/7 - Candidatos aceitos"),
//    RESOLVED_MARKERS("6/7 - Quatro marcadores"),
//    STABLE_MARKERS("7/7 - Estabilidade temporal");
//
//    private final String displayName;
//
//    VisionStage(String displayName) {
//        this.displayName = displayName;
//    }
//
//    public String getDisplayName() {
//        return displayName;
//    }
//}