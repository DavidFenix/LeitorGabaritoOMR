package com.example.leitorgabaritoomr.vision.interpretation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.leitorgabaritoomr.vision.aggregation.OptionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.layout.NormalizedCoordinate;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Regressao numerica dos casos controlados validados no
 * gabarito_casos_controlados_v2.png.
 *
 * Este teste nao usa Android, camera, OpenCV ou arquivos de imagem.
 * Ele protege a fronteira entre o ranking temporal e a interpretacao
 * semantica final.
 */
public final class QuestionInterpreterControlledCasesTest {

    private static final int FRAME_COUNT = 7;

    private QuestionInterpreter interpreter;

    @Before
    public void setUp() {
        interpreter = new QuestionInterpreter(
                QuestionInterpreterConfig
                        .developmentDefaults()
        );
    }

    @Test
    public void q01_temVencedorTemporalMasPermaneceBranca() {
        QuestionEvidenceAggregate aggregate =
                createAggregate(
                        "block-01-row-01",
                        new double[]{
                                0.065, 0.093, 0.139, 0.173
                        },
                        new double[]{
                                0.019, 0.069, 0.148, 0.752
                        },
                        3,
                        1.0
                );

        QuestionInterpretation result =
                interpreter.interpret(aggregate, true);

        assertEquals(
                QuestionMarkState.BLANK,
                result.getState()
        );

        assertNull(result.getSelectedOption());

        /*
         * Confirma o aspecto central deste caso: D venceu todos os
         * frames, mas sua evidencia absoluta continuou sendo fraca.
         */
        assertEquals(
                "D",
                aggregate.getWinner().getOption().getLabel()
        );

        assertEquals(
                1.0,
                aggregate.getWinnerVoteRatio(),
                0.000001
        );
    }

    @Test
    public void q15_duasEvidenciasFortesProduzemMultipla() {
        QuestionEvidenceAggregate aggregate =
                createAggregate(
                        "block-02-row-02",
                        new double[]{
                                0.097, 1.000, 0.095, 1.000
                        },
                        new double[]{
                                0.031, 0.450, 0.030, 0.450
                        },
                        1,
                        0.0
                );

        QuestionInterpretation result =
                interpreter.interpret(aggregate, true);

        assertEquals(
                QuestionMarkState.MULTIPLE_MARKS,
                result.getState()
        );

        assertNull(result.getSelectedOption());

        assertEquals(
                "B,D",
                relevantLabels(result)
        );
    }

    @Test
    public void q28_evidenciaVintePorCentoPermaneceBranca() {
        QuestionInterpretation result =
                interpreter.interpret(
                        createAggregate(
                                "block-03-row-02",
                                new double[]{
                                        0.076, 0.016, 0.112, 0.066
                                },
                                new double[]{
                                        0.108, 0.005, 0.734, 0.082
                                },
                                2,
                                1.0
                        ),
                        true
                );

        assertEquals(
                QuestionMarkState.BLANK,
                result.getState()
        );
    }

    @Test
    public void q29_evidenciaTrintaPorCentoPermaneceBranca() {
        QuestionInterpretation result =
                interpreter.interpret(
                        createAggregate(
                                "block-03-row-03",
                                new double[]{
                                        0.088, 0.013, 0.130, 0.042
                                },
                                new double[]{
                                        0.111, 0.004, 0.739, 0.043
                                },
                                2,
                                1.0
                        ),
                        true
                );

        assertEquals(
                QuestionMarkState.BLANK,
                result.getState()
        );
    }

    @Test
    public void q30_evidenciaSuficienteProduzMarcacaoUnica() {
        QuestionInterpretation result =
                interpreter.interpret(
                        createAggregate(
                                "block-03-row-04",
                                new double[]{
                                        0.709, 0.015, 0.043, 0.051
                                },
                                new double[]{
                                        0.913, 0.005, 0.018, 0.022
                                },
                                0,
                                1.0
                        ),
                        true
                );

        assertEquals(
                QuestionMarkState.SINGLE_MARK,
                result.getState()
        );

        assertEquals(
                "A",
                result.getSelectedOption().getLabel()
        );
    }

