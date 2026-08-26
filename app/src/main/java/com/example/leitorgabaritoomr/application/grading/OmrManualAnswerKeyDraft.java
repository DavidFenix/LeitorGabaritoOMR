package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Rascunho imutável de um gabarito oficial digitado manualmente.
 *
 * As questões e alternativas são extraídas do layout. Portanto, o
 * rascunho não pressupõe A-D, A-E, quantidade fixa de questões,
 * numeração sequencial ou uma geometria específica de folha.
 *
 * Cada alteração devolve uma nova instância, facilitando restauração de
 * estado, testes e futura persistência sem efeitos colaterais.
 */
public final class OmrManualAnswerKeyDraft
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Alternativa disponível para seleção na entrada manual.
     */
    public static final class OptionChoice
            implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String optionId;
        private final String label;

        private OptionChoice(
                String optionId,
                String label
        ) {
            this.optionId = requireText(
                    "optionId",
                    optionId
            );

            this.label = requireText(
                    "label",
                    label
            );
        }

        public String getOptionId() {
            return optionId;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof OptionChoice)) {
                return false;
            }

            OptionChoice that = (OptionChoice) other;

            return optionId.equals(that.optionId)
                    && label.equals(that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(optionId, label);
        }

        @Override
        public String toString() {
            return label + "[" + optionId + "]";
        }
    }

    /**
     * Uma linha do editor manual.
     */
    public static final class QuestionDraft
            implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int position;
        private final String questionId;
        private final List<OptionChoice> optionChoices;
        private final String selectedOptionId;

        private QuestionDraft(
                int position,
                String questionId,
                List<OptionChoice> optionChoices,
                String selectedOptionId
        ) {
            if (position <= 0) {
                throw new IllegalArgumentException(
                        "A posição da questão deve iniciar em 1."
                );
            }

            if (optionChoices == null
                    || optionChoices.isEmpty()) {

                throw new IllegalArgumentException(
                        "A questão deve possuir pelo menos uma"
                                + " alternativa disponível."
                );
            }

            this.position = position;
            this.questionId = requireText(
                    "questionId",
                    questionId
            );

            this.optionChoices =
                    Collections.unmodifiableList(
                            new ArrayList<>(optionChoices)
                    );

            if (selectedOptionId != null
                    && findOptionById(selectedOptionId) == null) {

                throw new IllegalArgumentException(
                        "A alternativa selecionada não pertence"
                                + " à questão "
                                + this.questionId
                                + ": "
                                + selectedOptionId
                                + "."
                );
            }

            this.selectedOptionId = selectedOptionId;
        }

        private QuestionDraft withSelectedOption(
                String optionId
        ) {
            String normalizedOptionId = requireText(
                    "optionId",
                    optionId
            );

            if (findOptionById(normalizedOptionId) == null) {
                throw new IllegalArgumentException(
                        "A alternativa não pertence à questão "
                                + questionId
                                + ": "
                                + normalizedOptionId
                                + "."
                );
            }

            if (normalizedOptionId.equals(selectedOptionId)) {
                return this;
            }

            return new QuestionDraft(
                    position,
                    questionId,
                    optionChoices,
                    normalizedOptionId
            );
        }

        private QuestionDraft withSelectedLabel(
                String optionLabel
        ) {
            String normalizedLabel = requireText(
                    "optionLabel",
                    optionLabel
            );

            OptionChoice matchingChoice = null;

            for (OptionChoice choice : optionChoices) {
                if (!choice.getLabel().equalsIgnoreCase(
                        normalizedLabel
                )) {
                    continue;
                }

                if (matchingChoice != null) {
                    throw new IllegalArgumentException(
                            "Rótulo de alternativa ambíguo"
                                    + " na questão "
                                    + questionId
                                    + ": "
                                    + normalizedLabel
                                    + "."
                    );
                }

                matchingChoice = choice;
            }

            if (matchingChoice == null) {
                throw new IllegalArgumentException(
                        "Alternativa inexistente na questão "
                                + questionId
                                + ": "
                                + normalizedLabel
                                + "."
                );
            }

            return withSelectedOption(
                    matchingChoice.getOptionId()
            );
        }

        private QuestionDraft withoutSelection() {
            if (selectedOptionId == null) {
                return this;
            }

            return new QuestionDraft(
                    position,
                    questionId,
                    optionChoices,
                    null
            );
        }

        public int getPosition() {
            return position;
        }

        public String getQuestionId() {
            return questionId;
        }

        public List<OptionChoice> getOptionChoices() {
            return optionChoices;
        }

        public int getOptionCount() {
            return optionChoices.size();
        }

        public String getSelectedOptionId() {
            return selectedOptionId;
        }

        public OptionChoice getSelectedOption() {
            return selectedOptionId == null
                    ? null
                    : findOptionById(selectedOptionId);
        }

        public String getSelectedOptionLabel() {
            OptionChoice selectedOption =
                    getSelectedOption();

            return selectedOption == null
                    ? null
                    : selectedOption.getLabel();
        }

        public boolean isAnswered() {
            return selectedOptionId != null;
        }

        public OptionChoice findOptionById(
                String optionId
        ) {
            if (optionId == null) {
                return null;
            }

            String normalizedOptionId = optionId.trim();

            for (OptionChoice choice : optionChoices) {
                if (choice.getOptionId().equals(
                        normalizedOptionId
                )) {
                    return choice;
                }
            }

            return null;
        }
    }

    private final String layoutId;
    private final int layoutVersion;
    private final String layoutName;

    private final List<QuestionDraft> questions;
    private final int answeredCount;

    private OmrManualAnswerKeyDraft(
            String layoutId,
            int layoutVersion,
            String layoutName,
            List<QuestionDraft> questions
    ) {
        this.layoutId = requireText("layoutId", layoutId);

        if (layoutVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão do layout deve ser positiva."
            );
        }

        this.layoutVersion = layoutVersion;
        this.layoutName = requireText(
                "layoutName",
                layoutName
        );

        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException(
                    "O rascunho deve possuir pelo menos uma questão."
            );
        }

        List<QuestionDraft> questionCopy =
                new ArrayList<>(questions.size());

        int calculatedAnsweredCount = 0;

        for (int index = 0;
             index < questions.size();
             index++) {

            QuestionDraft question = questions.get(index);

            if (question == null) {
                throw new IllegalArgumentException(
                        "O rascunho não pode conter questões nulas."
                );
            }

            int expectedPosition = index + 1;

            if (question.getPosition() != expectedPosition) {
                throw new IllegalArgumentException(
                        "A posição da questão deve acompanhar"
                                + " a ordem do rascunho: esperado="
                                + expectedPosition
                                + ", recebido="
                                + question.getPosition()
                                + "."
                );
            }

            questionCopy.add(question);

            if (question.isAnswered()) {
                calculatedAnsweredCount++;
            }
        }

        this.questions =
                Collections.unmodifiableList(questionCopy);

        this.answeredCount = calculatedAnsweredCount;
    }

    public static OmrManualAnswerKeyDraft create(
            OmrLayoutDefinition layoutDefinition
    ) {
        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "A definição do layout é obrigatória."
            );
        }

        List<QuestionDraft> questions =
                new ArrayList<>(
                        layoutDefinition.getQuestionCount()
                );

        int position = 1;

        for (OmrQuestionDefinition questionDefinition
                : layoutDefinition.getAllQuestions()) {

            List<OptionChoice> optionChoices =
                    new ArrayList<>(
                            questionDefinition
                                    .getOptions()
                                    .size()
                    );

            for (OmrOptionDefinition optionDefinition
                    : questionDefinition.getOptions()) {

                optionChoices.add(
                        new OptionChoice(
                                optionDefinition.getId(),
                                optionDefinition.getLabel()
                        )
                );
            }

            questions.add(
                    new QuestionDraft(
                            position,
                            questionDefinition.getId(),
                            optionChoices,
                            null
                    )
            );

            position++;
        }

        return new OmrManualAnswerKeyDraft(
                layoutDefinition.getId(),
                layoutDefinition.getVersion(),
                layoutDefinition.getName(),
                questions
        );
    }

    public OmrManualAnswerKeyDraft withSelection(
            String questionId,
            String optionId
    ) {
        int questionIndex = findQuestionIndex(questionId);

        QuestionDraft currentQuestion =
                questions.get(questionIndex);

        QuestionDraft updatedQuestion =
                currentQuestion.withSelectedOption(optionId);

        return replaceQuestion(
                questionIndex,
                currentQuestion,
                updatedQuestion
        );
    }

    public OmrManualAnswerKeyDraft withSelectionByLabel(
            String questionId,
            String optionLabel
    ) {
        int questionIndex = findQuestionIndex(questionId);

        QuestionDraft currentQuestion =
                questions.get(questionIndex);

        QuestionDraft updatedQuestion =
                currentQuestion.withSelectedLabel(optionLabel);

        return replaceQuestion(
                questionIndex,
                currentQuestion,
                updatedQuestion
        );
    }

    public OmrManualAnswerKeyDraft withoutSelection(
            String questionId
    ) {
        int questionIndex = findQuestionIndex(questionId);

        QuestionDraft currentQuestion =
                questions.get(questionIndex);

        QuestionDraft updatedQuestion =
                currentQuestion.withoutSelection();

        return replaceQuestion(
                questionIndex,
                currentQuestion,
                updatedQuestion
        );
    }

    private OmrManualAnswerKeyDraft replaceQuestion(
            int questionIndex,
            QuestionDraft currentQuestion,
            QuestionDraft updatedQuestion
    ) {
        if (currentQuestion == updatedQuestion) {
            return this;
        }

        List<QuestionDraft> updatedQuestions =
                new ArrayList<>(questions);

        updatedQuestions.set(
                questionIndex,
                updatedQuestion
        );

        return new OmrManualAnswerKeyDraft(
                layoutId,
                layoutVersion,
                layoutName,
                updatedQuestions
        );
    }

    private int findQuestionIndex(
            String questionId
    ) {
        String normalizedQuestionId = requireText(
                "questionId",
                questionId
        );

        for (int index = 0;
             index < questions.size();
             index++) {

            if (questions.get(index)
                    .getQuestionId()
                    .equals(normalizedQuestionId)) {

                return index;
            }
        }

        throw new IllegalArgumentException(
                "Questão inexistente no rascunho: "
                        + normalizedQuestionId
        );
    }

    public OmrAnswerKeyDefinition toAnswerKeyDefinition(
            OmrLayoutDefinition layoutDefinition,
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName,
            double uniformWeight
    ) {
        validateCompatibleLayout(layoutDefinition);

        if (!isComplete()) {
            throw new IllegalStateException(
                    "O gabarito ainda possui "
                            + getRemainingCount()
                            + " questão(ões) sem resposta."
            );
        }

        List<String> acceptedOptionLabels =
                new ArrayList<>(questions.size());

        for (QuestionDraft question : questions) {
            acceptedOptionLabels.add(
                    question.getSelectedOptionLabel()
            );
        }

        return new OmrAnswerKeyDefinitionFactory()
                .createSingleAnswerKey(
                        answerKeyId,
                        answerKeyVersion,
                        answerKeyName,
                        layoutDefinition,
                        acceptedOptionLabels,
                        uniformWeight
                );
    }

    private void validateCompatibleLayout(
            OmrLayoutDefinition layoutDefinition
    ) {
        if (layoutDefinition == null) {
            throw new IllegalArgumentException(
                    "A definição do layout é obrigatória."
            );
        }

        boolean sameLayoutId = layoutId.equals(
                layoutDefinition.getId()
        );

        boolean sameLayoutVersion =
                layoutVersion
                        == layoutDefinition.getVersion();

        if (!sameLayoutId || !sameLayoutVersion) {
            throw new IllegalArgumentException(
                    "O rascunho não pertence ao layout informado:"
                            + " rascunho="
                            + layoutId
                            + "@v"
                            + layoutVersion
                            + ", layout="
                            + layoutDefinition.getId()
                            + "@v"
                            + layoutDefinition.getVersion()
                            + "."
            );
        }

        if (questions.size()
                != layoutDefinition.getQuestionCount()) {

            throw new IllegalArgumentException(
                    "A quantidade de questões do rascunho"
                            + " diverge do layout informado."
            );
        }
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

    public String getLayoutId() {
        return layoutId;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public String getLayoutName() {
        return layoutName;
    }

    public List<QuestionDraft> getQuestions() {
        return questions;
    }

    public int getQuestionCount() {
        return questions.size();
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public int getRemainingCount() {
        return getQuestionCount() - answeredCount;
    }

    public boolean isComplete() {
        return answeredCount == getQuestionCount();
    }

    public QuestionDraft getQuestionAtPosition(
            int position
    ) {
        if (position <= 0 || position > questions.size()) {
            return null;
        }

        return questions.get(position - 1);
    }

    public QuestionDraft findQuestionById(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        String normalizedQuestionId = questionId.trim();

        for (QuestionDraft question : questions) {
            if (question.getQuestionId().equals(
                    normalizedQuestionId
            )) {
                return question;
            }
        }

        return null;
    }
}
