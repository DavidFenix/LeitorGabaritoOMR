package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.function.IntPredicate;

/**
 * Decorador que substitui frames escolhidos por um fundo uniforme.
 *
 * Permite reproduzir perda temporaria da folha ou dos marcadores sem
 * alterar o provedor original. O decorador assume a propriedade do
 * provedor delegado e o fecha junto com seus proprios recursos.
 */
public final class OmrFixtureFrameInterruptionProvider
        implements OmrFixtureFrameProvider {

    private static final Scalar DEFAULT_BACKGROUND_RGBA =
            new Scalar(174.0, 174.0, 174.0, 255.0);

    private final OmrFixtureFrameProvider delegate;
    private final IntPredicate interruptedFramePredicate;
    private final Scalar backgroundRgba;

    private boolean closed;

    /**
     * Interrompe apenas os primeiros frames e depois entrega a
     * sequencia original sem novas interferencias.
     */
    public static OmrFixtureFrameInterruptionProvider
    initiallyUnavailable(
            OmrFixtureFrameProvider delegate,
            int interruptedFrameCount
    ) {
        if (interruptedFrameCount < 0) {
            throw new IllegalArgumentException(
                    "interruptedFrameCount nao pode ser negativo."
            );
        }

        return new OmrFixtureFrameInterruptionProvider(
                delegate,
                frameIndex ->
                        frameIndex < interruptedFrameCount,
                DEFAULT_BACKGROUND_RGBA
        );
    }

    /**
     * Repete um ciclo com frames disponiveis seguidos de frames
     * interrompidos. Exemplo: 4, 1 representa quatro frames visiveis
     * e um frame sem folha.
     */
    public static OmrFixtureFrameInterruptionProvider
    repeatingPattern(
            OmrFixtureFrameProvider delegate,
            int availableFrameCount,
            int interruptedFrameCount
    ) {
        if (availableFrameCount <= 0) {
            throw new IllegalArgumentException(
                    "availableFrameCount deve ser positivo."
            );
        }

        if (interruptedFrameCount <= 0) {
            throw new IllegalArgumentException(
                    "interruptedFrameCount deve ser positivo."
            );
        }

        int cycleLength =
                availableFrameCount
                        + interruptedFrameCount;

        return new OmrFixtureFrameInterruptionProvider(
                delegate,
                frameIndex ->
                        Math.floorMod(
                                frameIndex,
                                cycleLength
                        ) >= availableFrameCount,
                DEFAULT_BACKGROUND_RGBA
        );
    }

    public OmrFixtureFrameInterruptionProvider(
            OmrFixtureFrameProvider delegate,
            IntPredicate interruptedFramePredicate,
            Scalar backgroundRgba
    ) {
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "O provedor delegado e obrigatorio."
            );
        }

        if (interruptedFramePredicate == null) {
            throw new IllegalArgumentException(
                    "O criterio de interrupcao e obrigatorio."
            );
        }

        validateBackground(backgroundRgba);

        this.delegate = delegate;
        this.interruptedFramePredicate =
                interruptedFramePredicate;

        this.backgroundRgba =
                new Scalar(
                        backgroundRgba.val[0],
                        backgroundRgba.val[1],
                        backgroundRgba.val[2],
                        backgroundRgba.val[3]
                );
    }

    @Override
    public Mat createRgbaFrame(int frameIndex) {
        ensureAvailable();

        if (frameIndex < 0) {
            throw new IllegalArgumentException(
                    "frameIndex nao pode ser negativo."
            );
        }

        Mat frame =
                delegate.createRgbaFrame(frameIndex);

        try {
            validateFrame(frame);

            if (interruptedFramePredicate.test(frameIndex)) {
                frame.setTo(backgroundRgba);
            }

            return frame;
        } catch (RuntimeException | Error exception) {
            if (frame != null) {
                frame.release();
            }

            throw exception;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        delegate.close();
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    private void ensureAvailable() {
        if (closed) {
            throw new IllegalStateException(
                    "O provedor de interrupcao ja foi fechado."
            );
        }
    }

    private static void validateFrame(Mat frame) {
        if (frame == null || frame.empty()) {
            throw new IllegalArgumentException(
                    "O provedor delegado retornou frame vazio."
            );
        }

        if (frame.channels() != 4) {
            throw new IllegalArgumentException(
                    "O frame delegado deve possuir quatro canais."
            );
        }
    }

    private static void validateBackground(
            Scalar backgroundRgba
    ) {
        if (backgroundRgba == null
                || backgroundRgba.val == null
                || backgroundRgba.val.length < 4) {

            throw new IllegalArgumentException(
                    "A cor RGBA de fundo e obrigatoria."
            );
        }
    }
}
