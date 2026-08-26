package com.example.leitorgabaritoomr.presentation.grading;

import com.example.leitorgabaritoomr.application.grading.OmrManualAnswerKeyDraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estado visual imutável e independente de Android do editor manual de
 * gabarito oficial.
 *
 * Não altera o rascunho nem cria regras novas. Apenas organiza as
 * questões, alternativas, seleções e o progresso em dados próprios para
 * a futura tela.
 */
public final class OmrManualAnswerKeyViewState {

    public static final class OptionItem {

        private final String optionId;
        private final String label;
        private final boolean selected;

        private OptionItem(
                String optionId,
                String label,
                boolean selected
        ) {
            this.optionId = optionId;
            this.label = label;
            this.selected = selected;
        }

        public String getOptionId() {
            return optionId;
        }

        public String getLabel() {
            return label;
        }

        public boolean isSelected() {
            return selected;
        }
    }

    public static final class QuestionItem {

        private final int position;
        private final String questionId;
        private final List<OptionItem> options;
        private final boolean answered;

        private QuestionItem(
                int position,
                String questionId,
                List<OptionItem> options,
                boolean answered
        ) {
            this.position = position;
            this.questionId = questionId;
            this.options = Collections.unmodifiableList(
                    new ArrayList<>(options)
            );
            this.answered = answered;
        }

        public int getPosition() {
            return position;
        }

        public String getQuestionId() {
            return questionId;
        }

        public List<OptionItem> getOptions() {
            return options;
        }

        public int getOptionCount() {
            return options.size();
        }

        public boolean isAnswered() {
            return answered;
        }

        public OptionItem getSelectedOption() {
            for (OptionItem option : options) {
                if (option.isSelected()) {
                    return option;
                }
            }

            return null;
        }

        public String getSelectedOptionId() {
            OptionItem selectedOption = getSelectedOption();

            return selectedOption == null
                    ? null
                    : selectedOption.getOptionId();
        }

        public String getSelectedOptionLabel() {
            OptionItem selectedOption = getSelectedOption();

            return selectedOption == null
                    ? null
                    : selectedOption.getLabel();
        }

        public OptionItem findOptionById(
                String optionId
        ) {
            if (optionId == null) {
                return null;
            }

            String normalizedOptionId = optionId.trim();

            for (OptionItem option : options) {
                if (option.getOptionId().equals(
                        normalizedOptionId
                )) {
                    return option;
                }
            }

            return null;
        }
    }

    private final String layoutId;
    private final int layoutVersion;
    private final String layoutName;

    private final int questionCount;
    private final int answeredCount;
    private final int remainingCount;
    private final int progressPercent;
    private final int firstUnansweredPosition;
    private final boolean complete;

    private final List<QuestionItem> questionItems;

    private OmrManualAnswerKeyViewState(
            String layoutId,
            int layoutVersion,
            String layoutName,
            int questionCount,
            int answeredCount,
            int remainingCount,
            int progressPercent,
            int firstUnansweredPosition,
            boolean complete,
            List<QuestionItem> questionItems
    ) {
        this.layoutId = layoutId;
        this.layoutVersion = layoutVersion;
        this.layoutName = layoutName;

        this.questionCount = questionCount;
        this.answeredCount = answeredCount;
        this.remainingCount = remainingCount;
        this.progressPercent = progressPercent;
        this.firstUnansweredPosition = firstUnansweredPosition;
        this.complete = complete;

        this.questionItems =
                Collections.unmodifiableList(
                        new ArrayList<>(questionItems)
                );
    }

