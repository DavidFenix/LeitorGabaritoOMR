package com.example.leitorgabaritoomr.vision.layout.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;

import java.util.List;

public final class OmrPhysicalStandardLayoutV2Test {

    private static final double DELTA = 0.000001;

    @Test
    public void v2BoundaryCountsUseFiveFixedColumnsAndEighteenRows() {
        assertPacking(1, 1, 1, 260);
        assertPacking(18, 1, 18, 1144);
        assertPacking(19, 2, 18, 1144);
        assertPacking(90, 5, 18, 1144);
    }

    @Test
    public void everyFourOptionV2CountCreatesOnlyRealQuestions() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            OmrLayoutDefinition layout =
                    createFourOptionLayout(questionCount);

            assertEquals(2, layout.getVersion());
            assertEquals(
                    String.format(
                            "omr-standard-ad-q%03d",
                            questionCount
                    ),
                    layout.getId()
            );
            assertEquals(
                    questionCount,
                    layout.getQuestionCount()
            );
            assertEquals(
                    questionCount * 4,
                    layout.getOptionCount()
            );
            assertEquals(
                    String.format(
                            "question-%03d",
                            questionCount
                    ),
                    layout.getAllQuestions()
                            .get(questionCount - 1)
                            .getId()
            );
            assertFalse(
                    containsQuestion(
                            layout,
                            String.format(
                                    "question-%03d",
                                    questionCount + 1
                            )
                    )
            );
        }
    }

    @Test
    public void everyV2CountKeepsCanonicalElementGeometryFixed() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            OmrSheetTemplateSpec spec =
                    OmrSheetTemplateCatalog
                            .standardFourOptionsV2(
                                    questionCount
                            );

            OmrLayoutDefinition layout =
                    OmrDynamicLayoutFactory.create(spec);

            OmrOptionDefinition firstOption =
                    layout.getAllOptions().get(0);

            assertEquals(
                    18.0,
                    firstOption.getSamplingRadiusX()
                            * layout.getCanonicalWidth(),
                    DELTA
            );
            assertEquals(
                    18.0,
                    firstOption.getSamplingRadiusY()
                            * layout.getCanonicalHeight(),
                    DELTA
            );
            assertEquals(
                    160.0,
                    firstOption.getCenter().getY()
                            * layout.getCanonicalHeight(),
                    DELTA
            );

            if (layout.getBlocks().get(0)
                    .getQuestionCount() > 1) {

                OmrOptionDefinition secondRowOption =
                        layout.getBlocks().get(0)
                                .getQuestions().get(1)
                                .getOptions().get(0);

                double rowSpacing =
                        (secondRowOption.getCenter().getY()
                                - firstOption.getCenter().getY())
                                * layout.getCanonicalHeight();

                assertEquals(52.0, rowSpacing, DELTA);
            }
        }
    }

    @Test
    public void occupiedColumnsStayCenteredAndKeepFixedWidth() {
        for (int questionCount : new int[]{1, 19, 37, 55, 73, 90}) {
            OmrSheetTemplateSpec spec =
                    OmrSheetTemplateCatalog
                            .standardFourOptionsV2(
                                    questionCount
                            );

            double blockWidth = spec.getBlockWidth();
            double leftMargin = spec.getBlockLeft(0);
            double rightMargin = 1.0
                    - (spec.getBlockLeft(
                            spec.getBlockCount() - 1
                    ) + blockWidth);

            assertEquals(5, spec.getLayoutColumnCount());
            assertEquals(0.20, blockWidth, DELTA);
            assertEquals(leftMargin, rightMargin, DELTA);
            assertEquals(
                    240.0,
                    blockWidth * spec.getCanonicalWidth(),
                    DELTA
            );
        }
    }

    @Test
    public void fourOptionSpacingIsFixedAcrossEveryOccupiedColumn() {
        OmrLayoutDefinition layout =
                createFourOptionLayout(90);

        for (int blockIndex = 0;
             blockIndex < layout.getBlockCount();
             blockIndex++) {

            List<OmrOptionDefinition> options =
                    layout.getBlocks().get(blockIndex)
                            .getQuestions().get(0)
                            .getOptions();

            assertCenterSpacing(layout, options, 44.4);
        }
    }

    @Test
    public void everyFiveOptionV2CountFitsWithoutOverlappingRegions() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            OmrSheetTemplateSpec spec =
                    OmrSheetTemplateCatalog
                            .standardFiveOptionsV2(
                                    questionCount
                            );

            OmrLayoutDefinition layout =
                    OmrDynamicLayoutFactory.create(spec);

            assertEquals(
                    String.format(
                            "omr-standard-ae-q%03d",
                            questionCount
                    ),
                    layout.getId()
            );
            assertEquals(2, layout.getVersion());
            assertEquals(
                    questionCount * 5,
                    layout.getOptionCount()
            );

            for (OmrOptionDefinition option
                    : layout.getAllOptions()) {

                assertTrue(option.getLeft() >= 0.0);
                assertTrue(option.getTop() >= 0.0);
                assertTrue(option.getRight() <= 1.0);
                assertTrue(option.getBottom() <= 1.0);
            }
        }
    }

    @Test
    public void fiveOptionV2ExposesLabelsAThroughEWithFixedSpacing() {
        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(
                        OmrSheetTemplateCatalog
                                .standardFiveOptionsV2(90)
                );

        List<OmrOptionDefinition> options =
                layout.getAllQuestions().get(0)
                        .getOptions();

        assertEquals(5, options.size());
        assertEquals("A", options.get(0).getLabel());
        assertEquals("B", options.get(1).getLabel());
        assertEquals("C", options.get(2).getLabel());
        assertEquals("D", options.get(3).getLabel());
        assertEquals("E", options.get(4).getLabel());
        assertCenterSpacing(layout, options, 37.2);
    }

    @Test
    public void v1PublishedGeometryRemainsTheCurrentDefault() {
        OmrSheetTemplateSpec compact =
                OmrSheetTemplateCatalog
                        .publishedFourOptions(7);

        OmrSheetTemplateSpec extended =
                OmrSheetTemplateCatalog
                        .publishedFourOptions(90);

        assertEquals("omr-compact-ad-q007", compact.getTemplateId());
        assertEquals(1, compact.getTemplateVersion());
        assertEquals(2, compact.getLayoutColumnCount());
        assertEquals(0.50, compact.getBlockWidth(), DELTA);

        assertEquals("omr-extended-ad-q090", extended.getTemplateId());
        assertEquals(1, extended.getTemplateVersion());
        assertEquals(6, extended.getLayoutColumnCount());
        assertEquals(1.0 / 6.0, extended.getBlockWidth(), DELTA);
    }

    @Test
    public void v2CatalogRejectsCountsOutsideOneToNinety() {
        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(0)
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(91)
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog
                        .standardFiveOptionsV2(0)
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog
                        .standardFiveOptionsV2(91)
        );
    }

    @Test
    public void templateRejectsFewerLayoutColumnsThanRealBlocks() {
        expectIllegalArgument(() ->
                new OmrSheetTemplateSpec(
                        "invalid-standard-v2",
                        2,
                        "Modelo invalido",
                        90,
                        1200,
                        1144,
                        18,
                        4,
                        new String[]{"A", "B", "C", "D"},
                        new double[]{0.340, 0.525, 0.710, 0.895},
                        160.0 / 1144.0,
                        52.0 / 1144.0,
                        18.0 / 1200.0,
                        18.0 / 1144.0,
                        1
                )
        );
    }

    private void assertPacking(
            int questionCount,
            int expectedBlocks,
            int expectedFirstBlockRows,
            int expectedHeight
    ) {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(
                                questionCount
                        );

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        assertEquals(1200, spec.getCanonicalWidth());
        assertEquals(expectedHeight, spec.getCanonicalHeight());
        assertEquals(18, spec.getQuestionsPerBlock());
        assertEquals(5, spec.getLayoutColumnCount());
        assertEquals(expectedBlocks, spec.getBlockCount());
        assertEquals(expectedBlocks, layout.getBlockCount());
        assertEquals(
                expectedFirstBlockRows,
                layout.getBlocks().get(0)
                        .getQuestionCount()
        );
    }

    private OmrLayoutDefinition createFourOptionLayout(
            int questionCount
    ) {
        return OmrDynamicLayoutFactory.create(
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(
                                questionCount
                        )
        );
    }

    private void assertCenterSpacing(
            OmrLayoutDefinition layout,
            List<OmrOptionDefinition> options,
            double expectedSpacing
    ) {
        for (int index = 1;
             index < options.size();
             index++) {

            double spacing =
                    (options.get(index).getCenter().getX()
                            - options.get(index - 1)
                            .getCenter().getX())
                            * layout.getCanonicalWidth();

            assertEquals(
                    expectedSpacing,
                    spacing,
                    DELTA
            );
        }
    }

    private boolean containsQuestion(
            OmrLayoutDefinition layout,
            String questionId
    ) {
        for (OmrQuestionDefinition question
                : layout.getAllQuestions()) {

            if (question.getId().equals(questionId)) {
                return true;
            }
        }

        return false;
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }
}
