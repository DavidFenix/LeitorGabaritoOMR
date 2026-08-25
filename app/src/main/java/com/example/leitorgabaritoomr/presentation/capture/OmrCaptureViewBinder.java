package com.example.leitorgabaritoomr.presentation.capture;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.vision.session.OmrCaptureSessionState;

/**
 * Aplica OmrCaptureViewState aos componentes da tela de captura.
 *
 * render() pode ser chamado pela thread da camera. As atualizacoes
 * sao consolidadas e executadas na thread principal, mantendo apenas
 * o estado mais recente quando varios frames chegam rapidamente.
 */
public final class OmrCaptureViewBinder {

    private final TextView instructionTextView;
    private final ProgressBar progressBar;
    private final TextView progressTextView;
    private final TextView diagnosticTextView;
    private final Button retryButton;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final Object pendingLock = new Object();

    private OmrCaptureViewState pendingViewState;
    private boolean pendingShowTechnicalDiagnostic;
    private boolean updateScheduled;
    private boolean released;

    private final Runnable applyPendingRunnable =
            new Runnable() {
                @Override
                public void run() {
                    applyPendingState();
                }
            };

    public OmrCaptureViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da captura e obrigatoria."
            );
        }

        instructionTextView =
                requireView(
                        rootView,
                        R.id.textOmrCaptureInstruction,
                        TextView.class
                );

        progressBar =
                requireView(
                        rootView,
                        R.id.progressOmrCapture,
                        ProgressBar.class
                );

        progressTextView =
                requireView(
                        rootView,
                        R.id.textOmrCaptureProgress,
                        TextView.class
                );

        diagnosticTextView =
                requireView(
                        rootView,
                        R.id.textOmrCaptureDiagnostic,
                        TextView.class
                );

        retryButton =
                requireView(
                        rootView,
                        R.id.buttonOmrCaptureRetry,
                        Button.class
                );
    }

    public void render(
            OmrCaptureViewState viewState
    ) {
        render(viewState, false);
    }

    /**
     * Agenda a apresentacao do estado mais recente.
     *
     * @param showTechnicalDiagnostic true apenas para laboratorio,
     *                                desenvolvimento ou suporte
     */
    public void render(
            OmrCaptureViewState viewState,
            boolean showTechnicalDiagnostic
    ) {
        if (viewState == null) {
            throw new IllegalArgumentException(
                    "OmrCaptureViewState e obrigatorio."
            );
        }

        synchronized (pendingLock) {
            if (released) {
                return;
            }

            pendingViewState = viewState;
            pendingShowTechnicalDiagnostic =
                    showTechnicalDiagnostic;

            if (updateScheduled) {
                return;
            }

            updateScheduled = true;
        }

        mainHandler.post(applyPendingRunnable);
    }

    public void setOnRetryClickListener(
            View.OnClickListener listener
    ) {
        retryButton.setOnClickListener(listener);
    }

    /**
     * Cancela atualizacoes pendentes e remove listeners da tela.
     */
    public void release() {
        synchronized (pendingLock) {
            if (released) {
                return;
            }

            released = true;
            pendingViewState = null;
            updateScheduled = false;
        }

        mainHandler.removeCallbacks(
                applyPendingRunnable
        );

        retryButton.setOnClickListener(null);
    }

    private void applyPendingState() {
        OmrCaptureViewState viewState;
        boolean showTechnicalDiagnostic;

        synchronized (pendingLock) {
            if (released) {
                updateScheduled = false;
                return;
            }

            viewState = pendingViewState;
            showTechnicalDiagnostic =
                    pendingShowTechnicalDiagnostic;

            pendingViewState = null;
            updateScheduled = false;
        }

        if (viewState == null) {
            return;
        }

        String instructionText =
                OmrCaptureTextResolver
                        .resolveInstruction(
                                instructionTextView
                                        .getContext(),
                                viewState
                        );

        setTextIfChanged(
                instructionTextView,
                instructionText
        );

        applyProgress(viewState);

        applyDiagnostic(
                viewState,
                showTechnicalDiagnostic
        );

        boolean retryVisible =
                viewState.getSessionState()
                        == OmrCaptureSessionState.FAILED;

        setVisible(
                retryButton,
                retryVisible
        );

        retryButton.setEnabled(retryVisible);
    }

    private void applyProgress(
            OmrCaptureViewState viewState
    ) {
        String progressText =
                OmrCaptureTextResolver
                        .resolveProgress(
                                progressTextView.getContext(),
                                viewState
                        );

        boolean progressVisible =
                progressText != null;

        if (progressVisible) {
            int progressPercent =
                    viewState.getProgressPercent();

            if (progressBar.getProgress()
                    != progressPercent) {

                progressBar.setProgress(
                        progressPercent
                );
            }

            setTextIfChanged(
                    progressTextView,
                    progressText
            );
        }

        setVisible(
                progressBar,
                progressVisible
        );

        setVisible(
                progressTextView,
                progressVisible
        );
    }

    private void applyDiagnostic(
            OmrCaptureViewState viewState,
            boolean showTechnicalDiagnostic
    ) {
        String diagnosticText =
                showTechnicalDiagnostic
                        ? OmrCaptureTextResolver
                        .resolveFailureDetail(
                                diagnosticTextView
                                        .getContext(),
                                viewState
                        )
                        : null;

        boolean diagnosticVisible =
                diagnosticText != null;

        if (diagnosticVisible) {
            setTextIfChanged(
                    diagnosticTextView,
                    diagnosticText
            );
        }

        setVisible(
                diagnosticTextView,
                diagnosticVisible
        );
    }

    private static void setTextIfChanged(
            TextView textView,
            String newText
    ) {
        if (TextUtils.equals(
                textView.getText(),
                newText
        )) {
            return;
        }

        textView.setText(newText);
    }

    private static void setVisible(
            View view,
            boolean visible
    ) {
        int expectedVisibility =
                visible
                        ? View.VISIBLE
                        : View.GONE;

        if (view.getVisibility()
                != expectedVisibility) {

            view.setVisibility(
                    expectedVisibility
            );
        }
    }

    private static <T extends View> T requireView(
            View rootView,
            @IdRes int viewId,
            Class<T> expectedType
    ) {
        View foundView =
                rootView.findViewById(viewId);

        if (foundView == null) {
            throw new IllegalStateException(
                    "View obrigatoria nao encontrada: "
                            + viewId
            );
        }

        if (!expectedType.isInstance(foundView)) {
            throw new IllegalStateException(
                    "Tipo inesperado para a View "
                            + viewId
                            + ": "
                            + foundView
                            .getClass()
                            .getName()
            );
        }

        return expectedType.cast(foundView);
    }
}
