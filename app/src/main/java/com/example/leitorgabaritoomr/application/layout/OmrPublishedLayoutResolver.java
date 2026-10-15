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

        OmrLayoutDefinition flexibleV4 =
                createFlexibleCandidateOrNull(
                        normalizedLayoutId,
                        layoutVersion,
                        questionCount,
                        FLEXIBLE_V4_LAYOUT_VERSION
                );

        if (hasIdentity(
                flexibleV4,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return flexibleV4;
        }

        OmrLayoutDefinition flexibleV3 =
                createFlexibleCandidateOrNull(
                        normalizedLayoutId,
                        layoutVersion,
                        questionCount,
                        FLEXIBLE_V3_LAYOUT_VERSION
                );

        if (hasIdentity(
                flexibleV3,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return flexibleV3;
        }

        OmrLayoutDefinition standardFourOptionsV2 =
                createStandardFourOptionsV2CandidateOrNull(
                        questionCount
                );

        if (hasIdentity(
                standardFourOptionsV2,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return standardFourOptionsV2;
        }

        OmrLayoutDefinition standardFiveOptionsV2 =
                createStandardFiveOptionsV2CandidateOrNull(
                        questionCount
                );

        if (hasIdentity(
                standardFiveOptionsV2,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return standardFiveOptionsV2;
        }

        OmrLayoutDefinition publishedV1Layout =
                createPublishedV1CandidateOrNull(questionCount);

        if (hasIdentity(
                publishedV1Layout,
                normalizedLayoutId,
                layoutVersion,
                questionCount
        )) {
            return publishedV1Layout;
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

    private OmrLayoutDefinition
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

            return createLayout(spec);

        } catch (IllegalArgumentException invalidGeometry) {
            return null;
        }
    }

    private OmrLayoutDefinition
    createStandardFourOptionsV2CandidateOrNull(
            int questionCount
    ) {
        if (!isPublishedQuestionCount(questionCount)) {
            return null;
        }

        return createLayout(
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(questionCount)
        );
    }

    private OmrLayoutDefinition
    createStandardFiveOptionsV2CandidateOrNull(
            int questionCount
    ) {
        if (!isPublishedQuestionCount(questionCount)) {
            return null;
        }

        return createLayout(
                OmrSheetTemplateCatalog
                        .standardFiveOptionsV2(questionCount)
        );
    }

    private OmrLayoutDefinition createPublishedV1CandidateOrNull(
            int questionCount
    ) {
        if (!isPublishedQuestionCount(questionCount)) {
            return null;
        }

        return createLayout(
                OmrSheetTemplateCatalog
                        .publishedFourOptions(questionCount)
        );
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
