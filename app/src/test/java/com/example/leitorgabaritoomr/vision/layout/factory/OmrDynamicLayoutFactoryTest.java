package com.example.leitorgabaritoomr.vision.layout.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;

import java.util.List;

public final class OmrDynamicLayoutFactoryTest {

    private static final double DELTA = 0.000001;

    @Test
    public void compactOneQuestionCreatesExactLayout() {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog
                        .compactFourOptions(1);

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        assertEquals("omr-compact-ad-q001", layout.getId());
        assertEquals(1, layout.getVersion());
        assertEquals(1200, layout.getCanonicalWidth());
        assertEquals(700, layout.getCanonicalHeight());
        assertEquals(1, layout.getBlockCount());
        assertEquals(1, layout.getQuestionCount());
        assertEquals(4, layout.getOptionCount());

        OmrQuestionDefinition question =
                layout.getAllQuestions().get(0);

        assertEquals("question-001", question.getId());
        assertEquals("1", question.getLabel());
        assertEquals(4, question.getOptionCount());
    }

    @Test
    public void compactTenQuestionsCreatesExactLayout() {
        OmrLayoutDefinition layout =
                createCompactLayout(10);

        assertEquals("omr-compact-ad-q010", layout.getId());
        assertEquals(2, layout.getBlockCount());
        assertEquals(10, layout.getQuestionCount());
        assertEquals(40, layout.getOptionCount());

        List<OmrQuestionDefinition> questions =
                layout.getAllQuestions();

        assertEquals("question-001", questions.get(0).getId());
        assertEquals("question-010", questions.get(9).getId());
        assertEquals("10", questions.get(9).getLabel());
    }

    @Test
    public void tenQuestionsAreDistributedFivePerBlock() {
        OmrLayoutDefinition layout =
                createCompactLayout(10);

        OmrBlockDefinition firstBlock =
                layout.getBlocks().get(0);

        OmrBlockDefinition secondBlock =
                layout.getBlocks().get(1);

        assertEquals("block-01", firstBlock.getId());
        assertEquals("block-02", secondBlock.getId());
        assertEquals(5, firstBlock.getQuestionCount());
        assertEquals(5, secondBlock.getQuestionCount());
        assertEquals(
                "question-005",
                firstBlock.getQuestions().get(4).getId()
        );
        assertEquals(
                "question-006",
                secondBlock.getQuestions().get(0).getId()
        );
    }

    @Test
    public void partialLastBlockDoesNotCreatePhantomQuestions() {
        OmrLayoutDefinition layout =
                createCompactLayout(7);

        assertEquals(2, layout.getBlockCount());
        assertEquals(5, layout.getBlocks().get(0).getQuestionCount());
        assertEquals(2, layout.getBlocks().get(1).getQuestionCount());
        assertEquals(7, layout.getQuestionCount());
        assertEquals(28, layout.getOptionCount());
        assertEquals(
                "question-007",
                layout.getAllQuestions().get(6).getId()
        );
        assertFalse(
                containsQuestion(layout, "question-008")
        );
    }

    @Test
    public void questionAndOptionIdsRemainStableWhenCountChanges() {
        OmrLayoutDefinition sevenQuestions =
                createCompactLayout(7);

        OmrLayoutDefinition tenQuestions =
                createCompactLayout(10);

        for (int questionIndex = 0;
             questionIndex < sevenQuestions.getQuestionCount();
             questionIndex++) {

            OmrQuestionDefinition shorterQuestion =
                    sevenQuestions.getAllQuestions()
                            .get(questionIndex);

            OmrQuestionDefinition longerQuestion =
                    tenQuestions.getAllQuestions()
                            .get(questionIndex);

            assertEquals(
                    shorterQuestion.getId(),
                    longerQuestion.getId()
            );

            for (int optionIndex = 0;
                 optionIndex < shorterQuestion.getOptionCount();
                 optionIndex++) {

                assertEquals(
                        shorterQuestion.getOptions()
                                .get(optionIndex)
                                .getId(),
                        longerQuestion.getOptions()
                                .get(optionIndex)
                                .getId()
                );
            }
        }
    }

