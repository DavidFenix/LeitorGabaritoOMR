package com.example.leitorgabaritoomr.presentation.assessment;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockResult;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Renderiza o relatório consolidado e encaminha ações de exportação. */
public final class OmrAssessmentResultViewBinder {

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView title;
    private final TextView assessment;
    private final TextView className;
    private final TextView summary;
    private final TextView average;
    private final TextView count;
    private final TextInputEditText search;
    private final ListView list;
    private final View noResults;
    private final ProgressBar progress;
    private final Button csvButton;
    private final Button xlsxButton;
    private final Button pdfButton;
    private final Button backButton;
    private final ResultAdapter adapter;
    private final TextWatcher searchWatcher;
    private final DateFormat dateFormat;

    private OmrAssessmentResultViewState currentState;
    private OnSearchQueryChangedListener searchListener;
    private boolean busy;
    private boolean released;

    public OmrAssessmentResultViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz dos resultados é obrigatória."
            );
        }
        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        title = requireView(rootView,
                R.id.textOmrAssessmentResultTitle, TextView.class);
        assessment = requireView(rootView,
                R.id.textOmrAssessmentResultAssessment, TextView.class);
        className = requireView(rootView,
                R.id.textOmrAssessmentResultClass, TextView.class);
        summary = requireView(rootView,
                R.id.textOmrAssessmentResultSummary, TextView.class);
        average = requireView(rootView,
                R.id.textOmrAssessmentResultAverage, TextView.class);
        count = requireView(rootView,
                R.id.textOmrAssessmentResultCount, TextView.class);
        search = requireView(rootView,
                R.id.editOmrAssessmentResultSearch,
                TextInputEditText.class);
        list = requireView(rootView,
                R.id.listOmrAssessmentResults, ListView.class);
        noResults = requireView(rootView,
                R.id.containerOmrAssessmentResultNoResults, View.class);
        progress = requireView(rootView,
                R.id.progressOmrAssessmentResult, ProgressBar.class);
        csvButton = requireView(rootView,
                R.id.buttonOmrAssessmentResultCsv, Button.class);
        xlsxButton = requireView(rootView,
                R.id.buttonOmrAssessmentResultXlsx, Button.class);
        pdfButton = requireView(rootView,
                R.id.buttonOmrAssessmentResultPdf, Button.class);
        backButton = requireView(rootView,
                R.id.buttonOmrAssessmentResultBack, Button.class);

        dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text, int start, int count, int after
            ) {
                // Nenhuma ação necessária.
            }

            @Override
            public void onTextChanged(
                    CharSequence text, int start, int before, int count
            ) {
                if (!released && !busy && searchListener != null) {
                    searchListener.onSearchQueryChanged(
                            text == null ? "" : text.toString()
                    );
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // O texto já foi encaminhado.
            }
        };
        search.addTextChangedListener(searchWatcher);
    }

    public void render(OmrAssessmentResultViewState state) {
        ensureNotReleased();
        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual dos resultados é obrigatório."
            );
        }
        currentState = state;
        OmrAssessmentResultReport report = state.getReport();
        title.setText(report.getAssessment().getTitle());
        assessment.setText(context.getString(
                R.string.omr_assessment_result_assessment,
                report.getAssessment().getSubject(),
                report.getAssessment().getVersion(),
                report.getAssessment().getSchoolYear()
        ));
        className.setText(context.getString(
                R.string.omr_assessment_result_class,
                report.getFrozenClassName(),
                report.getApplication().getApplicationId()
        ));
        summary.setText(context.getString(
                R.string.omr_assessment_result_summary,
                report.getCorrectedCount(),
                report.getParticipantCount(),
                context.getResources().getQuantityString(
                        R.plurals.omr_assessment_result_pending_summary,
                        pluralSelector(report.getPendingCount()),
                        report.getPendingCount()
                ),
                context.getResources().getQuantityString(
                        R.plurals.omr_assessment_result_issue_summary,
                        pluralSelector(report.getDataIntegrityIssueCount()),
                        report.getDataIntegrityIssueCount()
                )
        ));
        average.setText(context.getString(
                R.string.omr_assessment_result_average,
                report.getAveragePercentage()
        ));
        if (state.hasQuery()) {
            count.setText(context.getString(
                    R.string.omr_assessment_result_filtered_count,
                    state.getVisibleCount(),
                    report.getParticipantCount()
            ));
        } else {
            count.setText(context.getResources().getQuantityString(
                    R.plurals.omr_assessment_result_count,
                    pluralSelector(report.getParticipantCount()),
                    report.getParticipantCount()
            ));
        }
        adapter.replace(state.getVisibleRows());
        applyVisibility();
    }

    public String getSearchQuery() {
        ensureNotReleased();
        Editable value = search.getText();
        return value == null ? "" : value.toString();
    }

    public void setBusy(boolean busy) {
        ensureNotReleased();
        this.busy = busy;
        search.setEnabled(!busy);
        list.setEnabled(!busy);
        csvButton.setEnabled(!busy && currentState != null);
        xlsxButton.setEnabled(!busy && currentState != null);
        pdfButton.setEnabled(!busy && currentState != null);
        backButton.setEnabled(!busy);
        applyVisibility();
    }

    public void setOnSearchQueryChangedListener(
            OnSearchQueryChangedListener listener
    ) {
        ensureNotReleased();
        searchListener = listener;
    }

    public void setOnCsvClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        csvButton.setOnClickListener(listener);
    }

    public void setOnXlsxClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        xlsxButton.setOnClickListener(listener);
    }

    public void setOnPdfClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        pdfButton.setOnClickListener(listener);
    }

    public void setOnBackClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        backButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        searchListener = null;
        search.removeTextChangedListener(searchWatcher);
        csvButton.setOnClickListener(null);
        xlsxButton.setOnClickListener(null);
        pdfButton.setOnClickListener(null);
        backButton.setOnClickListener(null);
        adapter.replace(Collections.emptyList());
        list.setAdapter(null);
    }

    private void applyVisibility() {
        if (busy && currentState == null) {
            progress.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
            noResults.setVisibility(View.GONE);
            return;
        }
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        boolean empty = currentState != null && currentState.hasNoMatches();
        noResults.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder dos resultados já foi liberado."
            );
        }
    }

    private static <T extends View> T requireView(
            View root,
            @IdRes int id,
            Class<T> type
    ) {
        View view = root.findViewById(id);
        if (!type.isInstance(view)) {
            throw new IllegalStateException(
                    "View obrigatória ausente ou incorreta: " + id
            );
        }
        return type.cast(view);
    }

    private final class ResultAdapter extends BaseAdapter {
        private List<OmrAssessmentResultReport.Row> rows =
                Collections.emptyList();

        void replace(List<OmrAssessmentResultReport.Row> newRows) {
            rows = new ArrayList<>(newRows);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public OmrAssessmentResultReport.Row getItem(int position) {
            return rows.get(position);
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
            View item = convertView == null
                    ? inflater.inflate(
                    R.layout.item_omr_assessment_result,
                    parent,
                    false
            ) : convertView;
            OmrAssessmentResultReport.Row row = getItem(position);
            TextView positionView = requireView(item,
                    R.id.textOmrAssessmentResultItemPosition,
                    TextView.class);
            TextView nameView = requireView(item,
                    R.id.textOmrAssessmentResultItemName,
                    TextView.class);
            TextView registrationView = requireView(item,
                    R.id.textOmrAssessmentResultItemRegistration,
                    TextView.class);
            TextView gradeView = requireView(item,
                    R.id.textOmrAssessmentResultItemGrade,
                    TextView.class);
            TextView blocksView = requireView(item,
                    R.id.textOmrAssessmentResultItemBlocks,
                    TextView.class);
            TextView statusView = requireView(item,
                    R.id.textOmrAssessmentResultItemStatus,
                    TextView.class);

            positionView.setText(context.getString(
                    R.string.omr_assessment_result_position,
                    row.getParticipant().getRosterPosition()
            ));
            nameView.setText(row.getParticipant().getStudentName());
            registrationView.setText(context.getString(
                    R.string.omr_assessment_result_registration,
                    row.getParticipant().getRegistration(),
                    row.getParticipant().getStudentId()
            ));
            if (row.isCorrected()) {
                gradeView.setText(context.getString(
                        R.string.omr_assessment_result_grade,
                        row.getAwardedPoints(),
                        row.getPossiblePoints(),
                        row.getAwardedPercentage()
                ));
            } else {
                gradeView.setText(R.string.omr_assessment_result_no_grade);
            }

            if (row.hasBlockBreakdown()) {
                blocksView.setVisibility(View.VISIBLE);
                blocksView.setText(formatBlockBreakdown(row));
            } else {
                blocksView.setVisibility(View.GONE);
                blocksView.setText("");
            }

            String recordedAt = row.getRecordedAtEpochMillis() <= 0L
                    ? ""
                    : dateFormat.format(new Date(
                    row.getRecordedAtEpochMillis()
            ));
            switch (row.getDetailStatus()) {
                case FINAL:
                    statusView.setText(context.getString(
                            R.string.omr_assessment_result_corrected,
                            row.getRevision(),
                            recordedAt
                    ));
                    statusView.setTextColor(0xFF047857);
                    break;
                case ZERO:
                    statusView.setText(context.getString(
                            R.string.omr_assessment_result_zero,
                            row.getRevision(),
                            recordedAt
                    ));
                    statusView.setTextColor(0xFF047857);
                    break;
                case IN_REVIEW:
                    statusView.setText(context.getString(
                            R.string.omr_assessment_result_review,
                            row.getRevision(),
                            recordedAt
                    ));
                    statusView.setTextColor(0xFFB45309);
                    break;
                case ABSENT:
                    gradeView.setText(R.string.omr_assessment_result_no_grade);
                    statusView.setText(context.getString(
                            R.string.omr_assessment_result_missing_history,
                            row.getRevision()
                    ));
                    statusView.setTextColor(0xFFB91C1C);
                    break;
                case INCONSISTENT:
                    statusView.setText(context.getString(
                            R.string.omr_assessment_result_inconsistent,
                            row.getRevision()
                    ));
                    statusView.setTextColor(0xFFB91C1C);
                    break;
                case PENDING:
                    gradeView.setText(R.string.omr_assessment_result_no_grade);
                    statusView.setText(
                            R.string.omr_assessment_result_pending
                    );
                    statusView.setTextColor(0xFFB45309);
                    break;
                default:
                    throw new IllegalStateException(
                            "Situação de resultado desconhecida."
                    );
            }
            return item;
        }

        private String formatBlockBreakdown(
                OmrAssessmentResultReport.Row row
        ) {
            StringBuilder text = new StringBuilder();
            for (OmrAssessmentBlockResult result
                    : row.getBlockBreakdownOrNull().getBlockResults()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                OmrAssessmentBlock block = result.getBlock();
                if (block.hasAreaName()) {
                    text.append(block.getAreaName()).append(" • ");
                }
                if (block.hasComponentCode()) {
                    text.append(block.getComponentCode()).append(" — ");
                }
                text.append(block.getComponentName())
                        .append(": ")
                        .append(result.getCorrectCount())
                        .append('/')
                        .append(result.getQuestionCount())
                        .append(" acertos • nota ")
                        .append(localizedNumber(
                                result.getCalculatedScore().toPlainString()
                        ))
                        .append('/')
                        .append(localizedNumber(
                                result.getMaximumScore().toPlainString()
                        ));
                if (result.requiresReview()) {
                    text.append(" • provisória");
                } else if (result.getCalculatedScore().signum() == 0) {
                    text.append(" • zero");
                }
            }
            return text.toString();
        }

        private String localizedNumber(String value) {
            return Locale.getDefault().getLanguage().equals("pt")
                    ? value.replace('.', ',')
                    : value;
        }
    }

    private static int pluralSelector(int value) {
        return value == 1 ? 1 : 2;
    }
}
