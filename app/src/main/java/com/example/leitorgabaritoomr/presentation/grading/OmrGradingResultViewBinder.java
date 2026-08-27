package com.example.leitorgabaritoomr.presentation.grading;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Aplica {@link OmrGradingResultViewState} ao layout Android do
 * resultado da correção.
 *
 * Não corrige respostas nem recalcula a pontuação. O Binder apenas
 * apresenta um estado visual já validado e comunica as duas ações da
 * tela ao seu coordenador.
 */
public final class OmrGradingResultViewBinder {

    private static final int COLOR_SUCCESS =
            Color.rgb(22, 163, 74);

    private static final int COLOR_ERROR =
            Color.rgb(220, 38, 38);

    private static final int COLOR_NEUTRAL =
            Color.rgb(75, 85, 99);

    private static final int COLOR_MULTIPLE =
            Color.rgb(190, 24, 93);

    private static final int COLOR_AMBIGUOUS =
            Color.rgb(234, 88, 12);

    private static final int COLOR_REVIEW =
            Color.rgb(180, 83, 9);

    private final Context context;
    private final LayoutInflater layoutInflater;

    private final TextView overallTextView;
    private final TextView answerKeyTextView;
    private final TextView layoutTextView;
    private final TextView capturedAtTextView;

    private final TextView percentageTextView;
    private final TextView pointsTextView;

    private final TextView correctCountTextView;
    private final TextView incorrectCountTextView;
    private final TextView blankCountTextView;
    private final TextView reviewCountTextView;
    private final TextView multipleCountTextView;
    private final TextView ambiguousCountTextView;
    private final TextView notReadyCountTextView;
    private final TextView reviewTextView;

    private final ListView questionListView;
    private final QuestionAdapter questionAdapter;

    private final Button readAgainButton;
    private final Button finishButton;

    private boolean released;

