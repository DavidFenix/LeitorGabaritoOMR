package com.example.leitorgabaritoomr.presentation.capture;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.example.leitorgabaritoomr.application.history.OmrGradingHistoryRecorder;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

/**
 * Aplica a decisao final da tela de correcao ao historico do aluno.
 *
 * Somente {@link Activity#RESULT_OK} confirma a nota. Ler novamente,
 * cancelar, voltar ou qualquer outro resultado preservam o historico
 * sem alteracoes.
 */
public final class OmrCaptureHistoryCommitter {

    private OmrCaptureHistoryCommitter() {
    }

    /**
     * Registra a correcao apenas quando ela foi confirmada.
     *
     * @return o registro criado ou recuperado; {@code null} quando a
     * decisao nao confirma a nota ou quando o fluxo legado nao possui aluno.
     */
    @Nullable
    public static OmrGradingHistoryRecord recordIfConfirmed(
            int activityResultCode,
            @Nullable OmrStudentIdentity student,
            @Nullable OmrGradingResult gradingResult,
            @Nullable OmrGradingHistoryRecorder recorder
    ) {
        if (activityResultCode != Activity.RESULT_OK
                || student == null) {

            return null;
        }

        if (gradingResult == null) {
            throw new IllegalStateException(
                    "A correcao confirmada nao esta disponivel."
            );
        }

        if (recorder == null) {
            throw new IllegalStateException(
                    "O gravador do historico nao esta disponivel."
            );
        }

        return recorder.record(
                student,
                gradingResult
        );
    }
}
