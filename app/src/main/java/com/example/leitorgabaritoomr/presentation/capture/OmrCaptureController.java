package com.example.leitorgabaritoomr.presentation.capture;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.example.leitorgabaritoomr.application.reading.OmrReadingResultMapper;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.session.OmrCaptureFrameResult;
import com.example.leitorgabaritoomr.vision.session.OmrCaptureSession;
import com.example.leitorgabaritoomr.vision.session.OmrCaptureSessionState;

import org.opencv.core.Mat;

/**
 * Liga a sessao OMR ao estado visual da tela de captura.
 *
 * A Activity continua responsavel apenas pelo ciclo de vida da camera:
 * entrega os frames a processFrame(), recebe os eventos terminais e
 * chama retry() ou close() quando necessario.
 *
 * Os frames permanecem pertencendo a camera. Este controlador nunca
 * os armazena, substitui ou libera.
 */
public final class OmrCaptureController
        implements AutoCloseable {

    /**
     * Eventos terminais entregues na thread principal do Android.
     */
    public interface Listener {

        void onCaptureCompleted(
                OmrReadingResult readingResult
        );

        void onCaptureFailed(
                String failureMessage
        );
    }

    private final OmrCaptureSession session;
    private final OmrCaptureViewBinder viewBinder;
    private final Listener listener;

    private final OmrReadingResultMapper
            readingResultMapper =
            new OmrReadingResultMapper();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private boolean showTechnicalDiagnostic;
    private boolean terminalEventScheduled;
    private boolean terminalEventDelivered;
    private boolean closed;

    /**
     * Identifica a tentativa atual. Um retry() invalida qualquer
     * callback terminal que ainda esteja aguardando a thread principal.
     */
    private long captureGeneration;

    public OmrCaptureController(
            OmrCaptureSession session,
            OmrCaptureViewBinder viewBinder,
            Listener listener
    ) {
        if (session == null
                || viewBinder == null
                || listener == null) {

            throw new IllegalArgumentException(
                    "Sessao, Binder e Listener sao obrigatorios."
            );
        }

        if (session.isClosed()) {
            throw new IllegalArgumentException(
                    "A sessao informada ja esta encerrada."
            );
        }

        this.session = session;
        this.viewBinder = viewBinder;
        this.listener = listener;

        this.viewBinder.setOnRetryClickListener(
                ignoredView -> retry()
        );

        renderCurrentState();
    }

    /**
     * Cria a captura de producao usando quadrados solidos e o layout
     * de desenvolvimento atualmente adotado como padrao.
     */
    public static OmrCaptureController createDefault(
            View rootView,
            Listener listener
    ) {
        return new OmrCaptureController(
                OmrCaptureSession.createDefault(),
                new OmrCaptureViewBinder(rootView),
                listener
        );
    }

    /**
     * Cria a captura com quadrados solidos e um layout explicito.
     */
    public static OmrCaptureController create(
            View rootView,
            OmrLayoutDefinition layoutDefinition,
            Listener listener
    ) {
        return create(
                rootView,
                MarkerDetectorMode.SOLID_SQUARE,
                layoutDefinition,
                listener
        );
    }

    /**
     * Ponto de composicao completo para novos tipos de marcador e
     * novos modelos de folha, sem alterar a Activity.
     */
    public static OmrCaptureController create(
            View rootView,
            MarkerDetectorMode detectorMode,
            OmrLayoutDefinition layoutDefinition,
            Listener listener
    ) {
        return new OmrCaptureController(
                OmrCaptureSession.create(
                        detectorMode,
                        layoutDefinition
                ),
                new OmrCaptureViewBinder(rootView),
                listener
        );
    }

    /**
     * Processa um frame da camera.
     *
     * Depois de COMPLETED ou FAILED, devolve o ultimo resultado sem
     * executar novamente o pipeline. Isso protege a tela enquanto a
     * camera ainda entrega frames antes da navegacao ou do retry().
     */
    public synchronized OmrCaptureFrameResult processFrame(
            Mat grayFrame,
            Mat rgbaFrame
    ) {
        ensureNotClosed();

        if (session.getState().isTerminal()) {
            OmrCaptureFrameResult terminalResult =
                    session.getLastFrameResult();

            scheduleTerminalEventIfNecessary(
                    terminalResult
            );

            return terminalResult;
        }

        OmrCaptureFrameResult frameResult =
                session.processFrame(
                        grayFrame,
                        rgbaFrame
                );

        viewBinder.render(
                OmrCaptureViewState.from(frameResult),
                showTechnicalDiagnostic
        );

        scheduleTerminalEventIfNecessary(
                frameResult
        );

        return frameResult;
    }

    /**
     * Reinicia somente o consenso e os contadores da tentativa.
     * Os componentes pesados do pipeline continuam reutilizados.
     */
    public synchronized void retry() {
        ensureNotClosed();

        captureGeneration++;
        terminalEventScheduled = false;
        terminalEventDelivered = false;

        session.reset();

        renderCurrentState();
    }

    /**
     * Habilita detalhes tecnicos apenas para desenvolvimento, suporte
     * ou para uma futura versao do Laboratorio OMR.
     */
    public synchronized void setShowTechnicalDiagnostic(
            boolean showTechnicalDiagnostic
    ) {
        ensureNotClosed();

        if (this.showTechnicalDiagnostic
                == showTechnicalDiagnostic) {
            return;
        }

        this.showTechnicalDiagnostic =
                showTechnicalDiagnostic;

        renderCurrentState();
    }

    public synchronized boolean
    isShowingTechnicalDiagnostic() {
        return showTechnicalDiagnostic;
    }

    public synchronized OmrCaptureSessionState getState() {
        return session.getState();
    }

    public synchronized OmrCaptureFrameResult
    getLastFrameResult() {
        return session.getLastFrameResult();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Encerra sessao, callbacks e referencias da interface.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        captureGeneration++;
        terminalEventScheduled = false;

        mainHandler.removeCallbacksAndMessages(null);

        try {
            session.close();
        } finally {
            viewBinder.release();
        }
    }

    private void renderCurrentState() {
        OmrCaptureViewState viewState =
                OmrCaptureViewState.from(
                        session.getState(),
                        session.getLastFrameResult()
                );

        viewBinder.render(
                viewState,
                showTechnicalDiagnostic
        );
    }

    private void scheduleTerminalEventIfNecessary(
            OmrCaptureFrameResult frameResult
    ) {
        if (frameResult == null
                || !frameResult
                .getSessionState()
                .isTerminal()
                || terminalEventScheduled
                || terminalEventDelivered) {

            return;
        }

        terminalEventScheduled = true;

        long scheduledGeneration =
                captureGeneration;

        mainHandler.post(
                () -> deliverTerminalEvent(
                        scheduledGeneration,
                        frameResult
                )
        );
    }

    private void deliverTerminalEvent(
            long scheduledGeneration,
            OmrCaptureFrameResult frameResult
    ) {
        Listener eventListener;
        OmrCaptureSessionState terminalState;
        SheetInterpretationResult interpretationResult;
        String failureMessage;

        synchronized (this) {
            if (closed
                    || scheduledGeneration
                    != captureGeneration
                    || terminalEventDelivered) {

                return;
            }

            terminalState =
                    frameResult.getSessionState();

            if (!terminalState.isTerminal()) {
                terminalEventScheduled = false;
                return;
            }

            terminalEventScheduled = false;
            terminalEventDelivered = true;

            eventListener = listener;
            interpretationResult =
                    frameResult
                            .getInterpretationResult();
            failureMessage =
                    frameResult.getFailureMessage();
        }

        if (terminalState
                == OmrCaptureSessionState.COMPLETED) {

            if (interpretationResult == null
                    || !interpretationResult.isComplete()) {

                eventListener.onCaptureFailed(
                        "A sessao foi concluida sem "
                                + "interpretacao completa."
                );

                return;
            }

            OmrReadingResult readingResult;

            try {
                readingResult =
                        readingResultMapper.map(
                                interpretationResult
                        );

            } catch (RuntimeException exception) {
                eventListener.onCaptureFailed(
                        "Nao foi possivel preparar o resultado"
                                + " final da leitura: "
                                + exception
                                .getClass()
                                .getSimpleName()
                );

                return;
            }

            eventListener.onCaptureCompleted(
                    readingResult
            );

            return;
        }

        if (terminalState
                == OmrCaptureSessionState.FAILED) {

            eventListener.onCaptureFailed(
                    normalizeFailureMessage(
                            failureMessage
                    )
            );
        }
    }

    private static String normalizeFailureMessage(
            String failureMessage
    ) {
        if (failureMessage == null
                || failureMessage.trim().isEmpty()) {

            return "Falha nao especificada na captura OMR.";
        }

        return failureMessage.trim();
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException(
                    "O controlador de captura ja foi encerrado."
            );
        }
    }
}
