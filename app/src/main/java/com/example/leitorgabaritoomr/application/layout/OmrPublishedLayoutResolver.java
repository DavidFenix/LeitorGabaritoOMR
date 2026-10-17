package com.example.leitorgabaritoomr.application.layout;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstrói um layout publicado a partir de sua identidade persistida.
 *
 * Gabarito oficial, cartão exportado e captura precisam compartilhar
 * exatamente layoutId, versão e quantidade de questões. Este resolvedor
 * concentra essa decisão e impede que a câmera use silenciosamente uma
 * geometria diferente daquela registrada no gabarito.
 */
public final class OmrPublishedLayoutResolver {

    private static final int FLEXIBLE_V3_LAYOUT_VERSION = 3;
    private static final int FLEXIBLE_V4_LAYOUT_VERSION = 4;

    private static final Pattern FLEXIBLE_LAYOUT_ID_PATTERN =
            Pattern.compile(
                    "^omr-flex-(ad|ae)-q(\\d{3})-r(\\d{3})$"
            );

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

    /**
     * Reconstrói a especificação publicável usada para gerar cartões.
     *
     * O layout de desenvolvimento legado continua aceito pela leitura, mas
     * não possui um contrato de cartão publicado e, por isso, não é devolvido
     * por este método.
     */
    public OmrSheetTemplateSpec resolveTemplateForAnswerKey(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito oficial é obrigatório."
            );
        }

        return resolveTemplate(
                answerKeyDefinition.getLayoutId(),
                answerKeyDefinition.getLayoutVersion(),
                answerKeyDefinition.getQuestionCount()
        );
    }

    public OmrSheetTemplateSpec resolveTemplate(
            String layoutId,
            int layoutVersion,
            int questionCount
    ) {
        String normalizedLayoutId = requireText(layoutId);
        validateIdentity(layoutVersion, questionCount);

        OmrSheetTemplateSpec spec = resolveTemplateOrNull(
                normalizedLayoutId,
                layoutVersion,
                questionCount
        );

        if (spec == null) {
            throw new IllegalArgumentException(
                    "Nenhum modelo de cartão publicado corresponde a "
                            + normalizedLayoutId
                            + "@v"
                            + layoutVersion
                            + " com "
                            + questionCount
                            + " questões."
            );
        }

        return spec;
    }

    public OmrLayoutDefinition resolve(
            String layoutId,
            int layoutVersion,
            int questionCount
    ) {
        String normalizedLayoutId =
                requireText(layoutId);
        validateIdentity(layoutVersion, questionCount);

        OmrSheetTemplateSpec publishedSpec = resolveTemplateOrNull(
                normalizedLayoutId,
                layoutVersion,
                questionCount
        );

        if (publishedSpec != null) {
            return createLayout(publishedSpec);
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

    private OmrSheetTemplateSpec resolveTemplateOrNull(
            String layoutId,
            int layoutVersion,
            int questionCount
    ) {
        OmrSheetTemplateSpec flexibleV4 =
                createFlexibleCandidateOrNull(
                        layoutId,
                        layoutVersion,
                        questionCount,
                        FLEXIBLE_V4_LAYOUT_VERSION
                );

        if (hasIdentity(
                flexibleV4,
                layoutId,
                layoutVersion,
                questionCount
        )) {
            return flexibleV4;
        }

        OmrSheetTemplateSpec flexibleV3 =
                createFlexibleCandidateOrNull(
                        layoutId,
                        layoutVersion,
                        questionCount,
                        FLEXIBLE_V3_LAYOUT_VERSION
                );

        if (hasIdentity(
                flexibleV3,
                layoutId,
                layoutVersion,
                questionCount
        )) {
            return flexibleV3;
        }

        OmrSheetTemplateSpec standardFourOptionsV2 =
                createStandardFourOptionsV2CandidateOrNull(
                        questionCount
                );

        if (hasIdentity(
                standardFourOptionsV2,
                layoutId,
                layoutVersion,
                questionCount
        )) {
            return standardFourOptionsV2;
        }

        OmrSheetTemplateSpec standardFiveOptionsV2 =
                createStandardFiveOptionsV2CandidateOrNull(
                        questionCount
                );

        if (hasIdentity(
                standardFiveOptionsV2,
                layoutId,
                layoutVersion,
                questionCount
        )) {
            return standardFiveOptionsV2;
        }

        OmrSheetTemplateSpec publishedV1 =
                createPublishedV1CandidateOrNull(questionCount);

        return hasIdentity(
                publishedV1,
                layoutId,
                layoutVersion,
                questionCount
        ) ? publishedV1 : null;
    }

    private OmrSheetTemplateSpec
    createFlexibleCandidateOrNull(
            String layoutId,
            int layoutVersion,
            int questionCount,
            int expectedVersion
    ) {
        if (layoutVersion != expectedVersion
                || !isPublishedQuestionCount(questionCount)) {
            return null;
        }

        Matcher matcher =
                FLEXIBLE_LAYOUT_ID_PATTERN.matcher(layoutId);

        if (!matcher.matches()) {
            return null;
        }

        int encodedQuestionCount =
                Integer.parseInt(matcher.group(2));

        if (encodedQuestionCount != questionCount) {
            return null;
        }

        int optionCount = "ad".equals(matcher.group(1))
                ? OmrSheetTemplateCatalog
                .STANDARD_V2_FOUR_OPTION_COUNT
                : OmrSheetTemplateCatalog
                .STANDARD_V2_FIVE_OPTION_COUNT;

        int questionsPerColumn =
                Integer.parseInt(matcher.group(3));

        try {
            OmrSheetTemplateSpec spec = expectedVersion
                    == FLEXIBLE_V4_LAYOUT_VERSION
                    ? OmrSheetTemplateCatalog.flexibleOptionsV4(
                    questionCount,
                    optionCount,
                    questionsPerColumn
            )
                    : OmrSheetTemplateCatalog.flexibleOptionsV3(
                    questionCount,
                    optionCount,
                    questionsPerColumn
            );

            return spec;

        } catch (IllegalArgumentException invalidGeometry) {
            return null;
        }
    }

    private OmrSheetTemplateSpec
    createStandardFourOptionsV2CandidateOrNull(
            int questionCount
    ) {
        if (!isPublishedQuestionCount(questionCount)) {
            return null;
        }

        return OmrSheetTemplateCatalog
                .standardFourOptionsV2(questionCount);
    }

    private OmrSheetTemplateSpec
    createStandardFiveOptionsV2CandidateOrNull(
            int questionCount
    ) {
        if (!isPublishedQuestionCount(questionCount)) {
            return null;
        }

        return OmrSheetTemplateCatalog
                .standardFiveOptionsV2(questionCount);
    }

    private OmrSheetTemplateSpec createPublishedV1CandidateOrNull(
            int questionCount
    ) {
        if (!isPublishedQuestionCount(questionCount)) {
            return null;
        }

        return OmrSheetTemplateCatalog
                .publishedFourOptions(questionCount);
    }

    private OmrLayoutDefinition createLayout(
            OmrSheetTemplateSpec spec
    ) {
        return OmrDynamicLayoutFactory.create(spec);
    }

    private boolean isPublishedQuestionCount(
            int questionCount
    ) {
        return questionCount
                >= OmrSheetTemplateCatalog.MIN_QUESTION_COUNT
                && questionCount
                <= OmrSheetTemplateCatalog.MAX_QUESTION_COUNT;
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

    private boolean hasIdentity(
            OmrSheetTemplateSpec candidate,
            String layoutId,
            int layoutVersion,
            int questionCount
    ) {
        return candidate != null
                && candidate.getTemplateId().equals(layoutId)
                && candidate.getTemplateVersion() == layoutVersion
                && candidate.getQuestionCount() == questionCount;
    }

    private void validateIdentity(
            int layoutVersion,
            int questionCount
    ) {
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
