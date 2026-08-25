package com.example.leitorgabaritoomr.vision.processing;

import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAccumulator;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAccumulatorConfig;
import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.detector.ArucoMarkerDetector;
import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
import com.example.leitorgabaritoomr.vision.detector.SolidSquareMarkerDetector;
import com.example.leitorgabaritoomr.vision.drawing.BubbleMeasurementOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.LayoutOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.NormalizedRegionPreviewRenderer;
import com.example.leitorgabaritoomr.vision.drawing.QuestionComparisonOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.ResolvedMarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.StableMarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.drawing.TemporalConsensusOverlayRenderer;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolver;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurementConfig;
import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurer;
import com.example.leitorgabaritoomr.vision.measurement.OmrLayoutMeasurer;
import com.example.leitorgabaritoomr.vision.measurement.OmrQuestionMeasurer;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizer;
import com.example.leitorgabaritoomr.vision.normalization.OmrRegionNormalizerConfig;
import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorer;
import com.example.leitorgabaritoomr.vision.scoring.BubbleEvidenceScorerConfig;
import com.example.leitorgabaritoomr.vision.stability.MarkerSetStabilizer;

/**
 * Ponto unico de composicao do pipeline padrao do aplicativo.
 *
 * A Activity, os testes instrumentados e futuras fontes de imagem
 * devem solicitar o processador a esta fabrica. Assim todos usam os
 * mesmos detectores, resolvedor, estabilizador e configuracoes
 * internas criadas pelo construtor curto de MarkerFrameProcessor.
 */
public final class DefaultMarkerFrameProcessorFactory {

    private DefaultMarkerFrameProcessorFactory() {
        throw new AssertionError(
                "Esta classe nao deve ser instanciada."
        );
    }

    public static MarkerFrameProcessor create(
            MarkerDetectorMode detectorMode,
            VisionDebugController debugController
    ) {
        return create(
                detectorMode,
                debugController,
                AvalieCeDevelopmentLayoutFactory.create()
        );
    }

    /**
     * Compoe o mesmo pipeline padrao usando o layout informado.
     *
     * A mesma instancia de OmrLayoutDefinition e compartilhada pelo
     * processador e pelo acumulador temporal. Isso impede que a
     * geometria medida e a geometria acumulada pertençam a modelos
     * de folha diferentes.
     */
    public static MarkerFrameProcessor create(
            MarkerDetectorMode detectorMode,
            VisionDebugController debugController,
            OmrLayoutDefinition layoutDefinition
    ) {
        if (detectorMode == null) {
            throw new IllegalArgumentException(
                    "O modo do detector e obrigatorio."
            );
        }

        if (debugController == null) {
            throw new IllegalArgumentException(
                    "VisionDebugController e obrigatorio."
            );
        }

        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "OmrLayoutDefinition e obrigatorio."
            );
        }

        return new MarkerFrameProcessor(
                createDetector(detectorMode),
                new MarkerOverlayRenderer(),
                new MarkerSetResolver(),
                new ResolvedMarkerOverlayRenderer(),
                new MarkerSetStabilizer(),
                new StableMarkerOverlayRenderer(),
                debugController,
                new OmrRegionNormalizer(
                        OmrRegionNormalizerConfig
                                .developmentDefaults()
                ),
                new NormalizedRegionPreviewRenderer(),
                layoutDefinition,
                new LayoutOverlayRenderer(),
                new OmrLayoutMeasurer(
                        new BubbleMeasurer(
                                BubbleMeasurementConfig
                                        .developmentDefaults()
                        )
                ),
                new BubbleMeasurementOverlayRenderer(),
                new OmrQuestionMeasurer(
                        new BubbleEvidenceScorer(
                                BubbleEvidenceScorerConfig
                                        .developmentDefaults()
                        )
                ),
                new QuestionComparisonOverlayRenderer(),
                new QuestionEvidenceAccumulator(
                        QuestionEvidenceAccumulatorConfig
                                .developmentDefaults(),
                        layoutDefinition
                ),
                new TemporalConsensusOverlayRenderer()
        );
    }

    private static OmrMarkerDetector createDetector(
            MarkerDetectorMode detectorMode
    ) {
        switch (detectorMode) {

            case ARUCO:
                return new ArucoMarkerDetector();

            case SOLID_SQUARE:
                return new SolidSquareMarkerDetector();

            default:
                throw new IllegalStateException(
                        "Modo de detector nao suportado: "
                                + detectorMode
                );
        }
    }
}
