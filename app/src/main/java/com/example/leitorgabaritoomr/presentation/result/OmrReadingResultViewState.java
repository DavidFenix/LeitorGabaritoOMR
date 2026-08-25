package com.example.leitorgabaritoomr.presentation.result;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estado imutável e independente de Android da futura tela de
 * resultado da leitura OMR.
 *
 * Não interpreta evidências e não altera decisões. Apenas organiza
 * o OmrReadingResult em dados semânticos próprios para apresentação.
 */
public final class OmrReadingResultViewState {

    public enum OverallState {
        COMPLETED,
        COMPLETED_WITH_REVIEW,
        INCOMPLETE
    }

    public enum QuestionState {
        ANSWERED,
        BLANK,
        MULTIPLE,
        AMBIGUOUS,
        NOT_READY;

        public boolean hasAnswer() {
            return this == ANSWERED;
        }

        public boolean requiresReview() {
            return this == MULTIPLE
                    || this == AMBIGUOUS;
        }

        public boolean isReady() {
            return this != NOT_READY;
        }
    }

    /**
     * Uma linha lógica da lista de questões.
     */
    public static final class QuestionItem {

        private final int position;
        private final String questionId;
        private final QuestionState state;

        private final List<String>
                relevantOptionLabels;

        private final int confidencePercent;

