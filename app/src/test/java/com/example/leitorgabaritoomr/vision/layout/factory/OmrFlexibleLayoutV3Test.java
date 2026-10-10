package com.example.leitorgabaritoomr.vision.layout.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;

public final class OmrFlexibleLayoutV3Test {

    private static final double DELTA = 0.000001;

    @Test
    public void automaticDistributionKeepsEighteenItemsPerColumn() {
        assertAutomaticGeometry(1, 1, 1, 240, 260);
        assertAutomaticGeometry(2, 2, 1, 240, 312);
        assertAutomaticGeometry(18, 18, 1, 240, 1144);
        assertAutomaticGeometry(19, 18, 2, 480, 1144);
        assertAutomaticGeometry(45, 18, 3, 720, 1144);
        assertAutomaticGeometry(90, 18, 5, 1200, 1144);
    }

    @Test
    public void manualRowChoiceControlsWidthHeightAndColumnCount() {
        assertManualGeometry(45, 9, 5, 1200, 676);
        assertManualGeometry(45, 15, 3, 720, 988);
        assertManualGeometry(45, 30, 2, 480, 1768);
        assertManualGeometry(90, 30, 3, 720, 1768);
    }

    @Test
    public void everyOccupiedColumnKeepsTheSameCanonicalWidth() {
        for (int questionCount : new int[]{1, 19, 45, 90}) {
            int minimum = OmrSheetTemplateCatalog
                    .getFlexibleV3MinimumQuestionsPerColumn(
                            questionCount
                    );
            int maximum = OmrSheetTemplateCatalog
                    .getFlexibleV3MaximumQuestionsPerColumn(
                            questionCount
                    );

            for (int rows : new int[]{minimum, maximum}) {
                OmrSheetTemplateSpec spec =
                        OmrSheetTemplateCatalog.flexibleOptionsV3(
                                questionCount,
                                5,
                                rows
                        );

                assertEquals(
                        240,
                        spec.getCanonicalWidth()
                                / spec.getBlockCount()
                );
                assertEquals(
                        spec.getBlockCount(),
                        spec.getLayoutColumnCount()
                );
                assertEquals(
                        0.0,
                        spec.getBlockLeft(0),
                        DELTA
                );
            }
        }
    }

    @Test
    public void automaticAndEquivalentManualChoiceShareIdentity() {
        OmrSheetTemplateSpec automatic =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5
                );

