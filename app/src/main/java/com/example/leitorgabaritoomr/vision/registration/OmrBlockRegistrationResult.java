package com.example.leitorgabaritoomr.vision.registration;

import java.util.Locale;

/**
 * Resultado do registro geométrico de um bloco.
 */
public final class OmrBlockRegistrationResult {

    private final String blockId;
    private final boolean accepted;

    private final PixelAffineTransform transform;

    private final int expectedOptionCount;
    private final int registeredOptionCount;
    private final int directlyDetectedCount;

    private final double confidence;
    private final double medianResidualPixels;

    private final String message;

    private OmrBlockRegistrationResult(
            String blockId,
            boolean accepted,
            PixelAffineTransform transform,
            int expectedOptionCount,
            int registeredOptionCount,
            int directlyDetectedCount,
            double confidence,
            double medianResidualPixels,
            String message
    ) {
        this.blockId =
                requireText(
                        "blockId",
                        blockId
                );

        if (expectedOptionCount <= 0) {
            throw new IllegalArgumentException(
                    "expectedOptionCount deve ser positivo."
            );
        }

        if (registeredOptionCount < 0
                || registeredOptionCount
                > expectedOptionCount) {

            throw new IllegalArgumentException(
                    "registeredOptionCount é inválido."
            );
        }

        if (directlyDetectedCount < 0
                || directlyDetectedCount
                > registeredOptionCount) {

            throw new IllegalArgumentException(
                    "directlyDetectedCount é inválido."
            );
        }

        validateConfidence(confidence);

        if (!Double.isFinite(medianResidualPixels)
                || medianResidualPixels < 0.0) {

            throw new IllegalArgumentException(
                    "medianResidualPixels deve ser finito"
                            + " e não negativo."
            );
        }

        if (accepted) {
            if (transform == null) {
                throw new IllegalArgumentException(
                        "Um bloco aceito precisa"
                                + " de transformação."
                );
            }

            if (registeredOptionCount
                    != expectedOptionCount) {

                throw new IllegalArgumentException(
                        "Um bloco aceito precisa registrar"
                                + " todas as alternativas."
                );
            }
        }

        this.accepted = accepted;
        this.transform = transform;

        this.expectedOptionCount =
                expectedOptionCount;

        this.registeredOptionCount =
                registeredOptionCount;

        this.directlyDetectedCount =
                directlyDetectedCount;

        this.confidence = confidence;

        this.medianResidualPixels =
                medianResidualPixels;

        this.message =
                message == null
                        ? ""
                        : message.trim();
    }

    public static OmrBlockRegistrationResult accepted(
            String blockId,
            PixelAffineTransform transform,
            int expectedOptionCount,
            int directlyDetectedCount,
            double confidence,
            double medianResidualPixels
    ) {
        return new OmrBlockRegistrationResult(
                blockId,
                true,
                transform,
                expectedOptionCount,
                expectedOptionCount,
                directlyDetectedCount,
                confidence,
                medianResidualPixels,
                "Bloco registrado com sucesso."
        );
    }

    public static OmrBlockRegistrationResult rejected(
            String blockId,
            int expectedOptionCount,
            int registeredOptionCount,
            int directlyDetectedCount,
            double confidence,
            double medianResidualPixels,
            String message
    ) {
        return new OmrBlockRegistrationResult(
                blockId,
                false,
                null,
                expectedOptionCount,
                registeredOptionCount,
                directlyDetectedCount,
                confidence,
                medianResidualPixels,
                message
        );
    }

    private String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    private void validateConfidence(
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    "confidence deve estar entre 0.0 e 1.0."
            );
        }
    }

    public String getBlockId() {
        return blockId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean hasTransform() {
        return transform != null;
    }

    public PixelAffineTransform getTransform() {
        if (transform == null) {
            throw new IllegalStateException(
                    "O bloco rejeitado não possui"
                            + " transformação aceita."
            );
        }

        return transform;
    }

    public int getExpectedOptionCount() {
        return expectedOptionCount;
    }

    public int getRegisteredOptionCount() {
        return registeredOptionCount;
    }

    public int getDirectlyDetectedCount() {
        return directlyDetectedCount;
    }

    public int getInferredCount() {
        return registeredOptionCount
                - directlyDetectedCount;
    }

    public double getDirectDetectionRatio() {
        if (expectedOptionCount <= 0) {
            return 0.0;
        }

        return directlyDetectedCount
                / (double) expectedOptionCount;
    }

    public double getCompletionRatio() {
        if (expectedOptionCount <= 0) {
            return 0.0;
        }

        return registeredOptionCount
                / (double) expectedOptionCount;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getMedianResidualPixels() {
        return medianResidualPixels;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s %s direto=%d/%d registrado=%d/%d"
                        + " confiança=%.3f residual=%.2f",
                blockId,
                accepted ? "ACCEPTED" : "REJECTED",
                directlyDetectedCount,
                expectedOptionCount,
                registeredOptionCount,
                expectedOptionCount,
                confidence,
                medianResidualPixels
        );
    }
}