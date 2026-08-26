package com.example.leitorgabaritoomr.application.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.factory.RegularGridLayoutConfig;
import com.example.leitorgabaritoomr.vision.layout.factory.RegularGridLayoutFactory;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class OmrManualAnswerKeyDraftTest {

    private static final double DELTA = 0.000001;

    @Test
    public void avalieCeDraftUsesFiftyTwoQuestionsAndFourOptions() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        assertEquals(layout.getId(), draft.getLayoutId());
        assertEquals(
                layout.getVersion(),
                draft.getLayoutVersion()
        );
        assertEquals(layout.getName(), draft.getLayoutName());

        assertEquals(52, draft.getQuestionCount());
        assertEquals(0, draft.getAnsweredCount());
        assertEquals(52, draft.getRemainingCount());
        assertFalse(draft.isComplete());

        OmrManualAnswerKeyDraft.QuestionDraft first =
                draft.getQuestionAtPosition(1);

        assertEquals(1, first.getPosition());
        assertEquals(
                layout.getAllQuestions().get(0).getId(),
                first.getQuestionId()
        );
        assertEquals(4, first.getOptionCount());
        assertEquals("A", first.getOptionChoices().get(0).getLabel());
        assertEquals("D", first.getOptionChoices().get(3).getLabel());
        assertFalse(first.isAnswered());
        assertNull(first.getSelectedOption());

        assertNull(draft.getQuestionAtPosition(0));
        assertNull(draft.getQuestionAtPosition(53));
        assertNull(draft.findQuestionById("missing-question"));
    }

    @Test
    public void fiveOptionLayoutAutomaticallyExposesAThroughE() {
        OmrLayoutDefinition layout = createAeLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        assertEquals(3, draft.getQuestionCount());

        for (OmrManualAnswerKeyDraft.QuestionDraft question
                : draft.getQuestions()) {

            assertEquals(5, question.getOptionCount());
            assertEquals(
                    "A",
                    question.getOptionChoices().get(0).getLabel()
            );
            assertEquals(
                    "E",
                    question.getOptionChoices().get(4).getLabel()
            );
        }
    }

    @Test
    public void selectingByOptionIdReturnsNewImmutableDraft() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyDraft original =
                OmrManualAnswerKeyDraft.create(layout);

        OmrQuestionDefinition firstLayoutQuestion =
                layout.getAllQuestions().get(0);

        String selectedOptionId =
                firstLayoutQuestion.getOptions().get(2).getId();

        OmrManualAnswerKeyDraft updated =
                original.withSelection(
                        firstLayoutQuestion.getId(),
                        selectedOptionId
                );

        assertNotSame(original, updated);
        assertEquals(0, original.getAnsweredCount());
        assertFalse(
                original.getQuestionAtPosition(1).isAnswered()
        );

        assertEquals(1, updated.getAnsweredCount());
        assertEquals(51, updated.getRemainingCount());
        assertEquals(
                selectedOptionId,
                updated.getQuestionAtPosition(1)
                        .getSelectedOptionId()
        );
        assertEquals(
                "C",
                updated.getQuestionAtPosition(1)
                        .getSelectedOptionLabel()
        );

        assertSame(
                updated,
                updated.withSelection(
                        firstLayoutQuestion.getId(),
                        selectedOptionId
                )
        );
    }

    @Test
    public void selectingByLabelIsTrimmedCaseInsensitiveAndReplaceable() {
        OmrLayoutDefinition layout = createAeLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        String firstQuestionId =
                layout.getAllQuestions().get(0).getId();

        OmrManualAnswerKeyDraft selectedE =
                draft.withSelectionByLabel(
                        firstQuestionId,
                        "  e  "
                );

        assertEquals(1, selectedE.getAnsweredCount());
        assertEquals(
                "E",
                selectedE.getQuestionAtPosition(1)
                        .getSelectedOptionLabel()
        );

        OmrManualAnswerKeyDraft replaced =
                selectedE.withSelectionByLabel(
                        firstQuestionId,
                        "b"
                );

        assertEquals(1, replaced.getAnsweredCount());
        assertEquals(
                "B",
                replaced.getQuestionAtPosition(1)
                        .getSelectedOptionLabel()
        );
    }

    @Test
    public void selectionCanBeRemovedWithoutChangingOriginalDraft() {
        OmrLayoutDefinition layout = createAdLayout();
        String questionId = layout.getAllQuestions().get(0).getId();

        OmrManualAnswerKeyDraft original =
                OmrManualAnswerKeyDraft.create(layout);

        assertSame(
                original,
                original.withoutSelection(questionId)
        );

        OmrManualAnswerKeyDraft selected =
                original.withSelectionByLabel(questionId, "D");

        OmrManualAnswerKeyDraft cleared =
                selected.withoutSelection(questionId);

        assertEquals(1, selected.getAnsweredCount());
        assertEquals(0, cleared.getAnsweredCount());
        assertEquals(52, cleared.getRemainingCount());
        assertNull(
                cleared.getQuestionAtPosition(1)
                        .getSelectedOptionId()
        );
    }

    @Test
    public void invalidQuestionOptionAndLabelAreRejected() {
        OmrLayoutDefinition layout = createAdLayout();
        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        String firstQuestionId =
                layout.getAllQuestions().get(0).getId();

        expectIllegalArgument(() ->
                draft.withSelectionByLabel(
                        "missing-question",
                        "A"
                )
        );

        expectIllegalArgument(() ->
                draft.withSelection(
                        firstQuestionId,
                        "missing-option"
                )
        );

        expectIllegalArgument(() ->
                draft.withSelectionByLabel(
                        firstQuestionId,
                        "E"
                )
        );
    }

    @Test
    public void incompleteDraftCannotBecomeOfficialAnswerKey() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        expectIllegalState(() ->
                draft.toAnswerKeyDefinition(
                        layout,
                        "incomplete-key",
                        1,
                        "Gabarito incompleto",
                        1.0
                )
        );
    }

    @Test
    public void completeAdDraftProducesOfficialAnswerKeyWithRealIds() {
        OmrLayoutDefinition layout = createAdLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        String[] labels = {"A", "B", "C", "D"};

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            draft = draft.withSelectionByLabel(
                    layout.getAllQuestions().get(index).getId(),
                    labels[index % labels.length]
            );
        }

        assertTrue(draft.isComplete());
        assertEquals(52, draft.getAnsweredCount());
        assertEquals(0, draft.getRemainingCount());

        OmrAnswerKeyDefinition answerKey =
                draft.toAnswerKeyDefinition(
                        layout,
                        "manual-ad-key",
                        3,
                        "Gabarito manual A-D",
                        2.0
                );

        assertEquals("manual-ad-key", answerKey.getId());
        assertEquals(3, answerKey.getVersion());
        assertEquals(52, answerKey.getQuestionCount());
        assertEquals(104.0, answerKey.getTotalWeight(), DELTA);

        assertTrue(
                answerKey.getEntries().get(0).acceptsOption(
                        layout.getAllQuestions()
                                .get(0)
                                .getOptions()
                                .get(0)
                                .getId()
                )
        );

        assertTrue(
                answerKey.getEntries().get(1).acceptsOption(
                        layout.getAllQuestions()
                                .get(1)
                                .getOptions()
                                .get(1)
                                .getId()
                )
        );
    }

    @Test
    public void completeAeDraftAcceptsOptionEInOfficialAnswerKey() {
        OmrLayoutDefinition layout = createAeLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(layout);

        for (OmrQuestionDefinition question
                : layout.getAllQuestions()) {

            draft = draft.withSelectionByLabel(
                    question.getId(),
                    "E"
            );
        }

        OmrAnswerKeyDefinition answerKey =
                draft.toAnswerKeyDefinition(
                        layout,
                        "manual-ae-key",
                        1,
                        "Gabarito manual A-E",
                        1.0
                );

        assertEquals(3, answerKey.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            String optionEId =
                    layout.getAllQuestions()
                            .get(index)
                            .getOptions()
                            .get(4)
                            .getId();

            assertTrue(
                    answerKey.getEntries()
                            .get(index)
                            .acceptsOption(optionEId)
            );
        }
    }

    @Test
    public void draftCannotBeFinalizedWithAnotherLayout() {
        OmrLayoutDefinition adLayout = createAdLayout();
        OmrLayoutDefinition aeLayout = createAeLayout();

        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(adLayout);

        expectIllegalArgument(() ->
                draft.toAnswerKeyDefinition(
                        aeLayout,
                        "wrong-layout-key",
                        1,
                        "Gabarito incompatível",
                        1.0
                )
        );
    }

    @Test
    public void exposedCollectionsAreImmutable() {
        OmrManualAnswerKeyDraft draft =
                OmrManualAnswerKeyDraft.create(
                        createAdLayout()
                );

        expectUnsupportedOperation(() ->
                draft.getQuestions().clear()
        );

        expectUnsupportedOperation(() ->
                draft.getQuestionAtPosition(1)
                        .getOptionChoices()
                        .clear()
        );
    }

    @Test
    public void draftCanBeSerializedAndRestored() throws Exception {
        OmrLayoutDefinition layout = createAeLayout();
        String firstQuestionId =
                layout.getAllQuestions().get(0).getId();

        OmrManualAnswerKeyDraft original =
                OmrManualAnswerKeyDraft
                        .create(layout)
                        .withSelectionByLabel(
                                firstQuestionId,
                                "E"
                        );

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutput =
                     new ObjectOutputStream(byteOutput)) {

            objectOutput.writeObject(original);
        }

        OmrManualAnswerKeyDraft restored;

        try (ObjectInputStream objectInput =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrManualAnswerKeyDraft)
                    objectInput.readObject();
        }

        assertNotNull(restored);
        assertEquals(original.getLayoutId(), restored.getLayoutId());
        assertEquals(3, restored.getQuestionCount());
        assertEquals(1, restored.getAnsweredCount());
        assertEquals(
                "E",
                restored.getQuestionAtPosition(1)
                        .getSelectedOptionLabel()
        );
        assertFalse(restored.isComplete());
    }

    private OmrLayoutDefinition createAdLayout() {
        return AvalieCeDevelopmentLayoutFactory.create();
    }

    private OmrLayoutDefinition createAeLayout() {
        RegularGridLayoutConfig config =
                new RegularGridLayoutConfig(
                        "manual-ae-test",
                        1,
                        "Layout manual A-E de teste",
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

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private void expectIllegalState(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");

        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
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
