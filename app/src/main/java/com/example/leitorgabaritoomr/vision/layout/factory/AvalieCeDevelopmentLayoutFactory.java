package com.example.leitorgabaritoomr.vision.layout.factory;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;

/**
 * Layout provisório baseado na imagem Avalie-CE usada
 * durante o desenvolvimento.
 *
 * Estrutura observada:
 *
 * - quatro blocos;
 * - treze questões por bloco;
 * - quatro alternativas por questão;
 * - numeração contínua de 1 a 52.
 *
 * As coordenadas iniciais serão calibradas visualmente
 * usando a etapa 9 do Laboratório OMR.
 */
public final class AvalieCeDevelopmentLayoutFactory {

    private AvalieCeDevelopmentLayoutFactory() {
    }

    public static OmrLayoutDefinition create() {
        RegularGridLayoutConfig config =
                new RegularGridLayoutConfig(
                        "avalie-ce-development",
                        1,
                        "Avalie CE - perfil de desenvolvimento",

                        /*
                         * Canvas digital de referência.
                         * Proporção aproximada de 2,28:1.
                         */
                        1600,
                        700,

                        /*
                         * Quatro blocos com treze questões.
                         */
                        4,
                        13,

                        /*
                         * Quatro alternativas.
                         */
                        new String[]{
                                "A",
                                "B",
                                "C",
                                "D"
                        },

                        /*
                         * Posições horizontais das alternativas
                         * dentro de cada bloco.
                         */
                        new double[]{
                                0.28,
                                0.42,
                                0.56,
                                0.70
                        },

                        /*
                         * Primeira linha e distância entre linhas.
                         *
                         * Linha 1:  0.08
                         * Linha 13: 0.92
                         */
                        0.08,
                        0.07,

                        /*
                         * Limite externo da bolha.
                         *
                         * No canvas canônico de 1600 x 700:
                         *
                         * largura = 2 x 0.010 x 1600 = 32 pixels
                         * altura  = 2 x 0.023 x 700  = 32,2 pixels
                         *
                         * Portanto, a região calculada e desenhada
                         * fica praticamente quadrada.
                         */
                        0.010,
                        0.023,

                        /*
                         * Numeração começa em 1.
                         */
                        1,

                        /*
                         * false: numeração contínua:
                         *
                         * bloco 1: 1–13
                         * bloco 2: 14–26
                         * bloco 3: 27–39
                         * bloco 4: 40–52
                         */
                        false
                );

        OmrLayoutDefinition layout =
                RegularGridLayoutFactory.create(
                        config
                );

        validateGeneratedLayout(layout);

        return layout;
    }

    private static void validateGeneratedLayout(
            OmrLayoutDefinition layout
    ) {
        int expectedBlocks = 4;
        int expectedQuestions = 52;
        int expectedOptions = 208;

        if (layout.getBlockCount()
                != expectedBlocks) {

            throw new IllegalStateException(
                    "Esperados 4 blocos, mas foram gerados "
                            + layout.getBlockCount()
                            + "."
            );
        }

        if (layout.getQuestionCount()
                != expectedQuestions) {

            throw new IllegalStateException(
                    "Esperadas 52 questões, mas foram geradas "
                            + layout.getQuestionCount()
                            + "."
            );
        }

        if (layout.getOptionCount()
                != expectedOptions) {

            throw new IllegalStateException(
                    "Esperadas 208 alternativas, mas foram geradas "
                            + layout.getOptionCount()
                            + "."
            );
        }
    }
}