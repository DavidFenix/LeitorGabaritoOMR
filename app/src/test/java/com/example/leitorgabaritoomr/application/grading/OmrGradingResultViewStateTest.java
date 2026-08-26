package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyDefinitionFactory;
import com.example.leitorgabaritoomr.application.grading.OmrGradingService;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class OmrGradingResultViewStateTest {

    private static final double DELTA = 0.000001;

    private final OmrAnswerKeyDefinitionFactory answerKeyFactory =
            new OmrAnswerKeyDefinitionFactory();

    private final OmrGradingService gradingService =
            new OmrGradingService();

    @Test
    public void mixedCorrectionProducesCompleteVisualContract() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionResult> questions =
                createAllCorrectQuestions(layout);

        List<OmrQuestionDefinition> layoutQuestions =
                layout.getAllQuestions();

        questions.set(
                1,
                question(
                        2,
                        layoutQuestions.get(1),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        0.92,
                        1
                )
        );

        questions.set(
                2,
                question(
                        3,
                        layoutQuestions.get(2),
                        OmrQuestionResult.Status.BLANK,
                        0.98
                )
        );

        questions.set(
                3,
                question(
                        4,
                        layoutQuestions.get(3),
                        OmrQuestionResult.Status.MULTIPLE_MARKS,
                        0.71,
                        1,
                        3
                )
        );

        questions.set(
                4,
                question(
                        5,
                        layoutQuestions.get(4),
                        OmrQuestionResult.Status.AMBIGUOUS,
                        0.524,
                        2
                )
        );

        questions.set(
                5,
                question(
                        6,
                        layoutQuestions.get(5),
                        OmrQuestionResult.Status.NOT_READY,
                        0.0
                )
        );

        OmrAnswerKeyDefinition answerKey =
                createUniformAnswerKey(layout, 1.0);

        OmrGradingResult gradingResult =
                gradingService.grade(
                        layout,
                        answerKey,
                        createReading(
                                layout,
                                layout.getId(),
                                questions
                        )
                );

        OmrGradingResultViewState viewState =
                OmrGradingResultViewState.from(
                        gradingResult,
                        layout
                );

        assertEquals(
                OmrGradingResultViewState.OverallState.INCOMPLETE,
                viewState.getOverallState()
        );

        assertFalse(viewState.isComplete());
        assertTrue(viewState.requiresReview());
        assertFalse(viewState.isFinal());

        assertEquals("view-state-reading", viewState.getReadingId());
        assertEquals(layout.getId(), viewState.getLayoutId());
        assertEquals(
                layout.getVersion(),
                viewState.getLayoutVersion()
        );
        assertEquals(layout.getName(), viewState.getLayoutName());

        assertEquals(
                "view-state-answer-key",
                viewState.getAnswerKeyId()
        );
        assertEquals(1, viewState.getAnswerKeyVersion());
        assertEquals(
                "Gabarito da apresentação",
                viewState.getAnswerKeyName()
        );

        assertEquals(52, viewState.getQuestionCount());
        assertEquals(47, viewState.getCorrectCount());
        assertEquals(1, viewState.getIncorrectCount());
        assertEquals(1, viewState.getBlankCount());
        assertEquals(1, viewState.getMultipleMarkCount());
        assertEquals(1, viewState.getAmbiguousCount());
        assertEquals(1, viewState.getNotReadyCount());
        assertEquals(2, viewState.getReviewRequiredCount());
        assertEquals(49, viewState.getFinalQuestionCount());
        assertEquals(3, viewState.getUnresolvedCount());

        assertEquals(47.0, viewState.getAwardedPoints(), DELTA);
        assertEquals(52.0, viewState.getPossiblePoints(), DELTA);
        assertEquals(
                4700.0 / 52.0,
                viewState.getAwardedPercentage(),
                DELTA
        );
        assertEquals(90, viewState.getRoundedAwardedPercentage());

        OmrGradingResultViewState.QuestionItem correct =
                viewState.getQuestionAtPosition(1);

        assertEquals(
                OmrGradingResultViewState.QuestionState.CORRECT,
                correct.getState()
        );
        assertEquals("A", correct.getSelectedOptionLabel());
        assertEquals(
                Collections.singletonList("A"),
                correct.getAcceptedOptionLabels()
        );
        assertEquals(95, correct.getConfidencePercent());
        assertEquals(1.0, correct.getAwardedPoints(), DELTA);
        assertEquals(1.0, correct.getPossiblePoints(), DELTA);
        assertTrue(correct.isCorrect());
        assertTrue(correct.isFinal());

        OmrGradingResultViewState.QuestionItem incorrect =
                viewState.getQuestionAtPosition(2);

        assertEquals(
                OmrGradingResultViewState.QuestionState.INCORRECT,
                incorrect.getState()
        );
        assertEquals("B", incorrect.getSelectedOptionLabel());
        assertEquals(
                Collections.singletonList("A"),
                incorrect.getAcceptedOptionLabels()
        );
        assertEquals(0.0, incorrect.getAwardedPoints(), DELTA);

        OmrGradingResultViewState.QuestionItem blank =
                viewState.getQuestionAtPosition(3);

        assertEquals(
                OmrGradingResultViewState.QuestionState.BLANK,
                blank.getState()
        );
        assertNull(blank.getSelectedOptionLabel());
        assertTrue(blank.getRelevantOptionLabels().isEmpty());

        OmrGradingResultViewState.QuestionItem multiple =
                viewState.getQuestionAtPosition(4);

        assertEquals(
                OmrGradingResultViewState.QuestionState.MULTIPLE,
                multiple.getState()
        );
        assertEquals(
                Arrays.asList("B", "D"),
                multiple.getRelevantOptionLabels()
        );
        assertTrue(multiple.requiresReview());
        assertNull(multiple.getSelectedOptionLabel());

        OmrGradingResultViewState.QuestionItem ambiguous =
                viewState.getQuestionAtPosition(5);

        assertEquals(
                OmrGradingResultViewState.QuestionState.AMBIGUOUS,
                ambiguous.getState()
        );
        assertEquals(
                Collections.singletonList("C"),
                ambiguous.getRelevantOptionLabels()
        );
        assertEquals(52, ambiguous.getConfidencePercent());

        OmrGradingResultViewState.QuestionItem notReady =
                viewState.getQuestionAtPosition(6);

        assertEquals(
                OmrGradingResultViewState.QuestionState.NOT_READY,
                notReady.getState()
        );
        assertFalse(notReady.isReady());

        assertEquals(2, viewState.getReviewItems().size());
        assertEquals(
                4,
                viewState.getReviewItems().get(0).getPosition()
        );
        assertEquals(
                5,
                viewState.getReviewItems().get(1).getPosition()
        );

        assertNull(viewState.getQuestionAtPosition(0));
        assertNull(viewState.getQuestionAtPosition(53));
    }

    @Test
    public void completeCorrectionWithReviewUsesReviewState() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionResult> questions =
                createAllCorrectQuestions(layout);

        questions.set(
                0,
                question(
                        1,
                        layout.getAllQuestions().get(0),
                        OmrQuestionResult.Status.MULTIPLE_MARKS,
                        0.75,
                        0,
                        1
                )
        );

        OmrGradingResultViewState viewState = createViewState(
                layout,
                createUniformAnswerKey(layout, 1.0),
                questions
        );

        assertEquals(
                OmrGradingResultViewState
                        .OverallState
                        .REQUIRES_REVIEW,
                viewState.getOverallState()
        );
        assertTrue(viewState.isComplete());
        assertTrue(viewState.requiresReview());
        assertFalse(viewState.isFinal());
        assertEquals(51, viewState.getCorrectCount());
        assertEquals(1, viewState.getReviewRequiredCount());
        assertEquals(0, viewState.getNotReadyCount());
    }

    @Test
    public void resolvedCorrectionUsesFinalState() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionResult> questions =
                createAllCorrectQuestions(layout);

        questions.set(
                0,
                question(
                        1,
                        layout.getAllQuestions().get(0),
                        OmrQuestionResult.Status.BLANK,
                        0.98
                )
        );

        questions.set(
                1,
                question(
                        2,
                        layout.getAllQuestions().get(1),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        0.93,
                        1
                )
        );

        OmrGradingResultViewState viewState = createViewState(
                layout,
                createUniformAnswerKey(layout, 1.0),
                questions
        );

        assertEquals(
                OmrGradingResultViewState.OverallState.FINAL,
                viewState.getOverallState()
        );
        assertTrue(viewState.isComplete());
        assertFalse(viewState.requiresReview());
        assertTrue(viewState.isFinal());
        assertEquals(50, viewState.getCorrectCount());
        assertEquals(1, viewState.getIncorrectCount());
        assertEquals(1, viewState.getBlankCount());
        assertEquals(0, viewState.getUnresolvedCount());
    }

    @Test
    public void moreThanOneAcceptedAnswerIsTranslatedToLabels() {
        OmrLayoutDefinition layout = createLayout();

        List<Collection<String>> acceptedLabels =
                new ArrayList<>(layout.getQuestionCount());

        List<Double> weights =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            acceptedLabels.add(
                    index == 0
                            ? Arrays.asList("A", "C")
                            : Collections.singletonList("A")
            );

            weights.add(index == 0 ? 2.5 : 1.0);
        }

        OmrAnswerKeyDefinition answerKey =
                answerKeyFactory.create(
                        "multiple-accepted-key",
                        2,
                        "Gabarito com duas respostas",
                        layout,
                        acceptedLabels,
                        weights
                );

        List<OmrQuestionResult> questions =
                createAllCorrectQuestions(layout);

        questions.set(
                0,
                question(
                        1,
                        layout.getAllQuestions().get(0),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        0.97,
                        2
                )
        );

        OmrGradingResultViewState viewState = createViewState(
                layout,
                answerKey,
                questions
        );

        OmrGradingResultViewState.QuestionItem first =
                viewState.getQuestionAtPosition(1);

        assertEquals(
                OmrGradingResultViewState.QuestionState.CORRECT,
                first.getState()
        );
        assertEquals("C", first.getSelectedOptionLabel());
        assertEquals(
                Arrays.asList("A", "C"),
                first.getAcceptedOptionLabels()
        );
        assertEquals(2.5, first.getAwardedPoints(), DELTA);
        assertEquals(53.5, viewState.getAwardedPoints(), DELTA);
        assertEquals(53.5, viewState.getPossiblePoints(), DELTA);
        assertEquals(100.0, viewState.getAwardedPercentage(), DELTA);
    }

    @Test
    public void layoutWithDifferentIdentityIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition validAnswerKey =
                createUniformAnswerKey(layout, 1.0);

        OmrAnswerKeyDefinition foreignAnswerKey =
                new OmrAnswerKeyDefinition(
                        "foreign-key",
                        1,
                        "Gabarito externo",
                        "foreign-layout",
                        layout.getVersion(),
                        validAnswerKey.getEntries()
                );

        OmrReadingResult foreignReading = createReading(
                layout,
                "foreign-layout",
                createAllCorrectQuestions(layout)
        );

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        foreignReading,
                        foreignAnswerKey
                );

        expectIllegalArgument(() ->
                OmrGradingResultViewState.from(
                        gradingResult,
                        layout
                )
        );
    }

    @Test
    public void acceptedOptionAbsentFromLayoutIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition validAnswerKey =
                createUniformAnswerKey(layout, 1.0);

        List<OmrAnswerKeyEntry> entries =
                new ArrayList<>(validAnswerKey.getEntries());

        OmrAnswerKeyEntry firstEntry = entries.get(0);

        entries.set(
                0,
                OmrAnswerKeyEntry.singleAnswer(
                        firstEntry.getQuestionId(),
                        "option-that-does-not-exist",
                        firstEntry.getWeight()
                )
        );

        OmrAnswerKeyDefinition invalidAnswerKey =
                new OmrAnswerKeyDefinition(
                        "invalid-option-key",
                        1,
                        "Gabarito inválido",
                        layout.getId(),
                        layout.getVersion(),
                        entries
                );

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        createReading(
                                layout,
                                layout.getId(),
                                createAllCorrectQuestions(layout)
                        ),
                        invalidAnswerKey
                );

        expectIllegalArgument(() ->
                OmrGradingResultViewState.from(
                        gradingResult,
                        layout
                )
        );
    }

    @Test
    public void nullInputsAreRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrGradingResult gradingResult =
                gradingService.grade(
                        layout,
                        createUniformAnswerKey(layout, 1.0),
                        createReading(
                                layout,
                                layout.getId(),
                                createAllCorrectQuestions(layout)
                        )
                );

        expectIllegalArgument(() ->
                OmrGradingResultViewState.from(
                        null,
                        layout
                )
        );

        expectIllegalArgument(() ->
                OmrGradingResultViewState.from(
                        gradingResult,
                        null
                )
        );
    }

    @Test
    public void exposedListsAreImmutable() {
        OmrLayoutDefinition layout = createLayout();

        OmrGradingResultViewState viewState = createViewState(
                layout,
                createUniformAnswerKey(layout, 1.0),
                createAllCorrectQuestions(layout)
        );

        expectUnsupportedOperation(() ->
                viewState.getQuestionItems().clear()
        );

        expectUnsupportedOperation(() ->
                viewState.getReviewItems().clear()
        );

        expectUnsupportedOperation(() ->
                viewState.getQuestionAtPosition(1)
                        .getAcceptedOptionLabels()
                        .clear()
        );
    }

    private OmrLayoutDefinition createLayout() {
        return AvalieCeDevelopmentLayoutFactory.create();
    }

    private OmrAnswerKeyDefinition createUniformAnswerKey(
            OmrLayoutDefinition layout,
            double weight
    ) {
        List<String> labels =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            labels.add("A");
        }

        return answerKeyFactory.createSingleAnswerKey(
                "view-state-answer-key",
                1,
                "Gabarito da apresentação",
                layout,
                labels,
                weight
        );
    }

    private List<OmrQuestionResult> createAllCorrectQuestions(
            OmrLayoutDefinition layout
    ) {
        List<OmrQuestionResult> questions =
                new ArrayList<>(layout.getQuestionCount());

        List<OmrQuestionDefinition> layoutQuestions =
                layout.getAllQuestions();

        for (int index = 0;
             index < layoutQuestions.size();
             index++) {

            questions.add(
                    question(
                            index + 1,
                            layoutQuestions.get(index),
                            OmrQuestionResult.Status.SINGLE_MARK,
                            0.95,
                            0
                    )
            );
        }

        return questions;
    }

    private OmrQuestionResult question(
            int position,
            OmrQuestionDefinition questionDefinition,
            OmrQuestionResult.Status status,
            double confidence,
            int... optionIndexes
    ) {
        List<OmrQuestionResult.Option> options =
                new ArrayList<>(optionIndexes.length);

        for (int optionIndex : optionIndexes) {
            OmrOptionDefinition option =
                    questionDefinition
                            .getOptions()
                            .get(optionIndex);

            options.add(
                    new OmrQuestionResult.Option(
                            option.getId(),
                            option.getLabel()
                    )
            );
        }

        return new OmrQuestionResult(
                position,
                questionDefinition.getId(),
                status,
                options,
                confidence
        );
    }

    private OmrReadingResult createReading(
            OmrLayoutDefinition layout,
            String layoutId,
            List<OmrQuestionResult> questions
    ) {
        return new OmrReadingResult(
                "view-state-reading",
                1_800_000_000_000L,
                layoutId,
                layout.getVersion(),
                layout.getName(),
                questions
        );
    }

    private OmrGradingResultViewState createViewState(
            OmrLayoutDefinition layout,
            OmrAnswerKeyDefinition answerKey,
            List<OmrQuestionResult> questions
    ) {
        OmrGradingResult gradingResult =
                gradingService.grade(
                        layout,
                        answerKey,
                        createReading(
                                layout,
                                layout.getId(),
                                questions
                        )
                );

        return OmrGradingResultViewState.from(
                gradingResult,
                layout
        );
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
