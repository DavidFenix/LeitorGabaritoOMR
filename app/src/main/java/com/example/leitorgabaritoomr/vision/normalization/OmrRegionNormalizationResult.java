package com.example.leitorgabaritoomr.vision.normalization;

import org.opencv.core.Mat;

/**
 * Resultado da tentativa de normalização da região OMR.
 *
 * Quando success for verdadeiro, normalizedRegion conterá uma nova Mat
 * que deverá ser liberada com release() quando não for mais utilizada.
 */
public final class OmrRegionNormalizationResult {

    private final boolean success;
    private final Mat normalizedRegion;
    private final int outputWidth;
    private final int outputHeight;
    private final String message;

    private OmrRegionNormalizationResult(
            boolean success,
            Mat normalizedRegion,
            int outputWidth,
            int outputHeight,
            String message
    ) {
        this.success = success;
        this.normalizedRegion = normalizedRegion;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.message = message;
    }

    public static OmrRegionNormalizationResult success(
            Mat normalizedRegion,
            int outputWidth,
            int outputHeight
    ) {
        if (normalizedRegion == null || normalizedRegion.empty()) {
            throw new IllegalArgumentException(
                    "A região normalizada não pode ser nula ou vazia."
            );
        }

        return new OmrRegionNormalizationResult(
                true,
                normalizedRegion,
                outputWidth,
                outputHeight,
                "Região OMR normalizada com sucesso."
        );
    }

    public static OmrRegionNormalizationResult failure(String message) {
        return new OmrRegionNormalizationResult(
                false,
                null,
                0,
                0,
                message
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public Mat getNormalizedRegion() {
        return normalizedRegion;
    }

    public int getOutputWidth() {
        return outputWidth;
    }

    public int getOutputHeight() {
        return outputHeight;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Libera a Mat criada pelo normalizador.
     *
     * Chamar este método mais de uma vez não causa problema.
     */
    public void release() {
        if (normalizedRegion != null) {
            normalizedRegion.release();
        }
    }
}