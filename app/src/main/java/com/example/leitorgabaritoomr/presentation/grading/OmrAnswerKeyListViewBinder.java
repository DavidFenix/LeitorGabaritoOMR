package com.example.leitorgabaritoomr.presentation.grading;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aplica {@link OmrAnswerKeyListViewState} ao layout Android da lista de
 * gabaritos salvos.
 *
 * O Binder não acessa o repositório nem altera a seleção ativa. Ele apenas
 * apresenta a fotografia visual e encaminha as ações do usuário à Activity.
 */
public final class OmrAnswerKeyListViewBinder {

    /**
     * Recebe as ações associadas a uma linha da lista.
     */
    public interface OnAnswerKeyActionListener {

        void onSelectAnswerKey(
                String answerKeyId,
                int answerKeyVersion
        );

        void onDeleteAnswerKey(
                String answerKeyId,
                int answerKeyVersion,
                String answerKeyName,
                boolean active
        );
    }

    private static final int COLOR_ACTIVE =
            Color.rgb(22, 163, 74);

    private static final int COLOR_INACTIVE =
            Color.rgb(209, 213, 219);

    private final Context context;
    private final LayoutInflater layoutInflater;

    private final TextView countTextView;
    private final ListView answerKeyListView;
    private final View emptyContainer;
    private final Button backButton;
    private final Button createButton;

    private final AnswerKeyAdapter answerKeyAdapter;

    private OnAnswerKeyActionListener actionListener;
    private boolean released;