    public static OmrManualAnswerKeyViewState from(
            OmrManualAnswerKeyDraft draft
    ) {
        if (draft == null) {
            throw new IllegalArgumentException(
                    "O rascunho do gabarito é obrigatório."
            );
        }

        List<QuestionItem> questionItems =
                new ArrayList<>(draft.getQuestionCount());

        int firstUnansweredPosition = 0;

        for (OmrManualAnswerKeyDraft.QuestionDraft question
                : draft.getQuestions()) {

            QuestionItem item = createQuestionItem(question);
            questionItems.add(item);

            if (!item.isAnswered()
                    && firstUnansweredPosition == 0) {

                firstUnansweredPosition = item.getPosition();
            }
        }

        int progressPercent = (int) Math.round(
                draft.getAnsweredCount()
                        * 100.0
                        / draft.getQuestionCount()
        );

        OmrManualAnswerKeyViewState viewState =
                new OmrManualAnswerKeyViewState(
                        draft.getLayoutId(),
                        draft.getLayoutVersion(),
                        draft.getLayoutName(),
                        draft.getQuestionCount(),
                        draft.getAnsweredCount(),
                        draft.getRemainingCount(),
                        progressPercent,
                        firstUnansweredPosition,
                        draft.isComplete(),
                        questionItems
                );

        viewState.validateConsistency();

        return viewState;
    }

    private static QuestionItem createQuestionItem(
            OmrManualAnswerKeyDraft.QuestionDraft question
    ) {
        List<OptionItem> optionItems =
                new ArrayList<>(question.getOptionCount());

        String selectedOptionId =
                question.getSelectedOptionId();

        for (OmrManualAnswerKeyDraft.OptionChoice choice
                : question.getOptionChoices()) {

            optionItems.add(
                    new OptionItem(
                            choice.getOptionId(),
                            choice.getLabel(),
                            choice.getOptionId().equals(
                                    selectedOptionId
                            )
                    )
            );
        }

        return new QuestionItem(
                question.getPosition(),
                question.getQuestionId(),
                optionItems,
                question.isAnswered()
        );
    }

    private void validateConsistency() {
        if (questionItems.size() != questionCount) {
            throw new IllegalStateException(
                    "A quantidade de linhas divergiu do rascunho."
            );
        }

        if (answeredCount + remainingCount != questionCount) {
            throw new IllegalStateException(
                    "Questões respondidas e restantes não fecham"
                            + " a quantidade total."
            );
        }

        int calculatedAnsweredCount = 0;

        for (QuestionItem question : questionItems) {
            int selectedCount = 0;

            for (OptionItem option : question.getOptions()) {
                if (option.isSelected()) {
                    selectedCount++;
                }
            }

            if (selectedCount > 1) {
                throw new IllegalStateException(
                        "Uma questão do editor possui mais de uma"
                                + " alternativa selecionada."
                );
            }

            if (question.isAnswered() != (selectedCount == 1)) {
                throw new IllegalStateException(
                        "O estado visual da questão divergiu"
                                + " da alternativa selecionada."
                );
            }

            if (question.isAnswered()) {
                calculatedAnsweredCount++;
            }
        }

        if (calculatedAnsweredCount != answeredCount) {
            throw new IllegalStateException(
                    "A quantidade visual de respostas divergiu"
                            + " do rascunho."
            );
        }

        if (complete != (remainingCount == 0)) {
            throw new IllegalStateException(
                    "O estado de conclusão divergiu do progresso."
            );
        }

        if (complete && firstUnansweredPosition != 0) {
            throw new IllegalStateException(
                    "Um gabarito completo não pode indicar"
                            + " questão pendente."
            );
        }

        if (!complete && firstUnansweredPosition == 0) {
            throw new IllegalStateException(
                    "Um gabarito incompleto deve indicar"
                            + " a primeira questão pendente."
            );
        }
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

    public int getQuestionCount() {
        return questionCount;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public int getFirstUnansweredPosition() {
        return firstUnansweredPosition;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean canSave() {
        return complete;
    }

    public List<QuestionItem> getQuestionItems() {
        return questionItems;
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

    public QuestionItem findQuestionById(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        String normalizedQuestionId = questionId.trim();

        for (QuestionItem question : questionItems) {
            if (question.getQuestionId().equals(
                    normalizedQuestionId
            )) {
                return question;
            }
        }

        return null;
    }
}
