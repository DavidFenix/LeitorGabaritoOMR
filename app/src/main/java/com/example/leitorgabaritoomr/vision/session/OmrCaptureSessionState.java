package com.example.leitorgabaritoomr.vision.session;

/**
 * Estado de alto nivel de uma sessao de captura OMR.
 *
 * Este contrato nao depende de Android, OpenCV, camera, layout ou
 * Laboratorio OMR. Ele descreve apenas a evolucao observavel da
 * captura e pode ser compartilhado por Activities, testes, imagens
 * de arquivo e futuras fontes de video.
 */
public enum OmrCaptureSessionState {

    /**
     * Sessao criada ou reiniciada, ainda sem frame processado.
     */
    READY(false, true),

    /**
     * Ainda nao existe um conjunto confiavel de quatro marcadores.
     */
    SEARCHING_MARKERS(false, true),

    /**
     * Existem candidatos geometricos, mas a estabilidade temporal
     * ainda nao foi confirmada.
     */
    STABILIZING_MARKERS(false, true),

    /**
     * A geometria esta estavel e a folha esta sendo registrada,
     * medida ou acumulada para o consenso temporal.
     */
    READING_SHEET(false, true),

    /**
     * Uma referencia anteriormente valida foi interrompida e a sessao
     * esta aguardando a mesma folha voltar a ficar confiavel.
     */
    REACQUIRING_SHEET(false, true),

    /**
     * A interpretacao final foi concluida com sucesso.
     */
    COMPLETED(true, false),

    /**
     * A sessao encontrou uma falha que exige reinicio explicito.
     */
    FAILED(true, false),

    /**
     * A sessao foi encerrada e nao pode mais receber frames.
     */
    CLOSED(true, false);

    private final boolean terminal;
    private final boolean acceptsFrames;

    OmrCaptureSessionState(
            boolean terminal,
            boolean acceptsFrames
    ) {
        this.terminal = terminal;
        this.acceptsFrames = acceptsFrames;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canAcceptFrames() {
        return acceptsFrames;
    }

    public boolean isSuccessful() {
        return this == COMPLETED;
    }

    public boolean requiresResetBeforeProcessing() {
        return this == COMPLETED
                || this == FAILED;
    }
}
