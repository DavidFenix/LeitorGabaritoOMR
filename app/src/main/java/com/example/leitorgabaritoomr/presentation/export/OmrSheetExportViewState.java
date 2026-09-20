package com.example.leitorgabaritoomr.presentation.export;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

/**
 * Estado visual imutavel da escolha do modelo de cartao-resposta.
 *
 * O catalogo publicado pelo aplicativo permite qualquer quantidade inteira
 * entre 1 e 90 questoes, modelos com quatro ou cinco alternativas e uma
 * distribuicao automatica ou manual de itens por coluna. As conversoes para
 * indices existem apenas para manter os Spinners Android fora das regras de
 * dominio da tela.
 */
public final class OmrSheetExportViewState {

    public static final int DEFAULT_QUESTION_COUNT = 10;
    public static final int DEFAULT_OPTION_COUNT =
            OmrSheetTemplateCatalog
                    .STANDARD_V2_FOUR_OPTION_COUNT;
    public static final int AUTOMATIC_QUESTIONS_PER_COLUMN = 0;

    private final int questionCount;
    private final int optionCount;
    private final int configuredQuestionsPerColumn;

    private OmrSheetExportViewState(
            int questionCount,
            int optionCount,
            int configuredQuestionsPerColumn
    ) {
        validateQuestionCount(questionCount);
        validateOptionCount(optionCount);
        validateConfiguredQuestionsPerColumn(
                questionCount,
                configuredQuestionsPerColumn
        );

        this.questionCount = questionCount;
        this.optionCount = optionCount;
        this.configuredQuestionsPerColumn =
                configuredQuestionsPerColumn;
    }

    public static OmrSheetExportViewState defaultState() {
        return new OmrSheetExportViewState(
                DEFAULT_QUESTION_COUNT,
                DEFAULT_OPTION_COUNT,
                AUTOMATIC_QUESTIONS_PER_COLUMN
        );
    }

    public static OmrSheetExportViewState fromQuestionCount(
            int questionCount
    ) {
        return fromSelection(
                questionCount,
                DEFAULT_OPTION_COUNT
        );
    }

    public static OmrSheetExportViewState fromSelection(
            int questionCount,
            int optionCount
    ) {
        return fromSelection(
                questionCount,
                optionCount,
                AUTOMATIC_QUESTIONS_PER_COLUMN
        );
    }

    public static OmrSheetExportViewState fromSelection(
            int questionCount,
            int optionCount,
            int configuredQuestionsPerColumn
    ) {
        return new OmrSheetExportViewState(
                questionCount,
                optionCount,
                configuredQuestionsPerColumn
        );
    }

    public static OmrSheetExportViewState fromSelectionIndex(
            int selectionIndex
    ) {
        return fromSelectionIndexes(
                selectionIndex,
                0,
                0
        );
    }

    public static OmrSheetExportViewState fromSelectionIndexes(
            int questionSelectionIndex,
            int optionSelectionIndex
    ) {
        return fromSelectionIndexes(
                questionSelectionIndex,
                optionSelectionIndex,
                0
        );
    }

    public static OmrSheetExportViewState fromSelectionIndexes(
            int questionSelectionIndex,
            int optionSelectionIndex,
            int questionsPerColumnSelectionIndex
    ) {
        int questionCount =
                OmrSheetTemplateCatalog
                        .MIN_QUESTION_COUNT
                        + questionSelectionIndex;

        int optionCount =
                OmrSheetTemplateCatalog
                        .STANDARD_V2_FOUR_OPTION_COUNT
                        + optionSelectionIndex;

        int configuredQuestionsPerColumn =
                questionsPerColumnFromSelectionIndex(
                        questionCount,
                        questionsPerColumnSelectionIndex
                );

        return fromSelection(
                questionCount,
                optionCount,
                configuredQuestionsPerColumn
        );
    }

    public OmrSheetExportViewState withQuestionCount(
            int newQuestionCount
    ) {
        if (newQuestionCount == questionCount) {
            return this;
        }

        int adjustedQuestionsPerColumn =
                configuredQuestionsPerColumn;

        if (adjustedQuestionsPerColumn
                != AUTOMATIC_QUESTIONS_PER_COLUMN
                && !isValidManualQuestionsPerColumn(
                newQuestionCount,
                adjustedQuestionsPerColumn
        )) {
            adjustedQuestionsPerColumn =
                    AUTOMATIC_QUESTIONS_PER_COLUMN;
        }

        return fromSelection(
                newQuestionCount,
                optionCount,
                adjustedQuestionsPerColumn
        );
    }

    public OmrSheetExportViewState withOptionCount(
            int newOptionCount
    ) {
        if (newOptionCount == optionCount) {
            return this;
        }

        return fromSelection(
                questionCount,
                newOptionCount,
                configuredQuestionsPerColumn
        );
    }

    public OmrSheetExportViewState withQuestionsPerColumn(
            int newConfiguredQuestionsPerColumn
    ) {
        if (newConfiguredQuestionsPerColumn
                == configuredQuestionsPerColumn) {
            return this;
        }

        return fromSelection(
                questionCount,
                optionCount,
                newConfiguredQuestionsPerColumn
        );
    }