        OmrSheetTemplateSpec manual =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        18
                );

        assertEquals(
                "omr-flex-ae-q045-r018",
                automatic.getTemplateId()
        );
        assertEquals(
                automatic.getTemplateId(),
                manual.getTemplateId()
        );
        assertEquals(
                automatic.getCanonicalWidth(),
                manual.getCanonicalWidth()
        );
        assertEquals(
                automatic.getCanonicalHeight(),
                manual.getCanonicalHeight()
        );
    }

    @Test
    public void identitiesDistinguishOptionsAndRowsPerColumn() {
        assertEquals(
                "omr-flex-ad-q045-r009",
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        4,
                        9
                ).getTemplateId()
        );
        assertEquals(
                "omr-flex-ae-q045-r009",
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        9
                ).getTemplateId()
        );
        assertEquals(
                "omr-flex-ae-q045-r030",
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        30
                ).getTemplateId()
        );
    }

    @Test
    public void fourAndFiveOptionLayoutsKeepExpectedElements() {
        OmrLayoutDefinition four = createLayout(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        4,
                        15
                )
        );

        OmrLayoutDefinition five = createLayout(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        15
                )
        );

        assertEquals(45, four.getQuestionCount());
        assertEquals(180, four.getOptionCount());
        assertEquals(3, four.getBlockCount());
        assertEquals(45, five.getQuestionCount());
        assertEquals(225, five.getOptionCount());
        assertEquals(3, five.getBlockCount());
        assertEquals(
                "E",
                five.getAllOptions().get(4).getLabel()
        );
    }

    @Test
    public void bubblesAndRowsKeepTheV2CanonicalMeasurements() {
        OmrLayoutDefinition oneColumn = createLayout(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        18,
                        5,
                        18
                )
        );

        OmrLayoutDefinition fiveColumns = createLayout(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        90,
                        5,
                        18
                )
        );

        assertFirstBubbleGeometry(oneColumn, 52.8, 160.0);
        assertFirstBubbleGeometry(fiveColumns, 52.8, 160.0);

        OmrOptionDefinition first =
                fiveColumns.getAllOptions().get(0);

        OmrOptionDefinition nextRow =
                fiveColumns.getAllOptions().get(5);

        assertEquals(
                52.0,
                (nextRow.getCenter().getY()
                        - first.getCenter().getY())
                        * fiveColumns.getCanonicalHeight(),
                DELTA
        );
    }

    @Test
    public void supportedManualRangeKeepsAtMostFiveColumnsAndA4Height() {
        assertRange(1, 1, 1);
        assertRange(10, 2, 10);
        assertRange(45, 9, 30);
        assertRange(90, 18, 30);

        OmrSheetTemplateSpec tallest =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        90,
                        5,
                        30
                );

        assertEquals(3, tallest.getBlockCount());
        assertEquals(1768, tallest.getCanonicalHeight());
    }

    @Test
    public void rejectsRowsThatWouldCreateMoreThanFiveColumns() {
        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        4,
                        8
                )
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        90,
                        5,
                        17
                )
        );
    }

    @Test
    public void rejectsRowsAbovePhysicalOrQuestionLimit() {
        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        4,
                        31
                )
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        10,
                        5,
                        11
                )
        );
    }

    @Test
    public void rejectsUnsupportedOptionCounts() {
        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        10,
                        3,
                        10
                )
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        10,
                        6,
                        10
                )
        );
    }

    private void assertAutomaticGeometry(
            int questionCount,
            int expectedRows,
            int expectedColumns,
            int expectedWidth,
            int expectedHeight
    ) {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        questionCount,
                        5
                );

        assertEquals(3, spec.getTemplateVersion());
        assertEquals(expectedRows, spec.getQuestionsPerBlock());
        assertEquals(expectedColumns, spec.getBlockCount());
        assertEquals(expectedColumns, spec.getLayoutColumnCount());
        assertEquals(expectedWidth, spec.getCanonicalWidth());
        assertEquals(expectedHeight, spec.getCanonicalHeight());
    }

    private void assertManualGeometry(
            int questionCount,
            int questionsPerColumn,
            int expectedColumns,
            int expectedWidth,
            int expectedHeight
    ) {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        questionCount,
                        4,
                        questionsPerColumn
                );

        assertEquals(3, spec.getTemplateVersion());
        assertEquals(
                questionsPerColumn,
                spec.getQuestionsPerBlock()
        );
        assertEquals(expectedColumns, spec.getBlockCount());
        assertEquals(expectedColumns, spec.getLayoutColumnCount());
        assertEquals(expectedWidth, spec.getCanonicalWidth());
        assertEquals(expectedHeight, spec.getCanonicalHeight());
    }

    private void assertFirstBubbleGeometry(
            OmrLayoutDefinition layout,
            double expectedX,
            double expectedY
    ) {
        OmrOptionDefinition first =
                layout.getAllOptions().get(0);

        assertEquals(
                expectedX,
                first.getCenter().getX()
                        * layout.getCanonicalWidth(),
                DELTA
        );
        assertEquals(
                expectedY,
                first.getCenter().getY()
                        * layout.getCanonicalHeight(),
                DELTA
        );
        assertEquals(
                18.0,
                first.getSamplingRadiusX()
                        * layout.getCanonicalWidth(),
                DELTA
        );
        assertEquals(
                18.0,
                first.getSamplingRadiusY()
                        * layout.getCanonicalHeight(),
                DELTA
        );
    }

    private void assertRange(
            int questionCount,
            int expectedMinimum,
            int expectedMaximum
    ) {
        assertEquals(
                expectedMinimum,
                OmrSheetTemplateCatalog
                        .getFlexibleV3MinimumQuestionsPerColumn(
                                questionCount
                        )
        );
        assertEquals(
                expectedMaximum,
                OmrSheetTemplateCatalog
                        .getFlexibleV3MaximumQuestionsPerColumn(
                                questionCount
                        )
        );
    }

    private OmrLayoutDefinition createLayout(
            OmrSheetTemplateSpec spec
    ) {
        return OmrDynamicLayoutFactory.create(spec);
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
