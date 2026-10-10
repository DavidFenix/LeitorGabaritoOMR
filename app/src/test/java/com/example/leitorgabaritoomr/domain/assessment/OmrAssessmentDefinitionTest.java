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

public final class OmrAssessmentDefinitionTest {

    @Test
    public void preservesAndNormalizesAssessmentData() {
        OmrAssessmentDefinition assessment =
                new OmrAssessmentDefinition(
                        " assessment-001 ",
                        2,
                        " Prova bimestral ",
                        " Matemática ",
                        " 9º ano ",
                        " 2º bimestre ",
                        2026,
                        " answer-key-001 ",
                        3
                );

        assertEquals(
                "assessment-001",
                assessment.getAssessmentId()
        );
        assertEquals(2, assessment.getVersion());
        assertEquals(
                "Prova bimestral",
                assessment.getTitle()
        );
        assertEquals(
                "Matemática",
                assessment.getSubject()
        );
        assertEquals(
                "9º ano",
                assessment.getGradeLevel()
        );
        assertEquals(
                "2º bimestre",
                assessment.getAcademicPeriod()
        );
        assertEquals(2026, assessment.getSchoolYear());
        assertEquals(
                "answer-key-001",
                assessment.getAnswerKeyId()
        );
        assertEquals(3, assessment.getAnswerKeyVersion());
    }

    @Test
    public void identityUsesOnlyAssessmentIdAndVersion() {
        OmrAssessmentDefinition original = assessment();

        OmrAssessmentDefinition changedMetadata =
                new OmrAssessmentDefinition(
                        "assessment-001",
                        1,
                        "Título atualizado",
                        "Ciências",
                        "8º ano",
                        "3º bimestre",
                        2027,
                        "answer-key-999",
                        4
                );

        assertEquals(original, changedMetadata);
        assertEquals(
                original.hashCode(),
                changedMetadata.hashCode()
        );
    }

    @Test
    public void differentAssessmentIdMeansDifferentIdentity() {
        OmrAssessmentDefinition other =
                new OmrAssessmentDefinition(
                        "assessment-002",
                        1,
                        "Prova bimestral",
                        "Matemática",
                        "9º ano",
                        "2º bimestre",
                        2026,
                        "answer-key-001",
                        1
                );

        assertNotEquals(assessment(), other);
    }

    @Test
    public void differentVersionMeansDifferentIdentity() {
        OmrAssessmentDefinition other =
                new OmrAssessmentDefinition(
                        "assessment-001",
                        2,
                        "Prova bimestral",
                        "Matemática",
                        "9º ano",
                        "2º bimestre",
                        2026,
                        "answer-key-001",
                        1
                );

        assertNotEquals(assessment(), other);
    }

    @Test
    public void findsOnlyExactNormalizedAssessmentIdentity() {
        OmrAssessmentDefinition assessment = assessment();

        assertTrue(assessment.hasIdentity(
                " assessment-001 ",
                1
        ));
        assertFalse(assessment.hasIdentity(
                "assessment-002",
                1
        ));
        assertFalse(assessment.hasIdentity(
                "assessment-001",
                2
        ));
        assertFalse(assessment.hasIdentity(null, 1));
    }

    @Test
    public void matchesOnlyExactNormalizedAnswerKeyReference() {
        OmrAssessmentDefinition assessment = assessment();

        assertTrue(assessment.usesAnswerKey(
                " answer-key-001 ",
                1
        ));
        assertFalse(assessment.usesAnswerKey(
                "answer-key-002",
                1
        ));
        assertFalse(assessment.usesAnswerKey(
                "answer-key-001",
                2
        ));
        assertFalse(assessment.usesAnswerKey(null, 1));
    }

    @Test
    public void rejectsInvalidAssessmentId() {
        expectIllegalArgument(() -> assessmentWith(
                null,
                1,
                2026,
                "answer-key-001",
                1
        ));

        expectIllegalArgument(() -> assessmentWith(
                " ",
                1,
                2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsNonPositiveAssessmentVersion() {
        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                0,
                2026,
                "answer-key-001",
                1
        ));

        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                -1,
                2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsInvalidTitle() {
        expectIllegalArgument(() -> new OmrAssessmentDefinition(
                "assessment-001",
                1,
                " ",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsInvalidSubject() {
        expectIllegalArgument(() -> new OmrAssessmentDefinition(
                "assessment-001",
                1,
                "Prova bimestral",
                null,
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsInvalidGradeLevel() {
        expectIllegalArgument(() -> new OmrAssessmentDefinition(
                "assessment-001",
                1,
                "Prova bimestral",
                "Matemática",
                "",
                "2º bimestre",
                2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsInvalidAcademicPeriod() {
        expectIllegalArgument(() -> new OmrAssessmentDefinition(
                "assessment-001",
                1,
                "Prova bimestral",
                "Matemática",
                "9º ano",
                null,
                2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsNonPositiveSchoolYear() {
        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                1,
                0,
                "answer-key-001",
                1
        ));

        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                1,
                -2026,
                "answer-key-001",
                1
        ));
    }

    @Test
    public void rejectsInvalidAnswerKeyId() {
        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                1,
                2026,
                " ",
                1
        ));

        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                1,
                2026,
                null,
                1
        ));
    }

    @Test
    public void rejectsNonPositiveAnswerKeyVersion() {
        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                1,
                2026,
                "answer-key-001",
                0
        ));

        expectIllegalArgument(() -> assessmentWith(
                "assessment-001",
                1,
                2026,
                "answer-key-001",
                -1
        ));
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrAssessmentDefinition original = assessment();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrAssessmentDefinition restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrAssessmentDefinition)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getTitle(),
                restored.getTitle()
        );
        assertEquals(
                original.getSubject(),
                restored.getSubject()
        );
        assertEquals(
                original.getGradeLevel(),
                restored.getGradeLevel()
        );
        assertEquals(
                original.getAcademicPeriod(),
                restored.getAcademicPeriod()
        );
        assertEquals(
                original.getSchoolYear(),
                restored.getSchoolYear()
        );
        assertEquals(
                original.getAnswerKeyId(),
                restored.getAnswerKeyId()
        );
        assertEquals(
                original.getAnswerKeyVersion(),
                restored.getAnswerKeyVersion()
        );
    }

    private OmrAssessmentDefinition assessment() {
        return assessmentWith(
                "assessment-001",
                1,
                2026,
                "answer-key-001",
                1
        );
    }

    private OmrAssessmentDefinition assessmentWith(
            String assessmentId,
            int version,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                assessmentId,
                version,
                "Prova bimestral",
                "Matemática",
                "9º ano",
                "2º bimestre",
                schoolYear,
                answerKeyId,
                answerKeyVersion
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
