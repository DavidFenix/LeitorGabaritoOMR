package com.example.leitorgabaritoomr.domain.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

public final class OmrAssessmentApplicationTest {

    private static final long CREATED_AT = 1_799_000_000_000L;

    @Test
    public void preservesAndNormalizesApplicationData() {
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        " application-001 ",
                        " assessment-001 ",
                        2,
                        " class-001 ",
                        CREATED_AT
                );

        assertEquals(
                "application-001",
                application.getApplicationId()
        );
        assertEquals(
                "assessment-001",
                application.getAssessmentId()
        );
        assertEquals(2, application.getAssessmentVersion());
        assertEquals(
                "class-001",
                application.getClassGroupId()
        );
        assertEquals(
                CREATED_AT,
                application.getCreatedAtEpochMillis()
        );
    }

    @Test
    public void preservesFrozenMultidisciplinaryStructure() {
        OmrAssessmentStructure structure = structure();
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-001",
                        "assessment-001",
                        2,
                        "class-001",
                        CREATED_AT,
                        structure
                );

        assertTrue(application.hasFrozenStructure());
        assertEquals(structure, application.getFrozenStructureOrNull());
    }

    @Test
    public void rejectsFrozenStructureFromAnotherAssessmentVersion() {
        expectIllegalArgument(() -> new OmrAssessmentApplication(
                "application-001",
                "assessment-001",
                3,
                "class-001",
                CREATED_AT,
                structure()
        ));
    }

    @Test
    public void identityUsesOnlyStableApplicationId() {
        OmrAssessmentApplication original = application();

        OmrAssessmentApplication changedReferences =
                new OmrAssessmentApplication(
                        "application-001",
                        "assessment-999",
                        4,
                        "class-999",
                        CREATED_AT + 1L
                );

        assertEquals(original, changedReferences);
        assertEquals(
                original.hashCode(),
                changedReferences.hashCode()
        );
    }

    @Test
    public void differentApplicationIdMeansDifferentIdentity() {
        OmrAssessmentApplication other =
                new OmrAssessmentApplication(
                        "application-002",
                        "assessment-001",
                        2,
                        "class-001",
                        CREATED_AT
                );

        assertNotEquals(application(), other);
    }

    @Test
    public void findsApplicationByNormalizedId() {
        OmrAssessmentApplication application = application();

        assertTrue(application.hasApplicationId(
                " application-001 "
        ));
        assertFalse(application.hasApplicationId(
                "application-002"
        ));
        assertFalse(application.hasApplicationId(null));
    }

    @Test
    public void matchesOnlyExactAssessmentVersion() {
        OmrAssessmentApplication application = application();

        assertTrue(application.usesAssessment(
                " assessment-001 ",
                2
        ));
        assertFalse(application.usesAssessment(
                "assessment-002",
                2
        ));
        assertFalse(application.usesAssessment(
                "assessment-001",
                1
        ));
        assertFalse(application.usesAssessment(null, 2));
    }

    @Test
    public void identifiesItsClassGroup() {
        OmrAssessmentApplication application = application();

        assertTrue(application.belongsToClassGroup(
                " class-001 "
        ));
        assertFalse(application.belongsToClassGroup(
                "class-002"
        ));
        assertFalse(application.belongsToClassGroup(null));
    }

    @Test
    public void rejectsInvalidApplicationId() {
        expectIllegalArgument(() -> applicationWith(
                null,
                "assessment-001",
                2,
                "class-001",
                CREATED_AT
        ));

        expectIllegalArgument(() -> applicationWith(
                " ",
                "assessment-001",
                2,
                "class-001",
                CREATED_AT
        ));
    }

    @Test
    public void rejectsInvalidAssessmentId() {
        expectIllegalArgument(() -> applicationWith(
                "application-001",
                null,
                2,
                "class-001",
                CREATED_AT
        ));

        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "",
                2,
                "class-001",
                CREATED_AT
        ));
    }

    @Test
    public void rejectsNonPositiveAssessmentVersion() {
        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "assessment-001",
                0,
                "class-001",
                CREATED_AT
        ));

        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "assessment-001",
                -1,
                "class-001",
                CREATED_AT
        ));
    }

    @Test
    public void rejectsInvalidClassGroupId() {
        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "assessment-001",
                2,
                null,
                CREATED_AT
        ));

        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "assessment-001",
                2,
                " ",
                CREATED_AT
        ));
    }

    @Test
    public void rejectsNonPositiveCreationInstant() {
        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "assessment-001",
                2,
                "class-001",
                0L
        ));

        expectIllegalArgument(() -> applicationWith(
                "application-001",
                "assessment-001",
                2,
                "class-001",
                -1L
        ));
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrAssessmentApplication original = application();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrAssessmentApplication restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrAssessmentApplication)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getAssessmentId(),
                restored.getAssessmentId()
        );
        assertEquals(
                original.getAssessmentVersion(),
                restored.getAssessmentVersion()
        );
        assertEquals(
                original.getClassGroupId(),
                restored.getClassGroupId()
        );
        assertEquals(
                original.getCreatedAtEpochMillis(),
                restored.getCreatedAtEpochMillis()
        );
    }

    private OmrAssessmentApplication application() {
        return applicationWith(
                "application-001",
                "assessment-001",
                2,
                "class-001",
                CREATED_AT
        );
    }

    private OmrAssessmentStructure structure() {
        return new OmrAssessmentStructure(
                "assessment-001",
                2,
                2,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                "qui", 1, "Natureza", "QUI", "Química",
                                1, 1,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        ),
                        new OmrAssessmentBlock(
                                "fis", 2, "Natureza", "FIS", "Física",
                                2, 1,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        )
                )
        );
    }

    private OmrAssessmentApplication applicationWith(
            String applicationId,
            String assessmentId,
            int assessmentVersion,
            String classGroupId,
            long createdAtEpochMillis
    ) {
        return new OmrAssessmentApplication(
                applicationId,
                assessmentId,
                assessmentVersion,
                classGroupId,
                createdAtEpochMillis
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
