package com.example.leitorgabaritoomr.presentation.capture;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.leitorgabaritoomr.R;

/**
 * Resolve os recursos textuais associados ao estado de captura.
 *
 * A classe nao conserva Context e, portanto, pode ser utilizada por
 * Activities, Fragments ou outros componentes sem criar referencia
 * persistente para a interface Android.
 */
public final class OmrCaptureTextResolver {

    private OmrCaptureTextResolver() {
        throw new AssertionError(
                "Esta classe nao deve ser instanciada."
        );
    }

    public static String resolveInstruction(
            Context context,
            OmrCaptureViewState viewState
    ) {
        requireContextAndState(context, viewState);

        return context.getString(
                getInstructionResource(
                        viewState.getInstruction()
                )
        );
    }

    /**
     * Retorna o texto de progresso ja formatado ou null quando o
     * estado atual nao deve exibir progresso.
     */
    @Nullable
    public static String resolveProgress(
            Context context,
            OmrCaptureViewState viewState
    ) {
        requireContextAndState(context, viewState);

        if (!viewState.isProgressVisible()) {
            return null;
        }

        switch (viewState.getProgressMode()) {
            case MARKER_STABILITY:
                return context.getString(
                        R.string
                                .omr_capture_progress_marker_stability,
                        viewState.getProgressPercent()
                );

            case EVIDENCE_ACCUMULATION:
                return context.getString(
                        R.string
                                .omr_capture_progress_evidence_accumulation,
                        viewState.getProgressPercent()
                );

            case COMPLETE:
                return context.getString(
                        R.string.omr_capture_progress_complete
                );

            case HIDDEN:
                return null;

            default:
                throw new IllegalStateException(
                        "Modo de progresso nao suportado: "
                                + viewState.getProgressMode()
                );
        }
    }

    /**
     * Retorna o diagnostico tecnico formatado quando a sessao
     * possuir uma mensagem de falha. A tela pode optar por nao
     * apresenta-lo ao usuario final e registra-lo apenas em log.
     */
    @Nullable
    public static String resolveFailureDetail(
            Context context,
            OmrCaptureViewState viewState
    ) {
        requireContextAndState(context, viewState);

        String failureMessage =
                viewState.getFailureMessage();

        if (failureMessage == null) {
            return null;
        }

        return context.getString(
                R.string.omr_capture_failure_detail,
                failureMessage
        );
    }

    @StringRes
    public static int getInstructionResource(
            OmrCaptureViewState.Instruction instruction
    ) {
        if (instruction == null) {
            throw new IllegalArgumentException(
                    "A instrucao de captura e obrigatoria."
            );
        }

        switch (instruction) {
            case POSITION_SHEET:
                return R.string
                        .omr_capture_instruction_position_sheet;

            case KEEP_SHEET_STEADY:
                return R.string
                        .omr_capture_instruction_keep_sheet_steady;

            case READING_SHEET:
                return R.string
                        .omr_capture_instruction_reading_sheet;

            case REPOSITION_SHEET:
                return R.string
                        .omr_capture_instruction_reposition_sheet;

            case READING_COMPLETED:
                return R.string
                        .omr_capture_instruction_reading_completed;

            case CAPTURE_FAILED:
                return R.string
                        .omr_capture_instruction_capture_failed;

            case SESSION_CLOSED:
                return R.string
                        .omr_capture_instruction_session_closed;

            default:
                throw new IllegalStateException(
                        "Instrucao de captura nao suportada: "
                                + instruction
                );
        }
    }

    @StringRes
    public static int getRetryActionResource() {
        return R.string.omr_capture_action_retry;
    }

    private static void requireContextAndState(
            Context context,
            OmrCaptureViewState viewState
    ) {
        if (context == null || viewState == null) {
            throw new IllegalArgumentException(
                    "Context e OmrCaptureViewState sao obrigatorios."
            );
        }
    }
}
