package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.image.GrayImageBuffer;
import com.example.leitorgabaritoomr.vision.image.OpenCvGrayImageBufferAdapter;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mede todas as alternativas da folha usando exatamente o conjunto
 * de geometrias exibido e validado no Laboratorio OMR.
 *
 * A Mat cinza e convertida para GrayImageBuffer uma unica vez. Em
 * seguida, a mesma fotografia imutavel da imagem e compartilhada
 * por todas as chamadas ao BubbleSamplingMeasurer.
 *
 * Nenhuma regiao, centro, retangulo, nucleo, borda ou fundo local e
 * recalculado nesta classe. Cada medicao recebe diretamente uma
 * BubbleSamplingGeometry pertencente ao BubbleSamplingGeometrySet.
 */
public final class OmrSamplingSheetMeasurer {

    private final OpenCvGrayImageBufferAdapter
            grayImageBufferAdapter;

    private final BubbleSamplingMeasurer
            bubbleSamplingMeasurer;

    public OmrSamplingSheetMeasurer(
            OpenCvGrayImageBufferAdapter
                    grayImageBufferAdapter,
            BubbleSamplingMeasurer
                    bubbleSamplingMeasurer
    ) {
        if (grayImageBufferAdapter == null) {
            throw new IllegalArgumentException(
                    "OpenCvGrayImageBufferAdapter e obrigatorio."
            );
        }

        if (bubbleSamplingMeasurer == null) {
            throw new IllegalArgumentException(
                    "BubbleSamplingMeasurer e obrigatorio."
            );
        }

        this.grayImageBufferAdapter =
                grayImageBufferAdapter;

        this.bubbleSamplingMeasurer =
                bubbleSamplingMeasurer;
    }

    /**
     * Fronteira OpenCV da medicao da folha.
     *
     * A matriz deve estar em escala de cinza, com um canal e
     * profundidade CV_8U. A transferencia completa dos pixels ocorre
     * somente nesta chamada ao adaptador.
     */
    public OmrSheetMeasurementResult measure(
            Mat normalizedGray,
            OmrLayoutDefinition layout,
            BubbleSamplingGeometrySet geometrySet
    ) {
        GrayImageBuffer grayImage =
                grayImageBufferAdapter.copyFrom(
                        normalizedGray
                );

        return measure(
                grayImage,
                layout,
                geometrySet
        );
    }

    /**
     * Nucleo puro e testavel da medicao da folha.
     *
     * Este overload permite testes unitarios sem OpenCV e garante
     * que todas as alternativas sejam medidas sobre a mesma imagem
     * imutavel.
     */
    public OmrSheetMeasurementResult measure(
            GrayImageBuffer grayImage,
            OmrLayoutDefinition layout,
            BubbleSamplingGeometrySet geometrySet
    ) {
        validateInputs(
                grayImage,
                layout,
                geometrySet
        );

        List<BubbleMeasurement> measurements =
                new ArrayList<>(
                        layout.getOptionCount()
                );

        List<String> errors =
                new ArrayList<>();

        Set<String> consumedGeometryOptionIds =
                new HashSet<>();

        if (geometrySet.getGeometryCount()
                != layout.getOptionCount()) {

            errors.add(
                    "Quantidade de geometrias diferente do layout: "
                            + "geometrias="
                            + geometrySet.getGeometryCount()
                            + ", alternativas="
                            + layout.getOptionCount()
                            + "."
            );
        }

        measureLayoutOrder(
                grayImage,
                layout,
                geometrySet,
                measurements,
                errors,
                consumedGeometryOptionIds
        );

        findUnconsumedGeometries(
                geometrySet,
                consumedGeometryOptionIds,
                errors
        );

        return new OmrSheetMeasurementResult(
                layout,
                measurements,
                errors
        );
    }

