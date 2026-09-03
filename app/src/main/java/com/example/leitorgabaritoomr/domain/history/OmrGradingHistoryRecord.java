package com.example.leitorgabaritoomr.domain.history;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutavel de uma leitura corrigida vinculada a um aluno.
 *
 * O registro preserva a identidade do aluno e o resultado completo da
 * correcao no momento em que foram armazenados. Como todos os objetos
 * envolvidos sao imutaveis, alteracoes futuras no cadastro do aluno ou
 * no gabarito ativo nao modificam uma nota historica.
 */
public final class OmrGradingHistoryRecord
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String historyRecordId;
    private final long storedAtEpochMillis;
    private final OmrStudentIdentity student;
    private final OmrGradingResult gradingResult;

    public OmrGradingHistoryRecord(
            String historyRecordId,
            long storedAtEpochMillis,
            OmrStudentIdentity student,
            OmrGradingResult gradingResult
    ) {
        this.historyRecordId = requireText(
                "historyRecordId",
                historyRecordId
        );

        if (storedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException(
                    "storedAtEpochMillis deve ser positivo."
            );
        }

        if (student == null) {
            throw new IllegalArgumentException(
                    "O aluno e obrigatorio."
            );
        }

        if (gradingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da correcao e obrigatorio."
            );
        }

        this.storedAtEpochMillis = storedAtEpochMillis;
        this.student = student;
        this.gradingResult = gradingResult;
    }

    /**
     * Cria um novo registro com UUID e instante atuais.
     */
    public static OmrGradingHistoryRecord create(
            OmrStudentIdentity student,
            OmrGradingResult gradingResult
    ) {
        return new OmrGradingHistoryRecord(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                student,
                gradingResult
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

    public String getHistoryRecordId() {
        return historyRecordId;
    }

    public long getStoredAtEpochMillis() {
        return storedAtEpochMillis;
    }

    public OmrStudentIdentity getStudent() {
        return student;
    }

    public OmrGradingResult getGradingResult() {
        return gradingResult;
    }

    public String getReadingId() {
        return getReadingResult().getReadingId();
    }

    public long getCapturedAtEpochMillis() {
        return getReadingResult()
                .getCapturedAtEpochMillis();
    }

    public OmrReadingResult getReadingResult() {
        return gradingResult.getReadingResult();
    }

    public String getAnswerKeyId() {
        return getAnswerKeyDefinition().getId();
    }

    public int getAnswerKeyVersion() {
        return getAnswerKeyDefinition().getVersion();
    }

    public String getAnswerKeyName() {
        return getAnswerKeyDefinition().getName();
    }

    public OmrAnswerKeyDefinition getAnswerKeyDefinition() {
        return gradingResult.getAnswerKeyDefinition();
    }

    public double getAwardedPoints() {
        return gradingResult.getAwardedPoints();
    }

    public double getPossiblePoints() {
        return gradingResult.getPossiblePoints();
    }

    public double getAwardedPercentage() {
        return gradingResult.getAwardedPercentage();
    }

    public boolean requiresReview() {
        return gradingResult.requiresReview();
    }

    public boolean isFinal() {
        return gradingResult.isFinal();
    }

    public boolean belongsToStudent(
            String studentId
    ) {
        return student.hasStudentId(studentId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrGradingHistoryRecord)) {
            return false;
        }

        OmrGradingHistoryRecord that =
                (OmrGradingHistoryRecord) other;

        return historyRecordId.equals(
                that.historyRecordId
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(historyRecordId);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s[aluno=%s, leitura=%s, gabarito=%s@v%d, nota=%.2f%%]",
                historyRecordId,
                student.getStudentId(),
                getReadingId(),
                getAnswerKeyId(),
                getAnswerKeyVersion(),
                getAwardedPercentage()
        );
    }
}
