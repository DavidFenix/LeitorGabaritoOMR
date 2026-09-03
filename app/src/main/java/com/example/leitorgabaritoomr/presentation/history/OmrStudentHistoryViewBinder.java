package com.example.leitorgabaritoomr.presentation.history;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Liga o estado imutavel do historico aos componentes visuais da tela.
 *
 * Esta classe nao carrega banco, nao cria o estado e nao abre Activities.
 * Essas decisoes permanecem no controlador da tela por meio do Listener.
 */
public final class OmrStudentHistoryViewBinder {

    public interface Listener {

        void onBackRequested();

        void onHistoryDetailsRequested(
                OmrStudentHistoryViewState.HistoryItem item
        );
    }

    private static final Locale DISPLAY_LOCALE =
            new Locale("pt", "BR");

    private static final int COLOR_FINAL_INDICATOR =
            0xFF16A34A;
    private static final int COLOR_FINAL_BACKGROUND =
            0xFFDCFCE7;
    private static final int COLOR_FINAL_TEXT =
            0xFF166534;

    private static final int COLOR_REVIEW_INDICATOR =
            0xFFD97706;
    private static final int COLOR_REVIEW_BACKGROUND =
            0xFFFEF3C7;
    private static final int COLOR_REVIEW_TEXT =
            0xFF92400E;

    private static final int COLOR_PENDING_INDICATOR =
            0xFFDC2626;
    private static final int COLOR_PENDING_BACKGROUND =
            0xFFFEE2E2;
    private static final int COLOR_PENDING_TEXT =
            0xFF991B1B;

    private final Resources resources;
    private final Listener listener;
    private final DateFormat storedAtDateFormat;
    private final DecimalFormat pointsFormat;

    private final TextView studentNameView;
    private final TextView studentIdentityView;
    private final TextView summaryView;

    private final TextView finalCountView;
    private final TextView reviewCountView;
    private final TextView pendingCountView;

    private final ListView historyListView;
    private final View emptyView;
    private final Button backButton;

    private final HistoryAdapter historyAdapter;

    public OmrStudentHistoryViewBinder(
            View rootView,
            Listener listener
    ) {
        this(
                rootView,
                listener,
                createDefaultDateFormat()
        );
    }

