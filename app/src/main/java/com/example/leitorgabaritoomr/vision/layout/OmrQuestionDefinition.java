package com.example.leitorgabaritoomr.vision.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Define uma questão e suas alternativas.
 *
 * O label representa o número ou texto impresso, enquanto o id
 * identifica a questão de forma única dentro do layout.
 */
public final class OmrQuestionDefinition {

    private final String id;
    private final String label;

    private final List<OmrOptionDefinition> options;

    public OmrQuestionDefinition(
            String id,
            String label,
            List<OmrOptionDefinition> options
    ) {
        this.id = requireText("id", id);
        this.label = requireText("label", label);

        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException(
                    "A questão deve possuir pelo menos duas alternativas."
            );
        }

        List<OmrOptionDefinition> copy =
                new ArrayList<>(options);

        validateOptions(copy);

        this.options =
                Collections.unmodifiableList(copy);
    }

    private void validateOptions(
            List<OmrOptionDefinition> options
    ) {
        Set<String> ids = new HashSet<>();
        Set<String> labels = new HashSet<>();

        for (OmrOptionDefinition option : options) {
            if (option == null) {
                throw new IllegalArgumentException(
                        "A lista de alternativas não pode conter valores nulos."
                );
            }

            if (!ids.add(option.getId())) {
                throw new IllegalArgumentException(
                        "Id de alternativa repetido na questão "
                                + id + ": " + option.getId()
                );
            }

            if (!labels.add(option.getLabel())) {
                throw new IllegalArgumentException(
                        "Label de alternativa repetido na questão "
                                + id + ": " + option.getLabel()
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

    public String getLabel() {
        return label;
    }

    public List<OmrOptionDefinition> getOptions() {
        return options;
    }

    public int getOptionCount() {
        return options.size();
    }

    public OmrOptionDefinition findOptionById(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        for (OmrOptionDefinition option : options) {
            if (option.getId().equals(optionId)) {
                return option;
            }
        }

        return null;
    }

    public OmrOptionDefinition findOptionByLabel(
            String optionLabel
    ) {
        if (optionLabel == null) {
            return null;
        }

        for (OmrOptionDefinition option : options) {
            if (option.getLabel().equals(optionLabel)) {
                return option;
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof OmrQuestionDefinition)) {
            return false;
        }

        OmrQuestionDefinition other =
                (OmrQuestionDefinition) object;

        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id
                + "[label=" + label
                + ", alternativas=" + options.size()
                + "]";
    }
}