package com.example.leitorgabaritoomr.domain.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;

public final class OmrAssessmentStructureTest {

    @Test
    public void preservesNormalizedMultidisciplinaryBlocks() {
        OmrAssessmentStructure structure = structure();

        assertEquals("assessment-1", structure.getAssessmentId());
        assertEquals(2, structure.getAssessmentVersion());
        assertEquals(45, structure.getQuestionCount());
        assertEquals(3, structure.getBlockCount());

        OmrAssessmentBlock chemistry =
                structure.getBlocks().get(0);

        assertEquals("chemistry", chemistry.getBlockId());
        assertEquals(1, chemistry.getOrder());
        assertEquals(
                "Ciências da Natureza",
                chemistry.getAreaName()
        );
        assertEquals("QUI", chemistry.getComponentCode());
        assertEquals("Química", chemistry.getComponentName());
        assertEquals(1, chemistry.getFirstQuestionPosition());
        assertEquals(15, chemistry.getLastQuestionPosition());
        assertEquals(15, chemistry.getQuestionCount());
    }

    @Test
    public void locatesBlocksAtEveryBoundary() {
        OmrAssessmentStructure structure = structure();

        assertEquals(
                "chemistry",
                structure.findBlockByQuestionPosition(1)
                        .getBlockId()
        );
        assertEquals(
                "chemistry",
                structure.findBlockByQuestionPosition(15)
                        .getBlockId()
        );
        assertEquals(
                "physics",
                structure.findBlockByQuestionPosition(16)
                        .getBlockId()
        );
        assertEquals(
                "biology",
                structure.findBlockByQuestionPosition(45)
                        .getBlockId()
        );

        assertNull(structure.findBlockByQuestionPosition(0));
        assertNull(structure.findBlockByQuestionPosition(46));
    }

    @Test
    public void locatesBlocksByNormalizedId() {
        OmrAssessmentStructure structure = structure();

        assertEquals(
                "Física",
                structure.findBlockById(" physics ")
                        .getComponentName()
        );
        assertNull(structure.findBlockById("missing"));
        assertNull(structure.findBlockById(null));
    }

    @Test
    public void optionalAreaAndCodeCanRemainEmpty() {
        OmrAssessmentBlock block = new OmrAssessmentBlock(
                "general",
                1,
                " ",
                null,
                "Matemática",
                1,
                10,
                policy()
        );

        assertFalse(block.hasAreaName());
        assertFalse(block.hasComponentCode());
        assertNull(block.getAreaName());
        assertNull(block.getComponentCode());
    }

    @Test
    public void assessmentIdentityIsExactAndNormalized() {
        OmrAssessmentStructure structure = structure();

        assertTrue(structure.hasAssessmentIdentity(
                " assessment-1 ",
                2
        ));
        assertFalse(structure.hasAssessmentIdentity(
                "assessment-1",
                1
        ));
        assertFalse(structure.hasAssessmentIdentity(null, 2));
    }

    @Test
    public void legacyStructureCreatesOneWeightedGeneralBlock() {
        OmrAssessmentStructure structure =
                OmrAssessmentStructure.legacySingleBlock(
                        "assessment-old",
                        4,
                        90
                );

        assertTrue(structure.isLegacySingleBlock());
        assertEquals(1, structure.getBlockCount());

        OmrAssessmentBlock block = structure.getBlocks().get(0);

        assertEquals(
                OmrAssessmentStructure.LEGACY_BLOCK_ID,
                block.getBlockId()
        );
        assertEquals(
                OmrAssessmentStructure.LEGACY_BLOCK_NAME,
                block.getComponentName()
        );
        assertEquals(1, block.getFirstQuestionPosition());
        assertEquals(90, block.getLastQuestionPosition());

        OmrProportionalScoringPolicy policy =
                (OmrProportionalScoringPolicy)
                        block.getScoringPolicy();

        assertEquals(
                OmrProportionalScoringPolicy.Basis.WEIGHTED_POINTS,
                policy.getBasis()
        );
    }

    @Test
    public void customStructureIsNotReportedAsLegacy() {
        assertFalse(structure().isLegacySingleBlock());
    }

    @Test
    public void collectionsAreImmutable() {
        expectUnsupportedOperation(() ->
                structure().getBlocks().clear()
        );
    }

