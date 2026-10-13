package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fotografia consolidada dos resultados mais recentes de uma aplicação. */
public final class OmrAssessmentResultReport {

    public enum Status {
        PENDING,
        CORRECTED,
        MISSING_HISTORY
    }

    public static final class Row {
        private final OmrAssessmentParticipant participant;
        private final OmrAssessmentAttempt latestAttempt;
        private final OmrGradingHistoryRecord historyRecord;

        public Row(
                OmrAssessmentParticipant participant,
                OmrAssessmentAttempt latestAttempt,
                OmrGradingHistoryRecord historyRecord
        ) {
            if (participant == null) {
                throw new IllegalArgumentException(
                        "O participante do resultado é obrigatório."
                );
            }
            if (latestAttempt == null && historyRecord != null) {
                throw new IllegalArgumentException(
                        "Um histórico não pode existir sem tentativa."
                );
            }
            if (latestAttempt != null
                    && !latestAttempt.belongsToParticipant(
                    participant.getApplicationId(),
                    participant.getStudentId()
            )) {
                throw new IllegalArgumentException(
                        "A tentativa não pertence ao participante."
                );
            }
            if (historyRecord != null
                    && (!latestAttempt.referencesHistoryRecord(
                    historyRecord.getHistoryRecordId()
            ) || !historyRecord.belongsToStudent(
                    participant.getStudentId()
            ))) {
                throw new IllegalArgumentException(
                        "O histórico não pertence à tentativa e ao aluno."
                );
            }
            this.participant = participant;
            this.latestAttempt = latestAttempt;
            this.historyRecord = historyRecord;
        }

        public OmrAssessmentParticipant getParticipant() {
            return participant;
        }

        public OmrAssessmentAttempt getLatestAttempt() {
            return latestAttempt;
        }

        public OmrGradingHistoryRecord getHistoryRecord() {
            return historyRecord;
        }

        public Status getStatus() {
            if (latestAttempt == null) {
                return Status.PENDING;
            }
            return historyRecord == null
                    ? Status.MISSING_HISTORY
                    : Status.CORRECTED;
        }

        public boolean isCorrected() {
            return getStatus() == Status.CORRECTED;
        }

        public int getRevision() {
            return latestAttempt == null
                    ? 0 : latestAttempt.getRevision();
        }

        public long getRecordedAtEpochMillis() {
            return latestAttempt == null
                    ? 0L : latestAttempt.getRecordedAtEpochMillis();
        }

        public double getAwardedPoints() {
            return historyRecord == null
                    ? 0.0 : historyRecord.getAwardedPoints();
        }

        public double getPossiblePoints() {
            return historyRecord == null
                    ? 0.0 : historyRecord.getPossiblePoints();
        }

        public double getAwardedPercentage() {
            return historyRecord == null
                    ? 0.0 : historyRecord.getAwardedPercentage();
        }

        public int getCorrectCount() {
            return historyRecord == null
                    ? 0
                    : historyRecord.getGradingResult().getCorrectCount();
        }

        public int getIncorrectCount() {
            return historyRecord == null
                    ? 0
                    : historyRecord.getGradingResult().getIncorrectCount();
        }

        public int getBlankCount() {
            return historyRecord == null
                    ? 0
                    : historyRecord.getGradingResult().getBlankCount();
        }

        public int getReviewRequiredCount() {
            return historyRecord == null
                    ? 0
                    : historyRecord.getGradingResult()
                    .getReviewRequiredCount();
        }

        public boolean requiresReview() {
            return historyRecord != null
                    && historyRecord.requiresReview();
        }

        public boolean isFinal() {
            return historyRecord != null && historyRecord.isFinal();
        }
    }

    private final OmrAssessmentApplication application;
    private final OmrAssessmentDefinition assessment;
    private final String frozenClassName;
    private final long generatedAtEpochMillis;
    private final List<Row> rows;
    private final int correctedCount;
    private final int pendingCount;
    private final int missingHistoryCount;
    private final double averagePercentage;

    public OmrAssessmentResultReport(
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            String frozenClassName,
            long generatedAtEpochMillis,
            List<Row> rows
    ) {
        if (application == null || assessment == null) {
            throw new IllegalArgumentException(
                    "A aplicação e a prova são obrigatórias."
            );
        }
        if (frozenClassName == null
                || frozenClassName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome congelado da turma é obrigatório."
            );
        }
        if (generatedAtEpochMillis <= 0L || rows == null) {
            throw new IllegalArgumentException(
                    "O instante e as linhas do relatório são obrigatórios."
            );
        }

        this.application = application;
        this.assessment = assessment;
        this.frozenClassName = frozenClassName.trim();
        this.generatedAtEpochMillis = generatedAtEpochMillis;
        if (!application.usesAssessment(
                assessment.getAssessmentId(),
                assessment.getVersion()
        )) {
            throw new IllegalArgumentException(
                    "A prova não corresponde à aplicação."
            );
        }
        Set<String> studentIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (Row row : rows) {
            if (row == null) {
                throw new IllegalArgumentException(
                        "O relatório não aceita linhas nulas."
                );
            }
            OmrAssessmentParticipant participant = row.getParticipant();
            if (!participant.belongsToApplication(
                    application.getApplicationId()
            ) || !frozenClassName.trim().equals(
                    participant.getClassName()
            )) {
                throw new IllegalArgumentException(
                        "A linha não pertence à chamada congelada."
                );
            }
            if (!studentIds.add(participant.getStudentId())
                    || !positions.add(participant.getRosterPosition())) {
                throw new IllegalArgumentException(
                        "A chamada possui aluno ou posição repetida."
                );
            }
        }
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));

        int corrected = 0;
        int pending = 0;
        int missing = 0;
        double accumulatedPercentage = 0.0;
        for (Row row : this.rows) {
            switch (row.getStatus()) {
                case CORRECTED:
                    corrected++;
                    accumulatedPercentage += row.getAwardedPercentage();
                    break;
                case PENDING:
                    pending++;
                    break;
                case MISSING_HISTORY:
                    missing++;
                    break;
                default:
                    throw new IllegalStateException("Status desconhecido.");
            }
        }
        correctedCount = corrected;
        pendingCount = pending;
        missingHistoryCount = missing;
        averagePercentage = corrected == 0
                ? 0.0 : accumulatedPercentage / corrected;
    }

    public OmrAssessmentApplication getApplication() {
        return application;
    }

    public OmrAssessmentDefinition getAssessment() {
        return assessment;
    }

    public String getFrozenClassName() {
        return frozenClassName;
    }

    public long getGeneratedAtEpochMillis() {
        return generatedAtEpochMillis;
    }

    public List<Row> getRows() {
        return rows;
    }

    public int getParticipantCount() {
        return rows.size();
    }

    public int getCorrectedCount() {
        return correctedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public int getMissingHistoryCount() {
        return missingHistoryCount;
    }

    public double getAveragePercentage() {
        return averagePercentage;
    }

    public boolean hasDataIntegrityIssue() {
        return missingHistoryCount > 0;
    }
}
