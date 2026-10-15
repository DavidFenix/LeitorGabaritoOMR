package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Bloco sequencial de questoes pertencente a um componente curricular.
 *
 * Area e codigo sao opcionais e livres. Assim, o dominio nao fica preso a
 * uma lista fixa de disciplinas ou a uma organizacao especifica de escola.
 */
public final class OmrAssessmentBlock
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String blockId;
    private final int order;
    private final String areaName;
    private final String componentCode;
    private final String componentName;
    private final int firstQuestionPosition;
    private final int questionCount;
    private final OmrAssessmentScoringPolicy scoringPolicy;

    public OmrAssessmentBlock(
            String blockId,
            int order,
            String areaName,
            String componentCode,
            String componentName,
            int firstQuestionPosition,
            int questionCount,
            OmrAssessmentScoringPolicy scoringPolicy
    ) {
        this.blockId = requireText("blockId", blockId);

        if (order <= 0) {
            throw new IllegalArgumentException(
                    "A ordem do bloco deve ser positiva."
            );
        }

        if (firstQuestionPosition <= 0) {
            throw new IllegalArgumentException(
                    "A primeira posicao do bloco deve ser positiva."
            );
        }

        if (questionCount <= 0) {
            throw new IllegalArgumentException(
                    "O bloco deve possuir pelo menos uma questao."
            );
        }

        long lastPosition = (long) firstQuestionPosition
                + questionCount
                - 1L;

        if (lastPosition > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "O intervalo de questoes excede o limite suportado."
            );
        }

        if (scoringPolicy == null) {
            throw new IllegalArgumentException(
                    "A politica de nota do bloco e obrigatoria."
            );
        }

        this.order = order;
        this.areaName = optionalText(areaName);
        this.componentCode = optionalText(componentCode);
        this.componentName = requireText(
                "componentName",
                componentName
        );
        this.firstQuestionPosition = firstQuestionPosition;
        this.questionCount = questionCount;
        this.scoringPolicy = scoringPolicy;
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        String normalized = optionalText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public String getBlockId() {
        return blockId;
    }

    public int getOrder() {
        return order;
    }

    public String getAreaName() {
        return areaName;
    }

    public boolean hasAreaName() {
        return areaName != null;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public boolean hasComponentCode() {
        return componentCode != null;
    }

    public String getComponentName() {
        return componentName;
    }

    public int getFirstQuestionPosition() {
        return firstQuestionPosition;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getLastQuestionPosition() {
        return firstQuestionPosition + questionCount - 1;
    }

    public OmrAssessmentScoringPolicy getScoringPolicy() {
        return scoringPolicy;
    }

    public boolean containsQuestionPosition(int position) {
        return position >= firstQuestionPosition
                && position <= getLastQuestionPosition();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentBlock)) {
            return false;
        }

        OmrAssessmentBlock that = (OmrAssessmentBlock) other;

        return order == that.order
                && firstQuestionPosition
                == that.firstQuestionPosition
                && questionCount == that.questionCount
                && blockId.equals(that.blockId)
                && Objects.equals(areaName, that.areaName)
                && Objects.equals(
                componentCode,
                that.componentCode
        )
                && componentName.equals(that.componentName)
                && scoringPolicy.equals(that.scoringPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                blockId,
                order,
                areaName,
                componentCode,
                componentName,
                firstQuestionPosition,
                questionCount,
                scoringPolicy
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[%d:%s, questions=%d-%d, policy=%s@v%d]",
                blockId,
                order,
                componentName,
                firstQuestionPosition,
                getLastQuestionPosition(),
                scoringPolicy.getPolicyId(),
                scoringPolicy.getVersion()
        );
    }
}
