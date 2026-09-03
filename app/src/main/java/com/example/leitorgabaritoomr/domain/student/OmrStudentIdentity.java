package com.example.leitorgabaritoomr.domain.student;

import java.io.Serializable;
import java.util.Objects;

/**
 * Identidade imutavel de um aluno associado a uma leitura OMR.
 *
 * O studentId e a identidade tecnica estavel do aluno. Matricula,
 * nome e turma sao dados descritivos e podem mudar no cadastro sem
 * alterar essa identidade. A matricula permanece texto para preservar
 * zeros a esquerda e identificadores alfanumericos.
 */
public final class OmrStudentIdentity
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String registration;
    private final String name;
    private final String className;

    public OmrStudentIdentity(
            String studentId,
            String registration,
            String name,
            String className
    ) {
        this.studentId = requireText(
                "studentId",
                studentId
        );

        this.registration = requireText(
                "registration",
                registration
        );

        this.name = requireText(
                "name",
                name
        );

        this.className = requireText(
                "className",
                className
        );
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getStudentId() {
        return studentId;
    }

    public String getRegistration() {
        return registration;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public boolean hasStudentId(
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

        if (!(other instanceof OmrStudentIdentity)) {
            return false;
        }

        OmrStudentIdentity that =
                (OmrStudentIdentity) other;

        return studentId.equals(that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId);
    }

    @Override
    public String toString() {
        return studentId
                + "[matricula=" + registration
                + ", nome=" + name
                + ", turma=" + className
                + "]";
    }
}
