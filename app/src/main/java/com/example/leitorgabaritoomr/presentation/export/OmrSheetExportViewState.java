package com.example.leitorgabaritoomr.presentation.export;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

/**
 * Estado visual imutavel da escolha do modelo de cartao-resposta.
 *
 * A primeira familia publicada pelo aplicativo permite qualquer quantidade
 * inteira entre 1 e 10 questoes. A conversao para indice existe apenas para
 * manter o Spinner Android fora das regras de dominio da tela.
 */
public final class OmrSheetExportViewState {

    public static final int DEFAULT_QUESTION_COUNT = 10;

    private final int questionCount;

    private OmrSheetExportViewState(
            int questionCount
    ) {
        validateQuestionCount(questionCount);
        this.questionCount = questionCount;
    }

    public static OmrSheetExportViewState defaultState() {
        return new OmrSheetExportViewState(
                DEFAULT_QUESTION_COUNT
        );
    }

    public static OmrSheetExportViewState fromQuestionCount(
            int questionCount
    ) {
        return new OmrSheetExportViewState(questionCount);
    }

    public static OmrSheetExportViewState fromSelectionIndex(
            int selectionIndex
    ) {
        int questionCount =
                OmrSheetTemplateCatalog
                        .COMPACT_MIN_QUESTION_COUNT
                        + selectionIndex;

        return fromQuestionCount(questionCount);
    }

    public OmrSheetExportViewState withQuestionCount(
            int newQuestionCount
    ) {
        if (newQuestionCount == questionCount) {
            return this;
        }

        return fromQuestionCount(newQuestionCount);
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getSelectionIndex() {
        return questionCount
                - OmrSheetTemplateCatalog
                .COMPACT_MIN_QUESTION_COUNT;
    }

    private static void validateQuestionCount(
            int questionCount
    ) {
        if (questionCount
                < OmrSheetTemplateCatalog
                .COMPACT_MIN_QUESTION_COUNT
                || questionCount
                > OmrSheetTemplateCatalog
                .COMPACT_MAX_QUESTION_COUNT) {

            throw new IllegalArgumentException(
                    "A quantidade deve estar entre "
                            + OmrSheetTemplateCatalog
                            .COMPACT_MIN_QUESTION_COUNT
                            + " e "
                            + OmrSheetTemplateCatalog
                            .COMPACT_MAX_QUESTION_COUNT
                            + "."
            );
        }
    }
}
