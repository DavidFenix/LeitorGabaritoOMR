package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Mat;

/**
 * Uma variacao reproduzivel de uma fixture OMR.
 *
 * A instancia e dona do Mat armazenado. O consumidor recebe clones
 * limpos por createRgbaFrame(), exatamente como receberia frames
 * independentes da camera.
 */
public final class OmrFixtureVariant
        implements AutoCloseable {

    private final String id;
    private final String description;
    private final Mat rgbaSource;

    private boolean released;

    OmrFixtureVariant(
            String id,
            String description,
            Mat rgbaSource
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O id da variante e obrigatorio."
            );
        }

        if (description == null
                || description.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "A descricao da variante e obrigatoria."
            );
        }

        if (rgbaSource == null || rgbaSource.empty()) {
            throw new IllegalArgumentException(
                    "A imagem RGBA da variante e obrigatoria."
            );
        }

        this.id = id;
        this.description = description;
        this.rgbaSource = rgbaSource;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public int getWidth() {
        ensureAvailable();
        return rgbaSource.cols();
    }

    public int getHeight() {
        ensureAvailable();
        return rgbaSource.rows();
    }

    /**
     * Cria um frame independente que pode ser desenhado ou alterado
     * pelo pipeline sem contaminar a fixture nem o proximo frame.
     */
    public Mat createRgbaFrame() {
        ensureAvailable();
        return rgbaSource.clone();
    }

    public boolean isReleased() {
        return released;
    }

    public void release() {
        if (released) {
            return;
        }

        rgbaSource.release();
        released = true;
    }

    @Override
    public void close() {
        release();
    }

    private void ensureAvailable() {
        if (released) {
            throw new IllegalStateException(
                    "A variante "
                            + id
                            + " ja foi liberada."
            );
        }
    }

    @Override
    public String toString() {
        return id + " - " + description;
    }
}
