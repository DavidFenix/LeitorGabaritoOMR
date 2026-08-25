package com.example.leitorgabaritoomr.application.reading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.aggregation.OptionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.SheetEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.NormalizedCoordinate;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Protege a fronteira entre a interpretação técnica da visão e o
 * resultado transportável da camada de negócio.
 *
 * O teste é JVM puro: não usa Android, câmera, OpenCV ou imagem.
 */
public final class OmrReadingResultMapperTest {

    private static final int FRAME_COUNT = 7;

    private static final String READING_ID =
            "reading-mapper-controlled";

    private static final long CAPTURED_AT =
            1_800_000_000_000L;

    private OmrReadingResultMapper mapper;

    @Before
    public void setUp() {
        mapper = new OmrReadingResultMapper();
    }

    @Test
    public void mapeiaMetadadosEstadosAlternativasEConfiancasSemPerdas() {
        SheetInterpretationResult source =
                createControlledSource();

        OmrReadingResult result =
                mapper.map(
                        source,
                        READING_ID,
                        CAPTURED_AT
                );

        assertEquals(
                READING_ID,
                result.getReadingId()
        );

        assertEquals(
                CAPTURED_AT,
                result.getCapturedAtEpochMillis()
        );

        assertEquals(
                "layout-mapper-controlled",
                result.getLayoutId()
        );

        assertEquals(
                3,
                result.getLayoutVersion()
        );

        assertEquals(
                "Layout controlado do mapper",
                result.getLayoutName()
        );

        assertEquals(5, result.getQuestionCount());
        assertEquals(1, result.getSingleMarkCount());
        assertEquals(1, result.getBlankCount());
        assertEquals(1, result.getMultipleMarkCount());
        assertEquals(1, result.getAmbiguousCount());
        assertEquals(1, result.getNotReadyCount());
        assertEquals(2, result.getReviewRequiredCount());

        assertFalse(result.isComplete());
        assertTrue(result.requiresReview());

        assertSingleQuestion(result);
        assertBlankQuestion(result);
        assertMultipleQuestion(result);
        assertAmbiguousQuestion(result);
        assertNotReadyQuestion(result);
    }

