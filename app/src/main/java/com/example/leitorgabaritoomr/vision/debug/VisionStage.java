package com.example.leitorgabaritoomr.vision.debug;

public enum VisionStage {

    ORIGINAL("1/5 — Original"),
    GRAYSCALE("2/5 — Escala de cinza"),
    BLURRED("3/5 — Suavização"),
    BINARY("4/5 — Imagem binária"),
    ACCEPTED_CANDIDATES("5/5 — Candidatos aceitos");

    private final String displayName;

    VisionStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}