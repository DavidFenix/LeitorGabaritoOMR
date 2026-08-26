package com.example.leitorgabaritoomr.presentation.grading;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Aplica {@link OmrManualAnswerKeyViewState} ao layout Android do
 * cadastro manual de gabarito oficial.
 *
 * O Binder não mantém o rascunho e não contém regras de negócio. Ele
 * apenas apresenta o estado visual e comunica as ações do usuário à
 * futura Activity.
 */
public final class OmrManualAnswerKeyViewBinder {

    /**
     * Recebe mudanças feitas nas alternativas de uma questão.
     */
    public interface OnAnswerChangedListener {

        void onOptionSelected(
                String questionId,
                String optionId
        );

        void onSelectionCleared(
                String questionId
        );
    }

    private static final int COLOR_ANSWERED =
            Color.rgb(22, 163, 74);

    private static final int COLOR_PENDING =
            Color.rgb(245, 158, 11);

    private final Context context;
    private final LayoutInflater layoutInflater;

    private final TextView layoutTextView;
    private final TextView progressTextView;
    private final ProgressBar progressBar;
    private final TextView remainingTextView;

    private final ScrollView scrollView;
    private final TextInputLayout nameInputLayout;
    private final TextInputEditText nameEditText;
    private final Button goToPendingButton;
    private final LinearLayout questionContainer;

    private final Button cancelButton;
    private final Button saveButton;

    private OmrManualAnswerKeyViewState currentViewState;
    private OnAnswerChangedListener answerChangedListener;
    private boolean released;

