package com.example.leitorgabaritoomr.presentation.result;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import com.example.leitorgabaritoomr.R;

import java.text.DateFormat;
import java.util.Date;

/**
 * Aplica OmrReadingResultViewState ao layout Android da tela de
 * resultado.
 *
 * O Binder não conhece câmera, OpenCV, interpretação técnica ou
 * regras de classificação. Ele apenas apresenta o estado visual
 * previamente construído e testado.
 */
public final class OmrReadingResultViewBinder {

    private static final int COLOR_SUCCESS =
            Color.rgb(22, 163, 74);

    private static final int COLOR_NEUTRAL =
            Color.rgb(75, 85, 99);

    private static final int COLOR_MULTIPLE =
            Color.rgb(219, 39, 119);

    private static final int COLOR_AMBIGUOUS =
            Color.rgb(234, 88, 12);

    private static final int COLOR_WARNING =
            Color.rgb(217, 119, 6);

    private static final int COLOR_ERROR =
            Color.rgb(220, 38, 38);

    private final Context context;
    private final LayoutInflater layoutInflater;

    private final TextView overallTextView;
    private final TextView layoutTextView;
    private final TextView capturedAtTextView;

    private final TextView singleCountTextView;
    private final TextView blankCountTextView;
    private final TextView reviewCountTextView;
    private final TextView multipleCountTextView;
    private final TextView ambiguousCountTextView;
    private final TextView notReadyCountTextView;

    private final TextView reviewTextView;
    private final LinearLayout questionContainer;

    private final Button readAgainButton;
    private final Button finishButton;

    private boolean released;

