package com.example.leitorgabaritoomr.domain.classroom;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Vínculo imutável entre um aluno cadastrado e uma turma.
 *
 * A identidade da matrícula é o par classGroupId + studentId. A posição na
 * chamada é descritiva e pode ser alterada sem criar outro vínculo lógico.
 */
public final class OmrClassEnrollment
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String classGroupId;
    private final String studentId;
    private final int rosterPosition;

    public OmrClassEnrollment(
            String classGroupId,
            String studentId,
            int rosterPosition
    ) {
        this.classGroupId = requireText(
                "classGroupId",
                classGroupId
        );
        this.studentId = requireText(
                "studentId",
                studentId
        );

        if (rosterPosition <= 0) {
            throw new IllegalArgumentException(
                    "A posição do aluno na chamada deve ser positiva."
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

    public String getClassGroupId() {
        return classGroupId;
    }

    public String getStudentId() {
        return studentId;
    }

    public int getRosterPosition() {
        return rosterPosition;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrClassEnrollment)) {
            return false;
        }

        OmrClassEnrollment that =
                (OmrClassEnrollment) other;

        return classGroupId.equals(that.classGroupId)
                && studentId.equals(that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                classGroupId,
                studentId
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s/%s[posicao=%d]",
                classGroupId,
                studentId,
                rosterPosition
        );
    }
}
