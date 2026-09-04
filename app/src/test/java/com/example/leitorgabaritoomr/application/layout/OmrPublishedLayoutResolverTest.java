package com.example.leitorgabaritoomr.application.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyDefinitionFactory;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class OmrPublishedLayoutResolverTest {

    private static final double DELTA = 0.000001;

    private final OmrPublishedLayoutResolver resolver =
            new OmrPublishedLayoutResolver();

    @Test
    public void resolvesEveryCompactLayoutFromOneToTen() {
        for (int questionCount = 1;
             questionCount <= 10;
             questionCount++) {

            String layoutId = String.format(
                    "omr-compact-ad-q%03d",
                    questionCount
            );

            OmrLayoutDefinition layout = resolver.resolve(
                    layoutId,
                    1,
                    questionCount
            );

            assertEquals(layoutId, layout.getId());
            assertEquals(1, layout.getVersion());
            assertEquals(
                    questionCount,
                    layout.getQuestionCount()
            );
            assertEquals(
                    questionCount * 4,
                    layout.getOptionCount()
            );
        }
    }

    @Test
    public void resolvedCompactLayoutEqualsCatalogGeometry() {
        OmrLayoutDefinition expected =
                OmrDynamicLayoutFactory.create(
                        OmrSheetTemplateCatalog
                                .compactFourOptions(7)
                );

        OmrLayoutDefinition resolved = resolver.resolve(
                expected.getId(),
                expected.getVersion(),
                expected.getQuestionCount()
        );

        assertEquals(
                expected.getCanonicalWidth(),
                resolved.getCanonicalWidth()
        );
        assertEquals(
                expected.getCanonicalHeight(),
                resolved.getCanonicalHeight()
        );
        assertEquals(
                expected.getOptionCount(),
                resolved.getOptionCount()
        );

        for (int index = 0;
             index < expected.getOptionCount();
             index++) {

            OmrOptionDefinition expectedOption =
                    expected.getAllOptions().get(index);

            OmrOptionDefinition resolvedOption =
                    resolved.getAllOptions().get(index);

            assertEquals(
                    expectedOption.getId(),
                    resolvedOption.getId()
            );
            assertEquals(
                    expectedOption.getLabel(),
                    resolvedOption.getLabel()
            );
            assertEquals(
                    expectedOption.getCenter().getX(),
                    resolvedOption.getCenter().getX(),
                    DELTA
            );
            assertEquals(
                    expectedOption.getCenter().getY(),
                    resolvedOption.getCenter().getY(),
                    DELTA
            );
            assertEquals(
                    expectedOption.getSamplingRadiusX(),
                    resolvedOption.getSamplingRadiusX(),
                    DELTA
            );
            assertEquals(
                    expectedOption.getSamplingRadiusY(),
                    resolvedOption.getSamplingRadiusY(),
                    DELTA
            );
        }
    }

    @Test
    public void resolvesLayoutDirectlyFromAnswerKeyIdentity() {
        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(
                        OmrSheetTemplateCatalog
                                .compactFourOptions(3)
                );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinitionFactory()
                        .createSingleAnswerKey(
                                "answer-key-3",
                                1,
                                "Avaliação de três questões",
                                layout,
                                answerLabels(3),
                                1.0
                        );

        OmrLayoutDefinition resolved =
                resolver.resolveForAnswerKey(answerKey);

        assertEquals(
                answerKey.getLayoutId(),
                resolved.getId()
        );
        assertEquals(
                answerKey.getLayoutVersion(),
                resolved.getVersion()
        );
        assertEquals(
                answerKey.getQuestionCount(),
                resolved.getQuestionCount()
        );
    }

    @Test
    public void keepsLegacyFiftyTwoQuestionLayoutAvailable() {
        OmrLayoutDefinition legacy =
                AvalieCeDevelopmentLayoutFactory.create();

        OmrLayoutDefinition resolved = resolver.resolve(
                legacy.getId(),
                legacy.getVersion(),
                legacy.getQuestionCount()
        );

        assertEquals("avalie-ce-development", resolved.getId());
        assertEquals(1, resolved.getVersion());
        assertEquals(52, resolved.getQuestionCount());
        assertEquals(208, resolved.getOptionCount());
    }

    @Test
    public void rejectsCompactIdThatDisagreesWithQuestionCount() {
        expectIllegalArgument(() -> resolver.resolve(
                "omr-compact-ad-q010",
                1,
                7
        ));
    }

    @Test
    public void rejectsUnsupportedLayoutVersion() {
        expectIllegalArgument(() -> resolver.resolve(
                "omr-compact-ad-q010",
                2,
                10
        ));
    }

    @Test
    public void rejectsUnknownLayoutIdentity() {
        expectIllegalArgument(() -> resolver.resolve(
                "layout-inexistente",
                1,
                10
        ));
    }

    @Test
    public void rejectsNonPositiveQuestionCount() {
        expectIllegalArgument(() -> resolver.resolve(
                "omr-compact-ad-q001",
                1,
                0
        ));
    }

    @Test
    public void rejectsBlankLayoutId() {
        expectIllegalArgument(() -> resolver.resolve(
                "   ",
                1,
                10
        ));
    }

    @Test
    public void rejectsNullAnswerKey() {
        expectIllegalArgument(() ->
                resolver.resolveForAnswerKey(null)
        );
    }

    private List<String> answerLabels(
            int questionCount
    ) {
        List<String> labels =
                new ArrayList<>(questionCount);

        for (int index = 0;
             index < questionCount;
             index++) {

            labels.add(
                    new String[]{"A", "B", "C", "D"}
                            [index % 4]
            );
        }

        return labels;
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
