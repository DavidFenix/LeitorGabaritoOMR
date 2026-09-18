package com.example.leitorgabaritoomr.presentation.export;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

/**
 * Estado visual imutavel da escolha do modelo de cartao-resposta.
 *
 * O catalogo publicado pelo aplicativo permite qualquer quantidade inteira
 * entre 1 e 90 questoes e modelos com quatro ou cinco alternativas. As
 * conversoes para indices existem apenas para manter os Spinners Android
 * fora das regras de dominio da tela.
 */
public final class OmrSheetExportViewState {

    public static final int DEFAULT_QUESTION_COUNT = 10;
    public static final int DEFAULT_OPTION_COUNT =
            OmrSheetTemplateCatalog
                    .STANDARD_V2_FOUR_OPTION_COUNT;

    private final int questionCount;
    private final int optionCount;

    private OmrSheetExportViewState(
            int questionCount,
            int optionCount
    ) {
        validateQuestionCount(questionCount);
        validateOptionCount(optionCount);

        this.questionCount = questionCount;
        this.optionCount = optionCount;
    }

    public static OmrSheetExportViewState defaultState() {
        return new OmrSheetExportViewState(
                DEFAULT_QUESTION_COUNT,
                DEFAULT_OPTION_COUNT
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
        return new OmrSheetExportViewState(
                questionCount,
                optionCount
        );
    }

    public static OmrSheetExportViewState fromSelectionIndex(
            int selectionIndex
    ) {
        return fromSelectionIndexes(
                selectionIndex,
                0
        );
    }

    public static OmrSheetExportViewState fromSelectionIndexes(
            int questionSelectionIndex,
            int optionSelectionIndex
    ) {
        int questionCount =
                OmrSheetTemplateCatalog
                        .MIN_QUESTION_COUNT
                        + questionSelectionIndex;

        int optionCount =
                OmrSheetTemplateCatalog
                        .STANDARD_V2_FOUR_OPTION_COUNT
                        + optionSelectionIndex;

        return fromSelection(
                questionCount,
                optionCount
        );
    }

    public OmrSheetExportViewState withQuestionCount(
            int newQuestionCount
    ) {
        if (newQuestionCount == questionCount) {
            return this;
        }

        return fromSelection(
                newQuestionCount,
                optionCount
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
                newOptionCount
        );
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getOptionCount() {
        return optionCount;
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
}
