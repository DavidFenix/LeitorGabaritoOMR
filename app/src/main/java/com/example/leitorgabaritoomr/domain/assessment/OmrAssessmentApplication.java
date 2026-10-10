package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Vínculo imutável entre uma versão exata de prova e uma turma.
 *
 * A aplicação é a raiz do futuro fluxo prova -> turma -> aluno. Resultados
 * individuais serão associados ao applicationId sem modificar a definição da
 * prova nem o cadastro da turma.
 */
public final class OmrAssessmentApplication
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String applicationId;
    private final String assessmentId;
    private final int assessmentVersion;
    private final String classGroupId;
    private final long createdAtEpochMillis;

    public OmrAssessmentApplication(
            String applicationId,
            String assessmentId,
            int assessmentVersion,
            String classGroupId,
            long createdAtEpochMillis
    ) {
        this.applicationId = requireText(
                "applicationId",
                applicationId
        );
        this.assessmentId = requireText(
                "assessmentId",
                assessmentId
        );

        if (assessmentVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão da prova deve ser positiva."
            );
        }

        this.classGroupId = requireText(
                "classGroupId",
                classGroupId
        );

        if (createdAtEpochMillis <= 0L) {
            throw new IllegalArgumentException(
                    "O instante de criação da aplicação deve ser positivo."
            );
        }

        this.assessmentVersion = assessmentVersion;
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public int getAssessmentVersion() {
        return assessmentVersion;
    }

    public String getClassGroupId() {
        return classGroupId;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public boolean hasApplicationId(
            String candidateApplicationId
    ) {
        if (candidateApplicationId == null) {
            return false;
        }

        return applicationId.equals(
                candidateApplicationId.trim()
        );
    }

    public boolean usesAssessment(
            String candidateAssessmentId,
            int candidateAssessmentVersion
    ) {
        if (candidateAssessmentId == null) {
            return false;
        }

        return assessmentVersion
                == candidateAssessmentVersion
                && assessmentId.equals(
                candidateAssessmentId.trim()
        );
    }

    public boolean belongsToClassGroup(
            String candidateClassGroupId
    ) {
        if (candidateClassGroupId == null) {
            return false;
        }

        return classGroupId.equals(
                candidateClassGroupId.trim()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentApplication)) {
            return false;
        }

        OmrAssessmentApplication that =
                (OmrAssessmentApplication) other;

        return applicationId.equals(that.applicationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[prova=%s@v%d, turma=%s, criadaEm=%d]",
                applicationId,
                assessmentId,
                assessmentVersion,
                classGroupId,
                createdAtEpochMillis
        );
    }
}