    public OmrGradingResultViewBinder(
            View rootView
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz do resultado da correção é obrigatória."
            );
        }

        context = rootView.getContext();
        layoutInflater = LayoutInflater.from(context);

        overallTextView = requireView(
                rootView,
                R.id.textOmrGradingOverall,
                TextView.class
        );

        answerKeyTextView = requireView(
                rootView,
                R.id.textOmrGradingAnswerKey,
                TextView.class
        );

        layoutTextView = requireView(
                rootView,
                R.id.textOmrGradingLayout,
                TextView.class
        );

        capturedAtTextView = requireView(
                rootView,
                R.id.textOmrGradingCapturedAt,
                TextView.class
        );

        percentageTextView = requireView(
                rootView,
                R.id.textOmrGradingPercentage,
                TextView.class
        );

        pointsTextView = requireView(
                rootView,
                R.id.textOmrGradingPoints,
                TextView.class
        );

        correctCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountCorrect,
                TextView.class
        );

        incorrectCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountIncorrect,
                TextView.class
        );

        blankCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountBlank,
                TextView.class
        );

        reviewCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountReview,
                TextView.class
        );

        multipleCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountMultiple,
                TextView.class
        );

        ambiguousCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountAmbiguous,
                TextView.class
        );

        notReadyCountTextView = requireView(
                rootView,
                R.id.textOmrGradingCountNotReady,
                TextView.class
        );

        reviewTextView = requireView(
                rootView,
                R.id.textOmrGradingReview,
                TextView.class
        );

        questionListView = requireView(
                rootView,
                R.id.listOmrGradingQuestions,
                ListView.class
        );

        questionAdapter = new QuestionAdapter();
        questionListView.setAdapter(questionAdapter);

        readAgainButton = requireView(
                rootView,
                R.id.buttonOmrGradingReadAgain,
                Button.class
        );

        finishButton = requireView(
                rootView,
                R.id.buttonOmrGradingFinish,
                Button.class
        );
    }

    public void render(
            OmrGradingResultViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual da correção é obrigatório."
            );
        }

        applyHeader(viewState);
        applyScore(viewState);
        applyCounts(viewState);
        applyReviewMessage(viewState);

        questionAdapter.replaceItems(
                viewState.getQuestionItems()
        );
    }

    public void setOnReadAgainClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        readAgainButton.setOnClickListener(listener);
    }

    public void setOnFinishClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        finishButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;

        readAgainButton.setOnClickListener(null);
        finishButton.setOnClickListener(null);

        questionAdapter.clear();
        questionListView.setAdapter(null);
    }

    private void applyHeader(
            OmrGradingResultViewState viewState
    ) {
        overallTextView.setText(
                resolveOverallText(viewState)
        );

        overallTextView.setTextColor(
                resolveOverallColor(viewState)
        );

        answerKeyTextView.setText(
                context.getString(
                        R.string.omr_grading_answer_key_format,
                        viewState.getAnswerKeyName(),
                        viewState.getAnswerKeyVersion()
                )
        );

        layoutTextView.setText(
                context.getString(
                        R.string.omr_grading_layout_format,
                        viewState.getLayoutName(),
                        viewState.getLayoutVersion()
                )
        );

        capturedAtTextView.setText(
                context.getString(
                        R.string.omr_grading_captured_at_format,
                        formatCapturedAt(
                                viewState.getCapturedAtEpochMillis()
                        )
                )
        );
    }

    private void applyScore(
            OmrGradingResultViewState viewState
    ) {
        percentageTextView.setText(
                context.getString(
                        R.string.omr_grading_percentage_format,
                        viewState.getRoundedAwardedPercentage()
                )
        );

        percentageTextView.setTextColor(
                resolveOverallColor(viewState)
        );

        pointsTextView.setText(
                context.getString(
                        R.string.omr_grading_points_format,
                        formatPoints(
                                viewState.getAwardedPoints()
                        ),
                        formatPoints(
                                viewState.getPossiblePoints()
                        )
                )
        );
    }

    private void applyCounts(
            OmrGradingResultViewState viewState
    ) {
        correctCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_correct_format,
                        viewState.getCorrectCount()
                )
        );

        incorrectCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_incorrect_format,
                        viewState.getIncorrectCount()
                )
        );

        blankCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_blank_format,
                        viewState.getBlankCount()
                )
        );

        reviewCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_review_format,
                        viewState.getReviewRequiredCount()
                )
        );

        multipleCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_multiple_format,
                        viewState.getMultipleMarkCount()
                )
        );

        ambiguousCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_ambiguous_format,
                        viewState.getAmbiguousCount()
                )
        );

        notReadyCountTextView.setText(
                context.getString(
                        R.string.omr_grading_count_not_ready_format,
                        viewState.getNotReadyCount()
                )
        );
    }

    private void applyReviewMessage(
            OmrGradingResultViewState viewState
    ) {
        int reviewCount =
                viewState.getReviewRequiredCount();

        if (reviewCount <= 0) {
            reviewTextView.setVisibility(View.GONE);
            return;
        }

        reviewTextView.setText(
                context
                .getResources()
                .getQuantityString(
                        R.plurals.omr_grading_review_message,
                        reviewCount,
                        reviewCount
                )
        );

        reviewTextView.setVisibility(View.VISIBLE);
    }

    private String resolveOverallText(
            OmrGradingResultViewState viewState
    ) {
        switch (viewState.getOverallState()) {
            case FINAL:
                return context.getString(
                        R.string.omr_grading_overall_final
                );

            case REQUIRES_REVIEW:
                return context.getString(
                        R.string.omr_grading_overall_review
                );

            case INCOMPLETE:
                return context.getString(
                        R.string.omr_grading_overall_incomplete
                );

            default:
                throw new IllegalStateException(
                        "Estado geral da correção não suportado: "
                                + viewState.getOverallState()
                );
        }
    }

    private int resolveOverallColor(
            OmrGradingResultViewState viewState
    ) {
        switch (viewState.getOverallState()) {
            case FINAL:
                return COLOR_SUCCESS;

            case REQUIRES_REVIEW:
                return COLOR_REVIEW;

            case INCOMPLETE:
                return COLOR_ERROR;

            default:
                throw new IllegalStateException(
                        "Estado geral da correção não suportado: "
                                + viewState.getOverallState()
                );
        }
    }

    private void bindQuestionItem(
            View itemView,
            OmrGradingResultViewState.QuestionItem item
    ) {
        View indicatorView = requireView(
                itemView,
                R.id.viewOmrGradeQuestionIndicator,
                View.class
        );

        TextView positionTextView = requireView(
                itemView,
                R.id.textOmrGradeQuestionPosition,
                TextView.class
        );

        TextView statusTextView = requireView(
                itemView,
                R.id.textOmrGradeQuestionStatus,
                TextView.class
        );

        TextView pointsTextView = requireView(
                itemView,
                R.id.textOmrGradeQuestionPoints,
                TextView.class
        );

        TextView confidenceTextView = requireView(
                itemView,
                R.id.textOmrGradeQuestionConfidence,
                TextView.class
        );

        positionTextView.setText(
                context.getString(
                        R.string.omr_grading_question_position_format,
                        item.getPosition()
                )
        );

        statusTextView.setText(
                resolveQuestionStatusText(item)
        );

        int statusColor =
                resolveQuestionStatusColor(
                        item.getState()
                );

        statusTextView.setTextColor(statusColor);
        indicatorView.setBackgroundColor(statusColor);

        pointsTextView.setText(
                context.getString(
                        R.string.omr_grading_question_points_format,
                        formatPoints(item.getAwardedPoints()),
                        formatPoints(item.getPossiblePoints())
                )
        );

        boolean confidenceVisible = item.isReady();

        if (confidenceVisible) {
            confidenceTextView.setText(
                    context.getString(
                            R.string
                            .omr_grading_question_confidence_format,
                            item.getConfidencePercent()
                    )
            );
        }

        confidenceTextView.setVisibility(
                confidenceVisible
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private String resolveQuestionStatusText(
            OmrGradingResultViewState.QuestionItem item
    ) {
        String acceptedLabels = joinLabels(
                item.getAcceptedOptionLabels()
        );

        switch (item.getState()) {
            case CORRECT:
                return context.getString(
                        R.string.omr_grading_question_correct_format,
                        item.getSelectedOptionLabel()
                );

            case INCORRECT:
                return context.getString(
                        R.string.omr_grading_question_incorrect_format,
                        item.getSelectedOptionLabel(),
                        acceptedLabels
                );

            case BLANK:
                return context.getString(
                        R.string.omr_grading_question_blank_format,
                        acceptedLabels
                );

            case MULTIPLE:
                return context.getString(
                        R.string.omr_grading_question_multiple_format,
                        joinLabels(item.getRelevantOptionLabels()),
                        acceptedLabels
                );

            case AMBIGUOUS:
                return context.getString(
                        R.string.omr_grading_question_ambiguous_format,
                        joinLabels(item.getRelevantOptionLabels()),
                        acceptedLabels
                );

            case NOT_READY:
                return context.getString(
                        R.string.omr_grading_question_not_ready_format,
                        acceptedLabels
                );

            default:
                throw new IllegalStateException(
                        "Estado de questão não suportado: "
                                + item.getState()
                );
        }
    }

    private int resolveQuestionStatusColor(
            OmrGradingResultViewState.QuestionState state
    ) {
        switch (state) {
            case CORRECT:
                return COLOR_SUCCESS;

            case INCORRECT:
                return COLOR_ERROR;

            case BLANK:
                return COLOR_NEUTRAL;

            case MULTIPLE:
                return COLOR_MULTIPLE;

            case AMBIGUOUS:
                return COLOR_AMBIGUOUS;

            case NOT_READY:
                return COLOR_ERROR;

            default:
                throw new IllegalStateException(
                        "Estado de questão não suportado: "
                                + state
                );
        }
    }

    private String joinLabels(
            List<String> labels
    ) {
        if (labels == null || labels.isEmpty()) {
            return "—";
        }

        return TextUtils.join(", ", labels);
    }

    private String formatCapturedAt(
            long capturedAtEpochMillis
    ) {
        DateFormat dateFormat =
                DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT
                );

        return dateFormat.format(
                new Date(capturedAtEpochMillis)
        );
    }

    private String formatPoints(
            double value
    ) {
        NumberFormat numberFormat =
                NumberFormat.getNumberInstance();

        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(2);

        return numberFormat.format(value);
    }

    /**
     * Mantém em memória somente as linhas visíveis da correção.
     */
    private final class QuestionAdapter
            extends BaseAdapter {

        private List<OmrGradingResultViewState.QuestionItem>
                items = Collections.emptyList();

        void replaceItems(
                List<OmrGradingResultViewState.QuestionItem>
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
        public OmrGradingResultViewState.QuestionItem
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
                        R.layout.item_omr_question_grade,
                        parent,
                        false
                );
            }

            bindQuestionItem(
                    itemView,
                    getItem(position)
            );

            return itemView;
        }
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O Binder da correção já foi liberado."
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
