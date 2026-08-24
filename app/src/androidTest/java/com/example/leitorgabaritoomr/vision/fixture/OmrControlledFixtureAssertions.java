package com.example.leitorgabaritoomr.vision.fixture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Expectativas unicas da fixture controlada v3.
 *
 * Mantem os numeros e casos especiais fora dos executores, das
 * variantes e dos provedores de frames. Assim qualquer cenario usa
 * exatamente a mesma regua para validar a interpretacao final.
 */
public final class OmrControlledFixtureAssertions {

    private OmrControlledFixtureAssertions() {
        throw new AssertionError(
                "Esta classe nao deve ser instanciada."
        );
    }

    public static SheetInterpretationResult
    assertCompleteAndCorrect(
            String scenarioPrefix,
            OmrFixturePipelineRunner.Result runResult
    ) {
        String prefix = normalizePrefix(scenarioPrefix);

        assertNotNull(
                prefix + " | execucao ausente",
                runResult
        );

        assertNotNull(
                prefix + " | interpretacao ausente | "
                        + runResult,
                runResult.getInterpretationResult()
        );

        assertTrue(
                prefix + " | consenso incompleto | "
                        + runResult,
                runResult.isComplete()
        );

        SheetInterpretationResult result =
                runResult.getInterpretationResult();

        assertFinalTotals(prefix, result);
        assertControlledCases(prefix, result);

        return result;
    }

    public static String summarize(
            SheetInterpretationResult result
    ) {
        if (result == null) {
            return "interpretation=null";
        }

        return String.format(
                Locale.US,
                "questions=%d | single=%d | blank=%d"
                        + " | multiple=%d | ambiguous=%d"
                        + " | notReady=%d | review=%d",
                result.getQuestionCount(),
                result.getSingleMarkCount(),
                result.getBlankCount(),
                result.getMultipleMarkCount(),
                result.getAmbiguousCount(),
                result.getNotReadyCount(),
                result.getReviewRequiredCount()
        );
    }

    private static void assertFinalTotals(
            String prefix,
            SheetInterpretationResult result
    ) {
        assertEquals(
                prefix + " | questions",
                52,
                result.getQuestionCount()
        );

        assertEquals(
                prefix + " | single",
                47,
                result.getSingleMarkCount()
        );

        assertEquals(
                prefix + " | blank",
                3,
                result.getBlankCount()
        );

        assertEquals(
                prefix + " | multiple",
                1,
                result.getMultipleMarkCount()
        );

        assertEquals(
                prefix + " | ambiguous",
                1,
                result.getAmbiguousCount()
        );

        assertEquals(
                prefix + " | notReady",
                0,
                result.getNotReadyCount()
        );

        assertEquals(
                prefix + " | review",
                2,
                result.getReviewRequiredCount()
        );

        assertTrue(
                prefix + " | revisao esperada",
                result.requiresReview()
        );
    }

    private static void assertControlledCases(
            String prefix,
            SheetInterpretationResult result
    ) {
        assertState(
                prefix,
                result,
                "block-01-row-01",
                QuestionMarkState.BLANK
        );

        assertRelevantOptions(
                prefix,
                result,
                "block-02-row-02",
                QuestionMarkState.MULTIPLE_MARKS,
                "B",
                "D"
        );

        assertState(
                prefix,
                result,
                "block-03-row-02",
                QuestionMarkState.BLANK
        );

        assertState(
                prefix,
                result,
                "block-03-row-03",
                QuestionMarkState.BLANK
        );

        assertSingle(
                prefix,
                result,
                "block-03-row-04",
                "A"
        );

        assertRelevantOptions(
                prefix,
                result,
                "block-04-row-02",
                QuestionMarkState.AMBIGUOUS,
                "C"
        );

        assertSingle(
                prefix,
                result,
                "block-04-row-03",
                "B"
        );
    }

    private static void assertState(
            String prefix,
            SheetInterpretationResult result,
            String questionId,
            QuestionMarkState expectedState
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        prefix,
                        result,
                        questionId
                );

        assertEquals(
                prefix + " | " + questionId,
                expectedState,
                interpretation.getState()
        );
    }

    private static void assertSingle(
            String prefix,
            SheetInterpretationResult result,
            String questionId,
            String expectedOptionLabel
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        prefix,
                        result,
                        questionId
                );

        assertEquals(
                prefix + " | " + questionId,
                QuestionMarkState.SINGLE_MARK,
                interpretation.getState()
        );

        assertNotNull(
                prefix
                        + " | "
                        + questionId
                        + " sem alternativa selecionada",
                interpretation.getSelectedOption()
        );

        assertEquals(
                prefix + " | " + questionId,
                expectedOptionLabel,
                interpretation
                        .getSelectedOption()
                        .getLabel()
        );
    }

    private static void assertRelevantOptions(
            String prefix,
            SheetInterpretationResult result,
            String questionId,
            QuestionMarkState expectedState,
            String... expectedLabels
    ) {
        QuestionInterpretation interpretation =
                requireQuestion(
                        prefix,
                        result,
                        questionId
                );

        assertEquals(
                prefix + " | " + questionId,
                expectedState,
                interpretation.getState()
        );

        List<String> actualLabels =
                new ArrayList<>();

        for (OmrOptionDefinition option
                : interpretation.getRelevantOptions()) {

            actualLabels.add(option.getLabel());
        }

        List<String> expectedLabelList =
                new ArrayList<>();

        for (String expectedLabel : expectedLabels) {
            expectedLabelList.add(expectedLabel);
        }

        assertEquals(
                prefix + " | " + questionId,
                expectedLabelList,
                actualLabels
        );
    }

    private static QuestionInterpretation requireQuestion(
            String prefix,
            SheetInterpretationResult result,
            String questionId
    ) {
        QuestionInterpretation interpretation =
                result.findByQuestionId(questionId);

        assertNotNull(
                prefix
                        + " | questao ausente: "
                        + questionId,
                interpretation
        );

        return interpretation;
    }

    private static String normalizePrefix(
            String scenarioPrefix
    ) {
        if (scenarioPrefix == null
                || scenarioPrefix.trim().isEmpty()) {

            return "fixture-controlada-v3";
        }

        return scenarioPrefix.trim();
    }
}
