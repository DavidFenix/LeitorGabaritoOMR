package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrManualAnswerKeyDraft;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.factory.RegularGridLayoutConfig;
import com.example.leitorgabaritoomr.vision.layout.factory.RegularGridLayoutFactory;

import org.junit.Test;

public final class OmrManualAnswerKeyViewStateTest {

    @Test
    public void emptyAdDraftProducesZeroProgressAndFirstPendingQuestion() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyViewState viewState =
                OmrManualAnswerKeyViewState.from(
                        OmrManualAnswerKeyDraft.create(layout)
                );

        assertEquals(layout.getId(), viewState.getLayoutId());
        assertEquals(
                layout.getVersion(),
                viewState.getLayoutVersion()
        );
        assertEquals(layout.getName(), viewState.getLayoutName());

        assertEquals(52, viewState.getQuestionCount());
        assertEquals(0, viewState.getAnsweredCount());
        assertEquals(52, viewState.getRemainingCount());
        assertEquals(0, viewState.getProgressPercent());
        assertEquals(1, viewState.getFirstUnansweredPosition());
        assertFalse(viewState.isComplete());
        assertFalse(viewState.canSave());

        OmrManualAnswerKeyViewState.QuestionItem first =
                viewState.getQuestionAtPosition(1);

        assertEquals(1, first.getPosition());
        assertEquals(4, first.getOptionCount());
        assertFalse(first.isAnswered());
        assertNull(first.getSelectedOption());
        assertNull(first.getSelectedOptionId());
        assertNull(first.getSelectedOptionLabel());
    }

    @Test
    public void partialDraftCalculatesRoundedProgressAndFirstGap() {
        OmrLayoutDefinition layout = createAdLayout();

        String firstQuestionId =
                layout.getAllQuestions().get(0).getId();

        String thirdQuestionId =
                layout.getAllQuestions().get(2).getId();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft
                        .create(layout)
                        .withSelectionByLabel(
                                firstQuestionId,
                                "C"
                        )
                        .withSelectionByLabel(
                                thirdQuestionId,
                                "D"
                        );

        OmrManualAnswerKeyViewState viewState =
                OmrManualAnswerKeyViewState.from(draft);

        assertEquals(2, viewState.getAnsweredCount());
        assertEquals(50, viewState.getRemainingCount());
        assertEquals(4, viewState.getProgressPercent());
        assertEquals(2, viewState.getFirstUnansweredPosition());
        assertFalse(viewState.canSave());

        assertEquals(
                "C",
                viewState.getQuestionAtPosition(1)
                        .getSelectedOptionLabel()
        );

        assertFalse(
                viewState.getQuestionAtPosition(2).isAnswered()
        );

        assertEquals(
                "D",
                viewState.getQuestionAtPosition(3)
                        .getSelectedOptionLabel()
        );
    }

    @Test
    public void completeDraftEnablesSaveAndHasNoPendingQuestion() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        for (OmrQuestionDefinition question
                : layout.getAllQuestions()) {

            draft = draft.withSelectionByLabel(
                    question.getId(),
                    "A"
            );
        }

        OmrManualAnswerKeyViewState viewState =
                OmrManualAnswerKeyViewState.from(draft);

        assertEquals(52, viewState.getAnsweredCount());
        assertEquals(0, viewState.getRemainingCount());
        assertEquals(100, viewState.getProgressPercent());
        assertEquals(0, viewState.getFirstUnansweredPosition());
        assertTrue(viewState.isComplete());
        assertTrue(viewState.canSave());
    }

    @Test
    public void aeDraftExposesFiveOptionsAndMarksOnlyE() {
        OmrLayoutDefinition layout = createAeLayout();

        String firstQuestionId =
                layout.getAllQuestions().get(0).getId();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft
                        .create(layout)
                        .withSelectionByLabel(
                                firstQuestionId,
                                "E"
                        );

        OmrManualAnswerKeyViewState.QuestionItem first =
                OmrManualAnswerKeyViewState
                        .from(draft)
                        .getQuestionAtPosition(1);

        assertEquals(5, first.getOptionCount());
        assertTrue(first.isAnswered());
        assertEquals("E", first.getSelectedOptionLabel());

        int selectedCount = 0;

        for (OmrManualAnswerKeyViewState.OptionItem option
                : first.getOptions()) {

            if (option.isSelected()) {
                selectedCount++;
                assertEquals("E", option.getLabel());
            }
        }

        assertEquals(1, selectedCount);
    }

    @Test
    public void questionsAndOptionsSupportSafeLookup() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyViewState viewState =
                OmrManualAnswerKeyViewState.from(
                        OmrManualAnswerKeyDraft.create(layout)
                );

        String firstQuestionId =
                layout.getAllQuestions().get(0).getId();

        String firstOptionId =
                layout.getAllQuestions()
                        .get(0)
                        .getOptions()
                        .get(0)
                        .getId();

        OmrManualAnswerKeyViewState.QuestionItem first =
                viewState.findQuestionById(
                        " " + firstQuestionId + " "
                );

        assertNotNull(first);
        assertNotNull(
                first.findOptionById(
                        " " + firstOptionId + " "
                )
        );

        assertNull(viewState.getQuestionAtPosition(0));
        assertNull(viewState.getQuestionAtPosition(53));
        assertNull(viewState.findQuestionById(null));
        assertNull(viewState.findQuestionById("missing-question"));
        assertNull(first.findOptionById(null));
        assertNull(first.findOptionById("missing-option"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullDraftIsRejected() {
        OmrManualAnswerKeyViewState.from(null);
    }

    @Test
    public void exposedQuestionAndOptionListsAreImmutable() {
        OmrManualAnswerKeyViewState viewState =
                OmrManualAnswerKeyViewState.from(
                        OmrManualAnswerKeyDraft.create(
                                createAdLayout()
                        )
                );

        expectUnsupportedOperation(() ->
                viewState.getQuestionItems().clear()
        );

        expectUnsupportedOperation(() ->
                viewState.getQuestionAtPosition(1)
                        .getOptions()
                        .clear()
        );
    }

    private OmrLayoutDefinition createAdLayout() {
        return AvalieCeDevelopmentLayoutFactory.create();
    }

    private OmrLayoutDefinition createAeLayout() {
        RegularGridLayoutConfig config =
                new RegularGridLayoutConfig(
                        "manual-view-ae-test",
                        1,
                        "Layout visual A-E de teste",
                        1000,
                        1000,
                        1,
                        3,
                        new String[]{
                                "A", "B", "C", "D", "E"
                        },
                        new double[]{
                                0.15,
                                0.325,
                                0.50,
                                0.675,
                                0.85
                        },
                        0.20,
                        0.30,
                        0.04,
                        0.04,
                        1,
                        false
                );

        return RegularGridLayoutFactory.create(config);
    }

    private void expectUnsupportedOperation(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");

        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
