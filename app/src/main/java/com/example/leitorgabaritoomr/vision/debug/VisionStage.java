package com.example.leitorgabaritoomr.vision.debug;

public enum VisionStage {

    ORIGINAL("1/12 - Original"),

    GRAYSCALE("2/12 - Escala de cinza"),

    BLURRED("3/12 - Suavizacao"),

    BINARY("4/12 - Imagem binaria"),

    ACCEPTED_CANDIDATES(
            "5/12 - Candidatos aceitos"
    ),

    RESOLVED_MARKERS(
            "6/12 - Quatro marcadores"
    ),

    STABLE_MARKERS(
            "7/12 - Estabilidade temporal"
    ),

    NORMALIZED_REGION(
            "8/12 - Regiao normalizada"
    ),

    LAYOUT_MAP(
            "9/12 - Mapa esperado das bolhas"
    ),

    BUBBLE_MEASUREMENTS(
            "10/12 - Medicao das bolhas"
    ),

    QUESTION_COMPARISON(
            "11/12 - Comparacao por questao"
    ),

    TEMPORAL_CONSENSUS(
            "12/12 - Consenso temporal"
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
//    ORIGINAL("1/11 - Original"),
//
//    GRAYSCALE("2/11 - Escala de cinza"),
//
//    BLURRED("3/11 - Suavizacao"),
//
//    BINARY("4/11 - Imagem binaria"),
//
//    ACCEPTED_CANDIDATES(
//            "5/11 - Candidatos aceitos"
//    ),
//
//    RESOLVED_MARKERS(
//            "6/11 - Quatro marcadores"
//    ),
//
//    STABLE_MARKERS(
//            "7/11 - Estabilidade temporal"
//    ),
//
//    NORMALIZED_REGION(
//            "8/11 - Regiao normalizada"
//    ),
//
//    LAYOUT_MAP(
//            "9/11 - Mapa esperado das bolhas"
//    ),
//
//    BUBBLE_MEASUREMENTS(
//            "10/11 - Medicao das bolhas"
//    ),
//
//    QUESTION_COMPARISON(
//            "11/11 - Comparacao por questao"
//    );
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
//package com.example.leitorgabaritoomr.vision.debug;
//
//public enum VisionStage {
//
//    ORIGINAL("1/10 - Original"),
//
//    GRAYSCALE("2/10 - Escala de cinza"),
//
//    BLURRED("3/10 - Suavizacao"),
//
//    BINARY("4/10 - Imagem binaria"),
//
//    ACCEPTED_CANDIDATES(
//            "5/10 - Candidatos aceitos"
//    ),
//
//    RESOLVED_MARKERS(
//            "6/10 - Quatro marcadores"
//    ),
//
//    STABLE_MARKERS(
//            "7/10 - Estabilidade temporal"
//    ),
//
//    NORMALIZED_REGION(
//            "8/10 - Regiao normalizada"
//    ),
//
//    LAYOUT_MAP(
//            "9/10 - Mapa esperado das bolhas"
//    ),
//
//    BUBBLE_MEASUREMENTS(
//            "10/10 - Medicao das bolhas"
//    );
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

//package com.example.leitorgabaritoomr.vision.debug;
//
//public enum VisionStage {
//
//    ORIGINAL("1/9 - Original"),
//
//    GRAYSCALE("2/9 - Escala de cinza"),
//
//    BLURRED("3/9 - Suavizacao"),
//
//    BINARY("4/9 - Imagem binaria"),
//
//    ACCEPTED_CANDIDATES(
//            "5/9 - Candidatos aceitos"
//    ),
//
//    RESOLVED_MARKERS(
//            "6/9 - Quatro marcadores"
//    ),
//
//    STABLE_MARKERS(
//            "7/9 - Estabilidade temporal"
//    ),
//
//    NORMALIZED_REGION(
//            "8/9 - Regiao normalizada"
//    ),
//
//    LAYOUT_MAP(
//            "9/9 - Mapa esperado das bolhas"
//    );
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
//package com.example.leitorgabaritoomr.vision.debug;
//
//public enum VisionStage {
//
//    ORIGINAL("1/8 - Original"),
//
//    GRAYSCALE("2/8 - Escala de cinza"),
//
//    BLURRED("3/8 - Suavizacao"),
//
//    BINARY("4/8 - Imagem binaria"),
//
//    ACCEPTED_CANDIDATES(
//            "5/8 - Candidatos aceitos"
//    ),
//
//    RESOLVED_MARKERS(
//            "6/8 - Quatro marcadores"
//    ),
//
//    STABLE_MARKERS(
//            "7/8 - Estabilidade temporal"
//    ),
//
//    NORMALIZED_REGION(
//            "8/8 - Regiao normalizada"
//    );
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