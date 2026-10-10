package com.example.leitorgabaritoomr.domain.classroom;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Define uma turma escolar local.
 *
 * Os alunos não são armazenados dentro da turma. O vínculo é representado
 * por {@link OmrClassEnrollment}, permitindo consultar e persistir turmas,
 * alunos e matrículas separadamente.
 */
public final class OmrClassGroup
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String classGroupId;
    private final String name;
    private final String gradeLevel;
    private final int schoolYear;

    public OmrClassGroup(
            String classGroupId,
            String name,
            String gradeLevel,
            int schoolYear
    ) {
        this.classGroupId = requireText(
                "classGroupId",
                classGroupId
        );
        this.name = requireText("name", name);
        this.gradeLevel = requireText(
                "gradeLevel",
                gradeLevel
        );

        if (schoolYear <= 0) {
            throw new IllegalArgumentException(
                    "O ano letivo da turma deve ser positivo."
            );
        }

        this.schoolYear = schoolYear;
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

    public String getName() {
        return name;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public int getSchoolYear() {
        return schoolYear;
    }

    public boolean hasClassGroupId(
            String candidateClassGroupId
    ) {
        if (candidateClassGroupId == null) {
            return false;
        }

        return classGroupId.equals(
                candidateClassGroupId.trim()
        );
    }

    public boolean belongsToSchoolYear(
            int candidateSchoolYear
    ) {
        return schoolYear == candidateSchoolYear;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrClassGroup)) {
            return false;
        }

        OmrClassGroup that = (OmrClassGroup) other;

        return classGroupId.equals(that.classGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classGroupId);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[nome=%s, etapa=%s, ano=%d]",
                classGroupId,
                name,
                gradeLevel,
                schoolYear
        );
    }
}
