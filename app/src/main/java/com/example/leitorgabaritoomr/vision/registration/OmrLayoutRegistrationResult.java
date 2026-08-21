package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resultado completo do registro da grade OMR.
 *
 * Pode representar tanto um registro aceito quanto uma tentativa
 * rejeitada. Assim, o Laboratório consegue mostrar por que o
 * frame não foi utilizado.
 */
public final class OmrLayoutRegistrationResult {

    private final OmrLayoutDefinition layout;
    private final boolean accepted;

    private final PixelAffineTransform sheetTransform;

    private final List<RegisteredBubble>
            registeredBubbles;

    private final Map<String, RegisteredBubble>
            bubblesByOptionId;

    private final List<OmrBlockRegistrationResult>
            blockResults;

    private final Map<String, OmrBlockRegistrationResult>
            blockResultsById;

    private final List<String> errors;

    private final double confidence;

    private final int directlyDetectedCount;
    private final int inferredCount;

    private OmrLayoutRegistrationResult(
            OmrLayoutDefinition layout,
            boolean accepted,
            PixelAffineTransform sheetTransform,
            List<RegisteredBubble> registeredBubbles,
            List<OmrBlockRegistrationResult> blockResults,
            List<String> errors,
            double confidence
    ) {
        if (layout == null) {
            throw new IllegalArgumentException(
                    "O layout é obrigatório."
            );
        }

        if (registeredBubbles == null
                || blockResults == null
                || errors == null) {

            throw new IllegalArgumentException(
                    "As listas do resultado são obrigatórias."
            );
        }

        validateConfidence(confidence);

        Set<String> expectedOptionIds =
                collectExpectedOptionIds(layout);

        List<RegisteredBubble> bubbleCopy =
                new ArrayList<>(
                        registeredBubbles
                );

        Map<String, RegisteredBubble>
                bubbleIndex = new HashMap<>();

        int directCount = 0;
        int inferredBubbleCount = 0;

        for (RegisteredBubble bubble
                : bubbleCopy) {

            if (bubble == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter"
                                + " bolhas nulas."
                );
            }

            String optionId =
                    bubble
                            .getOption()
                            .getId();

            if (!expectedOptionIds.contains(
                    optionId
            )) {
                throw new IllegalArgumentException(
                        "A alternativa registrada não pertence"
                                + " ao layout: "
                                + optionId
                );
            }

            if (bubbleIndex.put(
                    optionId,
                    bubble
            ) != null) {

                throw new IllegalArgumentException(
                        "Alternativa registrada repetidamente: "
                                + optionId
                );
            }

            if (bubble.isDirectlyDetected()) {
                directCount++;
            } else {
                inferredBubbleCount++;
            }
        }

        List<OmrBlockRegistrationResult>
                blockCopy =
                new ArrayList<>(blockResults);

        Map<String, OmrBlockRegistrationResult>
                blockIndex = new HashMap<>();

        int acceptedBlockCount = 0;

        for (OmrBlockRegistrationResult blockResult
                : blockCopy) {

            if (blockResult == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter"
                                + " resultados de bloco nulos."
                );
            }

            String blockId =
                    blockResult.getBlockId();

            if (blockIndex.put(
                    blockId,
                    blockResult
            ) != null) {

                throw new IllegalArgumentException(
                        "Resultado repetido para o bloco: "
                                + blockId
                );
            }

