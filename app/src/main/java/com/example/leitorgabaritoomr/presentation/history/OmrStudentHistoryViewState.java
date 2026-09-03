package com.example.leitorgabaritoomr.presentation.history;

import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado visual imutavel e independente de Android do historico de um aluno.
 *
 * Cada item preserva a correcao completa para permitir a abertura posterior
 * dos detalhes. Os registros sao apresentados do armazenamento mais recente
 * para o mais antigo. Empates preservam a ordem recebida do repositorio.
 */
public final class OmrStudentHistoryViewState {

    /**
     * Uma linha logica do historico.
     */
    public static final class HistoryItem {

        private final String historyRecordId;
        private final String readingId;

        private final long storedAtEpochMillis;
        private final long capturedAtEpochMillis;

        private final String answerKeyId;
        private final int answerKeyVersion;
        private final String answerKeyName;

        private final double awardedPoints;
        private final double possiblePoints;
        private final double awardedPercentage;

        private final boolean requiresReview;
        private final boolean finalResult;

        private final OmrStudentIdentity studentSnapshot;
        private final OmrGradingResult gradingResult;

        private HistoryItem(
                OmrGradingHistoryRecord record
        ) {
            historyRecordId = record.getHistoryRecordId();
            readingId = record.getReadingId();

            storedAtEpochMillis =
                    record.getStoredAtEpochMillis();

            capturedAtEpochMillis =
                    record.getCapturedAtEpochMillis();

            answerKeyId = record.getAnswerKeyId();
            answerKeyVersion = record.getAnswerKeyVersion();
            answerKeyName = record.getAnswerKeyName();

            awardedPoints = record.getAwardedPoints();
            possiblePoints = record.getPossiblePoints();
            awardedPercentage = record.getAwardedPercentage();

            requiresReview = record.requiresReview();
            finalResult = record.isFinal();

            studentSnapshot = record.getStudent();
            gradingResult = record.getGradingResult();
        }

        public String getHistoryRecordId() {
            return historyRecordId;
        }

        public String getReadingId() {
            return readingId;
        }

        public long getStoredAtEpochMillis() {
            return storedAtEpochMillis;
        }

        public long getCapturedAtEpochMillis() {
            return capturedAtEpochMillis;
        }

        public String getAnswerKeyId() {
            return answerKeyId;
        }

        public int getAnswerKeyVersion() {
            return answerKeyVersion;
        }

        public String getAnswerKeyName() {
            return answerKeyName;
        }

        public double getAwardedPoints() {
            return awardedPoints;
        }

        public double getPossiblePoints() {
            return possiblePoints;
        }

        public double getAwardedPercentage() {
            return awardedPercentage;
        }

        public boolean requiresReview() {
            return requiresReview;
        }

        public boolean isFinal() {
            return finalResult;
        }

        public boolean isPending() {
            return !finalResult && !requiresReview;
        }

        public OmrStudentIdentity getStudentSnapshot() {
            return studentSnapshot;
        }

        public OmrGradingResult getGradingResult() {
            return gradingResult;
        }

        public boolean hasHistoryRecordId(
                String candidateId
        ) {
            return candidateId != null
                    && historyRecordId.equals(
                    candidateId.trim()
            );
        }
    }

    private static final Comparator<HistoryItem>
            NEWEST_FIRST =
            (first, second) -> Long.compare(
                    second.getStoredAtEpochMillis(),
                    first.getStoredAtEpochMillis()
            );

    private final OmrStudentIdentity student;
    private final List<HistoryItem> historyItems;

    private final int finalResultCount;
    private final int reviewRequiredCount;
    private final int pendingCount;

    private OmrStudentHistoryViewState(
            OmrStudentIdentity student,
            List<HistoryItem> historyItems,
            int finalResultCount,
            int reviewRequiredCount,
            int pendingCount
    ) {
        this.student = student;

        this.historyItems =
                Collections.unmodifiableList(
                        new ArrayList<>(historyItems)
                );

        this.finalResultCount = finalResultCount;
        this.reviewRequiredCount = reviewRequiredCount;
        this.pendingCount = pendingCount;
    }

    public static OmrStudentHistoryViewState from(
            OmrStudentIdentity student,
            List<OmrGradingHistoryRecord> records
    ) {
        if (student == null) {
            throw new IllegalArgumentException(
                    "O aluno e obrigatorio."
            );
        }

        if (records == null) {
            throw new IllegalArgumentException(
                    "A lista de historico e obrigatoria."
            );
        }

        List<HistoryItem> items =
                new ArrayList<>(records.size());

        Set<String> historyRecordIds =
                new HashSet<>(records.size());

        Set<String> readingIds =
                new HashSet<>(records.size());

        int finalCount = 0;
        int reviewCount = 0;
        int unresolvedCount = 0;

        for (OmrGradingHistoryRecord record : records) {
            if (record == null) {
                throw new IllegalArgumentException(
                        "O historico nao pode conter valores nulos."
                );
            }

            if (!record.belongsToStudent(
                    student.getStudentId()
            )) {
                throw new IllegalArgumentException(
                        "O historico possui resultado de outro aluno: "
                                + record.getHistoryRecordId()
                );
            }

            if (!historyRecordIds.add(
                    record.getHistoryRecordId()
            )) {
                throw new IllegalArgumentException(
                        "O historico possui registro repetido: "
                                + record.getHistoryRecordId()
                );
            }

            if (!readingIds.add(record.getReadingId())) {
                throw new IllegalArgumentException(
                        "O historico possui leitura repetida: "
                                + record.getReadingId()
                );
            }

            HistoryItem item = new HistoryItem(record);
            items.add(item);

            if (item.isFinal()) {
                finalCount++;
            } else if (item.requiresReview()) {
                reviewCount++;
            } else {
                unresolvedCount++;
            }
        }

        Collections.sort(items, NEWEST_FIRST);

        OmrStudentHistoryViewState viewState =
                new OmrStudentHistoryViewState(
                        student,
                        items,
                        finalCount,
                        reviewCount,
                        unresolvedCount
                );

        viewState.validateConsistency();
        return viewState;
    }

    private void validateConsistency() {
        int classifiedCount =
                finalResultCount
                        + reviewRequiredCount
                        + pendingCount;

        if (classifiedCount != historyItems.size()) {
            throw new IllegalStateException(
                    "A contagem do historico esta inconsistente."
            );
        }

        for (int index = 1;
             index < historyItems.size();
             index++) {

            long previousStoredAt =
                    historyItems
                            .get(index - 1)
                            .getStoredAtEpochMillis();

            long currentStoredAt =
                    historyItems
                            .get(index)
                            .getStoredAtEpochMillis();

            if (previousStoredAt < currentStoredAt) {
                throw new IllegalStateException(
                        "O historico nao esta ordenado."
                );
            }
        }
    }

    public OmrStudentIdentity getStudent() {
        return student;
    }

    public List<HistoryItem> getHistoryItems() {
        return historyItems;
    }

    public int getResultCount() {
        return historyItems.size();
    }

    public int getFinalResultCount() {
        return finalResultCount;
    }

    public int getReviewRequiredCount() {
        return reviewRequiredCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public boolean isEmpty() {
        return historyItems.isEmpty();
    }

    public HistoryItem getLatestItemOrNull() {
        return historyItems.isEmpty()
                ? null
                : historyItems.get(0);
    }

    public HistoryItem findItemOrNull(
            String historyRecordId
    ) {
        if (historyRecordId == null) {
            return null;
        }

        for (HistoryItem item : historyItems) {
            if (item.hasHistoryRecordId(historyRecordId)) {
                return item;
            }
        }

        return null;
    }
}
