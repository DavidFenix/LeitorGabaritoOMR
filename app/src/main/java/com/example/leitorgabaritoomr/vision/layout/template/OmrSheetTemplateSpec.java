package com.example.leitorgabaritoomr.vision.layout.template;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Especifica, de forma imutavel, a geometria de um modelo de
 * cartao-resposta OMR.
 *
 * A especificacao descreve somente as questoes que realmente
 * existem. A ultima coluna pode, portanto, possuir menos linhas
 * que as anteriores sem criar questoes fantasmas.
 *
 * As coordenadas sao normalizadas. As dimensoes canonicas servem
 * como referencia digital para manter as regioes de amostragem
 * aproximadamente quadradas.
 */
public final class OmrSheetTemplateSpec {

    public static final int MIN_QUESTION_COUNT = 1;
    public static final int MAX_QUESTION_COUNT = 90;

    private final String templateId;
    private final int templateVersion;
    private final String templateName;

    private final int questionCount;
    private final int canonicalWidth;
    private final int canonicalHeight;
    private final int questionsPerBlock;

    private final String[] optionLabels;
    private final double[] optionLocalX;

    private final double firstRowY;
    private final double rowSpacingY;
    private final double samplingRadiusX;
    private final double samplingRadiusY;

    private final int firstQuestionNumber;

    public OmrSheetTemplateSpec(
            String templateId,
            int templateVersion,
            String templateName,
            int questionCount,
            int canonicalWidth,
            int canonicalHeight,
            int questionsPerBlock,
            String[] optionLabels,
            double[] optionLocalX,
            double firstRowY,
            double rowSpacingY,
            double samplingRadiusX,
            double samplingRadiusY,
            int firstQuestionNumber
    ) {
        this.templateId =
                requireText("templateId", templateId);

        this.templateName =
                requireText("templateName", templateName);

        if (templateVersion <= 0) {
            throw new IllegalArgumentException(
                    "templateVersion deve ser positivo."
            );
        }

        if (questionCount < MIN_QUESTION_COUNT
                || questionCount > MAX_QUESTION_COUNT) {

            throw new IllegalArgumentException(
                    "questionCount deve estar entre "
                            + MIN_QUESTION_COUNT
                            + " e "
                            + MAX_QUESTION_COUNT
                            + "."
            );
        }

        if (canonicalWidth < 100
                || canonicalHeight < 100) {

            throw new IllegalArgumentException(
                    "As dimensoes canonicas devem ser"
                            + " maiores ou iguais a 100."
            );
        }

        if (questionsPerBlock <= 0
                || questionsPerBlock > MAX_QUESTION_COUNT) {

            throw new IllegalArgumentException(
                    "questionsPerBlock deve estar entre 1 e "
                            + MAX_QUESTION_COUNT
                            + "."
            );
        }

        if (firstQuestionNumber < 0) {
            throw new IllegalArgumentException(
                    "firstQuestionNumber nao pode ser negativo."
            );
        }

        String[] normalizedLabels =
                validateAndCopyOptionLabels(optionLabels);

        double[] normalizedPositions =
                validateAndCopyOptionPositions(
                        optionLocalX,
                        normalizedLabels.length
                );

        validateNormalizedValue(
                "firstRowY",
                firstRowY
        );

        validatePositiveNormalizedValue(
                "rowSpacingY",
                rowSpacingY
        );

        validateRadius(
                "samplingRadiusX",
                samplingRadiusX
        );

        validateRadius(
                "samplingRadiusY",
                samplingRadiusY
        );

        int blockCount = calculateBlockCount(
                questionCount,
                questionsPerBlock
        );

        validateVerticalGeometry(
                questionsPerBlock,
                firstRowY,
                rowSpacingY,
                samplingRadiusY
        );

        validateHorizontalGeometry(
                blockCount,
                normalizedPositions,
                samplingRadiusX
        );

        this.templateVersion = templateVersion;
        this.questionCount = questionCount;
        this.canonicalWidth = canonicalWidth;
        this.canonicalHeight = canonicalHeight;
        this.questionsPerBlock = questionsPerBlock;
        this.optionLabels = normalizedLabels;
        this.optionLocalX = normalizedPositions;
        this.firstRowY = firstRowY;
        this.rowSpacingY = rowSpacingY;
        this.samplingRadiusX = samplingRadiusX;
        this.samplingRadiusY = samplingRadiusY;
        this.firstQuestionNumber = firstQuestionNumber;
    }

    private String[] validateAndCopyOptionLabels(
            String[] labels
    ) {
        if (labels == null || labels.length < 2) {
            throw new IllegalArgumentException(
                    "O modelo deve possuir pelo menos"
                            + " duas alternativas."
            );
        }

        String[] copy = new String[labels.length];
        Set<String> uniqueLabels = new HashSet<>();

        for (int index = 0;
             index < labels.length;
             index++) {

            String label = requireText(
                    "optionLabels[" + index + "]",
                    labels[index]
            );

            if (!uniqueLabels.add(label)) {
                throw new IllegalArgumentException(
                        "Label de alternativa repetido: "
                                + label
                );
            }

            copy[index] = label;
        }

        return copy;
    }

