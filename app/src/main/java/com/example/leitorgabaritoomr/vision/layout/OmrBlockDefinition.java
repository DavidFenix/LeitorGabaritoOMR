package com.example.leitorgabaritoomr.vision.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agrupa questões que pertencem ao mesmo bloco, coluna,
 * disciplina, tema ou outra divisão lógica.
 *
 * A classe não obriga que os labels das questões sejam
 * sequenciais.
 */
public final class OmrBlockDefinition {

    private final String id;
    private final String title;

    private final List<OmrQuestionDefinition> questions;

    public OmrBlockDefinition(
            String id,
            String title,
            List<OmrQuestionDefinition> questions
    ) {
        this.id = requireText("id", id);
        this.title = requireText("title", title);

        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException(
                    "O bloco deve possuir pelo menos uma questão."
            );
        }

        List<OmrQuestionDefinition> copy =
                new ArrayList<>(questions);

        validateQuestions(copy);

        this.questions =
                Collections.unmodifiableList(copy);
    }

    private void validateQuestions(
            List<OmrQuestionDefinition> questions
    ) {
        Set<String> ids = new HashSet<>();

        for (OmrQuestionDefinition question : questions) {
            if (question == null) {
                throw new IllegalArgumentException(
                        "A lista de questões não pode conter valores nulos."
                );
            }

            if (!ids.add(question.getId())) {
                throw new IllegalArgumentException(
                        "Id de questão repetido no bloco "
                                + id + ": " + question.getId()
                );
            }
        }
    }

    private String requireText(
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

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<OmrQuestionDefinition> getQuestions() {
        return questions;
    }

    public int getQuestionCount() {
        return questions.size();
    }

    public int getOptionCount() {
        int total = 0;

        for (OmrQuestionDefinition question : questions) {
            total += question.getOptionCount();
        }

        return total;
    }

    public OmrQuestionDefinition findQuestionById(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        for (OmrQuestionDefinition question : questions) {
            if (question.getId().equals(questionId)) {
                return question;
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof OmrBlockDefinition)) {
            return false;
        }

        OmrBlockDefinition other =
                (OmrBlockDefinition) object;

        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id
                + "[titulo=" + title
                + ", questoes=" + questions.size()
                + "]";
    }
}