package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Define uma prova escolar imutável.
 *
 * A prova possui identidade e versão próprias e aponta para uma versão exata
 * do gabarito oficial. Essa separação permite reutilizar o mesmo mecanismo OMR
 * sem confundir a prova aplicada com suas respostas corretas ou com o desenho
 * físico do cartão-resposta.
 *
 * Turma e alunos não pertencem a esta definição. Eles serão associados por
 * uma aplicação da prova, permitindo aplicar a mesma prova a várias turmas.
 */
public final class OmrAssessmentDefinition
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String assessmentId;
    private final int version;
    private final String title;
    private final String subject;
    private final String gradeLevel;
    private final String academicPeriod;
    private final int schoolYear;
    private final String answerKeyId;
    private final int answerKeyVersion;

    public OmrAssessmentDefinition(
            String assessmentId,
            int version,
            String title,
            String subject,
            String gradeLevel,
            String academicPeriod,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        this.assessmentId = requireText(
                "assessmentId",
                assessmentId
        );

        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versão da prova deve ser positiva."
            );
        }

        this.title = requireText("title", title);
        this.subject = requireText("subject", subject);
        this.gradeLevel = requireText(
                "gradeLevel",
                gradeLevel
        );
        this.academicPeriod = requireText(
                "academicPeriod",
                academicPeriod
        );

        if (schoolYear <= 0) {
            throw new IllegalArgumentException(
                    "O ano letivo deve ser positivo."
            );
        }

        this.answerKeyId = requireText(
                "answerKeyId",
                answerKeyId
        );

        if (answerKeyVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão do gabarito deve ser positiva."
            );
        }

        this.version = version;
        this.schoolYear = schoolYear;
        this.answerKeyVersion = answerKeyVersion;
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

    public String getAssessmentId() {
        return assessmentId;
    }

    public int getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getSubject() {
        return subject;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public String getAcademicPeriod() {
        return academicPeriod;
    }

    public int getSchoolYear() {
        return schoolYear;
    }

    public String getAnswerKeyId() {
        return answerKeyId;
    }

    public int getAnswerKeyVersion() {
        return answerKeyVersion;
    }

    public boolean hasIdentity(
            String candidateAssessmentId,
            int candidateVersion
    ) {
        if (candidateAssessmentId == null) {
            return false;
        }

        return version == candidateVersion
                && assessmentId.equals(
                candidateAssessmentId.trim()
        );
    }

    public boolean usesAnswerKey(
            String candidateAnswerKeyId,
            int candidateAnswerKeyVersion
    ) {
        if (candidateAnswerKeyId == null) {
            return false;
        }

        return answerKeyVersion
                == candidateAnswerKeyVersion
                && answerKeyId.equals(
                candidateAnswerKeyId.trim()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentDefinition)) {
            return false;
        }

        OmrAssessmentDefinition that =
                (OmrAssessmentDefinition) other;

        return version == that.version
                && assessmentId.equals(that.assessmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                assessmentId,
                version
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s@v%d[titulo=%s, disciplina=%s, etapa=%s,"
                        + " periodo=%s, ano=%d, gabarito=%s@v%d]",
                assessmentId,
                version,
                title,
                subject,
                gradeLevel,
                academicPeriod,
                schoolYear,
                answerKeyId,
                answerKeyVersion
        );
    }
}