    public OmrManualAnswerKeyViewBinder(
            View rootView
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz do cadastro manual é obrigatória."
            );
        }

        context = rootView.getContext();
        layoutInflater = LayoutInflater.from(context);

        layoutTextView = requireView(
                rootView,
                R.id.textOmrManualLayout,
                TextView.class
        );

        progressTextView = requireView(
                rootView,
                R.id.textOmrManualProgress,
                TextView.class
        );

        progressBar = requireView(
                rootView,
                R.id.progressOmrManual,
                ProgressBar.class
        );

        remainingTextView = requireView(
                rootView,
                R.id.textOmrManualRemaining,
                TextView.class
        );

        scrollView = requireView(
                rootView,
                R.id.scrollOmrManual,
                ScrollView.class
        );

        nameInputLayout = requireView(
                rootView,
                R.id.inputLayoutOmrManualName,
                TextInputLayout.class
        );

        nameEditText = requireView(
                rootView,
                R.id.editOmrManualName,
                TextInputEditText.class
        );

        goToPendingButton = requireView(
                rootView,
                R.id.buttonOmrManualGoToPending,
                Button.class
        );

        questionContainer = requireView(
                rootView,
                R.id.containerOmrManualQuestions,
                LinearLayout.class
        );

        cancelButton = requireView(
                rootView,
                R.id.buttonOmrManualCancel,
                Button.class
        );

        saveButton = requireView(
                rootView,
                R.id.buttonOmrManualSave,
                Button.class
        );

        goToPendingButton.setOnClickListener(
                view -> scrollToFirstPendingQuestion()
        );
    }

    public void render(
            OmrManualAnswerKeyViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual do gabarito é obrigatório."
            );
        }

        currentViewState = viewState;

        applyHeader(viewState);
        applyQuestionItems(viewState);
        applyActions(viewState);
    }

    public void setOnAnswerChangedListener(
            OnAnswerChangedListener listener
    ) {
        ensureNotReleased();
        answerChangedListener = listener;
    }

    public void setOnCancelClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        cancelButton.setOnClickListener(listener);
    }

    public void setOnSaveClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        saveButton.setOnClickListener(listener);
    }

    public String getAnswerKeyName() {
        ensureNotReleased();

        Editable editable = nameEditText.getText();

        return editable == null
                ? ""
                : editable.toString().trim();
    }

    public void setAnswerKeyName(
            String answerKeyName
    ) {
        ensureNotReleased();

        nameEditText.setText(
                answerKeyName == null
                        ? ""
                        : answerKeyName
        );
    }

    public void showNameRequiredError() {
        ensureNotReleased();

        nameInputLayout.setError(
                context.getString(
                        R.string
                        .omr_manual_key_error_name_required
                )
        );

        nameEditText.requestFocus();

        scrollView.post(
                () -> scrollView.smoothScrollTo(0, 0)
        );
    }

    public void clearNameError() {
        ensureNotReleased();
        nameInputLayout.setError(null);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;
        currentViewState = null;
        answerChangedListener = null;

        goToPendingButton.setOnClickListener(null);
        cancelButton.setOnClickListener(null);
        saveButton.setOnClickListener(null);
        questionContainer.removeAllViews();
    }

    private void applyHeader(
            OmrManualAnswerKeyViewState viewState
    ) {
        layoutTextView.setText(
                context.getString(
                        R.string.omr_manual_key_layout_format,
                        viewState.getLayoutName(),
                        viewState.getLayoutVersion()
                )
        );

        progressTextView.setText(
                context.getString(
                        R.string.omr_manual_key_progress_format,
                        viewState.getAnsweredCount(),
                        viewState.getQuestionCount(),
                        viewState.getProgressPercent()
                )
        );

        progressBar.setProgress(
                viewState.getProgressPercent()
        );

        if (viewState.isComplete()) {
            remainingTextView.setText(
                    R.string.omr_manual_key_complete
            );
        } else {
            int remainingCount =
                    viewState.getRemainingCount();

            remainingTextView.setText(
                    context
                    .getResources()
                    .getQuantityString(
                            R.plurals
                            .omr_manual_key_remaining_format,
                            remainingCount,
                            remainingCount
                    )
            );
        }
    }

    private void applyQuestionItems(
            OmrManualAnswerKeyViewState viewState
    ) {
        questionContainer.removeAllViews();

        for (OmrManualAnswerKeyViewState.QuestionItem item
                : viewState.getQuestionItems()) {

            View itemView = layoutInflater.inflate(
                    R.layout
                    .item_omr_manual_answer_key_question,
                    questionContainer,
                    false
            );

            bindQuestionItem(itemView, item);
            questionContainer.addView(itemView);
        }
    }

    private void bindQuestionItem(
            View itemView,
            OmrManualAnswerKeyViewState.QuestionItem item
    ) {
        View indicatorView = requireView(
                itemView,
                R.id.viewOmrManualQuestionIndicator,
                View.class
        );

        TextView positionTextView = requireView(
                itemView,
                R.id.textOmrManualQuestionPosition,
                TextView.class
        );

        ChipGroup optionChipGroup = requireView(
                itemView,
                R.id.chipGroupOmrManualQuestionOptions,
                ChipGroup.class
        );

        TextView statusTextView = requireView(
                itemView,
                R.id.textOmrManualQuestionStatus,
                TextView.class
        );

        positionTextView.setText(
                context.getString(
                        R.string
                        .omr_manual_key_question_position_format,
                        item.getPosition()
                )
        );

        applyQuestionStatus(
                indicatorView,
                statusTextView,
                item
        );

        applyOptionChips(
                optionChipGroup,
                item
        );
    }

    private void applyQuestionStatus(
            View indicatorView,
            TextView statusTextView,
            OmrManualAnswerKeyViewState.QuestionItem item
    ) {
        int statusColor;

        if (item.isAnswered()) {
            statusColor = COLOR_ANSWERED;

            statusTextView.setText(
                    context.getString(
                            R.string
                            .omr_manual_key_question_answered_format,
                            item.getSelectedOptionLabel()
                    )
            );
        } else {
            statusColor = COLOR_PENDING;

            statusTextView.setText(
                    R.string
                    .omr_manual_key_question_pending
            );
        }

        indicatorView.setBackgroundColor(statusColor);
        statusTextView.setTextColor(statusColor);
    }

    @SuppressWarnings("deprecation")
    private void applyOptionChips(
            ChipGroup chipGroup,
            OmrManualAnswerKeyViewState.QuestionItem item
    ) {
        chipGroup.setOnCheckedChangeListener(null);
        chipGroup.removeAllViews();
        chipGroup.setSingleSelection(true);
        chipGroup.setSelectionRequired(false);

        int selectedChipId = View.NO_ID;

        for (OmrManualAnswerKeyViewState.OptionItem option
                : item.getOptions()) {

            Chip chip = new Chip(context);
            chip.setId(View.generateViewId());
            chip.setText(option.getLabel());
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTag(option.getOptionId());

            chipGroup.addView(chip);

            if (option.isSelected()) {
                selectedChipId = chip.getId();
            }
        }

        if (selectedChipId != View.NO_ID) {
            chipGroup.check(selectedChipId);
        }

        chipGroup.setOnCheckedChangeListener(
                (group, checkedId) -> notifyAnswerChanged(
                        group,
                        checkedId,
                        item.getQuestionId()
                )
        );
    }

    private void notifyAnswerChanged(
            ChipGroup chipGroup,
            int checkedId,
            String questionId
    ) {
        OnAnswerChangedListener listener =
                answerChangedListener;

        if (listener == null) {
            return;
        }

        if (checkedId == View.NO_ID) {
            listener.onSelectionCleared(questionId);
            return;
        }

        View selectedView =
                chipGroup.findViewById(checkedId);

        if (!(selectedView instanceof Chip)) {
            throw new IllegalStateException(
                    "A alternativa selecionada não é um Chip."
            );
        }

        Object optionId = selectedView.getTag();

        if (!(optionId instanceof String)) {
            throw new IllegalStateException(
                    "O Chip selecionado não possui optionId."
            );
        }

        listener.onOptionSelected(
                questionId,
                (String) optionId
        );
    }

    private void applyActions(
            OmrManualAnswerKeyViewState viewState
    ) {
        boolean hasPendingQuestion =
                !viewState.isComplete();

        goToPendingButton.setEnabled(hasPendingQuestion);
        goToPendingButton.setAlpha(
                hasPendingQuestion ? 1.0f : 0.5f
        );

        saveButton.setEnabled(viewState.canSave());
        saveButton.setAlpha(
                viewState.canSave() ? 1.0f : 0.5f
        );
    }

    private void scrollToFirstPendingQuestion() {
        ensureNotReleased();

        if (currentViewState == null
                || currentViewState.isComplete()) {
            return;
        }

        int position =
                currentViewState
                .getFirstUnansweredPosition();

        int childIndex = position - 1;

        if (childIndex < 0
                || childIndex >= questionContainer.getChildCount()) {
            throw new IllegalStateException(
                    "A primeira questão pendente não possui linha."
            );
        }

        View pendingQuestionView =
                questionContainer.getChildAt(childIndex);

        scrollView.post(
                () -> scrollView.smoothScrollTo(
                        0,
                        questionContainer.getTop()
                                + pendingQuestionView.getTop()
                )
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O Binder do gabarito manual já foi liberado."
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
