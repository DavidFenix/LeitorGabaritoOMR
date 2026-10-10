package com.example.leitorgabaritoomr.application.layout;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

/**
 * Seleciona a geometria usada por uma sessão de captura.
 *
 * Sem gabarito oficial, preserva o comportamento histórico do laboratório.
 * Com gabarito, exige que sua identidade corresponda a um layout publicado.
 */
public final class OmrCaptureLayoutProvider {

    private final OmrPublishedLayoutResolver
            publishedLayoutResolver;

    public OmrCaptureLayoutProvider() {
        this(new OmrPublishedLayoutResolver());
    }

    OmrCaptureLayoutProvider(
            OmrPublishedLayoutResolver publishedLayoutResolver
    ) {
        if (publishedLayoutResolver == null) {
            throw new IllegalArgumentException(
                    "O resolvedor de layouts é obrigatório."
            );
        }

        this.publishedLayoutResolver =
                publishedLayoutResolver;
    }

    public OmrLayoutDefinition resolve(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (answerKeyDefinition == null) {
            return AvalieCeDevelopmentLayoutFactory.create();
        }

        return publishedLayoutResolver.resolveForAnswerKey(
                answerKeyDefinition
        );
    }
}
