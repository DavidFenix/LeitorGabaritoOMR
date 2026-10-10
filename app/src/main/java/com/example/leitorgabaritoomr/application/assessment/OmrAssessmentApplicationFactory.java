package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

/**
 * Cria o vínculo entre prova e turma depois de validar seus dados escolares.
 */
public final class OmrAssessmentApplicationFactory {

    public OmrAssessmentApplication create(
            String applicationId,
            long createdAtEpochMillis,
            OmrAssessmentDefinition assessment,
            OmrClassGroup classGroup
    ) {
        if (assessment == null) {
            throw new IllegalArgumentException(
                    "A prova é obrigatória."
            );
        }

        if (classGroup == null) {
            throw new IllegalArgumentException(
                    "A turma é obrigatória."
            );
        }

        if (assessment.getSchoolYear()
                != classGroup.getSchoolYear()) {

            throw new IllegalArgumentException(
                    "A prova e a turma devem pertencer"
                            + " ao mesmo ano letivo: prova="
                            + assessment.getSchoolYear()
                            + ", turma="
                            + classGroup.getSchoolYear()
                            + "."
            );
        }

        return new OmrAssessmentApplication(
                applicationId,
                assessment.getAssessmentId(),
                assessment.getVersion(),
                classGroup.getClassGroupId(),
                createdAtEpochMillis
        );
    }
}
