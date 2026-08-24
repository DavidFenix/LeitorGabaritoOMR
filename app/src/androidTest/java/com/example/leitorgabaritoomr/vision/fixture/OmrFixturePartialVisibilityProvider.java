package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.function.IntToDoubleFunction;

/**
 * Decorador que desloca frames para simular uma folha parcialmente
 * fora do enquadramento da camera.
 *
 * O resultado conserva a resolucao e os quatro canais RGBA do frame
 * delegado. As regioes que saem da imagem sao substituidas por um
 * fundo uniforme. O decorador assume a propriedade do provedor
 * delegado e o fecha junto com seus proprios recursos.
 */
public final class OmrFixturePartialVisibilityProvider
        implements OmrFixtureFrameProvider {

    private static final Scalar DEFAULT_BACKGROUND_RGBA =
            new Scalar(174.0, 174.0, 174.0, 255.0);

    private static final double ZERO_TOLERANCE = 1.0e-12;

    private final OmrFixtureFrameProvider delegate;
    private final IntToDoubleFunction horizontalOffsetRatioFunction;
    private final IntToDoubleFunction verticalOffsetRatioFunction;
    private final Scalar backgroundRgba;

    private boolean closed;

    /**
     * Cria uma saida lateral temporaria com movimento suave de ida e
     * volta. Fora do intervalo afetado, o frame nao e deslocado.
     *
     * Um valor positivo de peakHorizontalOffsetRatio move a folha para
     * a direita; um valor negativo move para a esquerda.
     */
    public static OmrFixturePartialVisibilityProvider
    temporaryHorizontalExit(
            OmrFixtureFrameProvider delegate,
            int firstAffectedFrame,
            int affectedFrameCount,
            double peakHorizontalOffsetRatio
    ) {
        if (firstAffectedFrame < 0) {
            throw new IllegalArgumentException(
                    "firstAffectedFrame nao pode ser negativo."
            );
        }

        if (affectedFrameCount < 3) {
            throw new IllegalArgumentException(
                    "affectedFrameCount deve ser pelo menos 3."
            );
        }

        validateOffsetRatio(
                "peakHorizontalOffsetRatio",
                peakHorizontalOffsetRatio
        );

        if (Math.abs(peakHorizontalOffsetRatio)
                <= ZERO_TOLERANCE) {
            throw new IllegalArgumentException(
                    "O deslocamento maximo deve ser diferente de zero."
            );
        }

        return new OmrFixturePartialVisibilityProvider(
                delegate,
                frameIndex -> temporaryOffset(
                        frameIndex,
                        firstAffectedFrame,
                        affectedFrameCount,
                        peakHorizontalOffsetRatio
                ),
                frameIndex -> 0.0,
                DEFAULT_BACKGROUND_RGBA
        );
    }

    /**
     * Mantem todos os frames deslocados pela mesma proporcao. E util
     * para provar que uma folha permanentemente incompleta nao gera
     * interpretacao.
     */
    public static OmrFixturePartialVisibilityProvider
    persistentOffset(
            OmrFixtureFrameProvider delegate,
            double horizontalOffsetRatio,
            double verticalOffsetRatio
    ) {
        validateOffsetRatio(
                "horizontalOffsetRatio",
                horizontalOffsetRatio
        );

        validateOffsetRatio(
                "verticalOffsetRatio",
                verticalOffsetRatio
        );

        if (Math.abs(horizontalOffsetRatio)
                <= ZERO_TOLERANCE
                && Math.abs(verticalOffsetRatio)
                <= ZERO_TOLERANCE) {

            throw new IllegalArgumentException(
                    "Pelo menos um deslocamento deve ser diferente de zero."
            );
        }

        return new OmrFixturePartialVisibilityProvider(
                delegate,
                frameIndex -> horizontalOffsetRatio,
                frameIndex -> verticalOffsetRatio,
                DEFAULT_BACKGROUND_RGBA
        );
    }

    public OmrFixturePartialVisibilityProvider(
            OmrFixtureFrameProvider delegate,
            IntToDoubleFunction horizontalOffsetRatioFunction,
            IntToDoubleFunction verticalOffsetRatioFunction,
            Scalar backgroundRgba
    ) {
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "O provedor delegado e obrigatorio."
            );
        }

        if (horizontalOffsetRatioFunction == null) {
            throw new IllegalArgumentException(
                    "A funcao de deslocamento horizontal e obrigatoria."
            );
        }

        if (verticalOffsetRatioFunction == null) {
            throw new IllegalArgumentException(
                    "A funcao de deslocamento vertical e obrigatoria."
            );
        }

        validateBackground(backgroundRgba);

        this.delegate = delegate;
        this.horizontalOffsetRatioFunction =
                horizontalOffsetRatioFunction;
        this.verticalOffsetRatioFunction =
                verticalOffsetRatioFunction;

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

        Mat sourceFrame =
                delegate.createRgbaFrame(frameIndex);

        try {
            validateFrame(sourceFrame);

            double horizontalOffsetRatio =
                    horizontalOffsetRatioFunction
                            .applyAsDouble(frameIndex);

            double verticalOffsetRatio =
                    verticalOffsetRatioFunction
                            .applyAsDouble(frameIndex);

            validateOffsetRatio(
                    "horizontalOffsetRatio calculado",
                    horizontalOffsetRatio
            );

            validateOffsetRatio(
                    "verticalOffsetRatio calculado",
                    verticalOffsetRatio
            );

            if (Math.abs(horizontalOffsetRatio)
                    <= ZERO_TOLERANCE
                    && Math.abs(verticalOffsetRatio)
                    <= ZERO_TOLERANCE) {

                return sourceFrame;
            }

            return translate(
                    sourceFrame,
                    horizontalOffsetRatio,
                    verticalOffsetRatio
            );
        } catch (RuntimeException | Error exception) {
            if (sourceFrame != null) {
                sourceFrame.release();
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

    private Mat translate(
            Mat sourceFrame,
            double horizontalOffsetRatio,
            double verticalOffsetRatio
    ) {
        Mat transform = Mat.zeros(2, 3, CvType.CV_64F);
        Mat result = new Mat();

        try {
            transform.put(
                    0,
                    0,
                    1.0,
                    0.0,
                    sourceFrame.cols()
                            * horizontalOffsetRatio,
                    0.0,
                    1.0,
                    sourceFrame.rows()
                            * verticalOffsetRatio
            );

            Imgproc.warpAffine(
                    sourceFrame,
                    result,
                    transform,
                    new Size(
                            sourceFrame.cols(),
                            sourceFrame.rows()
                    ),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    backgroundRgba
            );

            sourceFrame.release();
            return result;
        } catch (RuntimeException | Error exception) {
            result.release();
            throw exception;
        } finally {
            transform.release();
        }
    }

    private void ensureAvailable() {
        if (closed) {
            throw new IllegalStateException(
                    "O provedor de visibilidade parcial ja foi fechado."
            );
        }
    }

    private static double temporaryOffset(
            int frameIndex,
            int firstAffectedFrame,
            int affectedFrameCount,
            double peakOffsetRatio
    ) {
        int localFrame = frameIndex - firstAffectedFrame;

        if (localFrame < 0
                || localFrame >= affectedFrameCount) {
            return 0.0;
        }

        double progress =
                localFrame
                        / (double) (affectedFrameCount - 1);

        return peakOffsetRatio
                * Math.sin(Math.PI * progress);
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

    private static void validateOffsetRatio(
            String name,
            double value
    ) {
        if (!Double.isFinite(value)
                || Math.abs(value) > 1.0) {

            throw new IllegalArgumentException(
                    name
                            + " deve estar entre -1.0 e 1.0."
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
