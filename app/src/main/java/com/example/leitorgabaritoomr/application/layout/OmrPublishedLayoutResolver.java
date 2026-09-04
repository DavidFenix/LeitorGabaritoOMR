package com.example.leitorgabaritoomr.application.layout;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

/**
 * Reconstrói um layout publicado a partir de sua identidade persistida.
 *
 * Gabarito oficial, cartão exportado e captura precisam compartilhar
 * exatamente layoutId, versão e quantidade de questões. Este resolvedor
 * concentra essa decisão e impede que a câmera use silenciosamente uma
 * geometria diferente daquela registrada no gabarito.
 */
public final class OmrPublishedLayoutResolver {

    public OmrLayoutDefinition resolveForAnswerKey(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito oficial é obrigatório."
            );
        }

        return resolve(
                answerKeyDefinition.getLayoutId(),
                answerKeyDefinition.getLayoutVersion(),
                answerKeyDefinition.getQuestionCount()
        );
    }

    public OmrLayoutDefinition resolve(
            String layoutId,
            int layoutVersion,
            int questionCount
    ) {
        String normalizedLayoutId =
                requireText(layoutId);

        if (layoutVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão do layout deve ser positiva."
            );
        }

        if (questionCount <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade de questões deve ser positiva."
            );
        }

        OmrLayoutDefinition compactLayout =
                createCompactCandidateOrNull(questionCount);

        if (hasIdentity(
                compactLayout,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return compactLayout;
        }

        OmrLayoutDefinition legacyLayout =
                AvalieCeDevelopmentLayoutFactory.create();

        if (hasIdentity(
                legacyLayout,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return legacyLayout;
        }

        throw new IllegalArgumentException(
                "Nenhum layout OMR publicado corresponde a "
                        + normalizedLayoutId
                        + "@v"
                        + layoutVersion
                        + " com "
                        + questionCount
                        + " questões."
        );
    }

    private OmrLayoutDefinition createCompactCandidateOrNull(
            int questionCount
    ) {
        if (questionCount
                < OmrSheetTemplateCatalog
                .COMPACT_MIN_QUESTION_COUNT
                || questionCount
                > OmrSheetTemplateCatalog
                .COMPACT_MAX_QUESTION_COUNT) {

            return null;
        }

        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog
                        .compactFourOptions(questionCount);

        return OmrDynamicLayoutFactory.create(spec);
    }

    private boolean hasIdentity(
            OmrLayoutDefinition candidate,
            String layoutId,
            int layoutVersion,
            int questionCount
    ) {
        return candidate != null
                && candidate.getId().equals(layoutId)
                && candidate.getVersion() == layoutVersion
                && candidate.getQuestionCount()
                == questionCount;
    }

    private String requireText(
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O identificador do layout é obrigatório."
            );
        }

        return value.trim();
    }
}
