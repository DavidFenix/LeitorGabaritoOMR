package com.example.leitorgabaritoomr.vision.layout.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;

public final class OmrFlexibleLayoutV4CompactTest {

    private static final double DELTA = 0.000001;

    @Test
    public void automaticDistributionUsesCompactFixedReserves() {
        assertGeometry(1, 1, 1, 240, 180);
        assertGeometry(18, 18, 1, 240, 1064);
        assertGeometry(19, 18, 2, 480, 1064);
        assertGeometry(45, 18, 3, 720, 1064);
        assertGeometry(90, 18, 5, 1200, 1064);
    }

    @Test
    public void manualDistributionKeepsWidthsAndCompactsHeights() {
        assertManualGeometry(45, 9, 5, 1200, 596);
        assertManualGeometry(45, 15, 3, 720, 908);
        assertManualGeometry(45, 30, 2, 480, 1688);
        assertManualGeometry(90, 30, 3, 720, 1688);
    }

    @Test
    public void bubblesMoveCloserWithoutChangingRowsOrSamplingSize() {
        OmrLayoutDefinition layout = OmrDynamicLayoutFactory.create(
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        5,
                        9
                )
        );

        OmrOptionDefinition first = layout.getAllOptions().get(0);
        OmrOptionDefinition secondRow = layout.getAllOptions().get(5);
        OmrOptionDefinition lastRow = layout.getAllOptions().get(40);

        assertEquals(110.0, absoluteY(first, layout), DELTA);
        assertEquals(52.0,
                absoluteY(secondRow, layout)
                        - absoluteY(first, layout),
                DELTA);
        assertEquals(526.0, absoluteY(lastRow, layout), DELTA);
        assertEquals(70.0,
                layout.getCanonicalHeight()
                        - absoluteY(lastRow, layout),
                DELTA);
        assertEquals(18.0,
                first.getSamplingRadiusX()
                        * layout.getCanonicalWidth(),
                DELTA);
        assertEquals(18.0,
                first.getSamplingRadiusY()
                        * layout.getCanonicalHeight(),
                DELTA);
    }

    @Test
    public void v3GeometryRemainsAvailableAndUnchanged() {
        OmrSheetTemplateSpec v3 =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        9
                );

        OmrSheetTemplateSpec v4 =
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        5,
                        9
                );

        OmrLayoutDefinition v3Layout =
                OmrDynamicLayoutFactory.create(v3);
        OmrLayoutDefinition v4Layout =
                OmrDynamicLayoutFactory.create(v4);

        assertEquals("omr-flex-ae-q045-r009", v3.getTemplateId());
        assertEquals(v3.getTemplateId(), v4.getTemplateId());
        assertEquals(3, v3.getTemplateVersion());
        assertEquals(4, v4.getTemplateVersion());
        assertEquals(676, v3.getCanonicalHeight());
        assertEquals(596, v4.getCanonicalHeight());
        assertEquals(160.0,
                absoluteY(v3Layout.getAllOptions().get(0), v3Layout),
                DELTA);
        assertEquals(110.0,
                absoluteY(v4Layout.getAllOptions().get(0), v4Layout),
                DELTA);
        assertNotEquals(
                v3.getTemplateVersion(),
                v4.getTemplateVersion()
        );
    }

    @Test
    public void fourAndFiveOptionsShareTheCompactVerticalGeometry() {
        OmrSheetTemplateSpec four =
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        4,
                        9
                );
        OmrSheetTemplateSpec five =
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        5,
                        9
                );

        assertEquals(596, four.getCanonicalHeight());
        assertEquals(four.getCanonicalHeight(), five.getCanonicalHeight());
        assertEquals(four.getCanonicalWidth(), five.getCanonicalWidth());
        assertEquals(4, four.getOptionCount());
        assertEquals(5, five.getOptionCount());
    }

    private void assertGeometry(
            int questionCount,
            int expectedRows,
            int expectedColumns,
            int expectedWidth,
            int expectedHeight
    ) {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        questionCount,
                        5
                );

        assertEquals(4, spec.getTemplateVersion());
        assertEquals(expectedRows, spec.getQuestionsPerBlock());
        assertEquals(expectedColumns, spec.getBlockCount());
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
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        questionCount,
                        4,
                        questionsPerColumn
                );

        assertEquals(4, spec.getTemplateVersion());
        assertEquals(questionsPerColumn, spec.getQuestionsPerBlock());
        assertEquals(expectedColumns, spec.getBlockCount());
        assertEquals(expectedWidth, spec.getCanonicalWidth());
        assertEquals(expectedHeight, spec.getCanonicalHeight());
    }

    private double absoluteY(
            OmrOptionDefinition option,
            OmrLayoutDefinition layout
    ) {
        return option.getCenter().getY()
                * layout.getCanonicalHeight();
    }
}