    @Test
    public void mapeamentoAutomaticoGeraIdentidadeEInstante() {
        SheetInterpretationResult source =
                createControlledSource();

        long before = System.currentTimeMillis();

        OmrReadingResult result =
                mapper.map(source);

        long after = System.currentTimeMillis();

        assertNotNull(result.getReadingId());
        assertFalse(result.getReadingId().trim().isEmpty());

        assertTrue(
                result.getCapturedAtEpochMillis()
                        >= before
        );

        assertTrue(
                result.getCapturedAtEpochMillis()
                        <= after
        );

        assertEquals(
                source.getQuestionCount(),
                result.getQuestionCount()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejeitaInterpretacaoNula() {
        mapper.map(null);
    }

    private void assertSingleQuestion(
            OmrReadingResult result
    ) {
        OmrQuestionResult question =
                result.getQuestionAtPosition(1);

        assertEquals(1, question.getPosition());
        assertEquals("q01", question.getQuestionId());
        assertEquals(
                OmrQuestionResult.Status.SINGLE_MARK,
                question.getStatus()
        );
        assertEquals(0.91, question.getConfidence(), 0.0);
        assertEquals(1, question.getRelevantOptions().size());
        assertEquals(
                "q01-option-B",
                question.getSelectedOption().getId()
        );
        assertEquals(
                "B",
                question.getSelectedOption().getLabel()
        );
    }

    private void assertBlankQuestion(
            OmrReadingResult result
    ) {
        OmrQuestionResult question =
                result.getQuestionAtPosition(2);

        assertEquals("q02", question.getQuestionId());
        assertEquals(
                OmrQuestionResult.Status.BLANK,
                question.getStatus()
        );
        assertEquals(0.85, question.getConfidence(), 0.0);
        assertTrue(question.getRelevantOptions().isEmpty());
    }

    private void assertMultipleQuestion(
            OmrReadingResult result
    ) {
        OmrQuestionResult question =
                result.getQuestionAtPosition(3);

        assertEquals("q03", question.getQuestionId());
        assertEquals(
                OmrQuestionResult.Status.MULTIPLE_MARKS,
                question.getStatus()
        );
        assertEquals(0.77, question.getConfidence(), 0.0);
        assertEquals(2, question.getRelevantOptions().size());
        assertEquals(
                "A",
                question.getRelevantOptions().get(0).getLabel()
        );
        assertEquals(
                "D",
                question.getRelevantOptions().get(1).getLabel()
        );
    }

    private void assertAmbiguousQuestion(
            OmrReadingResult result
    ) {
        OmrQuestionResult question =
                result.getQuestionAtPosition(4);

        assertEquals("q04", question.getQuestionId());
        assertEquals(
                OmrQuestionResult.Status.AMBIGUOUS,
                question.getStatus()
        );
        assertEquals(0.42, question.getConfidence(), 0.0);
        assertEquals(1, question.getRelevantOptions().size());
        assertEquals(
                "C",
                question.getRelevantOptions().get(0).getLabel()
        );
    }

    private void assertNotReadyQuestion(
            OmrReadingResult result
    ) {
        OmrQuestionResult question =
                result.getQuestionAtPosition(5);

        assertEquals("q05", question.getQuestionId());
        assertEquals(
                OmrQuestionResult.Status.NOT_READY,
                question.getStatus()
        );
        assertEquals(0.0, question.getConfidence(), 0.0);
        assertTrue(question.getRelevantOptions().isEmpty());
    }

    private SheetInterpretationResult
    createControlledSource() {
        List<OmrQuestionDefinition> questions =
                new ArrayList<>(5);

        for (int index = 0; index < 5; index++) {
            questions.add(
                    createQuestion(
                            index + 1,
                            0.15 + index * 0.15
                    )
            );
        }

        OmrBlockDefinition block =
                new OmrBlockDefinition(
                        "block-controlled",
                        "Bloco controlado",
                        questions
                );

        OmrLayoutDefinition layout =
                new OmrLayoutDefinition(
                        "layout-mapper-controlled",
                        3,
                        "Layout controlado do mapper",
                        1000,
                        1000,
                        Collections.singletonList(block)
                );

        List<QuestionEvidenceAggregate> aggregates =
                new ArrayList<>(questions.size());

        for (OmrQuestionDefinition question
                : questions) {

            aggregates.add(
                    createAggregate(question)
            );
        }

        SheetEvidenceAggregate sheet =
                new SheetEvidenceAggregate(
                        layout,
                        aggregates,
                        FRAME_COUNT,
                        FRAME_COUNT
                );

        List<QuestionInterpretation> interpretations =
                Arrays.asList(
                        new QuestionInterpretation(
                                aggregates.get(0),
                                QuestionMarkState.SINGLE_MARK,
                                Collections.singletonList(
                                        questions
                                        .get(0)
                                        .getOptions()
                                        .get(1)
                                ),
                                0.91
                        ),
                        new QuestionInterpretation(
                                aggregates.get(1),
                                QuestionMarkState.BLANK,
                                Collections
                                        .<OmrOptionDefinition>emptyList(),
                                0.85
                        ),
                        new QuestionInterpretation(
                                aggregates.get(2),
                                QuestionMarkState.MULTIPLE_MARKS,
                                Arrays.asList(
                                        questions
                                        .get(2)
                                        .getOptions()
                                        .get(0),
                                        questions
                                        .get(2)
                                        .getOptions()
                                        .get(3)
                                ),
                                0.77
                        ),
                        new QuestionInterpretation(
                                aggregates.get(3),
                                QuestionMarkState.AMBIGUOUS,
                                Collections.singletonList(
                                        questions
                                        .get(3)
                                        .getOptions()
                                        .get(2)
                                ),
                                0.42
                        ),
                        new QuestionInterpretation(
                                aggregates.get(4),
                                QuestionMarkState.NOT_READY,
                                Collections
                                        .<OmrOptionDefinition>emptyList(),
                                0.0
                        )
                );

        return new SheetInterpretationResult(
                sheet,
                interpretations
        );
    }

    private OmrQuestionDefinition createQuestion(
            int questionNumber,
            double centerY
    ) {
        String questionId = String.format(
                "q%02d",
                questionNumber
        );

        String[] labels = {"A", "B", "C", "D"};

        List<OmrOptionDefinition> options =
                new ArrayList<>(labels.length);

        for (int index = 0;
             index < labels.length;
             index++) {

            String label = labels[index];

            options.add(
                    OmrOptionDefinition.circular(
                            questionId
                                    + "-option-"
                                    + label,
                            label,
                            new NormalizedCoordinate(
                                    0.20 + index * 0.18,
                                    centerY
                            ),
                            0.03
                    )
            );
        }

        return new OmrQuestionDefinition(
                questionId,
                String.valueOf(questionNumber),
                options
        );
    }

    private QuestionEvidenceAggregate createAggregate(
            OmrQuestionDefinition question
    ) {
        List<OptionEvidenceAggregate> options =
                new ArrayList<>(
                        question.getOptionCount()
                );

        for (int index = 0;
             index < question.getOptionCount();
             index++) {

            boolean winner = index == 0;

            options.add(
                    new OptionEvidenceAggregate(
                            question.getOptions().get(index),
                            FRAME_COUNT,
                            winner ? FRAME_COUNT : 0,
                            winner ? 0.80 : 0.10,
                            winner ? 0.90 : 0.20,
                            winner ? 1.00 : 0.10,
                            winner ? 0.40 : 0.0,
                            winner ? 0.90 : 0.10
                    )
            );
        }

        return new QuestionEvidenceAggregate(
                question,
                options
        );
    }
}
