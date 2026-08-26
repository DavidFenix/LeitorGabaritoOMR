package com.example.leitorgabaritoomr.application.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
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

public final class OmrAnswerKeyDefinitionFactoryTest {

    private static final double DELTA = 0.000001;

    private final OmrAnswerKeyDefinitionFactory factory =
            new OmrAnswerKeyDefinitionFactory();

    @Test
    public void singleAnswerKeyMapsAllLabelsToRealOptionIds() {
        OmrLayoutDefinition layout = createLayout();

        List<String> labels =
                new ArrayList<>(layout.getQuestionCount());

        List<OmrOptionDefinition> expectedOptions =
                new ArrayList<>(layout.getQuestionCount());

        int questionIndex = 0;

        for (OmrQuestionDefinition question
                : layout.getAllQuestions()) {

            OmrOptionDefinition expectedOption =
                    question.getOptions().get(
                            questionIndex
                                    % question.getOptions().size()
                    );

            labels.add(expectedOption.getLabel());
            expectedOptions.add(expectedOption);
            questionIndex++;
        }

        OmrAnswerKeyDefinition answerKey =
                factory.createSingleAnswerKey(
                        "avalie-ce-key",
                        3,
                        "Gabarito Avalie CE",
                        layout,
                        labels,
                        1.5
                );

        assertEquals("avalie-ce-key", answerKey.getId());
        assertEquals(3, answerKey.getVersion());
        assertEquals(
                "Gabarito Avalie CE",
                answerKey.getName()
        );
        assertEquals(layout.getId(), answerKey.getLayoutId());
        assertEquals(
                layout.getVersion(),
                answerKey.getLayoutVersion()
        );
        assertEquals(52, answerKey.getQuestionCount());
        assertEquals(78.0, answerKey.getTotalWeight(), DELTA);

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            OmrQuestionDefinition question =
                    layout.getAllQuestions().get(index);

            OmrAnswerKeyEntry entry =
                    answerKey.getEntries().get(index);

            assertEquals(
                    question.getId(),
                    entry.getQuestionId()
            );
            assertEquals(1, entry.getAcceptedOptionCount());
            assertTrue(
                    entry.acceptsOption(
                            expectedOptions.get(index).getId()
                    )
            );
            assertEquals(1.5, entry.getWeight(), DELTA);
        }
    }

    @Test
    public void labelsAreTrimmedAndCaseInsensitive() {
        OmrLayoutDefinition layout = createLayout();

        List<String> labels =
                repeatedLabels(layout, "  a  ");

        OmrAnswerKeyDefinition answerKey =
                factory.createSingleAnswerKey(
                        "normalized-labels",
                        1,
                        "Rótulos normalizados",
                        layout,
                        labels,
                        1.0
                );

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            String expectedOptionId =
                    layout.getAllQuestions()
                            .get(index)
                            .getOptions()
                            .get(0)
                            .getId();

            assertTrue(
                    answerKey.getEntries()
                            .get(index)
                            .acceptsOption(expectedOptionId)
            );
        }
    }

    @Test
    public void multipleAcceptedLabelsAndDifferentWeightsAreSupported() {
        OmrLayoutDefinition layout = createLayout();

        List<Collection<String>> acceptedLabels =
                repeatedAcceptedLabels(layout, "B");

        acceptedLabels.set(
                0,
                Arrays.asList("A", "C")
        );

        List<Double> weights =
                repeatedWeights(layout, 0.5);

        weights.set(0, 2.5);

        OmrAnswerKeyDefinition answerKey =
                factory.create(
                        "weighted-key",
                        1,
                        "Gabarito com pesos",
                        layout,
                        acceptedLabels,
                        weights
                );

        OmrQuestionDefinition firstQuestion =
                layout.getAllQuestions().get(0);

        OmrAnswerKeyEntry firstEntry =
                answerKey.getEntries().get(0);

        assertEquals(2, firstEntry.getAcceptedOptionCount());
        assertTrue(
                firstEntry.acceptsOption(
                        firstQuestion.getOptions().get(0).getId()
                )
        );
        assertTrue(
                firstEntry.acceptsOption(
                        firstQuestion.getOptions().get(2).getId()
                )
        );
        assertEquals(2.5, firstEntry.getWeight(), DELTA);
        assertEquals(28.0, answerKey.getTotalWeight(), DELTA);
    }

    @Test
    public void differentAnswerCountIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<String> labels = repeatedLabels(layout, "A");
        labels.remove(labels.size() - 1);

        expectIllegalArgument(() ->
                factory.createSingleAnswerKey(
                        "wrong-answer-count",
                        1,
                        "Quantidade incorreta",
                        layout,
                        labels,
                        1.0
                )
        );
    }

    @Test
    public void differentWeightCountIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<Collection<String>> acceptedLabels =
                repeatedAcceptedLabels(layout, "A");

        List<Double> weights = repeatedWeights(layout, 1.0);
        weights.remove(weights.size() - 1);

        expectIllegalArgument(() ->
                factory.create(
                        "wrong-weight-count",
                        1,
                        "Quantidade incorreta",
                        layout,
                        acceptedLabels,
                        weights
                )
        );
    }

    @Test
    public void nonexistentLabelIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<String> labels = repeatedLabels(layout, "A");
        labels.set(10, "E");

        expectIllegalArgument(() ->
                factory.createSingleAnswerKey(
                        "invalid-label",
                        1,
                        "Alternativa inexistente",
                        layout,
                        labels,
                        1.0
                )
        );
    }

    @Test
    public void repeatedAcceptedLabelIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<Collection<String>> acceptedLabels =
                repeatedAcceptedLabels(layout, "B");

        acceptedLabels.set(
                0,
                Arrays.asList("A", "a")
        );

        expectIllegalArgument(() ->
                factory.create(
                        "repeated-label",
                        1,
                        "Alternativa repetida",
                        layout,
                        acceptedLabels,
                        repeatedWeights(layout, 1.0)
                )
        );
    }

    @Test
    public void emptyAcceptedLabelsAreRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<Collection<String>> acceptedLabels =
                repeatedAcceptedLabels(layout, "A");

        acceptedLabels.set(
                4,
                Collections.emptyList()
        );

        expectIllegalArgument(() ->
                factory.create(
                        "empty-answer",
                        1,
                        "Resposta vazia",
                        layout,
                        acceptedLabels,
                        repeatedWeights(layout, 1.0)
                )
        );
    }

    @Test
    public void nullWeightIsRejected() {
        OmrLayoutDefinition layout = createLayout();

        List<Double> weights = repeatedWeights(layout, 1.0);
        weights.set(5, null);

        expectIllegalArgument(() ->
                factory.create(
                        "null-weight",
                        1,
                        "Peso nulo",
                        layout,
                        repeatedAcceptedLabels(layout, "A"),
                        weights
                )
        );
    }

    @Test
    public void nullRequiredInputsAreRejected() {
        OmrLayoutDefinition layout = createLayout();

        expectIllegalArgument(() ->
                factory.createSingleAnswerKey(
                        "null-layout",
                        1,
                        "Layout nulo",
                        null,
                        Collections.emptyList(),
                        1.0
                )
        );

        expectIllegalArgument(() ->
                factory.createSingleAnswerKey(
                        "null-answers",
                        1,
                        "Respostas nulas",
                        layout,
                        null,
                        1.0
                )
        );

        expectIllegalArgument(() ->
                factory.create(
                        "null-answer-groups",
                        1,
                        "Grupos nulos",
                        layout,
                        null,
                        repeatedWeights(layout, 1.0)
                )
        );

        expectIllegalArgument(() ->
                factory.create(
                        "null-weights",
                        1,
                        "Pesos nulos",
                        layout,
                        repeatedAcceptedLabels(layout, "A"),
                        null
                )
        );
    }

    private OmrLayoutDefinition createLayout() {
        return AvalieCeDevelopmentLayoutFactory.create();
    }

    private List<String> repeatedLabels(
            OmrLayoutDefinition layout,
            String label
    ) {
        List<String> labels =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            labels.add(label);
        }

        return labels;
    }

    private List<Collection<String>> repeatedAcceptedLabels(
            OmrLayoutDefinition layout,
            String label
    ) {
        List<Collection<String>> acceptedLabels =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            acceptedLabels.add(
                    Collections.singleton(label)
            );
        }

        return acceptedLabels;
    }

    private List<Double> repeatedWeights(
            OmrLayoutDefinition layout,
            double weight
    ) {
        List<Double> weights =
                new ArrayList<>(layout.getQuestionCount());

        for (int index = 0;
             index < layout.getQuestionCount();
             index++) {

            weights.add(weight);
        }

        return weights;
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
