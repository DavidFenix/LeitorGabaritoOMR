package com.example.leitorgabaritoomr.vision.debug;

import android.util.Log;

import com.example.leitorgabaritoomr.vision.measurement.BubbleMeasurement;
import com.example.leitorgabaritoomr.vision.measurement.QuestionMeasurement;

import java.util.List;
import java.util.Locale;

/**
 * Registra um diagnóstico numérico completo das bolhas.
 *
 * Cada lote é registrado apenas uma vez, evitando milhares
 * de linhas repetidas depois da pausa automática.
 */
public final class BubbleMeasurementDiagnosticLogger {

    private static final String TAG =
            "OMR_BubbleDiagnostic";

    private boolean logged = false;

    /**
     * Registra uma única vez o conjunto de medições recebido.
     *
     * Retorna true quando o diagnóstico foi escrito no Logcat.
     */
    public synchronized boolean logOnce(
            List<QuestionMeasurement> questions
    ) {
        if (logged) {
            return false;
        }

        if (questions == null
                || questions.isEmpty()) {

            return false;
        }

        Log.d(
                TAG,
                "========== INÍCIO DO DIAGNÓSTICO =========="
        );

        Log.d(
                TAG,
                "Questões recebidas: "
                        + questions.size()
        );

        for (QuestionMeasurement question
                : questions) {

            if (question == null) {
                continue;
            }

            logQuestion(question);
        }

        Log.d(
                TAG,
                "=========== FIM DO DIAGNÓSTICO ==========="
        );

        logged = true;

        return true;
    }

    private void logQuestion(
            QuestionMeasurement question
    ) {
        String questionId =
                question
                        .getQuestion()
                        .getId();

        String bestOptionId =
                question
                        .getBestOption()
                        .getId();

        String secondOptionId =
                question
                        .getSecondBestOption()
                        .getId();

        StringBuilder line =
                new StringBuilder();

        line.append(
                String.format(
                        Locale.US,
                        "%s | vencedora=%s"
                                + " evidencia=%.3f"
                                + " | segunda=%s"
                                + " evidencia=%.3f"
                                + " | gap=%.3f",
                        questionId,
                        bestOptionId,
                        question.getBestEvidence(),
                        secondOptionId,
                        question.getSecondBestEvidence(),
                        question.getEvidenceGap()
                )
        );

        for (BubbleMeasurement measurement
                : question.getMeasurements()) {

            String optionId =
                    measurement
                            .getOption()
                            .getId();

            double evidence =
                    question.getEvidence(optionId);

            line.append(
                    String.format(
                            Locale.US,
                            " || %s:"
                                    + " score=%.3f"
                                    + ",core=%.1f"
                                    + ",fundo=%.1f"
                                    + ",contraste=%.3f"
                                    + ",escuroLocal=%.3f"
                                    + ",escuroCore=%.3f"
                                    + ",escuroBorda=%.3f"
                                    + ",regiao=%dx%d@%d,%d",
                            optionId,
                            evidence,
                            measurement
                                    .getCoreMeanIntensity(),
                            measurement
                                    .getLocalBackgroundMeanIntensity(),
                            measurement
                                    .getLocalContrastScore(),
                            measurement
                                    .getCoreLocallyDarkPixelRatio(),
                            measurement
                                    .getCoreDarkPixelRatio(),
                            measurement
                                    .getBorderDarkPixelRatio(),
                            measurement.getRegionWidth(),
                            measurement.getRegionHeight(),
                            measurement.getRegionLeft(),
                            measurement.getRegionTop()
                    )
            );
        }

        Log.d(
                TAG,
                line.toString()
        );
    }

    /**
     * Libera o logger para registrar uma nova aquisição.
     */
    public synchronized void reset() {
        logged = false;
    }

    public synchronized boolean hasLogged() {
        return logged;
    }
}