            if (blockResult.isAccepted()) {
                acceptedBlockCount++;
            }
        }

        if (accepted) {
            if (sheetTransform == null) {
                throw new IllegalArgumentException(
                        "Um registro aceito precisa"
                                + " da transformação da folha."
                );
            }

            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Um registro aceito não pode possuir erros."
                );
            }

            if (bubbleCopy.size()
                    != layout.getOptionCount()) {

                throw new IllegalArgumentException(
                        "Um registro aceito precisa registrar"
                                + " todas as alternativas."
                );
            }

            if (blockCopy.size()
                    != layout.getBlockCount()
                    || acceptedBlockCount
                    != layout.getBlockCount()) {

                throw new IllegalArgumentException(
                        "Um registro aceito precisa aceitar"
                                + " todos os blocos."
                );
            }
        }

        this.layout = layout;
        this.accepted = accepted;
        this.sheetTransform = sheetTransform;

        this.registeredBubbles =
                Collections.unmodifiableList(
                        bubbleCopy
                );

        this.bubblesByOptionId =
                Collections.unmodifiableMap(
                        bubbleIndex
                );

        this.blockResults =
                Collections.unmodifiableList(
                        blockCopy
                );

        this.blockResultsById =
                Collections.unmodifiableMap(
                        blockIndex
                );

        this.errors =
                Collections.unmodifiableList(
                        new ArrayList<>(errors)
                );

        this.confidence = confidence;

        this.directlyDetectedCount =
                directCount;

        this.inferredCount =
                inferredBubbleCount;
    }

    public static OmrLayoutRegistrationResult accepted(
            OmrLayoutDefinition layout,
            PixelAffineTransform sheetTransform,
            List<RegisteredBubble> registeredBubbles,
            List<OmrBlockRegistrationResult> blockResults,
            double confidence
    ) {
        return new OmrLayoutRegistrationResult(
                layout,
                true,
                sheetTransform,
                registeredBubbles,
                blockResults,
                Collections.emptyList(),
                confidence
        );
    }

    public static OmrLayoutRegistrationResult rejected(
            OmrLayoutDefinition layout,
            PixelAffineTransform tentativeSheetTransform,
            List<RegisteredBubble> partialBubbles,
            List<OmrBlockRegistrationResult> blockResults,
            List<String> errors,
            double confidence
    ) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Um registro rejeitado precisa"
                            + " informar pelo menos um erro."
            );
        }

        return new OmrLayoutRegistrationResult(
                layout,
                false,
                tentativeSheetTransform,
                partialBubbles,
                blockResults,
                errors,
                confidence
        );
    }

    private Set<String> collectExpectedOptionIds(
            OmrLayoutDefinition layout
    ) {
        Set<String> optionIds =
                new HashSet<>();

        for (OmrBlockDefinition block
                : layout.getBlocks()) {

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                for (OmrOptionDefinition option
                        : question.getOptions()) {

                    if (!optionIds.add(
                            option.getId()
                    )) {

                        throw new IllegalArgumentException(
                                "O layout possui alternativa"
                                        + " repetida: "
                                        + option.getId()
                        );
                    }
                }
            }
        }

        return optionIds;
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

    public OmrLayoutDefinition getLayout() {
        return layout;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean hasSheetTransform() {
        return sheetTransform != null;
    }

    public PixelAffineTransform getSheetTransform() {
        if (sheetTransform == null) {
            throw new IllegalStateException(
                    "O resultado não possui"
                            + " transformação da folha."
            );
        }

        return sheetTransform;
    }

    public List<RegisteredBubble>
    getRegisteredBubbles() {

        return registeredBubbles;
    }

    public RegisteredBubble findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return bubblesByOptionId.get(
                optionId
        );
    }

    public List<OmrBlockRegistrationResult>
    getBlockResults() {

        return blockResults;
    }

    public OmrBlockRegistrationResult
    findBlockResult(
            String blockId
    ) {
        if (blockId == null) {
            return null;
        }

        return blockResultsById.get(
                blockId
        );
    }

    public List<String> getErrors() {
        return errors;
    }

    public int getExpectedOptionCount() {
        return layout.getOptionCount();
    }

    public int getRegisteredOptionCount() {
        return registeredBubbles.size();
    }

    public int getDirectlyDetectedCount() {
        return directlyDetectedCount;
    }

    public int getInferredCount() {
        return inferredCount;
    }

    public double getCompletionRatio() {
        if (getExpectedOptionCount() <= 0) {
            return 0.0;
        }

        return getRegisteredOptionCount()
                / (double) getExpectedOptionCount();
    }

    public double getDirectDetectionRatio() {
        if (getExpectedOptionCount() <= 0) {
            return 0.0;
        }

        return directlyDetectedCount
                / (double) getExpectedOptionCount();
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isComplete() {
        return registeredBubbles.size()
                == layout.getOptionCount();
    }
}