    /**
     * Construtor com formatador injetavel para manter testes de data
     * deterministas sem alterar o fuso horario global do dispositivo.
     */
    OmrStudentHistoryViewBinder(
            View rootView,
            Listener listener,
            DateFormat storedAtDateFormat
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A raiz da tela e obrigatoria."
            );
        }

        if (listener == null) {
            throw new IllegalArgumentException(
                    "O listener da tela e obrigatorio."
            );
        }

        if (storedAtDateFormat == null) {
            throw new IllegalArgumentException(
                    "O formatador de data e obrigatorio."
            );
        }

        Context context = rootView.getContext();

        resources = context.getResources();
        this.listener = listener;
        this.storedAtDateFormat = storedAtDateFormat;
        pointsFormat = createPointsFormat();

        studentNameView = findRequiredView(
                rootView,
                R.id.textOmrStudentHistoryName,
                TextView.class
        );

        studentIdentityView = findRequiredView(
                rootView,
                R.id.textOmrStudentHistoryIdentity,
                TextView.class
        );

        summaryView = findRequiredView(
                rootView,
                R.id.textOmrStudentHistorySummary,
                TextView.class
        );

        finalCountView = findRequiredView(
                rootView,
                R.id.textOmrStudentHistoryFinalCount,
                TextView.class
        );

        reviewCountView = findRequiredView(
                rootView,
                R.id.textOmrStudentHistoryReviewCount,
                TextView.class
        );

        pendingCountView = findRequiredView(
                rootView,
                R.id.textOmrStudentHistoryPendingCount,
                TextView.class
        );

        historyListView = findRequiredView(
                rootView,
                R.id.listOmrStudentHistory,
                ListView.class
        );

        emptyView = findRequiredView(
                rootView,
                R.id.containerOmrStudentHistoryEmpty,
                View.class
        );

        backButton = findRequiredView(
                rootView,
                R.id.buttonOmrStudentHistoryBack,
                Button.class
        );

        historyAdapter = new HistoryAdapter(context);
        historyListView.setAdapter(historyAdapter);

        backButton.setOnClickListener(
                ignored -> this.listener.onBackRequested()
        );
    }

    public void bind(
            OmrStudentHistoryViewState viewState
    ) {
        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado do historico e obrigatorio."
            );
        }

        bindStudent(viewState);
        bindCounters(viewState);

        historyAdapter.replaceItems(
                viewState.getHistoryItems()
        );

        boolean empty = viewState.isEmpty();

        historyListView.setVisibility(
                empty ? View.GONE : View.VISIBLE
        );

        emptyView.setVisibility(
                empty ? View.VISIBLE : View.GONE
        );
    }

    private void bindStudent(
            OmrStudentHistoryViewState viewState
    ) {
        OmrStudentIdentity student = viewState.getStudent();

        studentNameView.setText(student.getName());

        studentIdentityView.setText(formatString(
                R.string.omr_student_history_identity_format,
                student.getRegistration(),
                student.getClassName()
        ));

        int resultCount = viewState.getResultCount();

        String summaryTemplate =
                resources.getQuantityString(
                        R.plurals.omr_student_history_result_count,
                        resultCount
                );

        summaryView.setText(String.format(
                DISPLAY_LOCALE,
                summaryTemplate,
                resultCount
        ));
    }

    private void bindCounters(
            OmrStudentHistoryViewState viewState
    ) {
        finalCountView.setText(formatString(
                R.string.omr_student_history_final_count_format,
                viewState.getFinalResultCount()
        ));

        reviewCountView.setText(formatString(
                R.string.omr_student_history_review_count_format,
                viewState.getReviewRequiredCount()
        ));

        pendingCountView.setText(formatString(
                R.string.omr_student_history_pending_count_format,
                viewState.getPendingCount()
        ));
    }

    private String formatString(
            int stringResourceId,
            Object... arguments
    ) {
        return String.format(
                DISPLAY_LOCALE,
                resources.getString(stringResourceId),
                arguments
        );
    }

    private static DateFormat createDefaultDateFormat() {
        return new SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                DISPLAY_LOCALE
        );
    }

    private static DecimalFormat createPointsFormat() {
        DecimalFormat format = new DecimalFormat(
                "0.##",
                DecimalFormatSymbols.getInstance(
                        DISPLAY_LOCALE
                )
        );

        format.setGroupingUsed(false);
        return format;
    }

    private static <T extends View> T findRequiredView(
            View rootView,
            int viewId,
            Class<T> expectedClass
    ) {
        View foundView = rootView.findViewById(viewId);

        if (foundView == null) {
            throw new IllegalStateException(
                    "Componente obrigatorio ausente: "
                            + resourcesName(rootView, viewId)
            );
        }

        if (!expectedClass.isInstance(foundView)) {
            throw new IllegalStateException(
                    "Componente com tipo inesperado: "
                            + resourcesName(rootView, viewId)
            );
        }

        return expectedClass.cast(foundView);
    }

    private static String resourcesName(
            View rootView,
            int viewId
    ) {
        try {
            return rootView
                    .getResources()
                    .getResourceName(viewId);
        } catch (Resources.NotFoundException ignored) {
            return Integer.toString(viewId);
        }
    }

    private final class HistoryAdapter extends BaseAdapter {

        private final LayoutInflater inflater;
        private final List<OmrStudentHistoryViewState.HistoryItem>
                items = new ArrayList<>();

        private HistoryAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }

        private void replaceItems(
                List<OmrStudentHistoryViewState.HistoryItem>
                        newItems
        ) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public OmrStudentHistoryViewState.HistoryItem getItem(
                int position
        ) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {
            HistoryItemViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(
                        R.layout.item_omr_student_history,
                        parent,
                        false
                );

                holder = new HistoryItemViewHolder(
                        convertView
                );

                convertView.setTag(holder);
            } else {
                Object tag = convertView.getTag();

                if (!(tag instanceof HistoryItemViewHolder)) {
                    throw new IllegalStateException(
                            "Linha do historico sem ViewHolder valido."
                    );
                }

                holder = (HistoryItemViewHolder) tag;
            }

            holder.bind(getItem(position));
            return convertView;
        }
    }

    private final class HistoryItemViewHolder {

        private final View statusIndicatorView;
        private final TextView answerKeyView;
        private final TextView statusView;
        private final TextView storedAtView;
        private final TextView percentageView;
        private final TextView pointsView;
        private final Button detailsButton;

        private HistoryItemViewHolder(View itemView) {
            statusIndicatorView = findRequiredView(
                    itemView,
                    R.id.viewOmrStudentHistoryStatusIndicator,
                    View.class
            );

            answerKeyView = findRequiredView(
                    itemView,
                    R.id.textOmrStudentHistoryAnswerKey,
                    TextView.class
            );

            statusView = findRequiredView(
                    itemView,
                    R.id.textOmrStudentHistoryStatus,
                    TextView.class
            );

            storedAtView = findRequiredView(
                    itemView,
                    R.id.textOmrStudentHistoryStoredAt,
                    TextView.class
            );

            percentageView = findRequiredView(
                    itemView,
                    R.id.textOmrStudentHistoryPercentage,
                    TextView.class
            );

            pointsView = findRequiredView(
                    itemView,
                    R.id.textOmrStudentHistoryPoints,
                    TextView.class
            );

            detailsButton = findRequiredView(
                    itemView,
                    R.id.buttonOmrStudentHistoryDetails,
                    Button.class
            );
        }

        private void bind(
                OmrStudentHistoryViewState.HistoryItem item
        ) {
            answerKeyView.setText(formatString(
                    R.string.omr_student_history_answer_key_format,
                    item.getAnswerKeyName(),
                    item.getAnswerKeyVersion()
            ));

            storedAtView.setText(formatString(
                    R.string.omr_student_history_stored_at_format,
                    storedAtDateFormat.format(
                            new Date(
                                    item.getStoredAtEpochMillis()
                            )
                    )
            ));

            percentageView.setText(formatString(
                    R.string.omr_student_history_percentage_format,
                    item.getAwardedPercentage()
            ));

            pointsView.setText(formatString(
                    R.string.omr_student_history_points_format,
                    pointsFormat.format(
                            item.getAwardedPoints()
                    ),
                    pointsFormat.format(
                            item.getPossiblePoints()
                    )
            ));

            bindStatus(item);

            detailsButton.setOnClickListener(
                    ignored -> listener
                            .onHistoryDetailsRequested(item)
            );
        }

        private void bindStatus(
                OmrStudentHistoryViewState.HistoryItem item
        ) {
            if (item.isFinal()) {
                applyStatus(
                        R.string.omr_student_history_status_final,
                        COLOR_FINAL_INDICATOR,
                        COLOR_FINAL_BACKGROUND,
                        COLOR_FINAL_TEXT
                );
                return;
            }

            if (item.requiresReview()) {
                applyStatus(
                        R.string.omr_student_history_status_review,
                        COLOR_REVIEW_INDICATOR,
                        COLOR_REVIEW_BACKGROUND,
                        COLOR_REVIEW_TEXT
                );
                return;
            }

            applyStatus(
                    R.string.omr_student_history_status_pending,
                    COLOR_PENDING_INDICATOR,
                    COLOR_PENDING_BACKGROUND,
                    COLOR_PENDING_TEXT
            );
        }

        private void applyStatus(
                int statusStringResourceId,
                int indicatorColor,
                int backgroundColor,
                int textColor
        ) {
            String status = resources.getString(
                    statusStringResourceId
            );

            statusIndicatorView.setBackgroundColor(
                    indicatorColor
            );

            statusIndicatorView.setContentDescription(status);

            statusView.setText(status);
            statusView.setBackgroundColor(backgroundColor);
            statusView.setTextColor(textColor);

            percentageView.setTextColor(textColor);
        }
    }
}
