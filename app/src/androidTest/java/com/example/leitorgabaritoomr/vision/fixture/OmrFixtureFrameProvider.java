package com.example.leitorgabaritoomr.vision.fixture;

import org.opencv.core.Mat;

/**
 * Fonte deterministica de frames RGBA para testes do pipeline OMR.
 *
 * Cada chamada deve devolver um Mat novo e independente. O chamador
 * passa a ser dono do frame retornado e deve libera-lo. Fechar o
 * provedor libera somente os recursos internos que ele conservar.
 *
 * O indice torna possivel reproduzir movimento, variacao de brilho,
 * desfoque ou qualquer outra mudanca temporal sem usar a camera.
 */
public interface OmrFixtureFrameProvider
        extends AutoCloseable {

    /**
     * Cria o frame correspondente ao indice informado.
     *
     * @param frameIndex indice iniciado em zero
     * @return novo Mat RGBA, pertencente ao chamador
     */
    Mat createRgbaFrame(int frameIndex);

    /**
     * Libera os recursos pertencentes ao provedor.
     * Nao deve liberar frames anteriormente entregues ao chamador.
     */
    @Override
    void close();
}
