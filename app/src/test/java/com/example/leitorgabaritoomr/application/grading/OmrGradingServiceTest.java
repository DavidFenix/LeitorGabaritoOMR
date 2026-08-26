package com.example.leitorgabaritoomr.application.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class OmrGradingServiceTest {

    private static final double DELTA = 0.000001;

    private final OmrGradingService gradingService =
            new OmrGradingService();

    private final OmrAnswerKeyDefinitionFactory answerKeyFactory =
            new OmrAnswerKeyDefinitionFactory();

    @Test
    public void validLayoutAnswerKeyAndReadingAreGraded() {
        OmrLayoutDefinition layout = createLayout();
        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(layout);
        OmrReadingResult reading = createValidReading(layout);

        OmrGradingResult result = gradingService.grade(
                layout,
                answerKey,
                reading
        );

        assertSame(reading, result.getReadingResult());
        assertSame(
                answerKey,
                result.getAnswerKeyDefinition()
        );

        assertEquals(52, result.getQuestionCount());
        assertEquals(26, result.getCorrectCount());
        assertEquals(26, result.getIncorrectCount());
        assertEquals(0, result.getBlankCount());
        assertEquals(0, result.getReviewRequiredCount());
        assertEquals(0, result.getNotReadyCount());

        assertEquals(52.0, result.getAwardedPoints(), DELTA);
        assertEquals(104.0, result.getPossiblePoints(), DELTA);
        assertEquals(50.0, result.getAwardedPercentage(), DELTA);

        assertTrue(result.isComplete());
        assertFalse(result.requiresReview());
        assertTrue(result.isFinal());
    }

    @Test
    public void readingFromDifferentLayoutIdIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrReadingResult reading = createReading(
                layout,
                "another-layout",
                layout.getVersion(),
                createValidQuestions(layout)
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        createAnswerKey(layout),
                        reading
                )
        );
    }

    @Test
    public void readingFromDifferentLayoutVersionIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrReadingResult reading = createReading(
                layout,
                layout.getId(),
                layout.getVersion() + 1,
                createValidQuestions(layout)
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        createAnswerKey(layout),
                        reading
                )
        );
    }

    @Test
    public void readingWithDifferentQuestionCountIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionResult> questions =
                createValidQuestions(layout);

        questions.remove(questions.size() - 1);

        OmrReadingResult reading = createReading(
                layout,
                layout.getId(),
                layout.getVersion(),
                questions
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        createAnswerKey(layout),
                        reading
                )
        );
    }

    @Test
    public void readingWithQuestionsOutsideLayoutOrderIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionDefinition> layoutQuestions =
                layout.getAllQuestions();

        List<OmrQuestionResult> readingQuestions =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layoutQuestions.size();
             index++) {

            int layoutQuestionIndex;

            if (index == 0) {
                layoutQuestionIndex = 1;
            } else if (index == 1) {
                layoutQuestionIndex = 0;
            } else {
                layoutQuestionIndex = index;
            }

            readingQuestions.add(
                    createSingleMark(
                            index + 1,
                            layoutQuestions.get(
                                    layoutQuestionIndex
                            ),
                            0
                    )
            );
        }

        OmrReadingResult reading = createReading(
                layout,
                layout.getId(),
                layout.getVersion(),
                readingQuestions
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        createAnswerKey(layout),
                        reading
                )
        );
    }

    @Test
    public void readingWithNonexistentRelevantOptionIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionResult> questions =
                createValidQuestions(layout);

        OmrQuestionDefinition firstQuestion =
                layout.getAllQuestions().get(0);

        questions.set(
                0,
                new OmrQuestionResult(
                        1,
                        firstQuestion.getId(),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        java.util.Collections.singletonList(
                                new OmrQuestionResult.Option(
                                        "option-that-does-not-exist",
                                        "A"
                                )
                        ),
                        0.95
                )
        );

        OmrReadingResult reading = createReading(
                layout,
                layout.getId(),
                layout.getVersion(),
                questions
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        createAnswerKey(layout),
                        reading
                )
        );
    }

    @Test
    public void readingWithIncorrectOptionLabelIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrQuestionResult> questions =
                createValidQuestions(layout);

        OmrQuestionDefinition firstQuestion =
                layout.getAllQuestions().get(0);

        OmrOptionDefinition firstOption =
                firstQuestion.getOptions().get(0);

        questions.set(
                0,
                new OmrQuestionResult(
                        1,
                        firstQuestion.getId(),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        java.util.Collections.singletonList(
                                new OmrQuestionResult.Option(
                                        firstOption.getId(),
                                        "RÓTULO-INCORRETO"
                                )
                        ),
                        0.95
                )
        );

        OmrReadingResult reading = createReading(
                layout,
                layout.getId(),
                layout.getVersion(),
                questions
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        createAnswerKey(layout),
                        reading
                )
        );
    }

    @Test
    public void answerKeyFromDifferentLayoutIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition validAnswerKey =
                createAnswerKey(layout);

        OmrAnswerKeyDefinition incompatibleAnswerKey =
                new OmrAnswerKeyDefinition(
                        "incompatible-key",
                        1,
                        "Gabarito incompatível",
                        "another-layout",
                        layout.getVersion(),
                        validAnswerKey.getEntries()
                );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        incompatibleAnswerKey,
                        createValidReading(layout)
                )
        );
    }

    @Test
    public void answerKeyWithNonexistentOptionIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition validAnswerKey =
                createAnswerKey(layout);

        List<OmrAnswerKeyEntry> entries =
                new ArrayList<>(
                        validAnswerKey.getEntries()
                );

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
                        "Gabarito com alternativa inexistente",
                        layout.getId(),
                        layout.getVersion(),
                        entries
                );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        invalidAnswerKey,
                        createValidReading(layout)
                )
        );
    }

    @Test
    public void nullInputsAreRejected() {
        OmrLayoutDefinition layout = createLayout();
        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(layout);
        OmrReadingResult reading = createValidReading(layout);

        expectIllegalArgument(() ->
                gradingService.grade(
                        null,
                        answerKey,
                        reading
                )
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        null,
                        reading
                )
        );

        expectIllegalArgument(() ->
                gradingService.grade(
                        layout,
                        answerKey,
                        null
                )
        );
    }

    private OmrLayoutDefinition createLayout() {
        return AvalieCeDevelopmentLayoutFactory.create();
    }

    private OmrAnswerKeyDefinition createAnswerKey(
            OmrLayoutDefinition layout
    ) {
        List<String> labels =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            labels.add("A");
        }

        return answerKeyFactory.createSingleAnswerKey(
                "grading-service-key",
                1,
                "Gabarito do serviço",
                layout,
                labels,
                2.0
        );
    }

    private OmrReadingResult createValidReading(
            OmrLayoutDefinition layout
    ) {
        return createReading(
                layout,
                layout.getId(),
                layout.getVersion(),
                createValidQuestions(layout)
        );
    }

    private List<OmrQuestionResult> createValidQuestions(
            OmrLayoutDefinition layout
    ) {
        List<OmrQuestionResult> questions =
                new ArrayList<>(layout.getQuestionCount());

        List<OmrQuestionDefinition> layoutQuestions =
                layout.getAllQuestions();

        for (int index = 0;
             index < layoutQuestions.size();
             index++) {

            int selectedOptionIndex = index % 2;

            questions.add(
                    createSingleMark(
                            index + 1,
                            layoutQuestions.get(index),
                            selectedOptionIndex
                    )
            );
        }

        return questions;
    }

    private OmrQuestionResult createSingleMark(
            int position,
            OmrQuestionDefinition question,
            int selectedOptionIndex
    ) {
        OmrOptionDefinition option =
                question.getOptions().get(
                        selectedOptionIndex
                );

        return new OmrQuestionResult(
                position,
                question.getId(),
                OmrQuestionResult.Status.SINGLE_MARK,
                java.util.Collections.singletonList(
                        new OmrQuestionResult.Option(
                                option.getId(),
                                option.getLabel()
                        )
                ),
                0.95
        );
    }

    private OmrReadingResult createReading(
            OmrLayoutDefinition layout,
            String layoutId,
            int layoutVersion,
            List<OmrQuestionResult> questions
    ) {
        return new OmrReadingResult(
                "grading-service-reading",
                1_800_000_000_000L,
                layoutId,
                layoutVersion,
                layout.getName(),
                questions
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
}