    public OmrSheetExportViewState
    withQuestionsPerColumnSelectionIndex(
            int selectionIndex
    ) {
        return withQuestionsPerColumn(
                questionsPerColumnFromSelectionIndex(
                        questionCount,
                        selectionIndex
                )
        );
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getOptionCount() {
        return optionCount;
    }

    public int getConfiguredQuestionsPerColumn() {
        return configuredQuestionsPerColumn;
    }

    public boolean isAutomaticQuestionsPerColumn() {
        return configuredQuestionsPerColumn
                == AUTOMATIC_QUESTIONS_PER_COLUMN;
    }

    public int getEffectiveQuestionsPerColumn() {
        if (isAutomaticQuestionsPerColumn()) {
            return OmrSheetTemplateCatalog
                    .getFlexibleV3AutomaticQuestionsPerColumn(
                            questionCount
                    );
        }

        return configuredQuestionsPerColumn;
    }

    public int getColumnCount() {
        int questionsPerColumn =
                getEffectiveQuestionsPerColumn();

        return (questionCount
                + questionsPerColumn
                - 1)
                / questionsPerColumn;
    }

    public int getSelectionIndex() {
        return questionCount
                - OmrSheetTemplateCatalog
                .MIN_QUESTION_COUNT;
    }

    public int getOptionSelectionIndex() {
        return optionCount
                - OmrSheetTemplateCatalog
                .STANDARD_V2_FOUR_OPTION_COUNT;
    }

    public int getQuestionsPerColumnSelectionIndex() {
        if (isAutomaticQuestionsPerColumn()) {
            return 0;
        }

        return configuredQuestionsPerColumn
                - OmrSheetTemplateCatalog
                .getFlexibleV3MinimumQuestionsPerColumn(
                        questionCount
                )
                + 1;
    }

    public int getQuestionsPerColumnSelectionCount() {
        return OmrSheetTemplateCatalog
                .getFlexibleV3MaximumQuestionsPerColumn(
                        questionCount
                )
                - OmrSheetTemplateCatalog
                .getFlexibleV3MinimumQuestionsPerColumn(
                        questionCount
                )
                + 2;
    }

    private static void validateQuestionCount(
            int questionCount
    ) {
        if (questionCount
                < OmrSheetTemplateCatalog
                .MIN_QUESTION_COUNT
                || questionCount
                > OmrSheetTemplateCatalog
                .MAX_QUESTION_COUNT) {

            throw new IllegalArgumentException(
                    "A quantidade deve estar entre "
                            + OmrSheetTemplateCatalog
                            .MIN_QUESTION_COUNT
                            + " e "
                            + OmrSheetTemplateCatalog
                            .MAX_QUESTION_COUNT
                            + "."
            );
        }
    }

    private static void validateOptionCount(
            int optionCount
    ) {
        if (optionCount
                != OmrSheetTemplateCatalog
                .STANDARD_V2_FOUR_OPTION_COUNT
                && optionCount
                != OmrSheetTemplateCatalog
                .STANDARD_V2_FIVE_OPTION_COUNT) {

            throw new IllegalArgumentException(
                    "A quantidade de alternativas deve ser 4 ou 5."
            );
        }
    }

    private static void validateConfiguredQuestionsPerColumn(
            int questionCount,
            int configuredQuestionsPerColumn
    ) {
        if (configuredQuestionsPerColumn
                == AUTOMATIC_QUESTIONS_PER_COLUMN) {
            return;
        }

        if (!isValidManualQuestionsPerColumn(
                questionCount,
                configuredQuestionsPerColumn
        )) {
            int minimum = OmrSheetTemplateCatalog
                    .getFlexibleV3MinimumQuestionsPerColumn(
                            questionCount
                    );

            int maximum = OmrSheetTemplateCatalog
                    .getFlexibleV3MaximumQuestionsPerColumn(
                            questionCount
                    );

            throw new IllegalArgumentException(
                    "Para "
                            + questionCount
                            + " questoes, a quantidade por coluna deve ser "
                            + "automatica ou estar entre "
                            + minimum
                            + " e "
                            + maximum
                            + "."
            );
        }
    }

    private static boolean isValidManualQuestionsPerColumn(
            int questionCount,
            int questionsPerColumn
    ) {
        if (questionCount
                < OmrSheetTemplateCatalog.MIN_QUESTION_COUNT
                || questionCount
                > OmrSheetTemplateCatalog.MAX_QUESTION_COUNT) {
            return false;
        }

        return questionsPerColumn
                >= OmrSheetTemplateCatalog
                .getFlexibleV3MinimumQuestionsPerColumn(
                        questionCount
                )
                && questionsPerColumn
                <= OmrSheetTemplateCatalog
                .getFlexibleV3MaximumQuestionsPerColumn(
                        questionCount
                );
    }

    private static int questionsPerColumnFromSelectionIndex(
            int questionCount,
            int selectionIndex
    ) {
        if (selectionIndex < 0) {
            throw new IllegalArgumentException(
                    "O indice de itens por coluna nao pode ser negativo."
            );
        }

        if (selectionIndex == 0) {
            return AUTOMATIC_QUESTIONS_PER_COLUMN;
        }

        return OmrSheetTemplateCatalog
                .getFlexibleV3MinimumQuestionsPerColumn(
                        questionCount
                )
                + selectionIndex
                - 1;
    }
}
