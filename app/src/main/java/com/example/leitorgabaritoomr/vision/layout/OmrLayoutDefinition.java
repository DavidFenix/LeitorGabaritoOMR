package com.example.leitorgabaritoomr.vision.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Define completamente a estrutura lógica e espacial de um
 * modelo de folha de respostas.
 *
 * canonicalWidth e canonicalHeight são dimensões digitais em
 * pixels. Não representam milímetros nem o tamanho físico da folha.
 *
 * No futuro, o normalizador usará essas dimensões para produzir
 * sempre uma imagem com proporção constante para este layout.
 */
public final class OmrLayoutDefinition {

    private final String id;
    private final int version;
    private final String name;

    private final int canonicalWidth;
    private final int canonicalHeight;

    private final List<OmrBlockDefinition> blocks;

    public OmrLayoutDefinition(
            String id,
            int version,
            String name,
            int canonicalWidth,
            int canonicalHeight,
            List<OmrBlockDefinition> blocks
    ) {
        this.id = requireText("id", id);
        this.name = requireText("name", name);

        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versão do layout deve ser positiva."
            );
        }

        if (canonicalWidth < 100) {
            throw new IllegalArgumentException(
                    "A largura canônica deve ser maior ou igual a 100."
            );
        }

        if (canonicalHeight < 100) {
            throw new IllegalArgumentException(
                    "A altura canônica deve ser maior ou igual a 100."
            );
        }

        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "O layout deve possuir pelo menos um bloco."
            );
        }

        List<OmrBlockDefinition> copy =
                new ArrayList<>(blocks);

        validateBlocks(copy);

        this.version = version;
        this.canonicalWidth = canonicalWidth;
        this.canonicalHeight = canonicalHeight;

        this.blocks =
                Collections.unmodifiableList(copy);
    }

    private void validateBlocks(
            List<OmrBlockDefinition> blocks
    ) {
        Set<String> blockIds = new HashSet<>();
        Set<String> questionIds = new HashSet<>();
        Set<String> optionIds = new HashSet<>();

        for (OmrBlockDefinition block : blocks) {
            if (block == null) {
                throw new IllegalArgumentException(
                        "A lista de blocos não pode conter valores nulos."
                );
            }

            if (!blockIds.add(block.getId())) {
                throw new IllegalArgumentException(
                        "Id de bloco repetido: "
                                + block.getId()
                );
            }

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                if (!questionIds.add(question.getId())) {
                    throw new IllegalArgumentException(
                            "Id de questão repetido no layout: "
                                    + question.getId()
                    );
                }

                for (OmrOptionDefinition option
                        : question.getOptions()) {

                    if (!optionIds.add(option.getId())) {
                        throw new IllegalArgumentException(
                                "Id de alternativa repetido no layout: "
                                        + option.getId()
                        );
                    }
                }
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

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public int getCanonicalWidth() {
        return canonicalWidth;
    }

    public int getCanonicalHeight() {
        return canonicalHeight;
    }

    public double getCanonicalAspectRatio() {
        return canonicalWidth
                / (double) canonicalHeight;
    }

    public List<OmrBlockDefinition> getBlocks() {
        return blocks;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public int getQuestionCount() {
        int total = 0;

        for (OmrBlockDefinition block : blocks) {
            total += block.getQuestionCount();
        }

        return total;
    }

    public int getOptionCount() {
        int total = 0;

        for (OmrBlockDefinition block : blocks) {
            total += block.getOptionCount();
        }

        return total;
    }

    public List<OmrQuestionDefinition> getAllQuestions() {
        List<OmrQuestionDefinition> questions =
                new ArrayList<>();

        for (OmrBlockDefinition block : blocks) {
            questions.addAll(block.getQuestions());
        }

        return Collections.unmodifiableList(questions);
    }

    public List<OmrOptionDefinition> getAllOptions() {
        List<OmrOptionDefinition> options =
                new ArrayList<>();

        for (OmrBlockDefinition block : blocks) {
            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                options.addAll(question.getOptions());
            }
        }

        return Collections.unmodifiableList(options);
    }

    public OmrBlockDefinition findBlockById(
            String blockId
    ) {
        if (blockId == null) {
            return null;
        }

        for (OmrBlockDefinition block : blocks) {
            if (block.getId().equals(blockId)) {
                return block;
            }
        }

        return null;
    }

    public OmrQuestionDefinition findQuestionById(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        for (OmrBlockDefinition block : blocks) {
            OmrQuestionDefinition question =
                    block.findQuestionById(questionId);

            if (question != null) {
                return question;
            }
        }

        return null;
    }

    public OmrOptionDefinition findOptionById(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        for (OmrBlockDefinition block : blocks) {
            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                OmrOptionDefinition option =
                        question.findOptionById(optionId);

                if (option != null) {
                    return option;
                }
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof OmrLayoutDefinition)) {
            return false;
        }

        OmrLayoutDefinition other =
                (OmrLayoutDefinition) object;

        return version == other.version
                && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }

    @Override
    public String toString() {
        return id
                + "@v" + version
                + "[nome=" + name
                + ", blocos=" + getBlockCount()
                + ", questoes=" + getQuestionCount()
                + ", alternativas=" + getOptionCount()
                + ", canvas=" + canonicalWidth
                + "x" + canonicalHeight
                + "]";
    }
}