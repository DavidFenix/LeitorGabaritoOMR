package com.example.leitorgabaritoomr.presentation.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrQuestionGrade;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estado imutável e independente de Android da futura apresentação da
 * correção OMR.
 *
 * Não corrige respostas nem recalcula pontos. Apenas organiza um
 * OmrGradingResult já validado e usa o layout para traduzir os optionId
 * aceitos pelo gabarito em rótulos visíveis como A, B, C e D.
 */
public final class OmrGradingResultViewState {

    public enum OverallState {
        FINAL,
        REQUIRES_REVIEW,
        INCOMPLETE
    }

    public enum QuestionState {
        CORRECT,
        INCORRECT,
        BLANK,
        MULTIPLE,
        AMBIGUOUS,
        NOT_READY;

        public boolean isCorrect() {
            return this == CORRECT;
        }

        public boolean isFinal() {
            return this == CORRECT
                    || this == INCORRECT
                    || this == BLANK;
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
     * Uma linha lógica da lista de questões corrigidas.
     */
    public static final class QuestionItem {

        private final int position;
        private final String questionId;
        private final QuestionState state;

        private final List<String> relevantOptionLabels;
        private final List<String> acceptedOptionLabels;

        private final int confidencePercent;
        private final double awardedPoints;
        private final double possiblePoints;

        private QuestionItem(
                int position,
                String questionId,
                QuestionState state,
                List<String> relevantOptionLabels,
                List<String> acceptedOptionLabels,
                int confidencePercent,
                double awardedPoints,
                double possiblePoints
        ) {
            this.position = position;
            this.questionId = questionId;
            this.state = state;

            this.relevantOptionLabels =
                    immutableCopy(relevantOptionLabels);

            this.acceptedOptionLabels =
                    immutableCopy(acceptedOptionLabels);

            this.confidencePercent = confidencePercent;
            this.awardedPoints = awardedPoints;
            this.possiblePoints = possiblePoints;
        }

        private static List<String> immutableCopy(
                List<String> values
        ) {
            return Collections.unmodifiableList(
                    new ArrayList<>(values)
            );
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

        public List<String> getRelevantOptionLabels() {
            return relevantOptionLabels;
        }

        public String getSelectedOptionLabel() {
            if (state != QuestionState.CORRECT
                    && state != QuestionState.INCORRECT) {

                return null;
            }

            return relevantOptionLabels.get(0);
        }

        public List<String> getAcceptedOptionLabels() {
            return acceptedOptionLabels;
        }

        public int getConfidencePercent() {
            return confidencePercent;
        }

        public double getAwardedPoints() {
            return awardedPoints;
        }

        public double getPossiblePoints() {
            return possiblePoints;
        }

        public boolean isCorrect() {
            return state.isCorrect();
        }

        public boolean isFinal() {
            return state.isFinal();
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

    private final String answerKeyId;
    private final int answerKeyVersion;
    private final String answerKeyName;

    private final OverallState overallState;

    private final int questionCount;
    private final int correctCount;
    private final int incorrectCount;
    private final int blankCount;
    private final int multipleMarkCount;
    private final int ambiguousCount;
    private final int notReadyCount;
    private final int reviewRequiredCount;
    private final int finalQuestionCount;
    private final int unresolvedCount;

    private final double awardedPoints;
    private final double possiblePoints;
    private final double awardedPercentage;

    private final List<QuestionItem> questionItems;
    private final List<QuestionItem> reviewItems;

    private OmrGradingResultViewState(
            String readingId,
            long capturedAtEpochMillis,
            String layoutId,
            int layoutVersion,
            String layoutName,
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName,
            OverallState overallState,
            int questionCount,
            int correctCount,
            int incorrectCount,
            int blankCount,
            int multipleMarkCount,
            int ambiguousCount,
            int notReadyCount,
            int reviewRequiredCount,
            int finalQuestionCount,
            int unresolvedCount,
            double awardedPoints,
            double possiblePoints,
            double awardedPercentage,
            List<QuestionItem> questionItems,
            List<QuestionItem> reviewItems
    ) {
        this.readingId = readingId;
        this.capturedAtEpochMillis = capturedAtEpochMillis;

        this.layoutId = layoutId;
        this.layoutVersion = layoutVersion;
        this.layoutName = layoutName;

        this.answerKeyId = answerKeyId;
        this.answerKeyVersion = answerKeyVersion;
        this.answerKeyName = answerKeyName;

        this.overallState = overallState;

        this.questionCount = questionCount;
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.blankCount = blankCount;
        this.multipleMarkCount = multipleMarkCount;
        this.ambiguousCount = ambiguousCount;
        this.notReadyCount = notReadyCount;
        this.reviewRequiredCount = reviewRequiredCount;
        this.finalQuestionCount = finalQuestionCount;
        this.unresolvedCount = unresolvedCount;

        this.awardedPoints = awardedPoints;
        this.possiblePoints = possiblePoints;
        this.awardedPercentage = awardedPercentage;

        this.questionItems = immutableItemCopy(questionItems);
        this.reviewItems = immutableItemCopy(reviewItems);
    }

    public static OmrGradingResultViewState from(
            OmrGradingResult gradingResult,
            OmrLayoutDefinition layoutDefinition
    ) {
        requireInputs(gradingResult, layoutDefinition);
        validateLayoutIdentity(gradingResult, layoutDefinition);

        List<QuestionItem> questionItems =
                new ArrayList<>(
                        gradingResult.getQuestionCount()
                );

        List<QuestionItem> reviewItems =
                new ArrayList<>(
                        gradingResult.getReviewRequiredCount()
                );

        for (OmrQuestionGrade questionGrade
                : gradingResult.getQuestionGrades()) {

            QuestionItem item = createQuestionItem(
                    questionGrade,
                    layoutDefinition
            );

            questionItems.add(item);

            if (item.requiresReview()) {
                reviewItems.add(item);
            }
        }

        OmrReadingResult readingResult =
                gradingResult.getReadingResult();

        OmrAnswerKeyDefinition answerKeyDefinition =
                gradingResult.getAnswerKeyDefinition();

        OmrGradingResultViewState viewState =
                new OmrGradingResultViewState(
                        readingResult.getReadingId(),
                        readingResult.getCapturedAtEpochMillis(),
                        readingResult.getLayoutId(),
                        readingResult.getLayoutVersion(),
                        readingResult.getLayoutName(),
                        answerKeyDefinition.getId(),
                        answerKeyDefinition.getVersion(),
                        answerKeyDefinition.getName(),
                        resolveOverallState(gradingResult),
                        gradingResult.getQuestionCount(),
                        gradingResult.getCorrectCount(),
                        gradingResult.getIncorrectCount(),
                        gradingResult.getBlankCount(),
                        gradingResult.getMultipleMarkCount(),
                        gradingResult.getAmbiguousCount(),
                        gradingResult.getNotReadyCount(),
                        gradingResult.getReviewRequiredCount(),
                        gradingResult.getFinalQuestionCount(),
                        gradingResult.getUnresolvedCount(),
                        gradingResult.getAwardedPoints(),
                        gradingResult.getPossiblePoints(),
                        gradingResult.getAwardedPercentage(),
                        questionItems,
                        reviewItems
                );

        viewState.validateConsistency();

        return viewState;
    }

    private static QuestionItem createQuestionItem(
            OmrQuestionGrade questionGrade,
            OmrLayoutDefinition layoutDefinition
    ) {
        OmrQuestionDefinition layoutQuestion =
                layoutDefinition.findQuestionById(
                        questionGrade.getQuestionId()
                );

        if (layoutQuestion == null) {
            throw new IllegalArgumentException(
                    "Questão da correção ausente no layout: "
                            + questionGrade.getQuestionId()
            );
        }

        return new QuestionItem(
                questionGrade.getPosition(),
                questionGrade.getQuestionId(),
                mapQuestionState(questionGrade.getStatus()),
                readRelevantOptionLabels(questionGrade),
                resolveAcceptedOptionLabels(
                        questionGrade,
                        layoutQuestion
                ),
                (int) Math.round(
                        questionGrade.getConfidence() * 100.0
                ),
                questionGrade.getAwardedPoints(),
                questionGrade.getPossiblePoints()
        );
    }

    private static List<String> readRelevantOptionLabels(
            OmrQuestionGrade questionGrade
    ) {
        List<String> labels =
                new ArrayList<>(
                        questionGrade
                                .getRelevantOptions()
                                .size()
                );

        for (OmrQuestionResult.Option option
                : questionGrade.getRelevantOptions()) {

            labels.add(option.getLabel());
        }

        return labels;
    }

    private static List<String> resolveAcceptedOptionLabels(
            OmrQuestionGrade questionGrade,
            OmrQuestionDefinition layoutQuestion
    ) {
        List<String> labels =
                new ArrayList<>(
                        questionGrade
                                .getAcceptedOptionIds()
                                .size()
                );

        for (String acceptedOptionId
                : questionGrade.getAcceptedOptionIds()) {

            OmrOptionDefinition layoutOption =
                    layoutQuestion.findOptionById(
                            acceptedOptionId
                    );

            if (layoutOption == null) {
                throw new IllegalArgumentException(
                        "Alternativa aceita ausente no layout:"
                                + " questionId="
                                + questionGrade.getQuestionId()
                                + ", optionId="
                                + acceptedOptionId
                                + "."
                );
            }

            labels.add(layoutOption.getLabel());
        }

        return labels;
    }

    private static QuestionState mapQuestionState(
            OmrQuestionGrade.Status status
    ) {
        switch (status) {
            case CORRECT:
                return QuestionState.CORRECT;

            case INCORRECT:
                return QuestionState.INCORRECT;

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
                        "Status de correção não suportado: "
                                + status
                );
        }
    }

    private static OverallState resolveOverallState(
            OmrGradingResult gradingResult
    ) {
        if (!gradingResult.isComplete()) {
            return OverallState.INCOMPLETE;
        }

        if (gradingResult.requiresReview()) {
            return OverallState.REQUIRES_REVIEW;
        }

        return OverallState.FINAL;
    }

    private static void requireInputs(
            OmrGradingResult gradingResult,
            OmrLayoutDefinition layoutDefinition
    ) {
        if (gradingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da correção é obrigatório."
            );
        }

        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "A definição do layout é obrigatória."
            );
        }
    }