    public OmrAnswerKeyListViewBinder(
            View rootView
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da lista de gabaritos é obrigatória."
            );
        }

        context = rootView.getContext();
        layoutInflater = LayoutInflater.from(context);

        countTextView = requireView(
                rootView,
                R.id.textOmrAnswerKeyListCount,
                TextView.class
        );

        answerKeyListView = requireView(
                rootView,
                R.id.listOmrAnswerKeys,
                ListView.class
        );

        emptyContainer = requireView(
                rootView,
                R.id.containerOmrAnswerKeyListEmpty,
                View.class
        );

        backButton = requireView(
                rootView,
                R.id.buttonOmrAnswerKeyListBack,
                Button.class
        );

        createButton = requireView(
                rootView,
                R.id.buttonOmrAnswerKeyListCreate,
                Button.class
        );

        answerKeyAdapter = new AnswerKeyAdapter();
        answerKeyListView.setAdapter(answerKeyAdapter);
    }

    public void render(
            OmrAnswerKeyListViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual da lista é obrigatório."
            );
        }

        applyCount(viewState);
        applyContentVisibility(viewState);

        answerKeyAdapter.replaceItems(
                viewState.getAnswerKeyItems()
        );
    }

    public void setOnAnswerKeyActionListener(
            OnAnswerKeyActionListener listener
    ) {
        ensureNotReleased();
        actionListener = listener;
    }

    public void setOnBackClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        backButton.setOnClickListener(listener);
    }

    public void setOnCreateClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        createButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;
        actionListener = null;

        backButton.setOnClickListener(null);
        createButton.setOnClickListener(null);

        answerKeyAdapter.clear();
        answerKeyListView.setAdapter(null);
    }

    private void applyCount(
            OmrAnswerKeyListViewState viewState
    ) {
        int answerKeyCount =
                viewState.getAnswerKeyCount();

        countTextView.setText(
                context
                .getResources()
                .getQuantityString(
                        R.plurals.omr_answer_key_list_count,
                        answerKeyCount,
                        answerKeyCount
                )
        );
    }

    private void applyContentVisibility(
            OmrAnswerKeyListViewState viewState
    ) {
        boolean empty = viewState.isEmpty();

        emptyContainer.setVisibility(
                empty ? View.VISIBLE : View.GONE
        );

        answerKeyListView.setVisibility(
                empty ? View.GONE : View.VISIBLE
        );
    }

    private void bindAnswerKeyItem(
            View itemView,
            OmrAnswerKeyListViewState.AnswerKeyItem item
    ) {
        View indicatorView = requireView(
                itemView,
                R.id.viewOmrAnswerKeyActiveIndicator,
                View.class
        );

        TextView nameTextView = requireView(
                itemView,
                R.id.textOmrAnswerKeyName,
                TextView.class
        );

        TextView activeBadgeTextView = requireView(
                itemView,
                R.id.textOmrAnswerKeyActiveBadge,
                TextView.class
        );

        TextView versionTextView = requireView(
                itemView,
                R.id.textOmrAnswerKeyVersion,
                TextView.class
        );

        TextView questionsTextView = requireView(
                itemView,
                R.id.textOmrAnswerKeyQuestions,
                TextView.class
        );

        TextView weightTextView = requireView(
                itemView,
                R.id.textOmrAnswerKeyWeight,
                TextView.class
        );

        TextView layoutTextView = requireView(
                itemView,
                R.id.textOmrAnswerKeyLayout,
                TextView.class
        );

        Button selectButton = requireView(
                itemView,
                R.id.buttonOmrAnswerKeySelect,
                Button.class
        );

        Button deleteButton = requireView(
                itemView,
                R.id.buttonOmrAnswerKeyDelete,
                Button.class
        );

        nameTextView.setText(item.getAnswerKeyName());

        versionTextView.setText(
                context.getString(
                        R.string
                        .omr_answer_key_list_version_format,
                        item.getAnswerKeyVersion()
                )
        );

        int questionCount = item.getQuestionCount();

        questionsTextView.setText(
                context
                .getResources()
                .getQuantityString(
                        R.plurals
                        .omr_answer_key_list_questions_format,
                        questionCount,
                        questionCount
                )
        );

        weightTextView.setText(
                context.getString(
                        R.string
                        .omr_answer_key_list_weight_format,
                        item.getTotalWeight()
                )
        );

        layoutTextView.setText(
                context.getString(
                        R.string
                        .omr_answer_key_list_layout_format,
                        item.getLayoutId(),
                        item.getLayoutVersion()
                )
        );

        applyActiveState(
                indicatorView,
                activeBadgeTextView,
                selectButton,
                item.isActive()
        );

        selectButton.setOnClickListener(
                view -> notifySelect(item)
        );

        deleteButton.setOnClickListener(
                view -> notifyDelete(item)
        );
    }

    private void applyActiveState(
            View indicatorView,
            TextView activeBadgeTextView,
            Button selectButton,
            boolean active
    ) {
        indicatorView.setBackgroundColor(
                active ? COLOR_ACTIVE : COLOR_INACTIVE
        );

        activeBadgeTextView.setVisibility(
                active ? View.VISIBLE : View.GONE
        );

        selectButton.setEnabled(!active);
        selectButton.setAlpha(active ? 0.55f : 1.0f);

        selectButton.setText(
                active
                        ? R.string
                        .omr_answer_key_list_action_selected
                        : R.string
                        .omr_answer_key_list_action_select
        );
    }

    private void notifySelect(
            OmrAnswerKeyListViewState.AnswerKeyItem item
    ) {
        OnAnswerKeyActionListener listener =
                actionListener;

        if (listener == null) {
            return;
        }

        listener.onSelectAnswerKey(
                item.getAnswerKeyId(),
                item.getAnswerKeyVersion()
        );
    }

    private void notifyDelete(
            OmrAnswerKeyListViewState.AnswerKeyItem item
    ) {
        OnAnswerKeyActionListener listener =
                actionListener;

        if (listener == null) {
            return;
        }

        listener.onDeleteAnswerKey(
                item.getAnswerKeyId(),
                item.getAnswerKeyVersion(),
                item.getAnswerKeyName(),
                item.isActive()
        );
    }

    /**
     * Mantém em memória somente os cartões atualmente visíveis.
     */
    private final class AnswerKeyAdapter
            extends BaseAdapter {

        private List<OmrAnswerKeyListViewState.AnswerKeyItem>
                items = Collections.emptyList();

        void replaceItems(
                List<OmrAnswerKeyListViewState.AnswerKeyItem>
                        newItems
        ) {
            items = Collections.unmodifiableList(
                    new ArrayList<>(newItems)
            );

            notifyDataSetChanged();
        }

        void clear() {
            items = Collections.emptyList();
            notifyDataSetInvalidated();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public OmrAnswerKeyListViewState.AnswerKeyItem
        getItem(
                int position
        ) {
            return items.get(position);
        }

        @Override
        public long getItemId(
                int position
        ) {
            return position;
        }

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {
            View itemView = convertView;

            if (itemView == null) {
                itemView = layoutInflater.inflate(
                        R.layout.item_omr_saved_answer_key,
                        parent,
                        false
                );
            }

            bindAnswerKeyItem(
                    itemView,
                    getItem(position)
            );

            return itemView;
        }
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O Binder da lista de gabaritos"
                            + " já foi liberado."
            );
        }
    }

    private static <T extends View> T requireView(
            View rootView,
            @IdRes int viewId,
            Class<T> expectedType
    ) {
        View foundView =
                rootView.findViewById(viewId);

        if (foundView == null) {
            throw new IllegalStateException(
                    "View obrigatória não encontrada: "
                            + viewId
            );
        }

        if (!expectedType.isInstance(foundView)) {
            throw new IllegalStateException(
                    "Tipo inesperado para a View "
                            + viewId
                            + ": "
                            + foundView
                            .getClass()
                            .getName()
            );
        }

        return expectedType.cast(foundView);
    }
}
