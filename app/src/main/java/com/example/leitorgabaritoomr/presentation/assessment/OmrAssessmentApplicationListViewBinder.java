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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Renderiza aplicações de prova e o progresso agregado das correções. */
public final class OmrAssessmentApplicationListViewBinder {

    public interface OnApplicationActionListener {
        void onOpenFrozenRoster(String applicationId);
    }

    public interface OnResultActionListener {
        void onOpenResults(String applicationId);
    }

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    public interface OnIdentifierCopyListener {
        void onCopyIdentifier(String identifier);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView countTextView;
    private final TextInputEditText searchEditText;
    private final ListView applicationListView;
    private final View emptyContainer;
    private final View noResultsContainer;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button createButton;
    private final Button importCsvButton;
    private final ApplicationAdapter adapter;
    private final TextWatcher searchWatcher;
    private final DateFormat dateFormat;

    private OmrAssessmentApplicationListViewState currentState;
    private OnApplicationActionListener applicationActionListener;
    private OnResultActionListener resultActionListener;
    private OnSearchQueryChangedListener searchListener;
    private OnIdentifierCopyListener identifierCopyListener;
    private boolean busy;
    private boolean released;

    public OmrAssessmentApplicationListViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz das aplicações é obrigatória."
            );
        }

        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        countTextView = requireView(
                rootView,
                R.id.textOmrAssessmentApplicationListCount,
                TextView.class
        );
        searchEditText = requireView(
                rootView,
                R.id.editOmrAssessmentApplicationSearch,
                TextInputEditText.class
        );
        applicationListView = requireView(
                rootView,
                R.id.listOmrAssessmentApplications,
                ListView.class
        );
        emptyContainer = requireView(
                rootView,
                R.id.containerOmrAssessmentApplicationListEmpty,
                View.class
        );
        noResultsContainer = requireView(
                rootView,
                R.id.containerOmrAssessmentApplicationListNoResults,
                View.class
        );
        progressBar = requireView(
                rootView,
                R.id.progressOmrAssessmentApplicationList,
                ProgressBar.class
        );
        backButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentApplicationListBack,
                Button.class
        );
        createButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentApplicationListCreate,
                Button.class
        );
        importCsvButton = requireView(
                rootView,
                R.id.buttonOmrAssessmentApplicationListImportCsv,
                Button.class
        );

        dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );
        adapter = new ApplicationAdapter();
        applicationListView.setAdapter(adapter);

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
                // O texto já foi encaminhado.
            }
        };
        searchEditText.addTextChangedListener(searchWatcher);
    }

    public void render(OmrAssessmentApplicationListViewState state) {
        ensureNotReleased();
        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual das aplicações é obrigatório."
            );
        }
        currentState = state;
        applyCount(state);
        adapter.replaceItems(state.getVisibleApplications());
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
        applicationListView.setEnabled(!busy);
        backButton.setEnabled(!busy);
        createButton.setEnabled(!busy);
        importCsvButton.setEnabled(!busy);
        applyVisibility();
    }

    public void setOnApplicationActionListener(
            OnApplicationActionListener listener
    ) {
        ensureNotReleased();
        applicationActionListener = listener;
    }

    public void setOnResultActionListener(
            OnResultActionListener listener
    ) {
        ensureNotReleased();
        resultActionListener = listener;
    }

    public void setOnSearchQueryChangedListener(
            OnSearchQueryChangedListener listener
    ) {
        ensureNotReleased();
        searchListener = listener;
    }

    public void setOnIdentifierCopyListener(
            OnIdentifierCopyListener listener
    ) {
        ensureNotReleased();
        identifierCopyListener = listener;
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

    public void release() {
        if (released) {
            return;
        }
        released = true;
        applicationActionListener = null;
        resultActionListener = null;
        searchListener = null;
        identifierCopyListener = null;
        searchEditText.removeTextChangedListener(searchWatcher);
        backButton.setOnClickListener(null);
        createButton.setOnClickListener(null);
        importCsvButton.setOnClickListener(null);
        adapter.clear();
        applicationListView.setAdapter(null);
    }

    private void notifySearchChanged(CharSequence text) {
        if (released || busy || searchListener == null) {
            return;
        }
        searchListener.onSearchQueryChanged(
                text == null ? "" : text.toString()
        );
    }

    private void notifyOpenRoster(
            OmrAssessmentApplicationListViewState.ApplicationItem item
    ) {
        if (!busy && applicationActionListener != null) {
            applicationActionListener.onOpenFrozenRoster(
                    item.getApplicationId()
            );
        }
    }

    private void notifyOpenResults(
            OmrAssessmentApplicationListViewState.ApplicationItem item
    ) {
        if (!busy && resultActionListener != null) {
            resultActionListener.onOpenResults(item.getApplicationId());
        }
    }

    private void notifyCopyIdentifier(String identifier) {
        if (!busy && identifierCopyListener != null) {
            identifierCopyListener.onCopyIdentifier(identifier);
        }
    }

    private void applyCount(OmrAssessmentApplicationListViewState state) {
        if (state.hasQuery()) {
            countTextView.setText(context.getString(
                    R.string.omr_assessment_application_list_filtered_count,
                    state.getVisibleCount(),
                    state.getTotalCount()
            ));
            return;
        }
        countTextView.setText(context.getResources().getQuantityString(
                R.plurals.omr_assessment_application_list_count,
                pluralSelector(state.getTotalCount()),
                state.getTotalCount()
        ));
    }

    private void applyVisibility() {
        if (busy) {
            progressBar.setVisibility(View.VISIBLE);
            applicationListView.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
            noResultsContainer.setVisibility(View.GONE);
            return;
        }
        progressBar.setVisibility(View.GONE);
        boolean empty = currentState != null
                && currentState.isCatalogEmpty();
        boolean noResults = currentState != null
                && currentState.hasNoMatches();
        emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        noResultsContainer.setVisibility(
                noResults ? View.VISIBLE : View.GONE
        );
        applicationListView.setVisibility(
                !empty && !noResults ? View.VISIBLE : View.GONE
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder das aplicações já foi liberado."
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
                    "View obrigatória ausente ou incorreta: " + viewId
            );
        }
        return expectedType.cast(view);
    }

    private final class ApplicationAdapter extends BaseAdapter {
        private List<OmrAssessmentApplicationListViewState.ApplicationItem>
                items = Collections.emptyList();

        private void replaceItems(
                List<OmrAssessmentApplicationListViewState.ApplicationItem>
                        newItems
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
        public OmrAssessmentApplicationListViewState.ApplicationItem getItem(
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
                    R.layout.item_omr_assessment_application,
                    parent,
                    false
            ) : convertView;
            OmrAssessmentApplicationListViewState.ApplicationItem item =
                    getItem(position);

            TextView title = requireView(
                    itemView,
                    R.id.textOmrAssessmentApplicationItemTitle,
                    TextView.class
            );
            TextView assessment = requireView(
                    itemView,
                    R.id.textOmrAssessmentApplicationItemAssessment,
                    TextView.class
            );
            TextView className = requireView(
                    itemView,
                    R.id.textOmrAssessmentApplicationItemClass,
                    TextView.class
            );
            TextView progress = requireView(
                    itemView,
                    R.id.textOmrAssessmentApplicationItemProgress,
                    TextView.class
            );
            TextView identity = requireView(
                    itemView,
                    R.id.textOmrAssessmentApplicationItemIdentity,
                    TextView.class
            );
            Button roster = requireView(
                    itemView,
                    R.id.buttonOmrAssessmentApplicationItemRoster,
                    Button.class
            );
            Button results = requireView(
                    itemView,
                    R.id.buttonOmrAssessmentApplicationItemResults,
                    Button.class
            );

            title.setText(item.getAssessmentTitle());
            assessment.setText(context.getString(
                    R.string.omr_assessment_application_list_assessment,
                    item.getSubject(),
                    item.getAssessmentVersion(),
                    item.getSchoolYear()
            ));
            className.setText(context.getString(
                    R.string.omr_assessment_application_list_class,
                    item.getFrozenClassName()
            ));
            if (item.isComplete()) {
                progress.setText(context.getString(
                        R.string
                                .omr_assessment_application_list_progress_complete,
                        item.getCorrectedParticipantCount(),
                        item.getParticipantCount()
                ));
            } else {
                progress.setText(context.getResources().getQuantityString(
                        R.plurals.omr_assessment_application_list_progress,
                        pluralSelector(item.getPendingParticipantCount()),
                        item.getCorrectedParticipantCount(),
                        item.getParticipantCount(),
                        item.getPendingParticipantCount()
                ));
            }
            identity.setText(context.getString(
                    R.string.omr_assessment_application_list_identity,
                    item.getApplicationId(),
                    dateFormat.format(new Date(
                            item.getCreatedAtEpochMillis()
                    ))
            ));
            identity.setOnClickListener(
                    view -> notifyCopyIdentifier(item.getApplicationId())
            );
            roster.setOnClickListener(view -> notifyOpenRoster(item));
            results.setOnClickListener(view -> notifyOpenResults(item));
            itemView.setOnClickListener(view -> notifyOpenRoster(item));
            return itemView;
        }
    }

    private static int pluralSelector(int value) {
        return value == 1 ? 1 : 2;
    }
}
