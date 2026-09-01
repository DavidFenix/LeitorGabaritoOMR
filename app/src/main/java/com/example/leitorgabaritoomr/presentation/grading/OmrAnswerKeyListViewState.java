package com.example.leitorgabaritoomr.presentation.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Estado visual imutável e independente de Android da lista de gabaritos
 * oficiais armazenados no dispositivo.
 *
 * A ordem recebida é preservada. A seleção ativa é relacionada pelo par
 * {@code id + version}, pois o gabarito ativo e os itens da coleção podem ter
 * sido desserializados em momentos diferentes.
 */
public final class OmrAnswerKeyListViewState {

    /**
     * Uma linha lógica da lista de gabaritos salvos.
     */
    public static final class AnswerKeyItem {

        private final String answerKeyId;
        private final int answerKeyVersion;
        private final String answerKeyName;

        private final String layoutId;
        private final int layoutVersion;

        private final int questionCount;
        private final double totalWeight;
        private final boolean active;

        private AnswerKeyItem(
                OmrAnswerKeyDefinition answerKeyDefinition,
                boolean active
        ) {
            answerKeyId = answerKeyDefinition.getId();
            answerKeyVersion = answerKeyDefinition.getVersion();
            answerKeyName = answerKeyDefinition.getName();

            layoutId = answerKeyDefinition.getLayoutId();
            layoutVersion =
                    answerKeyDefinition.getLayoutVersion();

            questionCount =
                    answerKeyDefinition.getQuestionCount();

            totalWeight =
                    answerKeyDefinition.getTotalWeight();

            this.active = active;
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

        public String getLayoutId() {
            return layoutId;
        }

        public int getLayoutVersion() {
            return layoutVersion;
        }

        public int getQuestionCount() {
            return questionCount;
        }

        public double getTotalWeight() {
            return totalWeight;
        }

        public boolean isActive() {
            return active;
        }

        public boolean hasIdentity(
                String answerKeyId,
                int answerKeyVersion
        ) {
            return answerKeyId != null
                    && this.answerKeyVersion
                    == answerKeyVersion
                    && this.answerKeyId.equals(
                    answerKeyId.trim()
            );
        }
    }

    private final List<AnswerKeyItem> answerKeyItems;
    private final AnswerKeyItem activeAnswerKeyItem;

    private OmrAnswerKeyListViewState(
            List<AnswerKeyItem> answerKeyItems,
            AnswerKeyItem activeAnswerKeyItem
    ) {
        this.answerKeyItems =
                Collections.unmodifiableList(
                        new ArrayList<>(answerKeyItems)
                );

        this.activeAnswerKeyItem =
                activeAnswerKeyItem;
    }

    public static OmrAnswerKeyListViewState from(
            List<OmrAnswerKeyDefinition> answerKeys,
            OmrAnswerKeyDefinition activeAnswerKey
    ) {
        if (answerKeys == null) {
            throw new IllegalArgumentException(
                    "A lista de gabaritos é obrigatória."
            );
        }

        List<AnswerKeyItem> items =
                new ArrayList<>(answerKeys.size());

        Set<String> identities =
                new HashSet<>(answerKeys.size());

        AnswerKeyItem activeItem = null;

        for (OmrAnswerKeyDefinition answerKey
                : answerKeys) {

            if (answerKey == null) {
                throw new IllegalArgumentException(
                        "A lista de gabaritos não pode"
                                + " conter valores nulos."
                );
            }

            if (!identities.add(identityOf(answerKey))) {
                throw new IllegalArgumentException(
                        "A lista possui uma identidade"
                                + " de gabarito repetida."
                );
            }

            boolean active = sameIdentity(
                    answerKey,
                    activeAnswerKey
            );

            AnswerKeyItem item =
                    new AnswerKeyItem(answerKey, active);

            items.add(item);

            if (active) {
                activeItem = item;
            }
        }

        if (activeAnswerKey != null
                && activeItem == null) {
            throw new IllegalArgumentException(
                    "O gabarito ativo deve pertencer à lista."
            );
        }

        OmrAnswerKeyListViewState viewState =
                new OmrAnswerKeyListViewState(
                        items,
                        activeItem
                );

        viewState.validateConsistency();

        return viewState;
    }

    private void validateConsistency() {
        int activeCount = 0;

        for (AnswerKeyItem item : answerKeyItems) {
            if (item.isActive()) {
                activeCount++;
            }
        }

        if (activeCount > 1) {
            throw new IllegalStateException(
                    "A lista visual possui mais de um"
                            + " gabarito ativo."
            );
        }

        if ((activeAnswerKeyItem == null)
                != (activeCount == 0)) {
            throw new IllegalStateException(
                    "A referência ativa divergiu"
                            + " das linhas da lista."
            );
        }
    }

    private static boolean sameIdentity(
            OmrAnswerKeyDefinition first,
            OmrAnswerKeyDefinition second
    ) {
        return first != null
                && second != null
                && first.getVersion() == second.getVersion()
                && first.getId().equals(second.getId());
    }

    private static String identityOf(
            OmrAnswerKeyDefinition answerKey
    ) {
        String id = answerKey.getId();

        return id.length()
                + ":"
                + id
                + "@"
                + answerKey.getVersion();
    }

    public List<AnswerKeyItem> getAnswerKeyItems() {
        return answerKeyItems;
    }

    public int getAnswerKeyCount() {
        return answerKeyItems.size();
    }

    public boolean isEmpty() {
        return answerKeyItems.isEmpty();
    }

    public boolean hasActiveAnswerKey() {
        return activeAnswerKeyItem != null;
    }

    public AnswerKeyItem getActiveAnswerKeyItemOrNull() {
        return activeAnswerKeyItem;
    }

    public AnswerKeyItem findItemOrNull(
            String answerKeyId,
            int answerKeyVersion
    ) {
        if (answerKeyId == null) {
            return null;
        }

        for (AnswerKeyItem item : answerKeyItems) {
            if (item.hasIdentity(
                    answerKeyId,
                    answerKeyVersion
            )) {
                return item;
            }
        }

        return null;
    }
}
