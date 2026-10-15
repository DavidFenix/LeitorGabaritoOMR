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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Renderiza as versões mais recentes das provas e encaminha suas ações. */
public final class OmrAssessmentListViewBinder {

    public interface OnAssessmentActionListener {
        void onCreateNewVersion(String assessmentId);

        void onManageStructure(String assessmentId);
    }

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView countTextView;
    private final TextInputEditText searchEditText;
    private final ListView assessmentListView;
    private final View emptyContainer;
    private final View noResultsContainer;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button createButton;
    private final Button importCsvButton;
    private final Button importBlocksCsvButton;
    private final AssessmentAdapter adapter;
    private final TextWatcher searchWatcher;

    private OmrAssessmentListViewState currentState;
    private OnAssessmentActionListener assessmentActionListener;
    private OnSearchQueryChangedListener searchListener;
    private boolean busy;
    private boolean released;

    public OmrAssessmentListViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da lista de provas é obrigatória."
            );
        }

        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        countTextView = requireView(
                rootView,
                R.id.textOmrAssessmentListCount,
                TextView.class
        );
        searchEditText = requireView(
                rootView,
                R.id.editOmrAssessmentSearch,
                TextInputEditText.class
        );
        assessmentListView = requireView(
                rootView,
                R.id.listOmrAssessments,
                ListView.class
        );
        emptyContainer = requireView(
                rootView,
                R.id.containerOmrAssessmentListEmpty,
                View.class
        );
        noResultsContainer = requireView(
                rootView,
                R.id.containerOmrAssessmentListNoResults,
                View.class
        );
        progressBar = requireView(
                rootView,
                R.id.progressOmrAssessmentList,
                ProgressBar.class
        );
        backButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentListBack,
                Button.class
        );
        createButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentListCreate,
                Button.class
        );
        importCsvButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentListImportCsv,
                Button.class
        );
        importBlocksCsvButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentListImportBlocksCsv,
                Button.class
        );

        adapter = new AssessmentAdapter();
        assessmentListView.setAdapter(adapter);

        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
                // Nenhuma ação necessária.
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
                notifySearchChanged(text);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // O valor já foi encaminhado em onTextChanged.
            }
        };

        searchEditText.addTextChangedListener(searchWatcher);
    }

    public void render(OmrAssessmentListViewState state) {
        ensureNotReleased();

        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual da lista de provas é obrigatório."
            );
        }

        currentState = state;
        applyCount(state);
        adapter.replaceItems(state.getVisibleAssessments());
        applyVisibility();
    }

    public String getSearchQuery() {
        ensureNotReleased();
        Editable value = searchEditText.getText();
        return value == null ? "" : value.toString();
    }

    public void setBusy(boolean busy) {
        ensureNotReleased();
        this.busy = busy;
        searchEditText.setEnabled(!busy);
        assessmentListView.setEnabled(!busy);
        createButton.setEnabled(!busy);
        importCsvButton.setEnabled(!busy);
        importBlocksCsvButton.setEnabled(!busy);
        applyVisibility();
    }

    public void setOnAssessmentActionListener(
            OnAssessmentActionListener listener
    ) {
        ensureNotReleased();
        assessmentActionListener = listener;
    }

    public void setOnSearchQueryChangedListener(
            OnSearchQueryChangedListener listener
    ) {
        ensureNotReleased();
        searchListener = listener;
    }

    public void setOnBackClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        backButton.setOnClickListener(listener);
    }

    public void setOnCreateClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        createButton.setOnClickListener(listener);
    }

    public void setOnImportCsvClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        importCsvButton.setOnClickListener(listener);
    }

    public void setOnImportBlocksCsvClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        importBlocksCsvButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;
        assessmentActionListener = null;
        searchListener = null;
        searchEditText.removeTextChangedListener(searchWatcher);
        backButton.setOnClickListener(null);
        createButton.setOnClickListener(null);
        importCsvButton.setOnClickListener(null);
        importBlocksCsvButton.setOnClickListener(null);
        adapter.clear();
        assessmentListView.setAdapter(null);
    }

    private void notifySearchChanged(CharSequence text) {
        if (released || busy) {
            return;
        }

        OnSearchQueryChangedListener listener = searchListener;
        if (listener != null) {
            listener.onSearchQueryChanged(
                    text == null ? "" : text.toString()
            );
        }
    }

    private void notifyCreateNewVersion(
            OmrAssessmentListViewState.AssessmentItem item
    ) {
        OnAssessmentActionListener listener = assessmentActionListener;
        if (listener != null && !busy) {
            listener.onCreateNewVersion(item.getAssessmentId());
        }
    }

    private void notifyManageStructure(
            OmrAssessmentListViewState.AssessmentItem item
    ) {
        OnAssessmentActionListener listener = assessmentActionListener;
        if (listener != null && !busy) {
            listener.onManageStructure(item.getAssessmentId());
        }
    }

    private void applyCount(OmrAssessmentListViewState state) {
        if (state.hasQuery()) {
            countTextView.setText(context.getString(
                    R.string.omr_assessment_list_filtered_count,
                    state.getVisibleCount(),
                    state.getTotalCount()
            ));
            return;
        }

        countTextView.setText(context.getResources().getQuantityString(
                R.plurals.omr_assessment_list_count,
                state.getTotalCount(),
                state.getTotalCount()
        ));
    }

    private void applyVisibility() {
        if (busy) {
            progressBar.setVisibility(View.VISIBLE);
            assessmentListView.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
            noResultsContainer.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.GONE);
        boolean catalogEmpty = currentState != null
                && currentState.isCatalogEmpty();
        boolean noResults = currentState != null
                && currentState.hasNoMatches();

        emptyContainer.setVisibility(
                catalogEmpty ? View.VISIBLE : View.GONE
        );
        noResultsContainer.setVisibility(
                noResults ? View.VISIBLE : View.GONE
        );
        assessmentListView.setVisibility(
                !catalogEmpty && !noResults
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da lista de provas já foi liberado."
            );
        }
    }

    private static <T extends View> T requireView(
            View rootView,
            @IdRes int viewId,
            Class<T> expectedType
    ) {
        View view = rootView.findViewById(viewId);
        if (!expectedType.isInstance(view)) {
            throw new IllegalStateException(
                    "View obrigatória ausente ou com tipo incorreto: "
                            + viewId
            );
        }
        return expectedType.cast(view);
    }

    private final class AssessmentAdapter extends BaseAdapter {
        private List<OmrAssessmentListViewState.AssessmentItem> items =
                Collections.emptyList();

        private void replaceItems(
                List<OmrAssessmentListViewState.AssessmentItem> newItems
        ) {
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        private void clear() {
            items = Collections.emptyList();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public OmrAssessmentListViewState.AssessmentItem getItem(
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
            View itemView = convertView == null
                    ? inflater.inflate(
                    R.layout.item_omr_assessment,
                    parent,
                    false
            )
                    : convertView;

            OmrAssessmentListViewState.AssessmentItem item =
                    getItem(position);

            TextView title = requireView(
                    itemView,
                    R.id.textOmrAssessmentItemTitle,
                    TextView.class
            );
            TextView metadata = requireView(
                    itemView,
                    R.id.textOmrAssessmentItemMetadata,
                    TextView.class
            );
            TextView periodVersion = requireView(
                    itemView,
                    R.id.textOmrAssessmentItemPeriodVersion,
                    TextView.class
            );
            TextView answerKey = requireView(
                    itemView,
                    R.id.textOmrAssessmentItemAnswerKey,
                    TextView.class
            );
            TextView structure = requireView(
                    itemView,
                    R.id.textOmrAssessmentItemStructure,
                    TextView.class
            );
            Button newVersion = requireView(
                    itemView,
                    R.id.buttonOmrAssessmentItemNewVersion,
                    Button.class
            );
            Button manageStructure = requireView(
                    itemView,
                    R.id.buttonOmrAssessmentItemStructure,
                    Button.class
            );

            title.setText(item.getTitle());
            metadata.setText(context.getString(
                    R.string.omr_assessment_list_metadata,
                    item.getSubject(),
                    item.getGradeLevel(),
                    item.getSchoolYear()
            ));
            periodVersion.setText(context.getString(
                    R.string.omr_assessment_list_period_version,
                    item.getAcademicPeriod(),
                    item.getVersion()
            ));
            answerKey.setText(context.getString(
                    R.string.omr_assessment_list_answer_key,
                    item.getAnswerKeyId(),
                    item.getAnswerKeyVersion()
            ));
            if (item.hasPublishedStructure()) {
                structure.setText(context.getResources().getQuantityString(
                        R.plurals.omr_assessment_list_structure_summary,
                        item.getBlockCount(),
                        item.getBlockCount(),
                        item.getStructureQuestionCount()
                ));
                structure.setTextColor(0xFF047857);
                manageStructure.setText(
                        R.string.omr_assessment_list_action_view_blocks
                );
            } else {
                structure.setText(
                        R.string.omr_assessment_list_structure_missing
                );
                structure.setTextColor(0xFFB45309);
                manageStructure.setText(
                        R.string.omr_assessment_list_action_configure_blocks
                );
            }
            newVersion.setOnClickListener(
                    view -> notifyCreateNewVersion(item)
            );
            manageStructure.setOnClickListener(
                    view -> notifyManageStructure(item)
            );

            return itemView;
        }
    }
}
