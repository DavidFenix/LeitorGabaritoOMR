package com.example.leitorgabaritoomr.domain.assessment;

import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Fotografia imutável de um aluno participante de uma aplicação de prova.
 *
 * Nome, matrícula, turma e posição na chamada são preservados no momento em
 * que a lista da aplicação é criada. Mudanças posteriores nos cadastros não
 * reescrevem a composição histórica dessa aplicação.
 */
public final class OmrAssessmentParticipant
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String applicationId;
    private final String studentId;
    private final String registration;
    private final String studentName;
    private final String className;
    private final int rosterPosition;

    public OmrAssessmentParticipant(
            String applicationId,
            String studentId,
            String registration,
            String studentName,
            String className,
            int rosterPosition
    ) {
        this.applicationId = requireText(
                "applicationId",
                applicationId
        );
        this.studentId = requireText(
                "studentId",
                studentId
        );
        this.registration = normalizeRegistration(
                registration
        );
        this.studentName = requireText(
                "studentName",
                studentName
        );
        this.className = requireText(
                "className",
                className
        );

        if (rosterPosition <= 0) {
            throw new IllegalArgumentException(
                    "A posição do participante na chamada"
                            + " deve ser positiva."
            );
        }

        this.rosterPosition = rosterPosition;
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

    private static String normalizeRegistration(
            String registration
    ) {
        return requireText(
                "registration",
                registration
        ).toUpperCase(Locale.ROOT);
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getRegistration() {
        return registration;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getClassName() {
        return className;
    }

    public int getRosterPosition() {
        return rosterPosition;
    }

    public boolean belongsToApplication(
            String candidateApplicationId
    ) {
        if (candidateApplicationId == null) {
            return false;
        }

        return applicationId.equals(
                candidateApplicationId.trim()
        );
    }

    public boolean belongsToStudent(
            String candidateStudentId
    ) {
        if (candidateStudentId == null) {
            return false;
        }

        return studentId.equals(
                candidateStudentId.trim()
        );
    }

    public OmrStudentIdentity toStudentIdentity() {
        return new OmrStudentIdentity(
                studentId,
                registration,
                studentName,
                className
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentParticipant)) {
            return false;
        }

        OmrAssessmentParticipant that =
                (OmrAssessmentParticipant) other;

        return applicationId.equals(that.applicationId)
                && studentId.equals(that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                applicationId,
                studentId
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s/%s[matricula=%s, nome=%s, turma=%s, posicao=%d]",
                applicationId,
                studentId,
                registration,
                studentName,
                className,
                rosterPosition
        );
    }
}
