package com.example.leitorgabaritoomr.vision.geometry;

public enum CornerRole {

    TOP_LEFT("TL"),
    TOP_RIGHT("TR"),
    BOTTOM_RIGHT("BR"),
    BOTTOM_LEFT("BL");

    private final String shortLabel;

    CornerRole(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public String getShortLabel() {
        return shortLabel;
    }
}