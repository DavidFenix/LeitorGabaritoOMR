package com.example.leitorgabaritoomr.presentation.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Testes JVM do contrato visual da tela de resultado OMR.
 */
public final class OmrReadingResultViewStateTest {

    @Test
    public void leituraLimpaProduzEstadoConcluido() {
        OmrReadingResult result =
                createReading(
                        single(1, "q01", "A", 0.874),
                        blank(2, "q02", 0.91),
                        single(3, "q03", "C", 0.995)
                );

        OmrReadingResultViewState viewState =
                OmrReadingResultViewState.from(result);

        assertEquals(
                OmrReadingResultViewState
                        .OverallState
                        .COMPLETED,
                viewState.getOverallState()
        );

        assertTrue(viewState.isComplete());
        assertFalse(viewState.requiresReview());
        assertEquals(3, viewState.getQuestionCount());
        assertEquals(2, viewState.getSingleMarkCount());
        assertEquals(1, viewState.getBlankCount());
        assertEquals(0, viewState.getReviewRequiredCount());
        assertTrue(viewState.getReviewItems().isEmpty());

        OmrReadingResultViewState.QuestionItem first =
                viewState.getQuestionAtPosition(1);

        assertEquals(1, first.getPosition());
        assertEquals("q01", first.getQuestionId());
        assertEquals(
                OmrReadingResultViewState
                        .QuestionState
                        .ANSWERED,
                first.getState()
        );
        assertEquals("A", first.getSelectedOptionLabel());
        assertEquals(87, first.getConfidencePercent());

        OmrReadingResultViewState.QuestionItem second =
                viewState.getQuestionAtPosition(2);

        assertEquals(
                OmrReadingResultViewState
                        .QuestionState
                        .BLANK,
                second.getState()
        );
        assertNull(second.getSelectedOptionLabel());
        assertTrue(
                second
                .getRelevantOptionLabels()
                .isEmpty()
        );
    }

    @Test
    public void multiplaEAmbiguaProduzemConclusaoComRevisao() {
        OmrReadingResult result =
                createReading(
                        single(1, "q01", "B", 0.96),
                        multiple(
                                2,
                                "q02",
                                0.78,
                                "A",
                                "D"
                        ),
                        ambiguous(
                                3,
                                "q03",
                                0.43,
                                "C"
                        )
                );

        OmrReadingResultViewState viewState =
                OmrReadingResultViewState.from(result);

        assertEquals(
                OmrReadingResultViewState
                        .OverallState
                        .COMPLETED_WITH_REVIEW,
                viewState.getOverallState()
        );

        assertTrue(viewState.isComplete());
        assertTrue(viewState.requiresReview());
        assertEquals(2, viewState.getReviewRequiredCount());
        assertEquals(2, viewState.getReviewItems().size());

        OmrReadingResultViewState.QuestionItem multiple =
                viewState.getReviewItems().get(0);

        assertEquals(2, multiple.getPosition());
        assertEquals(
                OmrReadingResultViewState
                        .QuestionState
                        .MULTIPLE,
                multiple.getState()
        );
        assertEquals(
                Arrays.asList("A", "D"),
                multiple.getRelevantOptionLabels()
        );
        assertNull(multiple.getSelectedOptionLabel());

        OmrReadingResultViewState.QuestionItem ambiguous =
                viewState.getReviewItems().get(1);

        assertEquals(3, ambiguous.getPosition());
        assertEquals(
                OmrReadingResultViewState
                        .QuestionState
                        .AMBIGUOUS,
                ambiguous.getState()
        );
        assertEquals(
                Collections.singletonList("C"),
                ambiguous.getRelevantOptionLabels()
        );
    }

    @Test
    public void leituraIncompletaContinuaExpondoRevisoesExistentes() {
        OmrReadingResult result =
                createReading(
                        notReady(1, "q01"),
                        multiple(
                                2,
                                "q02",
                                0.70,
                                "B",
                                "C"
                        )
                );

        OmrReadingResultViewState viewState =
                OmrReadingResultViewState.from(result);

        assertEquals(
                OmrReadingResultViewState
                        .OverallState
                        .INCOMPLETE,
                viewState.getOverallState()
        );

        assertFalse(viewState.isComplete());
        assertTrue(viewState.requiresReview());
        assertEquals(1, viewState.getNotReadyCount());
        assertEquals(1, viewState.getReviewRequiredCount());
        assertEquals(2, viewState.getReviewItems().get(0).getPosition());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejeitaResultadoNulo() {
        OmrReadingResultViewState.from(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void listaDeQuestoesNaoPodeSerAlterada() {
        OmrReadingResultViewState viewState =
                OmrReadingResultViewState.from(
                        createReading(
                                single(
                                        1,
                                        "q01",
                                        "A",
                                        0.90
                                )
                        )
                );

        viewState.getQuestionItems().clear();
    }

    private OmrReadingResult createReading(
            OmrQuestionResult... questions
    ) {
        return new OmrReadingResult(
                "reading-view-state-test",
                1_800_000_000_000L,
                "layout-view-state-test",
                2,
                "Layout de teste da tela",
                Arrays.asList(questions)
        );
    }

    private OmrQuestionResult single(
            int position,
            String questionId,
            String optionLabel,
            double confidence
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        option(
                                questionId,
                                optionLabel
                        )
                ),
                confidence
        );
    }

    private OmrQuestionResult blank(
            int position,
            String questionId,
            double confidence
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.BLANK,
                Collections
                        .<OmrQuestionResult.Option>emptyList(),
                confidence
        );
    }

    private OmrQuestionResult multiple(
            int position,
            String questionId,
            double confidence,
            String... optionLabels
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.MULTIPLE_MARKS,
                options(questionId, optionLabels),
                confidence
        );
    }

    private OmrQuestionResult ambiguous(
            int position,
            String questionId,
            double confidence,
            String... optionLabels
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.AMBIGUOUS,
                options(questionId, optionLabels),
                confidence
        );
    }

    private OmrQuestionResult notReady(
            int position,
            String questionId
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.NOT_READY,
                Collections
                        .<OmrQuestionResult.Option>emptyList(),
                0.0
        );
    }

    private List<OmrQuestionResult.Option> options(
            String questionId,
            String... optionLabels
    ) {
        List<OmrQuestionResult.Option> options =
                new ArrayList<>(optionLabels.length);

        for (String optionLabel : optionLabels) {
            options.add(
                    option(
                            questionId,
                            optionLabel
                    )
            );
        }

        return options;
    }

    private OmrQuestionResult.Option option(
            String questionId,
            String optionLabel
    ) {
        return new OmrQuestionResult.Option(
                questionId
                        + "-option-"
                        + optionLabel,
                optionLabel
        );
    }
}
