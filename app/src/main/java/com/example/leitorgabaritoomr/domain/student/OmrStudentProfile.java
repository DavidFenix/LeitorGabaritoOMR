package com.example.leitorgabaritoomr.domain.student;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Cadastro permanente e imutável de um aluno no dispositivo.
 *
 * Diferentemente de {@link OmrStudentIdentity}, este objeto não guarda turma.
 * A turma pertence à matrícula e pode mudar sem alterar a identidade estável
 * do aluno. Quando uma correção for registrada, {@link #snapshotForClass}
 * produz a fotografia histórica esperada pelo núcleo OMR existente.
 */
public final class OmrStudentProfile
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String registration;
    private final String name;

    public OmrStudentProfile(
            String studentId,
            String registration,
            String name
    ) {
        this.studentId = requireText(
                "studentId",
                studentId
        );
        this.registration = normalizeRegistration(
                registration
        );
        this.name = requireText("name", name);
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

    public String getStudentId() {
        return studentId;
    }

    public String getRegistration() {
        return registration;
    }

    public String getName() {
        return name;
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

    public boolean hasRegistration(
            String candidateRegistration
    ) {
        if (candidateRegistration == null
                || candidateRegistration.trim().isEmpty()) {
            return false;
        }

        return registration.equals(
                candidateRegistration
                        .trim()
                        .toUpperCase(Locale.ROOT)
        );
    }

    /**
     * Cria a identidade descritiva que será preservada junto ao resultado.
     */
    public OmrStudentIdentity snapshotForClass(
            String className
    ) {
        return new OmrStudentIdentity(
                studentId,
                registration,
                name,
                className
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrStudentProfile)) {
            return false;
        }

        OmrStudentProfile that =
                (OmrStudentProfile) other;

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
                + "]";
    }
}