    private void validateInputs(
            GrayImageBuffer grayImage,
            OmrLayoutDefinition layout,
            BubbleSamplingGeometrySet geometrySet
    ) {
        if (grayImage == null) {
            throw new IllegalArgumentException(
                    "A imagem cinza e obrigatoria."
            );
        }

        if (layout == null) {
            throw new IllegalArgumentException(
                    "O layout e obrigatorio."
            );
        }

        if (geometrySet == null) {
            throw new IllegalArgumentException(
                    "O conjunto de geometrias de amostragem"
                            + " e obrigatorio."
            );
        }

        if (!geometrySet.isComplete()) {
            throw new IllegalArgumentException(
                    "O conjunto de geometrias de amostragem"
                            + " precisa estar completo."
            );
        }

        if (!grayImage.hasDimensions(
                geometrySet.getImageWidth(),
                geometrySet.getImageHeight()
        )) {
            throw new IllegalArgumentException(
                    "A imagem cinza "
                            + grayImage.getWidth()
                            + "x"
                            + grayImage.getHeight()
                            + " nao corresponde ao conjunto de"
                            + " geometrias "
                            + geometrySet.getImageWidth()
                            + "x"
                            + geometrySet.getImageHeight()
                            + "."
            );
        }
    }

    private void measureLayoutOrder(
            GrayImageBuffer grayImage,
            OmrLayoutDefinition layout,
            BubbleSamplingGeometrySet geometrySet,
            List<BubbleMeasurement> measurements,
            List<String> errors,
            Set<String> consumedGeometryOptionIds
    ) {
        List<OmrBlockDefinition> blocks =
                layout.getBlocks();

        for (int blockIndex = 0;
             blockIndex < blocks.size();
             blockIndex++) {

            OmrBlockDefinition block =
                    blocks.get(blockIndex);

            List<OmrQuestionDefinition> questions =
                    block.getQuestions();

            for (int questionIndex = 0;
                 questionIndex < questions.size();
                 questionIndex++) {

                OmrQuestionDefinition question =
                        questions.get(questionIndex);

                List<OmrOptionDefinition> options =
                        question.getOptions();

                for (int optionIndex = 0;
                     optionIndex < options.size();
                     optionIndex++) {

                    OmrOptionDefinition option =
                            options.get(optionIndex);

                    measureOption(
                            grayImage,
                            geometrySet,
                            option,
                            blockIndex,
                            questionIndex,
                            optionIndex,
                            measurements,
                            errors,
                            consumedGeometryOptionIds
                    );
                }
            }
        }
    }

    private void measureOption(
            GrayImageBuffer grayImage,
            BubbleSamplingGeometrySet geometrySet,
            OmrOptionDefinition option,
            int blockIndex,
            int questionIndex,
            int optionIndex,
            List<BubbleMeasurement> measurements,
            List<String> errors,
            Set<String> consumedGeometryOptionIds
    ) {
        String optionId = option.getId();

        BubbleSamplingGeometry geometry =
                geometrySet.findByPosition(
                        blockIndex,
                        questionIndex,
                        optionIndex
                );

        if (geometry == null) {
            errors.add(
                    optionId
                            + ": geometria ausente na posicao "
                            + blockIndex
                            + ":"
                            + questionIndex
                            + ":"
                            + optionIndex
                            + "."
            );

            return;
        }

        if (!optionId.equals(geometry.getOptionId())) {
            errors.add(
                    optionId
                            + ": a posicao estrutural aponta para "
                            + geometry.getOptionId()
                            + "."
            );

            return;
        }

        if (!consumedGeometryOptionIds.add(optionId)) {
            errors.add(
                    optionId
                            + ": geometria consumida mais de uma vez."
            );

            return;
        }

        try {
            BubbleMeasurement measurement =
                    bubbleSamplingMeasurer.measure(
                            grayImage,
                            geometry
                    );

            if (measurement.getOption() != option
                    && !measurement
                    .getOption()
                    .getId()
                    .equals(optionId)) {

                errors.add(
                        optionId
                                + ": a medicao retornou a alternativa "
                                + measurement
                                .getOption()
                                .getId()
                                + "."
                );

                return;
            }

            measurements.add(measurement);

        } catch (RuntimeException exception) {
            errors.add(
                    optionId
                            + ": "
                            + safeMessage(exception)
            );
        }
    }

    private void findUnconsumedGeometries(
            BubbleSamplingGeometrySet geometrySet,
            Set<String> consumedGeometryOptionIds,
            List<String> errors
    ) {
        for (BubbleSamplingGeometry geometry
                : geometrySet.getGeometries()) {

            String optionId = geometry.getOptionId();

            if (!consumedGeometryOptionIds.contains(
                    optionId
            )) {
                errors.add(
                        optionId
                                + ": geometria sem alternativa"
                                + " correspondente no layout."
                );
            }
        }
    }

    private String safeMessage(
            RuntimeException exception
    ) {
        String message = exception.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }
}