    @Test
    public void compactCoordinatesAndSamplingRegionsStayInsideLayout() {
        OmrLayoutDefinition layout =
                createCompactLayout(10);

        for (OmrOptionDefinition option
                : layout.getAllOptions()) {

            assertTrue(option.getLeft() >= 0.0);
            assertTrue(option.getTop() >= 0.0);
            assertTrue(option.getRight() <= 1.0);
            assertTrue(option.getBottom() <= 1.0);
            assertEquals(
                    0.015,
                    option.getSamplingRadiusX(),
                    DELTA
            );
            assertEquals(
                    0.026,
                    option.getSamplingRadiusY(),
                    DELTA
            );
        }

        OmrOptionDefinition firstOption =
                layout.getBlocks().get(0)
                        .getQuestions().get(0)
                        .getOptions().get(0);

        OmrOptionDefinition secondBlockFirstOption =
                layout.getBlocks().get(1)
                        .getQuestions().get(0)
                        .getOptions().get(0);

        assertEquals(
                0.15,
                firstOption.getCenter().getX(),
                DELTA
        );
        assertEquals(
                0.65,
                secondBlockFirstOption.getCenter().getX(),
                DELTA
        );
        assertEquals(
                0.20,
                firstOption.getCenter().getY(),
                DELTA
        );
    }

    @Test
    public void templateSpecProtectsItsOptionArrays() {
        String[] labels = {"A", "B", "C", "D"};
        double[] positions = {0.30, 0.44, 0.58, 0.72};

        OmrSheetTemplateSpec spec =
                createSpec(
                        10,
                        5,
                        labels,
                        positions
                );

        labels[0] = "X";
        positions[0] = 0.99;

        String[] returnedLabels = spec.getOptionLabels();
        double[] returnedPositions = spec.getOptionLocalX();

        returnedLabels[1] = "Y";
        returnedPositions[1] = 0.98;

        assertEquals("A", spec.getOptionLabels()[0]);
        assertEquals("B", spec.getOptionLabels()[1]);
        assertEquals(
                0.30,
                spec.getOptionLocalX()[0],
                DELTA
        );
        assertEquals(
                0.44,
                spec.getOptionLocalX()[1],
                DELTA
        );
    }

    @Test
    public void dynamicFactorySupportsNinetyQuestions() {
        OmrSheetTemplateSpec spec =
                createSpec(
                        90,
                        15,
                        new String[]{"A", "B", "C", "D"},
                        new double[]{0.20, 0.38, 0.56, 0.74}
                );

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        assertEquals(6, layout.getBlockCount());
        assertEquals(90, layout.getQuestionCount());
        assertEquals(360, layout.getOptionCount());
        assertEquals(
                "question-090",
                layout.getAllQuestions().get(89).getId()
        );
    }

    @Test
    public void templateSpecRejectsCountsOutsideOneToNinety() {
        expectIllegalArgument(() ->
                createSpec(
                        0,
                        5,
                        new String[]{"A", "B", "C", "D"},
                        new double[]{0.30, 0.44, 0.58, 0.72}
                )
        );

        expectIllegalArgument(() ->
                createSpec(
                        91,
                        5,
                        new String[]{"A", "B", "C", "D"},
                        new double[]{0.30, 0.44, 0.58, 0.72}
                )
        );
    }

    @Test
    public void compactCatalogRejectsCountsOutsideItsFamily() {
        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog
                        .compactFourOptions(0)
        );

        expectIllegalArgument(() ->
                OmrSheetTemplateCatalog
                        .compactFourOptions(11)
        );
    }

    private OmrLayoutDefinition createCompactLayout(
            int questionCount
    ) {
        return OmrDynamicLayoutFactory.create(
                OmrSheetTemplateCatalog
                        .compactFourOptions(questionCount)
        );
    }

    private OmrSheetTemplateSpec createSpec(
            int questionCount,
            int questionsPerBlock,
            String[] labels,
            double[] positions
    ) {
        double firstRowY = 0.08;
        double rowSpacingY =
                questionsPerBlock == 1
                        ? 0.10
                        : 0.84
                        / (questionsPerBlock - 1);

        int blockCount =
                (questionCount
                        + questionsPerBlock
                        - 1)
                        / questionsPerBlock;

        if (blockCount <= 0) {
            blockCount = 1;
        }

        double samplingRadiusX =
                0.01 / blockCount;

        return new OmrSheetTemplateSpec(
                "custom-template-" + questionCount,
                1,
                "Modelo de teste",
                questionCount,
                1800,
                1000,
                questionsPerBlock,
                labels,
                positions,
                firstRowY,
                rowSpacingY,
                samplingRadiusX,
                0.02,
                1
        );
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