    @Test
    public void q41_evidenciaNaFaixaIntermediariaProduzAmbigua() {
        QuestionInterpretation result =
                interpreter.interpret(
                        createAggregate(
                                "block-04-row-02",
                                new double[]{
                                        0.032, 0.024, 0.209, 0.039
                                },
                                new double[]{
                                        0.016, 0.008, 0.763, 0.021
                                },
                                2,
                                1.0
                        ),
                        true
                );

        assertEquals(
                QuestionMarkState.AMBIGUOUS,
                result.getState()
        );

        assertNull(result.getSelectedOption());

        assertEquals(
                "C",
                relevantLabels(result)
        );
    }

    @Test
    public void q42_residuoFracoNaoProduzMultipla() {
        QuestionInterpretation result =
                interpreter.interpret(
                        createAggregate(
                                "block-04-row-03",
                                new double[]{
                                        0.026, 1.000, 0.159, 0.030
                                },
                                new double[]{
                                        0.009, 1.000, 0.067, 0.010
                                },
                                1,
                                1.0
                        ),
                        true
                );

        assertEquals(
                QuestionMarkState.SINGLE_MARK,
                result.getState()
        );

        assertEquals(
                "B",
                result.getSelectedOption().getLabel()
        );
    }

    @Test
    public void consensoIncompletoNuncaProduzRespostaFinal() {
        QuestionEvidenceAggregate aggregate =
                createAggregate(
                        "not-ready",
                        new double[]{
                                0.020, 1.000, 0.030, 0.040
                        },
                        new double[]{
                                0.010, 1.000, 0.012, 0.015
                        },
                        1,
                        1.0
                );

        QuestionInterpretation result =
                interpreter.interpret(aggregate, false);

        assertEquals(
                QuestionMarkState.NOT_READY,
                result.getState()
        );

        assertNull(result.getSelectedOption());
    }

    private QuestionEvidenceAggregate createAggregate(
            String questionId,
            double[] robustEvidence,
            double[] consensusScores,
            int temporalWinnerIndex,
            double winnerWeightedVoteRatio
    ) {
        if (robustEvidence.length != 4
                || consensusScores.length != 4) {

            throw new IllegalArgumentException(
                    "O caso controlado deve possuir quatro alternativas."
            );
        }

        OmrQuestionDefinition question =
                createQuestion(questionId);

        List<OptionEvidenceAggregate> aggregates =
                new ArrayList<>(4);

        double maximumRobustEvidence = 0.0;

        for (double evidence : robustEvidence) {
            maximumRobustEvidence = Math.max(
                    maximumRobustEvidence,
                    evidence
            );
        }

        for (int index = 0; index < 4; index++) {
            boolean temporalWinner =
                    index == temporalWinnerIndex;

            int winCount =
                    temporalWinner ? FRAME_COUNT : 0;

            double weightedWinRatio =
                    temporalWinner
                            ? winnerWeightedVoteRatio
                            : 0.0;

            double relativeEvidence =
                    maximumRobustEvidence <= 0.0
                            ? 0.0
                            : robustEvidence[index]
                            / maximumRobustEvidence;

            aggregates.add(
                    new OptionEvidenceAggregate(
                            question.getOptions().get(index),
                            FRAME_COUNT,
                            winCount,
                            weightedWinRatio * FRAME_COUNT,
                            weightedWinRatio,
                            robustEvidence[index],
                            robustEvidence[index],
                            robustEvidence[index],
                            relativeEvidence,
                            temporalWinner ? 0.25 : 0.0,
                            consensusScores[index]
                    )
            );
        }

        return new QuestionEvidenceAggregate(
                question,
                aggregates
        );
    }

    private OmrQuestionDefinition createQuestion(
            String questionId
    ) {
        List<OmrOptionDefinition> options =
                new ArrayList<>(4);

        String[] labels = {"A", "B", "C", "D"};

        for (int index = 0; index < labels.length; index++) {
            double centerX =
                    0.20 + index * 0.20;

            options.add(
                    OmrOptionDefinition.circular(
                            questionId + "-" + labels[index],
                            labels[index],
                            new NormalizedCoordinate(
                                    centerX,
                                    0.50
                            ),
                            0.02
                    )
            );
        }

        return new OmrQuestionDefinition(
                questionId,
                questionId,
                options
        );
    }

    private String relevantLabels(
            QuestionInterpretation interpretation
    ) {
        StringBuilder builder =
                new StringBuilder();

        for (OmrOptionDefinition option
                : interpretation.getRelevantOptions()) {

            if (builder.length() > 0) {
                builder.append(',');
            }

            builder.append(option.getLabel());
        }

        return builder.toString();
    }
}
