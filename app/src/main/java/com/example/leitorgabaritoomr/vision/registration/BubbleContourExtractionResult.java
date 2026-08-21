package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado da extração de contornos candidatos a bolhas.
 *
 * Não mantém objetos Mat e, portanto, não exige release().
 */
public final class BubbleContourExtractionResult {

    private final boolean success;

    private final List<BubbleContourCandidate>
            candidates;

    private final int totalContourCount;
    private final int rejectedContourCount;

    private final int adaptiveBlockSize;

    private final String message;

    private BubbleContourExtractionResult(
            boolean success,
            List<BubbleContourCandidate> candidates,
            int totalContourCount,
            int rejectedContourCount,
            int adaptiveBlockSize,
            String message
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException(
                    "A lista de candidatos é obrigatória."
            );
        }

        if (totalContourCount < 0
                || rejectedContourCount < 0
                || rejectedContourCount
                > totalContourCount) {

            throw new IllegalArgumentException(
                    "As quantidades de contornos são inválidas."
            );
        }

        if (adaptiveBlockSize != 0
                && (
                adaptiveBlockSize < 3
                        || adaptiveBlockSize % 2 == 0
        )) {
            throw new IllegalArgumentException(
                    "adaptiveBlockSize deve ser zero"
                            + " ou um número ímpar maior"
                            + " ou igual a 3."
            );
        }

        this.success = success;

        this.candidates =
                Collections.unmodifiableList(
                        new ArrayList<>(candidates)
                );

        this.totalContourCount =
                totalContourCount;

        this.rejectedContourCount =
                rejectedContourCount;

        this.adaptiveBlockSize =
                adaptiveBlockSize;

        this.message =
                message == null
                        ? ""
                        : message.trim();
    }

    public static BubbleContourExtractionResult success(
            List<BubbleContourCandidate> candidates,
            int totalContourCount,
            int rejectedContourCount,
            int adaptiveBlockSize
    ) {
        return new BubbleContourExtractionResult(
                true,
                candidates,
                totalContourCount,
                rejectedContourCount,
                adaptiveBlockSize,
                "Contornos extraídos com sucesso."
        );
    }

    public static BubbleContourExtractionResult failure(
            String message
    ) {
        return new BubbleContourExtractionResult(
                false,
                Collections.emptyList(),
                0,
                0,
                0,
                message
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public List<BubbleContourCandidate>
    getCandidates() {

        return candidates;
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    public int getTotalContourCount() {
        return totalContourCount;
    }

    public int getRejectedContourCount() {
        return rejectedContourCount;
    }

    public int getAdaptiveBlockSize() {
        return adaptiveBlockSize;
    }

    public String getMessage() {
        return message;
    }
}