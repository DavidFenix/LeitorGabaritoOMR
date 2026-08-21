package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurementGeometry;

import java.util.Locale;

/**
 * Resultado final do registro de uma alternativa.
 *
 * Contém a mesma BubbleMeasurementGeometry que futuramente
 * será consumida pelo BubbleMeasurer e pelo Laboratório OMR.
 */
public final class RegisteredBubble {

    private final BubbleMeasurementGeometry geometry;

    private final BubbleRegistrationSource source;

    /*
     * Centro previsto originalmente pelo layout.
     */
    private final double expectedCenterX;
    private final double expectedCenterY;

    /*
     * Centro observado diretamente na imagem.
     *
     * Só existe quando source == DIRECT_DETECTION.
     */
    private final boolean directObservationAvailable;

    private final double observedCenterX;
    private final double observedCenterY;

    /*
     * Confiança desta posição final.
     */
    private final double confidence;

    /*
     * Distância entre a observação direta e o modelo
     * geométrico que produziu a região final.
     *
     * Só existe para detecção direta.
     */
    private final double fitResidualPixels;

    private RegisteredBubble(
            BubbleMeasurementGeometry geometry,
            BubbleRegistrationSource source,
            double expectedCenterX,
            double expectedCenterY,
            boolean directObservationAvailable,
            double observedCenterX,
            double observedCenterY,
            double confidence,
            double fitResidualPixels
    ) {
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "A geometria registrada é obrigatória."
            );
        }

        if (source == null) {
            throw new IllegalArgumentException(
                    "A origem do registro é obrigatória."
            );
        }

        validateFinite(
                "expectedCenterX",
                expectedCenterX
        );

        validateFinite(
                "expectedCenterY",
                expectedCenterY
        );

        validateConfidence(confidence);

        if (directObservationAvailable) {
            validateFinite(
                    "observedCenterX",
                    observedCenterX
            );

            validateFinite(
                    "observedCenterY",
                    observedCenterY
            );

            if (!Double.isFinite(fitResidualPixels)
                    || fitResidualPixels < 0.0) {

                throw new IllegalArgumentException(
                        "fitResidualPixels deve ser finito"
                                + " e não negativo."
                );
            }

            if (!source.isDirectDetection()) {
                throw new IllegalArgumentException(
                        "Uma observação direta exige"
                                + " DIRECT_DETECTION."
                );
            }

        } else if (source.isDirectDetection()) {
            throw new IllegalArgumentException(
                    "DIRECT_DETECTION exige"
                            + " uma observação direta."
            );
        }

        this.geometry = geometry;
        this.source = source;

        this.expectedCenterX =
                expectedCenterX;

        this.expectedCenterY =
                expectedCenterY;

        this.directObservationAvailable =
                directObservationAvailable;

        this.observedCenterX =
                observedCenterX;

        this.observedCenterY =
                observedCenterY;

        this.confidence =
                confidence;

        this.fitResidualPixels =
                fitResidualPixels;
    }

    public static RegisteredBubble directlyDetected(
            BubbleMeasurementGeometry geometry,
            double expectedCenterX,
            double expectedCenterY,
            double observedCenterX,
            double observedCenterY,
            double confidence,
            double fitResidualPixels
    ) {
        return new RegisteredBubble(
                geometry,
                BubbleRegistrationSource
                        .DIRECT_DETECTION,
                expectedCenterX,
                expectedCenterY,
                true,
                observedCenterX,
                observedCenterY,
                confidence,
                fitResidualPixels
        );
    }

    public static RegisteredBubble inferred(
            BubbleMeasurementGeometry geometry,
            BubbleRegistrationSource source,
            double expectedCenterX,
            double expectedCenterY,
            double confidence
    ) {
        if (source == null
                || !source.isInferred()) {

            throw new IllegalArgumentException(
                    "A origem deve representar"
                            + " uma inferência."
            );
        }

        return new RegisteredBubble(
                geometry,
                source,
                expectedCenterX,
                expectedCenterY,
                false,
                0.0,
                0.0,
                confidence,
                0.0
        );
    }

    private void validateFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser finito."
            );
        }
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

    public BubbleMeasurementGeometry getGeometry() {
        return geometry;
    }

    public OmrOptionDefinition getOption() {
        return geometry.getOption();
    }

    public BubbleRegistrationSource getSource() {
        return source;
    }

    public double getExpectedCenterX() {
        return expectedCenterX;
    }

    public double getExpectedCenterY() {
        return expectedCenterY;
    }

    public double getRegisteredCenterX() {
        PixelRectangle region =
                geometry.getBubbleRegion();

        return region.getLeft()
                + (
                region.getWidth() - 1
        ) / 2.0;
    }

    public double getRegisteredCenterY() {
        PixelRectangle region =
                geometry.getBubbleRegion();

        return region.getTop()
                + (
                region.getHeight() - 1
        ) / 2.0;
    }

    public double getOffsetX() {
        return getRegisteredCenterX()
                - expectedCenterX;
    }

    public double getOffsetY() {
        return getRegisteredCenterY()
                - expectedCenterY;
    }

    public boolean hasDirectObservation() {
        return directObservationAvailable;
    }

    public double getObservedCenterX() {
        requireDirectObservation();

        return observedCenterX;
    }

    public double getObservedCenterY() {
        requireDirectObservation();

        return observedCenterY;
    }

    public double getFitResidualPixels() {
        requireDirectObservation();

        return fitResidualPixels;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isDirectlyDetected() {
        return source.isDirectDetection();
    }

    public boolean isInferred() {
        return source.isInferred();
    }

    private void requireDirectObservation() {
        if (!directObservationAvailable) {
            throw new IllegalStateException(
                    "Esta bolha não possui"
                            + " observação direta."
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s source=%s offset=(%.2f, %.2f)"
                        + " confidence=%.3f",
                getOption().getId(),
                source,
                getOffsetX(),
                getOffsetY(),
                confidence
        );
    }
}