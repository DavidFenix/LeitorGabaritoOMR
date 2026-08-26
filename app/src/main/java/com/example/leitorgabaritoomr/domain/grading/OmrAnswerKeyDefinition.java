package com.example.leitorgabaritoomr.domain.grading;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Define um gabarito oficial completo e imutável.
 *
 * O gabarito é vinculado ao id e à versão exata do layout para impedir
 * que respostas sejam corrigidas com uma geometria diferente daquela
 * para a qual foram configuradas.
 *
 * A correspondência entre leitura e gabarito sempre acontece pelo
 * questionId. A ordem da lista é preservada apenas para apresentação
 * e serialização determinística.
 */
public final class OmrAnswerKeyDefinition
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final int version;
    private final String name;

    private final String layoutId;
    private final int layoutVersion;

    private final List<OmrAnswerKeyEntry> entries;
    private final Map<String, OmrAnswerKeyEntry> entriesByQuestionId;

    private final double totalWeight;

    public OmrAnswerKeyDefinition(
            String id,
            int version,
            String name,
            String layoutId,
            int layoutVersion,
            List<OmrAnswerKeyEntry> entries
    ) {
        this.id = requireText(
                "id",
                id
        );

        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versão do gabarito deve ser positiva."
            );
        }

        this.name = requireText(
                "name",
                name
        );

        this.layoutId = requireText(
                "layoutId",
                layoutId
        );

        if (layoutVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão do layout deve ser positiva."
            );
        }

        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "O gabarito deve possuir pelo menos uma questão."
            );
        }

        List<OmrAnswerKeyEntry> entryCopy =
                new ArrayList<>(entries.size());

        Map<String, OmrAnswerKeyEntry> entryMap =
                new LinkedHashMap<>(entries.size());

        double accumulatedWeight = 0.0;

        for (OmrAnswerKeyEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException(
                        "A lista de questões do gabarito"
                                + " não pode conter valores nulos."
                );
            }

            String questionId = entry.getQuestionId();

            if (entryMap.put(questionId, entry) != null) {
                throw new IllegalArgumentException(
                        "Questão repetida no gabarito: "
                                + questionId
                );
            }

            entryCopy.add(entry);
            accumulatedWeight += entry.getWeight();
        }

        if (!Double.isFinite(accumulatedWeight)
                || accumulatedWeight <= 0.0) {

            throw new IllegalArgumentException(
                    "O peso total do gabarito deve ser"
                            + " finito e maior que zero."
            );
        }

        this.version = version;
        this.layoutVersion = layoutVersion;

        this.entries = Collections.unmodifiableList(
                entryCopy
        );

        this.entriesByQuestionId =
                Collections.unmodifiableMap(entryMap);

        this.totalWeight = accumulatedWeight;
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

    public String getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getLayoutId() {
        return layoutId;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public List<OmrAnswerKeyEntry> getEntries() {
        return entries;
    }

    public int getQuestionCount() {
        return entries.size();
    }

    public double getTotalWeight() {
        return totalWeight;
    }

    public boolean containsQuestion(
            String questionId
    ) {
        return findEntryByQuestionId(questionId) != null;
    }

    public OmrAnswerKeyEntry findEntryByQuestionId(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        return entriesByQuestionId.get(
                questionId.trim()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAnswerKeyDefinition)) {
            return false;
        }

        OmrAnswerKeyDefinition that =
                (OmrAnswerKeyDefinition) other;

        return version == that.version
                && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                version
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s@v%d[name=%s, layout=%s@v%d,"
                        + " questions=%d, totalWeight=%.3f]",
                id,
                version,
                name,
                layoutId,
                layoutVersion,
                getQuestionCount(),
                totalWeight
        );
    }
}
