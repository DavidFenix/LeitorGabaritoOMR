package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte todos os alvos esperados do layout em regioes finais
 * registradas na imagem normalizada.
 *
 * A fabrica apenas aplica as transformacoes que ja foram aceitas
 * pelo BubbleGridRegistrar. Ela nao procura contornos, nao corrige
 * centros e nao estima novos parametros.
 *
 * Uma regiao e criada para cada alvo do layout, mesmo quando esse
 * alvo nao permaneceu como apoio do refinamento geometrico. Os
 * apoios servem para estimar e validar o modelo; eles nao definem
 * quais alternativas existem ou serao medidas.
 */
public final class RegisteredBubbleRegionFactory {

    /**
     * Cria o conjunto completo de regioes finais.
     *
     * O metodo e intencionalmente estrito. Uma inconsistencia entre
     * layout, registro e imagem interrompe a criacao, em vez de
     * produzir silenciosamente uma leitura parcial.
     */
    public RegisteredBubbleRegionSet create(
            List<ExpectedBubbleTarget> targets,
            BubbleGridRegistrationResult registrationResult,
            int imageWidth,
            int imageHeight
    ) {
        validateInput(
                targets,
                registrationResult,
                imageWidth,
                imageHeight
        );

        List<RegisteredBubbleRegion> regions =
                new ArrayList<>(targets.size());

        for (ExpectedBubbleTarget target : targets) {
            if (target == null) {
                throw new IllegalArgumentException(
                        "A lista de alvos possui elemento nulo."
                );
            }

            BubbleBlockRegistration registration =
                    registrationResult.findByBlockIndex(
                            target.getBlockIndex()
                    );

            validateRegistrationForTarget(
                    target,
                    registration
            );

            RegisteredBubbleRegion region =
                    new RegisteredBubbleRegion(
                            target,
                            registration,
                            imageWidth,
                            imageHeight
                    );

            if (region.isClippedByImage()) {
                throw new IllegalArgumentException(
                        "A regiao final de "
                                + target.getOptionId()
                                + " foi cortada pelo limite"
                                + " da imagem normalizada."
                );
            }

            regions.add(region);
        }

        RegisteredBubbleRegionSet result =
                new RegisteredBubbleRegionSet(
                        imageWidth,
                        imageHeight,
                        registrationResult,
                        regions
                );

        if (!result.isComplete()) {
            throw new IllegalStateException(
                    "O conjunto de regioes foi criado"
                            + " incompleto."
            );
        }

        return result;
    }

    private void validateInput(
            List<ExpectedBubbleTarget> targets,
            BubbleGridRegistrationResult registrationResult,
            int imageWidth,
            int imageHeight
    ) {
        if (targets == null) {
            throw new IllegalArgumentException(
                    "A lista de alvos e obrigatoria."
            );
        }

        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "A lista de alvos nao pode ser vazia."
            );
        }

        if (registrationResult == null) {
            throw new IllegalArgumentException(
                    "O resultado do registro e obrigatorio."
            );
        }

        if (!registrationResult.isSuccess()) {
            throw new IllegalArgumentException(
                    "O registro geometrico nao terminou"
                            + " com sucesso: "
                            + registrationResult.getMessage()
            );
        }

        if (!registrationResult.areAllBlocksAccepted()) {
            throw new IllegalArgumentException(
                    "Nem todos os blocos foram aceitos"
                            + " pelo registro geometrico."
            );
        }

        if (registrationResult.getTargetCount()
                != targets.size()) {

            throw new IllegalArgumentException(
                    "A quantidade de alvos recebida ("
                            + targets.size()
                            + ") difere da quantidade usada"
                            + " pelo registro ("
                            + registrationResult.getTargetCount()
                            + ")."
            );
        }

        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "As dimensoes da imagem devem ser positivas."
            );
        }
    }

    private void validateRegistrationForTarget(
            ExpectedBubbleTarget target,
            BubbleBlockRegistration registration
    ) {
        if (registration == null) {
            throw new IllegalArgumentException(
                    "Nao existe registro para o bloco "
                            + target.getBlockId()
                            + " do alvo "
                            + target.getOptionId()
            );
        }

        if (!registration.isAccepted()) {
            throw new IllegalArgumentException(
                    "O bloco "
                            + registration.getBlockId()
                            + " nao foi aceito para medir "
                            + target.getOptionId()
            );
        }

        if (registration.getBlockIndex()
                != target.getBlockIndex()
                || !registration.getBlockId().equals(
                target.getBlockId()
        )) {

            throw new IllegalArgumentException(
                    "O registro encontrado nao corresponde"
                            + " ao bloco do alvo "
                            + target.getOptionId()
            );
        }
    }
}
