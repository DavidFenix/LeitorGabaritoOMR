package com.example.leitorgabaritoomr.vision.debug;

import android.util.Log;

import com.example.leitorgabaritoomr.vision.aggregation.OptionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.aggregation.QuestionEvidenceAggregate;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpreterConfig;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.List;
import java.util.Locale;

/**
 * Publica no Logcat um retrato numerico auditavel da interpretacao
 * semantica da folha.
 *
 * Cada valor exibido vem das mesmas instancias de evidencia usadas
 * por QuestionInterpreter. Esta classe nao mede pixels, nao refaz
 * geometria e nao reclassifica questoes.
 *
 * Uma instancia registra no maximo uma folha completa. Chame reset()
 * quando a aquisicao atual for descartada e uma nova folha puder ser
 * iniciada.
 */
public final class QuestionInterpretationDiagnosticLogger {

    public static final String DEFAULT_TAG =
            "OMR_INTERPRETATION";

    private final String tag;

    private final QuestionInterpreterConfig
            interpreterConfig;

    private boolean resultLogged;

    public QuestionInterpretationDiagnosticLogger(
            QuestionInterpreterConfig interpreterConfig
    ) {
        this(
                DEFAULT_TAG,
                interpreterConfig
        );
    }

    public QuestionInterpretationDiagnosticLogger(
            String tag,
            QuestionInterpreterConfig interpreterConfig
    ) {
        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "A tag do Logcat e obrigatoria."
            );
        }

        if (interpreterConfig == null) {
            throw new IllegalArgumentException(
                    "A configuracao do interpretador e obrigatoria."
            );
        }

        this.tag = tag.trim();
        this.interpreterConfig = interpreterConfig;
    }

    /**
     * Registra uma unica vez o primeiro resultado completo recebido.
     *
     * @return true quando o relatorio foi publicado nesta chamada.
     */
    public synchronized boolean logOnce(
            SheetInterpretationResult result
    ) {
        if (resultLogged
                || result == null
                || !result.isComplete()) {

            return false;
        }

        Log.i(
                tag,
                "BEGIN | "
                        + result
                        + " | thresholds="
                        + interpreterConfig
        );

        for (QuestionInterpretation interpretation
                : result.getQuestionInterpretations()) {

            logQuestion(interpretation);
        }

        Log.i(
                tag,
                "END | questions="
                        + result.getQuestionCount()
                        + " | review="
                        + result.getReviewRequiredCount()
        );

        resultLogged = true;

        return true;
    }

    private void logQuestion(
            QuestionInterpretation interpretation
    ) {
        QuestionEvidenceAggregate questionAggregate =
                interpretation.getEvidenceAggregate();

        OptionEvidenceAggregate winner =
                questionAggregate.getWinner();

        OptionEvidenceAggregate runnerUp =
                questionAggregate.getRunnerUp();

        String selectedLabel =
                interpretation.getSelectedOption() == null
                        ? "-"
                        : interpretation
                        .getSelectedOption()
                        .getLabel();

        String message = String.format(
                Locale.US,
                "Q=%s | state=%s | selected=%s"
                        + " | relevant=%s | confidence=%.3f"
                        + " | winner=%s | runner=%s"
                        + " | frames=%d | winnerVote=%.3f"
                        + " | winnerWeightedVote=%.3f"
                        + " | consensusGap=%.3f | options=%s",
                interpretation.getQuestion().getId(),
                interpretation.getState(),
                selectedLabel,
                relevantLabels(
                        interpretation.getRelevantOptions()
                ),
                interpretation.getConfidence(),
                winner.getOption().getLabel(),
                runnerUp.getOption().getLabel(),
                questionAggregate.getAccumulatedFrames(),
                questionAggregate.getWinnerVoteRatio(),
                winner.getWeightedWinRatio(),
                questionAggregate.getConsensusGap(),
                optionEvidenceSummary(
                        questionAggregate.getOptionAggregates()
                )
        );

        Log.i(tag, message);
    }

    private String relevantLabels(
            List<OmrOptionDefinition> options
    ) {
        if (options.isEmpty()) {
            return "-";
        }

        StringBuilder builder =
                new StringBuilder();

        for (OmrOptionDefinition option : options) {
            if (builder.length() > 0) {
                builder.append(',');
            }

            builder.append(option.getLabel());
        }

        return builder.toString();
    }

    private String optionEvidenceSummary(
            List<OptionEvidenceAggregate> optionAggregates
    ) {
        StringBuilder builder =
                new StringBuilder("[");

        for (OptionEvidenceAggregate aggregate
                : optionAggregates) {

            if (builder.length() > 1) {
                builder.append(';');
            }

            builder.append(
                    String.format(
                            Locale.US,
                            "%s{rob=%.3f,avg=%.3f,max=%.3f,"
                                    + "wins=%d/%d,vote=%.3f,"
                                    + "wVote=%.3f,relative=%.3f,"
                                    + "winGap=%.3f,score=%.3f}",
                            aggregate.getOption().getLabel(),
                            aggregate.getRobustEvidence(),
                            aggregate.getAverageEvidence(),
                            aggregate.getMaximumEvidence(),
                            aggregate.getWinCount(),
                            aggregate.getSampleCount(),
                            aggregate.getWinRatio(),
                            aggregate.getWeightedWinRatio(),
                            aggregate.getAverageRelativeEvidence(),
                            aggregate.getAverageWinningGap(),
                            aggregate.getConsensusScore()
                    )
            );
        }

        builder.append(']');

        return builder.toString();
    }

    public synchronized void reset() {
        resultLogged = false;
    }

    public synchronized boolean hasLoggedResult() {
        return resultLogged;
    }

    public String getTag() {
        return tag;
    }
}
