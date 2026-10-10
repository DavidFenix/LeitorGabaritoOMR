package com.example.leitorgabaritoomr.application.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyDefinitionFactory;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class OmrPublishedFlexibleLayoutV3ResolverTest {

    private static final double DELTA = 0.000001;

    private final OmrPublishedLayoutResolver resolver =
            new OmrPublishedLayoutResolver();

    @Test
    public void resolvesEveryAutomaticFourOptionLayout() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            int rows = Math.min(questionCount, 18);

            OmrLayoutDefinition layout = resolver.resolve(
                    createId("ad", questionCount, rows),
                    3,
                    questionCount
            );

            assertEquals(questionCount, layout.getQuestionCount());
            assertEquals(questionCount * 4, layout.getOptionCount());
            assertEquals(3, layout.getVersion());
        }
    }

    @Test
    public void resolvesEveryAutomaticFiveOptionLayout() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            int rows = Math.min(questionCount, 18);

            OmrLayoutDefinition layout = resolver.resolve(
                    createId("ae", questionCount, rows),
                    3,
                    questionCount
            );

            assertEquals(questionCount, layout.getQuestionCount());
            assertEquals(questionCount * 5, layout.getOptionCount());
            assertEquals(3, layout.getVersion());
        }
    }

    @Test
    public void resolvesRepresentativeManualDistributions() {
        assertResolvedGeometry(45, 4, 9, 5, 1200, 676);
        assertResolvedGeometry(45, 5, 15, 3, 720, 988);
        assertResolvedGeometry(45, 5, 30, 2, 480, 1768);
        assertResolvedGeometry(90, 4, 30, 3, 720, 1768);
    }

    @Test
    public void resolvedGeometryExactlyMatchesTheCatalog() {
        OmrLayoutDefinition expected = OmrDynamicLayoutFactory.create(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        15
                )
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
                expected.getBlockCount(),
                resolved.getBlockCount()
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
    public void resolvesFlexibleLayoutFromPersistedAnswerKeyIdentity() {
        OmrLayoutDefinition layout = OmrDynamicLayoutFactory.create(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        15
                )
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinitionFactory()
                        .createSingleAnswerKey(
                                "answer-key-flex-v3-45",
                                1,
                                "Avaliacao flexivel de 45 questoes",
                                layout,
                                answerLabels(45),
                                1.0
                        );

        OmrLayoutDefinition resolved =
                resolver.resolveForAnswerKey(answerKey);

        assertEquals(answerKey.getLayoutId(), resolved.getId());
        assertEquals(
                answerKey.getLayoutVersion(),
                resolved.getVersion()
        );
        assertEquals(
                answerKey.getQuestionCount(),
                resolved.getQuestionCount()
        );
        assertEquals(3, resolved.getBlockCount());
        assertEquals(225, resolved.getOptionCount());
    }

    @Test
    public void rejectsIdentityWithDifferentQuestionCountOrVersion() {
        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-ae-q045-r015",
                3,
                44
        ));

        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-ae-q045-r015",
                2,
                45
        ));
    }

    @Test
    public void rejectsIdentityWithInvalidRowsPerColumn() {
        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-ad-q045-r008",
                3,
                45
        ));

        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-ae-q045-r031",
                3,
                45
        ));
    }

    @Test
    public void rejectsMalformedFlexibleIdentity() {
        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-af-q045-r015",
                3,
                45
        ));

        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-ae-q45-r15",
                3,
                45
        ));

        expectIllegalArgument(() -> resolver.resolve(
                "omr-flex-ae-q045-r015-extra",
                3,
                45
        ));
    }

    private void assertResolvedGeometry(
            int questionCount,
            int optionCount,
            int rows,
            int expectedColumns,
            int expectedWidth,
            int expectedHeight
    ) {
        String optionCode = optionCount == 4 ? "ad" : "ae";

        OmrLayoutDefinition layout = resolver.resolve(
                createId(optionCode, questionCount, rows),
                3,
                questionCount
        );

        assertEquals(expectedColumns, layout.getBlockCount());
        assertEquals(expectedWidth, layout.getCanonicalWidth());
        assertEquals(expectedHeight, layout.getCanonicalHeight());
        assertEquals(
                questionCount * optionCount,
                layout.getOptionCount()
        );
    }

    private String createId(
            String optionCode,
            int questionCount,
            int rows
    ) {
        return String.format(
                "omr-flex-%s-q%03d-r%03d",
                optionCode,
                questionCount,
                rows
        );
    }

    private List<String> answerLabels(int questionCount) {
        String[] labels = {"A", "B", "C", "D", "E"};
        List<String> answers = new ArrayList<>();

        for (int index = 0;
             index < questionCount;
             index++) {
            answers.add(labels[index % labels.length]);
        }

        return answers;
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }
}