    private double[] validateAndCopyOptionPositions(
            double[] positions,
            int expectedCount
    ) {
        if (positions == null
                || positions.length != expectedCount) {

            throw new IllegalArgumentException(
                    "Deve existir uma posicao horizontal"
                            + " para cada alternativa."
            );
        }

        double[] copy = positions.clone();
        double previous = -1.0;

        for (int index = 0;
             index < copy.length;
             index++) {

            double position = copy[index];

            validateNormalizedValue(
                    "optionLocalX[" + index + "]",
                    position
            );

            if (position <= previous) {
                throw new IllegalArgumentException(
                        "As posicoes das alternativas devem"
                                + " estar em ordem crescente."
                );
            }

            previous = position;
        }

        return copy;
    }

    private void validateVerticalGeometry(
            int rowCapacity,
            double firstRow,
            double rowSpacing,
            double radiusY
    ) {
        double lastRow =
                firstRow
                        + (rowCapacity - 1)
                        * rowSpacing;

        if (firstRow - radiusY < 0.0
                || lastRow + radiusY > 1.0) {

            throw new IllegalArgumentException(
                    "As linhas e suas regioes de amostragem"
                            + " ultrapassam os limites verticais"
                            + " do modelo."
            );
        }

        if (rowCapacity > 1
                && rowSpacing <= radiusY * 2.0) {

            throw new IllegalArgumentException(
                    "As regioes de linhas consecutivas"
                            + " nao podem se sobrepor."
            );
        }
    }

    private void validateHorizontalGeometry(
            int blockCount,
            double[] localPositions,
            double radiusX
    ) {
        double blockWidth = 1.0 / blockCount;
        double previousRight = -1.0;

        for (int blockIndex = 0;
             blockIndex < blockCount;
             blockIndex++) {

            double blockLeft = blockIndex * blockWidth;

            for (double localX : localPositions) {
                double centerX =
                        blockLeft
                                + localX * blockWidth;

                double left = centerX - radiusX;
                double right = centerX + radiusX;

                if (left < 0.0 || right > 1.0) {
                    throw new IllegalArgumentException(
                            "Uma regiao de alternativa ultrapassa"
                                    + " os limites horizontais"
                                    + " do modelo."
                    );
                }

                if (left <= previousRight) {
                    throw new IllegalArgumentException(
                            "As regioes de alternativas"
                                    + " nao podem se sobrepor."
                    );
                }

                previousRight = right;
            }
        }
    }

    private int calculateBlockCount(
            int totalQuestions,
            int rowCapacity
    ) {
        return (totalQuestions + rowCapacity - 1)
                / rowCapacity;
    }

    private void validateNormalizedValue(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve estar entre 0.0 e 1.0."
            );
        }
    }

    private void validatePositiveNormalizedValue(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser maior que 0.0"
                            + " e menor ou igual a 1.0."
            );
        }
    }

    private void validateRadius(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0
                || value > 0.5) {

            throw new IllegalArgumentException(
                    fieldName
                            + " deve ser maior que 0.0"
                            + " e menor ou igual a 0.5."
            );
        }
    }

    private String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getTemplateId() {
        return templateId;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getCanonicalWidth() {
        return canonicalWidth;
    }

    public int getCanonicalHeight() {
        return canonicalHeight;
    }

    public int getQuestionsPerBlock() {
        return questionsPerBlock;
    }

    public int getBlockCount() {
        return calculateBlockCount(
                questionCount,
                questionsPerBlock
        );
    }

    public int getQuestionCountForBlock(
            int blockIndex
    ) {
        if (blockIndex < 0
                || blockIndex >= getBlockCount()) {

            throw new IllegalArgumentException(
                    "blockIndex fora dos limites do modelo."
            );
        }

        int firstQuestionIndex =
                blockIndex * questionsPerBlock;

        int remaining =
                questionCount - firstQuestionIndex;

        return Math.min(
                questionsPerBlock,
                remaining
        );
    }

    public String[] getOptionLabels() {
        return optionLabels.clone();
    }

    public double[] getOptionLocalX() {
        return optionLocalX.clone();
    }

    public int getOptionCount() {
        return optionLabels.length;
    }

    public int getTotalOptionCount() {
        return questionCount * getOptionCount();
    }

    public double getFirstRowY() {
        return firstRowY;
    }

    public double getRowSpacingY() {
        return rowSpacingY;
    }

    public double getSamplingRadiusX() {
        return samplingRadiusX;
    }

    public double getSamplingRadiusY() {
        return samplingRadiusY;
    }

    public int getFirstQuestionNumber() {
        return firstQuestionNumber;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s@v%d[questoes=%d, blocos=%d, alternativas=%d, canvas=%dx%d]",
                templateId,
                templateVersion,
                questionCount,
                getBlockCount(),
                getOptionCount(),
                canonicalWidth,
                canonicalHeight
        );
    }
}
