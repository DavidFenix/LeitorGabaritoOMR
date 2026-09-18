package com.example.leitorgabaritoomr.presentation.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class OmrSheetExportViewStateTest {

    @Test
    public void defaultStateStartsWithTenQuestions() {
        OmrSheetExportViewState state =
                OmrSheetExportViewState.defaultState();

        assertEquals(10, state.getQuestionCount());
        assertEquals(9, state.getSelectionIndex());
        assertEquals(4, state.getOptionCount());
        assertEquals(0, state.getOptionSelectionIndex());
    }

    @Test
    public void oneQuestionUsesFirstSelectionIndex() {
        OmrSheetExportViewState state =
                OmrSheetExportViewState
                        .fromQuestionCount(1);

        assertEquals(1, state.getQuestionCount());
        assertEquals(0, state.getSelectionIndex());
    }

    @Test
    public void everyPublishedCountConvertsToAndFromIndex() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            for (int optionCount = 4;
                 optionCount <= 5;
                 optionCount++) {

                OmrSheetExportViewState state =
                        OmrSheetExportViewState
                                .fromSelection(
                                        questionCount,
                                        optionCount
                                );

                OmrSheetExportViewState restored =
                        OmrSheetExportViewState
                                .fromSelectionIndexes(
                                        state.getSelectionIndex(),
                                        state.getOptionSelectionIndex()
                                );

                assertEquals(
                        questionCount,
                        restored.getQuestionCount()
                );
                assertEquals(
                        optionCount,
                        restored.getOptionCount()
                );
            }
        }
    }

    @Test
    public void fiveOptionsUsesSecondOptionSelectionIndex() {
        OmrSheetExportViewState state =
                OmrSheetExportViewState
                        .fromSelection(90, 5);

        assertEquals(90, state.getQuestionCount());
        assertEquals(5, state.getOptionCount());
        assertEquals(1, state.getOptionSelectionIndex());
    }

    @Test
    public void zeroQuestionsIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromQuestionCount(0)
        );
    }

    @Test
    public void ninetyOneQuestionsIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromQuestionCount(91)
        );
    }

    @Test
    public void negativeSelectionIndexIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelectionIndex(-1)
        );
    }

    @Test
    public void selectionIndexNinetyIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelectionIndex(90)
        );
    }

    @Test
    public void unsupportedOptionCountsAreRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelection(10, 3)
        );

        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelection(10, 6)
        );

        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelectionIndexes(9, -1)
        );

        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelectionIndexes(9, 2)
        );
    }

    @Test
    public void withQuestionCountKeepsStateImmutable() {
        OmrSheetExportViewState original =
                OmrSheetExportViewState
                        .fromSelection(3, 5);

        OmrSheetExportViewState changed =
                original.withQuestionCount(7);

        assertNotSame(original, changed);
        assertEquals(3, original.getQuestionCount());
        assertEquals(7, changed.getQuestionCount());
        assertEquals(5, changed.getOptionCount());

        assertSame(
                changed,
                changed.withQuestionCount(7)
        );
    }

    @Test
    public void withOptionCountKeepsStateImmutable() {
        OmrSheetExportViewState original =
                OmrSheetExportViewState
                        .fromSelection(12, 4);

        OmrSheetExportViewState changed =
                original.withOptionCount(5);

        assertNotSame(original, changed);
        assertEquals(4, original.getOptionCount());
        assertEquals(5, changed.getOptionCount());
        assertEquals(12, changed.getQuestionCount());

        assertSame(
                changed,
                changed.withOptionCount(5)
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
