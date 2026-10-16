package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Referência imutável entre um participante de aplicação e uma correção.
 *
 * A correção completa permanece no histórico OMR e é referenciada pelo
 * historyRecordId. Quando o cartão do mesmo aluno é corrigido novamente,
 * cria-se outra tentativa com a próxima revisão; o registro anterior não é
 * apagado nem reescrito.
 */
public final class OmrAssessmentAttempt
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String attemptId;
    private final String applicationId;
    private final String studentId;
    private final int revision;
    private final String historyRecordId;
    private final long recordedAtEpochMillis;
    private final OmrAssessmentStructure frozenStructure;

    public OmrAssessmentAttempt(
            String attemptId,
            String applicationId,
            String studentId,
            int revision,
            String historyRecordId,
            long recordedAtEpochMillis
    ) {
        this(
                attemptId,
                applicationId,
                studentId,
                revision,
                historyRecordId,
                recordedAtEpochMillis,
                null
        );
    }

    /**
     * Cria uma revisão com a fotografia curricular efetivamente usada na
     * correção. O valor nulo existe somente para tentativas legadas.
     */
    public OmrAssessmentAttempt(
            String attemptId,
            String applicationId,
            String studentId,
            int revision,
            String historyRecordId,
            long recordedAtEpochMillis,
            OmrAssessmentStructure frozenStructure
    ) {
        this.attemptId = requireText(
                "attemptId",
                attemptId
        );
        this.applicationId = requireText(
                "applicationId",
                applicationId
        );
        this.studentId = requireText(
                "studentId",
                studentId
        );

        if (revision <= 0) {
            throw new IllegalArgumentException(
                    "A revisão da tentativa deve ser positiva."
            );
        }

        this.historyRecordId = requireText(
                "historyRecordId",
                historyRecordId
        );

        if (recordedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException(
                    "O instante da tentativa deve ser positivo."
            );
        }

        this.revision = revision;
        this.recordedAtEpochMillis = recordedAtEpochMillis;
        this.frozenStructure = frozenStructure;
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

    public String getAttemptId() {
        return attemptId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStudentId() {
        return studentId;
    }

    public int getRevision() {
        return revision;
    }

    public String getHistoryRecordId() {
        return historyRecordId;
    }

    public long getRecordedAtEpochMillis() {
        return recordedAtEpochMillis;
    }

    public boolean hasFrozenStructure() {
        return frozenStructure != null;
    }

    public OmrAssessmentStructure getFrozenStructureOrNull() {
        return frozenStructure;
    }

    public boolean hasAttemptId(
            String candidateAttemptId
    ) {
        if (candidateAttemptId == null) {
            return false;
        }

        return attemptId.equals(
                candidateAttemptId.trim()
        );
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

    public boolean belongsToParticipant(
            String candidateApplicationId,
            String candidateStudentId
    ) {
        return belongsToApplication(candidateApplicationId)
                && belongsToStudent(candidateStudentId);
    }

    public boolean referencesHistoryRecord(
            String candidateHistoryRecordId
    ) {
        if (candidateHistoryRecordId == null) {
            return false;
        }

        return historyRecordId.equals(
                candidateHistoryRecordId.trim()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentAttempt)) {
            return false;
        }

        OmrAssessmentAttempt that =
                (OmrAssessmentAttempt) other;

        return attemptId.equals(that.attemptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[aplicacao=%s, aluno=%s, revisao=%d,"
                        + " historico=%s, registradaEm=%d]",
                attemptId,
                applicationId,
                studentId,
                revision,
                historyRecordId,
                recordedAtEpochMillis
        );
    }
}
