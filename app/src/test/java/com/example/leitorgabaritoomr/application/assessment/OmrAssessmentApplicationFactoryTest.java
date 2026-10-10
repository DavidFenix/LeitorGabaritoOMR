package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import org.junit.Test;

public final class OmrAssessmentApplicationFactoryTest {

    private static final long CREATED_AT = 1_799_000_000_000L;

    private final OmrAssessmentApplicationFactory factory =
            new OmrAssessmentApplicationFactory();

    @Test
    public void createsApplicationWithExactAssessmentAndClass() {
        OmrAssessmentDefinition assessment = assessment(
                2026,
                "9º ano"
        );
        OmrClassGroup classGroup = classGroup(
                2026,
                "9º ano"
        );

        OmrAssessmentApplication application = factory.create(
                " application-001 ",
                CREATED_AT,
                assessment,
                classGroup
        );

        assertEquals(
                "application-001",
                application.getApplicationId()
        );
        assertTrue(application.usesAssessment(
                assessment.getAssessmentId(),
                assessment.getVersion()
        ));
        assertTrue(application.belongsToClassGroup(
                classGroup.getClassGroupId()
        ));
        assertEquals(
                CREATED_AT,
                application.getCreatedAtEpochMillis()
        );
    }

    @Test
    public void acceptsDifferentGradeLabelsInSameSchoolYear() {
        OmrAssessmentApplication application = factory.create(
                "application-001",
                CREATED_AT,
                assessment(2026, "Ensino Fundamental"),
                classGroup(2026, "9º ano")
        );

        assertEquals(
                "application-001",
                application.getApplicationId()
        );
    }

    @Test
    public void rejectsMissingAssessment() {
        expectIllegalArgument(() -> factory.create(
                "application-001",
                CREATED_AT,
                null,
                classGroup(2026, "9º ano")
        ));
    }

    @Test
    public void rejectsMissingClassGroup() {
        expectIllegalArgument(() -> factory.create(
                "application-001",
                CREATED_AT,
                assessment(2026, "9º ano"),
                null
        ));
    }

    @Test
    public void rejectsDifferentSchoolYears() {
        expectIllegalArgument(() -> factory.create(
                "application-001",
                CREATED_AT,
                assessment(2026, "9º ano"),
                classGroup(2027, "9º ano")
        ));
    }

    private OmrAssessmentDefinition assessment(
            int schoolYear,
            String gradeLevel
    ) {
        return new OmrAssessmentDefinition(
                "assessment-001",
                3,
                "Prova bimestral",
                "Matemática",
                gradeLevel,
                "2º bimestre",
                schoolYear,
                "answer-key-001",
                2
        );
    }

    private OmrClassGroup classGroup(
            int schoolYear,
            String gradeLevel
    ) {
        return new OmrClassGroup(
                "class-001",
                "9º A",
                gradeLevel,
                schoolYear
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
