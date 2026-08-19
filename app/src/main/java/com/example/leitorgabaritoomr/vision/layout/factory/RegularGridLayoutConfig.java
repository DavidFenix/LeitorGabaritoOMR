package com.example.leitorgabaritoomr.vision.layout.factory;

import java.util.HashSet;
import java.util.Set;

/**
 * Configura uma folha OMR formada por uma grade regular.
 *
 * Não possui dependência de OpenCV, câmera, papel ou milímetros.
 */
public final class RegularGridLayoutConfig {

    private final String layoutId;
    private final int layoutVersion;
    private final String layoutName;

    private final int canonicalWidth;
    private final int canonicalHeight;

    private final int blockCount;
    private final int questionsPerBlock;

    private final String[] optionLabels;
    private final double[] optionLocalX;

    private final double firstRowY;
    private final double rowSpacingY;

    private final double samplingRadiusX;
    private final double samplingRadiusY;

    private final int firstQuestionNumber;
    private final boolean restartQuestionLabelsEachBlock;

    public RegularGridLayoutConfig(
            String layoutId,
            int layoutVersion,
            String layoutName,
            int canonicalWidth,
            int canonicalHeight,
            int blockCount,
            int questionsPerBlock,
            String[] optionLabels,
            double[] optionLocalX,
            double firstRowY,
            double rowSpacingY,
            double samplingRadiusX,
            double samplingRadiusY,
            int firstQuestionNumber,
            boolean restartQuestionLabelsEachBlock
    ) {
        this.layoutId =
                requireText("layoutId", layoutId);

        this.layoutName =
                requireText("layoutName", layoutName);

        if (layoutVersion <= 0) {
            throw new IllegalArgumentException(
                    "layoutVersion deve ser positivo."
            );
        }

        if (canonicalWidth < 100
                || canonicalHeight < 100) {

            throw new IllegalArgumentException(
                    "As dimensões canônicas devem ser"
                            + " maiores ou iguais a 100."
            );
        }

        if (blockCount <= 0) {
            throw new IllegalArgumentException(
                    "blockCount deve ser positivo."
            );
        }

        if (questionsPerBlock <= 0) {
            throw new IllegalArgumentException(
                    "questionsPerBlock deve ser positivo."
            );
        }

        validateOptions(
                optionLabels,
                optionLocalX
        );

        validateNormalizedValue(
                "firstRowY",
                firstRowY
        );

        if (!Double.isFinite(rowSpacingY)
                || rowSpacingY <= 0.0
                || rowSpacingY > 1.0) {

            throw new IllegalArgumentException(
                    "rowSpacingY deve estar entre 0.0 e 1.0."
            );
        }

        validateRadius(
                "samplingRadiusX",
                samplingRadiusX
        );

        validateRadius(
                "samplingRadiusY",
                samplingRadiusY
        );

        if (firstQuestionNumber < 0) {
            throw new IllegalArgumentException(
                    "firstQuestionNumber não pode ser negativo."
            );
        }

        double lastRowY =
                firstRowY
                        + (questionsPerBlock - 1)
                        * rowSpacingY;

        if (firstRowY - samplingRadiusY < 0.0
                || lastRowY + samplingRadiusY > 1.0) {

            throw new IllegalArgumentException(
                    "As linhas e suas regiões de amostragem"
                            + " ultrapassam os limites verticais"
                            + " do layout."
            );
        }

        validateHorizontalRegions(
                blockCount,
                optionLocalX,
                samplingRadiusX
        );

        this.layoutVersion = layoutVersion;
        this.canonicalWidth = canonicalWidth;
        this.canonicalHeight = canonicalHeight;
        this.blockCount = blockCount;
        this.questionsPerBlock = questionsPerBlock;

        this.optionLabels =
                optionLabels.clone();

        this.optionLocalX =
                optionLocalX.clone();

        this.firstRowY = firstRowY;
        this.rowSpacingY = rowSpacingY;
        this.samplingRadiusX = samplingRadiusX;
        this.samplingRadiusY = samplingRadiusY;
        this.firstQuestionNumber = firstQuestionNumber;

        this.restartQuestionLabelsEachBlock =
                restartQuestionLabelsEachBlock;
    }

    private void validateOptions(
            String[] labels,
            double[] localPositions
    ) {
        if (labels == null
                || localPositions == null
                || labels.length < 2
                || labels.length
                != localPositions.length) {

            throw new IllegalArgumentException(
                    "As alternativas devem possuir pelo menos"
                            + " dois labels e uma posição para"
                            + " cada label."
            );
        }

        Set<String> uniqueLabels =
                new HashSet<>();

        for (int index = 0;
             index < labels.length;
             index++) {

            String label =
                    requireText(
                            "optionLabels[" + index + "]",
                            labels[index]
                    );

            if (!uniqueLabels.add(label)) {
                throw new IllegalArgumentException(
                        "Label de alternativa repetido: "
                                + label
                );
            }

            validateNormalizedValue(
                    "optionLocalX[" + index + "]",
                    localPositions[index]
            );
        }
    }

    private void validateHorizontalRegions(
            int blockCount,
            double[] localPositions,
            double radiusX
    ) {
        double blockWidth =
                1.0 / blockCount;

        for (int blockIndex = 0;
             blockIndex < blockCount;
             blockIndex++) {

            double blockLeft =
                    blockIndex * blockWidth;

            for (double localX : localPositions) {
                double centerX =
                        blockLeft
                                + localX * blockWidth;

                if (centerX - radiusX < 0.0
                        || centerX + radiusX > 1.0) {

                    throw new IllegalArgumentException(
                            "Uma região de alternativa ultrapassa"
                                    + " os limites horizontais"
                                    + " do layout."
                    );
                }
            }
        }
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
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getLayoutId() {
        return layoutId;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public String getLayoutName() {
        return layoutName;
    }

    public int getCanonicalWidth() {
        return canonicalWidth;
    }

    public int getCanonicalHeight() {
        return canonicalHeight;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getQuestionsPerBlock() {
        return questionsPerBlock;
    }

    public String[] getOptionLabels() {
        return optionLabels.clone();
    }

    public double[] getOptionLocalX() {
        return optionLocalX.clone();
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

    public boolean
    isRestartQuestionLabelsEachBlock() {

        return restartQuestionLabelsEachBlock;
    }
}