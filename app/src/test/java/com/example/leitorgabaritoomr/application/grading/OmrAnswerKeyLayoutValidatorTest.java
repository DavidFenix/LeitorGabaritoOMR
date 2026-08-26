package com.example.leitorgabaritoomr.application.grading;

import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OmrAnswerKeyLayoutValidatorTest {

    private final OmrAnswerKeyLayoutValidator validator =
            new OmrAnswerKeyLayoutValidator();

    @Test
    public void validAnswerKeyIsAccepted() {
        OmrLayoutDefinition layout = createLayout();

        validator.validateOrThrow(
                layout,
                createValidAnswerKey(layout)
        );
    }

    @Test
    public void moreThanOneExistingAcceptedOptionIsValid() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrAnswerKeyEntry> entries =
                createValidEntries(layout);

        OmrQuestionDefinition firstQuestion =
                layout.getAllQuestions().get(0);

        entries.set(
                0,
                new OmrAnswerKeyEntry(
                        firstQuestion.getId(),
                        Arrays.asList(
                                firstQuestion.getOptions()
                                        .get(0).getId(),
                                firstQuestion.getOptions()
                                        .get(1).getId()
                        ),
                        1.0
                )
        );

        validator.validateOrThrow(
                layout,
                createAnswerKey(
                        layout.getId(),
                        layout.getVersion(),
                        entries
                )
        );
    }

    @Test
    public void nonexistentAcceptedOptionIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrAnswerKeyEntry> entries =
                createValidEntries(layout);

        OmrQuestionDefinition firstQuestion =
                layout.getAllQuestions().get(0);

        entries.set(
                0,
                OmrAnswerKeyEntry.singleAnswer(
                        firstQuestion.getId(),
                        "option-that-does-not-exist",
                        1.0
                )
        );

        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(
                        layout.getId(),
                        layout.getVersion(),
                        entries
                );

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        layout,
                        answerKey
                )
        );
    }

    @Test
    public void nonexistentQuestionIsRejectedEvenWhenCountMatches() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrAnswerKeyEntry> entries =
                createValidEntries(layout);

        entries.set(
                0,
                OmrAnswerKeyEntry.singleAnswer(
                        "question-that-does-not-exist",
                        "option-that-does-not-exist",
                        1.0
                )
        );

        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(
                        layout.getId(),
                        layout.getVersion(),
                        entries
                );

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        layout,
                        answerKey
                )
        );
    }

    @Test
    public void differentQuestionCountIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<OmrAnswerKeyEntry> entries =
                createValidEntries(layout);

        entries.remove(entries.size() - 1);

        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(
                        layout.getId(),
                        layout.getVersion(),
                        entries
                );

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        layout,
                        answerKey
                )
        );
    }

    @Test
    public void differentLayoutIdIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(
                        "another-layout",
                        layout.getVersion(),
                        createValidEntries(layout)
                );

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        layout,
                        answerKey
                )
        );
    }

    @Test
    public void differentLayoutVersionIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition answerKey =
                createAnswerKey(
                        layout.getId(),
                        layout.getVersion() + 1,
                        createValidEntries(layout)
                );

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        layout,
                        answerKey
                )
        );
    }

    @Test
    public void nullArgumentsAreRejected() {
        OmrLayoutDefinition layout = createLayout();

        OmrAnswerKeyDefinition answerKey =
                createValidAnswerKey(layout);

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        null,
                        answerKey
                )
        );

        expectIllegalArgument(() ->
                validator.validateOrThrow(
                        layout,
                        null
                )
        );
    }

    private OmrLayoutDefinition createLayout() {
        return AvalieCeDevelopmentLayoutFactory.create();
    }

    private OmrAnswerKeyDefinition createValidAnswerKey(
            OmrLayoutDefinition layout
    ) {
        return createAnswerKey(
                layout.getId(),
                layout.getVersion(),
                createValidEntries(layout)
        );
    }

    private List<OmrAnswerKeyEntry> createValidEntries(
            OmrLayoutDefinition layout
    ) {
        List<OmrAnswerKeyEntry> entries =
                new ArrayList<>(layout.getQuestionCount());

        for (OmrQuestionDefinition question
                : layout.getAllQuestions()) {

            entries.add(
                    OmrAnswerKeyEntry.singleAnswer(
                            question.getId(),
                            question.getOptions()
                                    .get(0).getId(),
                            1.0
                    )
            );
        }

        return entries;
    }

    private OmrAnswerKeyDefinition createAnswerKey(
            String layoutId,
            int layoutVersion,
            List<OmrAnswerKeyEntry> entries
    ) {
        return new OmrAnswerKeyDefinition(
                "answer-key-layout-test",
                1,
                "Gabarito de teste do layout",
                layoutId,
                layoutVersion,
                entries
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