        private QuestionItem(
                int position,
                String questionId,
                QuestionState state,
                List<String> relevantOptionLabels,
                int confidencePercent
        ) {
            this.position = position;
            this.questionId = questionId;
            this.state = state;
            this.relevantOptionLabels =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    relevantOptionLabels
                            )
                    );
            this.confidencePercent =
                    confidencePercent;
        }

        public int getPosition() {
            return position;
        }

        public String getQuestionId() {
            return questionId;
        }

        public QuestionState getState() {
            return state;
        }

        public List<String>
        getRelevantOptionLabels() {
            return relevantOptionLabels;
        }

        public String getSelectedOptionLabel() {
            if (!state.hasAnswer()) {
                return null;
            }

            return relevantOptionLabels.get(0);
        }

        public int getConfidencePercent() {
            return confidencePercent;
        }

        public boolean hasAnswer() {
            return state.hasAnswer();
        }

        public boolean requiresReview() {
            return state.requiresReview();
        }

        public boolean isReady() {
            return state.isReady();
        }
    }

    private final String readingId;
    private final long capturedAtEpochMillis;

    private final String layoutId;
    private final int layoutVersion;
    private final String layoutName;

    private final OverallState overallState;

    private final int questionCount;
    private final int singleMarkCount;
    private final int blankCount;
    private final int multipleMarkCount;
    private final int ambiguousCount;
    private final int notReadyCount;
    private final int reviewRequiredCount;

    private final List<QuestionItem> questionItems;
    private final List<QuestionItem> reviewItems;

    private OmrReadingResultViewState(
            String readingId,
            long capturedAtEpochMillis,
            String layoutId,
            int layoutVersion,
            String layoutName,
            OverallState overallState,
            int questionCount,
            int singleMarkCount,
            int blankCount,
            int multipleMarkCount,
            int ambiguousCount,
            int notReadyCount,
            int reviewRequiredCount,
            List<QuestionItem> questionItems,
            List<QuestionItem> reviewItems
    ) {
        this.readingId = readingId;
        this.capturedAtEpochMillis =
                capturedAtEpochMillis;
        this.layoutId = layoutId;
        this.layoutVersion = layoutVersion;
        this.layoutName = layoutName;
        this.overallState = overallState;
        this.questionCount = questionCount;
        this.singleMarkCount = singleMarkCount;
        this.blankCount = blankCount;
        this.multipleMarkCount = multipleMarkCount;
        this.ambiguousCount = ambiguousCount;
        this.notReadyCount = notReadyCount;
        this.reviewRequiredCount =
                reviewRequiredCount;
        this.questionItems =
                Collections.unmodifiableList(
                        new ArrayList<>(questionItems)
                );
        this.reviewItems =
                Collections.unmodifiableList(
                        new ArrayList<>(reviewItems)
                );
    }

    public static OmrReadingResultViewState from(
            OmrReadingResult result
    ) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "O resultado da leitura é obrigatório."
            );
        }

        List<QuestionItem> questionItems =
                new ArrayList<>(
                        result.getQuestionCount()
                );

        List<QuestionItem> reviewItems =
                new ArrayList<>(
                        result.getReviewRequiredCount()
                );

        for (OmrQuestionResult questionResult
                : result.getQuestionResults()) {

            QuestionItem item =
                    createQuestionItem(
                            questionResult
                    );

            questionItems.add(item);

            if (item.requiresReview()) {
                reviewItems.add(item);
            }
        }

        OmrReadingResultViewState viewState =
                new OmrReadingResultViewState(
                        result.getReadingId(),
                        result.getCapturedAtEpochMillis(),
                        result.getLayoutId(),
                        result.getLayoutVersion(),
                        result.getLayoutName(),
                        resolveOverallState(result),
                        result.getQuestionCount(),
                        result.getSingleMarkCount(),
                        result.getBlankCount(),
                        result.getMultipleMarkCount(),
                        result.getAmbiguousCount(),
                        result.getNotReadyCount(),
                        result.getReviewRequiredCount(),
                        questionItems,
                        reviewItems
                );

        viewState.validateConsistency();

        return viewState;
    }

    private static QuestionItem createQuestionItem(
            OmrQuestionResult questionResult
    ) {
        List<String> optionLabels =
                new ArrayList<>(
                        questionResult
                        .getRelevantOptions()
                        .size()
                );

        for (OmrQuestionResult.Option option
                : questionResult
                .getRelevantOptions()) {

            optionLabels.add(option.getLabel());
        }

        int confidencePercent =
                (int) Math.round(
                        questionResult.getConfidence()
                                * 100.0
                );

        return new QuestionItem(
                questionResult.getPosition(),
                questionResult.getQuestionId(),
                mapQuestionState(
                        questionResult.getStatus()
                ),
                optionLabels,
                confidencePercent
        );
    }

    private static OverallState resolveOverallState(
            OmrReadingResult result
    ) {
        if (!result.isComplete()) {
            return OverallState.INCOMPLETE;
        }

        if (result.requiresReview()) {
            return OverallState.COMPLETED_WITH_REVIEW;
        }

        return OverallState.COMPLETED;
    }

    private static QuestionState mapQuestionState(
            OmrQuestionResult.Status status
    ) {
        switch (status) {
            case SINGLE_MARK:
                return QuestionState.ANSWERED;

            case BLANK:
                return QuestionState.BLANK;

            case MULTIPLE_MARKS:
                return QuestionState.MULTIPLE;

            case AMBIGUOUS:
                return QuestionState.AMBIGUOUS;

            case NOT_READY:
                return QuestionState.NOT_READY;

            default:
                throw new IllegalStateException(
                        "Status de questão não suportado: "
                                + status
                );
        }
    }

    private void validateConsistency() {
        if (questionItems.size() != questionCount) {
            throw new IllegalStateException(
                    "A quantidade de linhas divergiu da leitura."
            );
        }

        if (reviewItems.size()
                != reviewRequiredCount) {

            throw new IllegalStateException(
                    "A quantidade de revisões divergiu da leitura."
            );
        }

        int classifiedCount =
                singleMarkCount
                        + blankCount
                        + multipleMarkCount
                        + ambiguousCount
                        + notReadyCount;

        if (classifiedCount != questionCount) {
            throw new IllegalStateException(
                    "Os totais classificados não fecham"
                            + " a quantidade de questões."
            );
        }
    }

    public String getReadingId() {
        return readingId;
    }

    public long getCapturedAtEpochMillis() {
        return capturedAtEpochMillis;
    }

    public String getLayoutId() {
        return layoutId;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public String getLayoutName() {
        return layoutName;
    }

    public OverallState getOverallState() {
        return overallState;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getSingleMarkCount() {
        return singleMarkCount;
    }

    public int getBlankCount() {
        return blankCount;
    }

    public int getMultipleMarkCount() {
        return multipleMarkCount;
    }

    public int getAmbiguousCount() {
        return ambiguousCount;
    }

    public int getNotReadyCount() {
        return notReadyCount;
    }

    public int getReviewRequiredCount() {
        return reviewRequiredCount;
    }

    public List<QuestionItem> getQuestionItems() {
        return questionItems;
    }

    public List<QuestionItem> getReviewItems() {
        return reviewItems;
    }

    public QuestionItem getQuestionAtPosition(
            int position
    ) {
        if (position <= 0
                || position > questionItems.size()) {

            return null;
        }

        return questionItems.get(position - 1);
    }

    public boolean isComplete() {
        return overallState
                != OverallState.INCOMPLETE;
    }

    public boolean requiresReview() {
        return reviewRequiredCount > 0;
    }
}
