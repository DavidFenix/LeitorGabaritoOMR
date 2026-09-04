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
             questionCount <= 10;
             questionCount++) {

            OmrSheetExportViewState state =
                    OmrSheetExportViewState
                            .fromQuestionCount(
                                    questionCount
                            );

            OmrSheetExportViewState restored =
                    OmrSheetExportViewState
                            .fromSelectionIndex(
                                    state.getSelectionIndex()
                            );

            assertEquals(
                    questionCount,
                    restored.getQuestionCount()
            );
        }
    }

    @Test
    public void zeroQuestionsIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromQuestionCount(0)
        );
    }

    @Test
    public void elevenQuestionsIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromQuestionCount(11)
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
    public void selectionIndexTenIsRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState
                        .fromSelectionIndex(10)
        );
    }

    @Test
    public void withQuestionCountKeepsStateImmutable() {
        OmrSheetExportViewState original =
                OmrSheetExportViewState
                        .fromQuestionCount(3);

        OmrSheetExportViewState changed =
                original.withQuestionCount(7);

        assertNotSame(original, changed);
        assertEquals(3, original.getQuestionCount());
        assertEquals(7, changed.getQuestionCount());

        assertSame(
                changed,
                changed.withQuestionCount(7)
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
