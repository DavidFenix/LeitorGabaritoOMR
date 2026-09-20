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
        assertEquals(
                OmrSheetExportViewState
                        .AUTOMATIC_QUESTIONS_PER_COLUMN,
                state.getConfiguredQuestionsPerColumn()
        );
        assertEquals(10, state.getEffectiveQuestionsPerColumn());
        assertEquals(1, state.getColumnCount());
        assertEquals(0, state.getQuestionsPerColumnSelectionIndex());
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
                assertEquals(
                        0,
                        restored.getConfiguredQuestionsPerColumn()
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
    public void automaticDistributionUsesUpToEighteenRows() {
        OmrSheetExportViewState twoQuestions =
                OmrSheetExportViewState.fromSelection(2, 5);

        OmrSheetExportViewState fortyFiveQuestions =
                OmrSheetExportViewState.fromSelection(45, 5);

        OmrSheetExportViewState ninetyQuestions =
                OmrSheetExportViewState.fromSelection(90, 4);

        assertEquals(
                2,
                twoQuestions.getEffectiveQuestionsPerColumn()
        );
        assertEquals(1, twoQuestions.getColumnCount());

        assertEquals(
                18,
                fortyFiveQuestions.getEffectiveQuestionsPerColumn()
        );
        assertEquals(3, fortyFiveQuestions.getColumnCount());

        assertEquals(
                18,
                ninetyQuestions.getEffectiveQuestionsPerColumn()
        );
        assertEquals(5, ninetyQuestions.getColumnCount());
    }

    @Test
    public void manualDistributionConvertsToAndFromSpinnerIndex() {
        OmrSheetExportViewState state =
                OmrSheetExportViewState.fromSelection(
                        45,
                        5,
                        15
                );

        assertEquals(15, state.getConfiguredQuestionsPerColumn());
        assertEquals(15, state.getEffectiveQuestionsPerColumn());
        assertEquals(3, state.getColumnCount());
        assertEquals(7, state.getQuestionsPerColumnSelectionIndex());

        OmrSheetExportViewState restored =
                OmrSheetExportViewState.fromSelectionIndexes(
                        state.getSelectionIndex(),
                        state.getOptionSelectionIndex(),
                        state.getQuestionsPerColumnSelectionIndex()
                );

        assertEquals(45, restored.getQuestionCount());
        assertEquals(5, restored.getOptionCount());
        assertEquals(15, restored.getConfiguredQuestionsPerColumn());
    }

    @Test
    public void spinnerContainsAutomaticAndEveryValidManualValue() {
        assertEquals(
                10,
                OmrSheetExportViewState.fromSelection(10, 4)
                        .getQuestionsPerColumnSelectionCount()
        );
        assertEquals(
                23,
                OmrSheetExportViewState.fromSelection(45, 4)
                        .getQuestionsPerColumnSelectionCount()
        );
        assertEquals(
                14,
                OmrSheetExportViewState.fromSelection(90, 5)
                        .getQuestionsPerColumnSelectionCount()
        );
    }

    @Test
    public void changingQuestionCountKeepsValidManualDistribution() {
        OmrSheetExportViewState original =
                OmrSheetExportViewState.fromSelection(
                        45,
                        5,
                        15
                );

        OmrSheetExportViewState changed =
                original.withQuestionCount(30);

        assertEquals(30, changed.getQuestionCount());
        assertEquals(15, changed.getConfiguredQuestionsPerColumn());
        assertEquals(2, changed.getColumnCount());
    }

    @Test
    public void changingQuestionCountResetsInvalidManualDistribution() {
        OmrSheetExportViewState original =
                OmrSheetExportViewState.fromSelection(
                        45,
                        5,
                        15
                );

        OmrSheetExportViewState changed =
                original.withQuestionCount(90);

        assertEquals(90, changed.getQuestionCount());
        assertEquals(
                0,
                changed.getConfiguredQuestionsPerColumn()
        );
        assertEquals(18, changed.getEffectiveQuestionsPerColumn());
        assertEquals(5, changed.getColumnCount());
    }

    @Test
    public void manualDistributionKeepsStateImmutable() {
        OmrSheetExportViewState original =
                OmrSheetExportViewState.fromSelection(45, 4);

        OmrSheetExportViewState changed =
                original.withQuestionsPerColumn(30);

        assertNotSame(original, changed);
        assertEquals(0, original.getConfiguredQuestionsPerColumn());
        assertEquals(30, changed.getConfiguredQuestionsPerColumn());
        assertEquals(2, changed.getColumnCount());

        assertSame(
                changed,
                changed.withQuestionsPerColumn(30)
        );
    }

    @Test
    public void invalidManualDistributionsAreRejected() {
        expectIllegalArgument(() ->
                OmrSheetExportViewState.fromSelection(
                        45,
                        4,
                        8
                )
        );

        expectIllegalArgument(() ->
                OmrSheetExportViewState.fromSelection(
                        45,
                        5,
                        31
                )
        );

        expectIllegalArgument(() ->
                OmrSheetExportViewState.fromSelectionIndexes(
                        44,
                        0,
                        23
                )
        );

        expectIllegalArgument(() ->
                OmrSheetExportViewState.fromSelectionIndexes(
                        44,
                        0,
                        -1
                )
        );
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