    private static void validateLayoutIdentity(
            OmrGradingResult gradingResult,
            OmrLayoutDefinition layoutDefinition
    ) {
        OmrReadingResult readingResult =
                gradingResult.getReadingResult();

        boolean sameLayoutId = layoutDefinition.getId().equals(
                readingResult.getLayoutId()
        );

        boolean sameLayoutVersion =
                layoutDefinition.getVersion()
                        == readingResult.getLayoutVersion();

        if (!sameLayoutId || !sameLayoutVersion) {
            throw new IllegalArgumentException(
                    "A correção não pertence ao layout informado:"
                            + " layout="
                            + layoutDefinition.getId()
                            + "@v"
                            + layoutDefinition.getVersion()
                            + ", correção="
                            + readingResult.getLayoutId()
                            + "@v"
                            + readingResult.getLayoutVersion()
                            + "."
            );
        }
    }

    private static List<QuestionItem> immutableItemCopy(
            List<QuestionItem> items
    ) {
        return Collections.unmodifiableList(
                new ArrayList<>(items)
        );
    }

    private void validateConsistency() {
        if (questionItems.size() != questionCount) {
            throw new IllegalStateException(
                    "A quantidade de linhas divergiu da correção."
            );
        }

        if (reviewItems.size() != reviewRequiredCount) {
            throw new IllegalStateException(
                    "A quantidade de revisões divergiu da correção."
            );
        }

        int classifiedCount =
                correctCount
                        + incorrectCount
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

        if (finalQuestionCount + unresolvedCount
                != questionCount) {

            throw new IllegalStateException(
                    "Questões finais e não resolvidas não fecham"
                            + " a quantidade total."
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

    public String getAnswerKeyId() {
        return answerKeyId;
    }

    public int getAnswerKeyVersion() {
        return answerKeyVersion;
    }

    public String getAnswerKeyName() {
        return answerKeyName;
    }

    public OverallState getOverallState() {
        return overallState;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getIncorrectCount() {
        return incorrectCount;
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

    public int getFinalQuestionCount() {
        return finalQuestionCount;
    }

    public int getUnresolvedCount() {
        return unresolvedCount;
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

    public int getRoundedAwardedPercentage() {
        return (int) Math.round(awardedPercentage);
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
        return overallState != OverallState.INCOMPLETE;
    }

    public boolean requiresReview() {
        return reviewRequiredCount > 0;
    }

    public boolean isFinal() {
        return overallState == OverallState.FINAL;
    }
}