    @Test
    public void rejectsGapBetweenBlocks() {
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                20,
                Arrays.asList(
                        block("one", 1, 1, 10),
                        block("two", 2, 12, 9)
                )
        ));
    }

    @Test
    public void rejectsOverlapBetweenBlocks() {
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                20,
                Arrays.asList(
                        block("one", 1, 1, 10),
                        block("two", 2, 10, 11)
                )
        ));
    }

    @Test
    public void rejectsCoverageShorterOrLongerThanAssessment() {
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                11,
                Collections.singletonList(
                        block("one", 1, 1, 10)
                )
        ));

        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                9,
                Collections.singletonList(
                        block("one", 1, 1, 10)
                )
        ));
    }

    @Test
    public void rejectsDuplicateBlockId() {
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                20,
                Arrays.asList(
                        block("same", 1, 1, 10),
                        block("same", 2, 11, 10)
                )
        ));
    }

    @Test
    public void rejectsOrderThatDoesNotFollowList() {
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                10,
                Collections.singletonList(
                        block("one", 2, 1, 10)
                )
        ));
    }

    @Test
    public void rejectsInvalidStructureArguments() {
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                " ",
                1,
                10,
                Collections.singletonList(
                        block("one", 1, 1, 10)
                )
        ));
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                0,
                10,
                Collections.singletonList(
                        block("one", 1, 1, 10)
                )
        ));
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                0,
                Collections.singletonList(
                        block("one", 1, 1, 10)
                )
        ));
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                10,
                Collections.emptyList()
        ));
        expectIllegalArgument(() -> new OmrAssessmentStructure(
                "assessment-1",
                1,
                10,
                null
        ));
    }

    @Test
    public void rejectsInvalidBlockArguments() {
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                " ", 1, null, null, "Química",
                1, 10, policy()
        ));
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                "chemistry", 0, null, null, "Química",
                1, 10, policy()
        ));
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                "chemistry", 1, null, null, " ",
                1, 10, policy()
        ));
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                "chemistry", 1, null, null, "Química",
                0, 10, policy()
        ));
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                "chemistry", 1, null, null, "Química",
                1, 0, policy()
        ));
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                "chemistry", 1, null, null, "Química",
                Integer.MAX_VALUE, 2, policy()
        ));
        expectIllegalArgument(() -> new OmrAssessmentBlock(
                "chemistry", 1, null, null, "Química",
                1, 10, null
        ));
    }

    @Test
    public void structureSurvivesJavaSerialization() throws Exception {
        OmrAssessmentStructure original = structure();
        OmrAssessmentStructure restored = roundTrip(original);

        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertEquals(
                "biology",
                restored.findBlockByQuestionPosition(45)
                        .getBlockId()
        );
        assertSame(
                restored.getBlocks().get(2),
                restored.findBlockById("biology")
        );
    }

    private OmrAssessmentStructure structure() {
        return new OmrAssessmentStructure(
                " assessment-1 ",
                2,
                45,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                " chemistry ",
                                1,
                                " Ciências da Natureza ",
                                " QUI ",
                                " Química ",
                                1,
                                15,
                                policy()
                        ),
                        new OmrAssessmentBlock(
                                "physics",
                                2,
                                "Ciências da Natureza",
                                "FIS",
                                "Física",
                                16,
                                15,
                                policy()
                        ),
                        new OmrAssessmentBlock(
                                "biology",
                                3,
                                "Ciências da Natureza",
                                "BIO",
                                "Biologia",
                                31,
                                15,
                                policy()
                        )
                )
        );
    }

    private OmrAssessmentBlock block(
            String id,
            int order,
            int firstPosition,
            int count
    ) {
        return new OmrAssessmentBlock(
                id,
                order,
                null,
                null,
                "Componente " + order,
                firstPosition,
                count,
                policy()
        );
    }

    private OmrProportionalScoringPolicy policy() {
        return OmrProportionalScoringPolicy
                .correctQuestionsToTenV1();
    }

    private OmrAssessmentStructure roundTrip(
            OmrAssessmentStructure source
    ) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(bytes)) {
            output.writeObject(source);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            return (OmrAssessmentStructure) input.readObject();
        }
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private void expectUnsupportedOperation(Runnable action) {
        try {
            action.run();
            fail("Era esperada UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }
}