    public OmrReadingResultViewBinder(
            View rootView
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz do resultado é obrigatória."
            );
        }

        context = rootView.getContext();
        layoutInflater = LayoutInflater.from(context);

        overallTextView = requireView(
                rootView,
                R.id.textOmrResultOverall,
                TextView.class
        );

        layoutTextView = requireView(
                rootView,
                R.id.textOmrResultLayout,
                TextView.class
        );

        capturedAtTextView = requireView(
                rootView,
                R.id.textOmrResultCapturedAt,
                TextView.class
        );

        singleCountTextView = requireView(
                rootView,
                R.id.textOmrResultCountSingle,
                TextView.class
        );

        blankCountTextView = requireView(
                rootView,
                R.id.textOmrResultCountBlank,
                TextView.class
        );

        reviewCountTextView = requireView(
                rootView,
                R.id.textOmrResultCountReview,
                TextView.class
        );

        multipleCountTextView = requireView(
                rootView,
                R.id.textOmrResultCountMultiple,
                TextView.class
        );

        ambiguousCountTextView = requireView(
                rootView,
                R.id.textOmrResultCountAmbiguous,
                TextView.class
        );

        notReadyCountTextView = requireView(
                rootView,
                R.id.textOmrResultCountNotReady,
                TextView.class
        );

        reviewTextView = requireView(
                rootView,
                R.id.textOmrResultReview,
                TextView.class
        );

        questionContainer = requireView(
                rootView,
                R.id.containerOmrResultQuestions,
                LinearLayout.class
        );

        readAgainButton = requireView(
                rootView,
                R.id.buttonOmrResultReadAgain,
                Button.class
        );

        finishButton = requireView(
                rootView,
                R.id.buttonOmrResultFinish,
                Button.class
        );
    }

    public void render(
            OmrReadingResultViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual do resultado é obrigatório."
            );
        }

        applyOverallState(viewState);

        layoutTextView.setText(
                context.getString(
                        R.string.omr_result_layout_format,
                        viewState.getLayoutName(),
                        viewState.getLayoutVersion()
                )
        );

        capturedAtTextView.setText(
                context.getString(
                        R.string.omr_result_captured_at_format,
                        formatCapturedAt(
                                viewState
                                .getCapturedAtEpochMillis()
                        )
                )
        );

        applySummary(viewState);
        applyReviewMessage(viewState);
        applyQuestionItems(viewState);
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
        questionContainer.removeAllViews();
    }

    private void applyOverallState(
            OmrReadingResultViewState viewState
    ) {
        @StringRes int textResource;
        int textColor;

        switch (viewState.getOverallState()) {
            case COMPLETED:
                textResource =
                        R.string
                        .omr_result_overall_completed;
                textColor = COLOR_SUCCESS;
                break;

            case COMPLETED_WITH_REVIEW:
                textResource =
                        R.string
                        .omr_result_overall_completed_with_review;
                textColor = COLOR_WARNING;
                break;

            case INCOMPLETE:
                textResource =
                        R.string
                        .omr_result_overall_incomplete;
                textColor = COLOR_ERROR;
                break;

            default:
                throw new IllegalStateException(
                        "Estado geral não suportado: "
                                + viewState.getOverallState()
                );
        }

        overallTextView.setText(textResource);
        overallTextView.setTextColor(textColor);
    }

    private void applySummary(
            OmrReadingResultViewState viewState
    ) {
        singleCountTextView.setText(
                context.getString(
                        R.string
                        .omr_result_count_single_format,
                        viewState.getSingleMarkCount()
                )
        );

        blankCountTextView.setText(
                context.getString(
                        R.string
                        .omr_result_count_blank_format,
                        viewState.getBlankCount()
                )
        );

        reviewCountTextView.setText(
                context.getString(
                        R.string
                        .omr_result_count_review_format,
                        viewState.getReviewRequiredCount()
                )
        );

        multipleCountTextView.setText(
                context.getString(
                        R.string
                        .omr_result_count_multiple_format,
                        viewState.getMultipleMarkCount()
                )
        );

        ambiguousCountTextView.setText(
                context.getString(
                        R.string
                        .omr_result_count_ambiguous_format,
                        viewState.getAmbiguousCount()
                )
        );

        notReadyCountTextView.setText(
                context.getString(
                        R.string
                        .omr_result_count_not_ready_format,
                        viewState.getNotReadyCount()
                )
        );
    }

    private void applyReviewMessage(
            OmrReadingResultViewState viewState
    ) {
        int reviewCount =
                viewState.getReviewRequiredCount();

        boolean visible = reviewCount > 0;

        if (visible) {
            reviewTextView.setText(
                    context
                    .getResources()
                    .getQuantityString(
                            R.plurals
                            .omr_result_review_message,
                            reviewCount,
                            reviewCount
                    )
            );
        }

        reviewTextView.setVisibility(
                visible ? View.VISIBLE : View.GONE
        );
    }

    private void applyQuestionItems(
            OmrReadingResultViewState viewState
    ) {
        questionContainer.removeAllViews();

        for (OmrReadingResultViewState.QuestionItem item
                : viewState.getQuestionItems()) {

            View itemView = layoutInflater.inflate(
                    R.layout.item_omr_question_result,
                    questionContainer,
                    false
            );

            bindQuestionItem(itemView, item);
            questionContainer.addView(itemView);
        }
    }

    private void bindQuestionItem(
            View itemView,
            OmrReadingResultViewState.QuestionItem item
    ) {
        TextView positionTextView = requireView(
                itemView,
                R.id.textOmrQuestionPosition,
                TextView.class
        );

        TextView statusTextView = requireView(
                itemView,
                R.id.textOmrQuestionStatus,
                TextView.class
        );

        TextView confidenceTextView = requireView(
                itemView,
                R.id.textOmrQuestionConfidence,
                TextView.class
        );

        View statusIndicator = requireView(
                itemView,
                R.id.viewOmrQuestionStatusIndicator,
                View.class
        );

        positionTextView.setText(
                context.getString(
                        R.string
                        .omr_result_question_position_format,
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
        statusIndicator.setBackgroundColor(statusColor);

        boolean confidenceVisible = item.isReady();

        if (confidenceVisible) {
            confidenceTextView.setText(
                    context.getString(
                            R.string
                            .omr_result_question_confidence_format,
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
            OmrReadingResultViewState.QuestionItem item
    ) {
        switch (item.getState()) {
            case ANSWERED:
                return context.getString(
                        R.string
                        .omr_result_question_answered_format,
                        item.getSelectedOptionLabel()
                );

            case BLANK:
                return context.getString(
                        R.string
                        .omr_result_question_blank
                );

            case MULTIPLE:
                return context.getString(
                        R.string
                        .omr_result_question_multiple_format,
                        joinRelevantLabels(item)
                );

            case AMBIGUOUS:
                return context.getString(
                        R.string
                        .omr_result_question_ambiguous_format,
                        joinRelevantLabels(item)
                );

            case NOT_READY:
                return context.getString(
                        R.string
                        .omr_result_question_not_ready
                );

            default:
                throw new IllegalStateException(
                        "Estado de questão não suportado: "
                                + item.getState()
                );
        }
    }

    private int resolveQuestionStatusColor(
            OmrReadingResultViewState.QuestionState state
    ) {
        switch (state) {
            case ANSWERED:
                return COLOR_SUCCESS;

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

    private String joinRelevantLabels(
            OmrReadingResultViewState.QuestionItem item
    ) {
        if (item.getRelevantOptionLabels().isEmpty()) {
            return "—";
        }

        return TextUtils.join(
                ", ",
                item.getRelevantOptionLabels()
        );
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

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O Binder do resultado já foi liberado."
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
