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

/** Renderiza a fotografia imutável da chamada de uma aplicação. */
public final class OmrAssessmentApplicationRosterViewBinder {

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    public interface OnParticipantCorrectionListener {
        void onCorrectParticipant(String studentId);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView titleTextView;
    private final TextView assessmentTextView;
    private final TextView classTextView;
    private final TextView progressTextView;
    private final TextView createdAtTextView;
    private final TextView countTextView;
    private final TextInputEditText searchEditText;
    private final ListView participantListView;
    private final View noResultsContainer;
    private final ProgressBar progressBar;
    private final Button nextPendingButton;
    private final Button resultsButton;
    private final Button backButton;
    private final ParticipantAdapter adapter;
    private final TextWatcher searchWatcher;
    private final DateFormat dateFormat;

    private OmrAssessmentApplicationRosterViewState currentState;
    private OnSearchQueryChangedListener searchListener;
    private OnParticipantCorrectionListener participantCorrectionListener;
    private boolean busy;
    private boolean released;

    public OmrAssessmentApplicationRosterViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da chamada congelada é obrigatória."
            );
        }
        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        titleTextView = requireView(rootView,
                R.id.textOmrApplicationRosterTitle, TextView.class);
        assessmentTextView = requireView(rootView,
                R.id.textOmrApplicationRosterAssessment, TextView.class);
        classTextView = requireView(rootView,
                R.id.textOmrApplicationRosterClass, TextView.class);
        progressTextView = requireView(rootView,
                R.id.textOmrApplicationRosterProgress, TextView.class);
        createdAtTextView = requireView(rootView,
                R.id.textOmrApplicationRosterCreatedAt, TextView.class);
        countTextView = requireView(rootView,
                R.id.textOmrApplicationRosterCount, TextView.class);
        searchEditText = requireView(rootView,
                R.id.editOmrApplicationRosterSearch,
                TextInputEditText.class);
        participantListView = requireView(rootView,
                R.id.listOmrApplicationRoster, ListView.class);
        noResultsContainer = requireView(rootView,
                R.id.containerOmrApplicationRosterNoResults, View.class);
        progressBar = requireView(rootView,
                R.id.progressOmrApplicationRoster, ProgressBar.class);
        nextPendingButton = requireView(rootView,
                R.id.buttonOmrApplicationRosterNextPending, Button.class);
        resultsButton = requireView(rootView,
                R.id.buttonOmrApplicationRosterResults, Button.class);
        backButton = requireView(rootView,
                R.id.buttonOmrApplicationRosterBack, Button.class);

        dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );
        adapter = new ParticipantAdapter();
        participantListView.setAdapter(adapter);
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
        searchEditText.addTextChangedListener(searchWatcher);
    }

    public void render(OmrAssessmentApplicationRosterViewState state) {
        ensureNotReleased();
        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual da chamada é obrigatório."
            );
        }
        currentState = state;
        titleTextView.setText(state.getAssessmentTitle());
        assessmentTextView.setText(context.getString(
                R.string.omr_application_roster_assessment,
                state.getSubject(),
                state.getAssessmentVersion(),
                state.getAssessmentId()
        ));
        classTextView.setText(context.getString(
                R.string.omr_application_roster_class,
                state.getFrozenClassName()
        ));
        if (state.getRecoveryCount() > 0) {
            progressTextView.setText(context.getString(
                    R.string.omr_application_roster_progress_recovery,
                    state.getCorrectedCount(),
                    state.getTotalCount(),
                    state.getPendingCount(),
                    state.getRecoveryCount()
            ));
        } else {
            progressTextView.setText(context.getResources().getQuantityString(
                    R.plurals.omr_application_roster_progress,
                    pluralSelector(state.getPendingCount()),
                    state.getCorrectedCount(),
                    state.getTotalCount(),
                    state.getPendingCount()
            ));
        }
        createdAtTextView.setText(context.getString(
                R.string.omr_application_roster_created,
                dateFormat.format(new Date(
                        state.getCreatedAtEpochMillis()
                )),
                state.getApplicationId()
        ));
        if (state.hasQuery()) {
            countTextView.setText(context.getString(
                    R.string.omr_application_roster_filtered_count,
                    state.getVisibleCount(),
                    state.getTotalCount()
            ));
        } else {
            countTextView.setText(context.getResources().getQuantityString(
                    R.plurals.omr_application_roster_count,
                    pluralSelector(state.getTotalCount()),
                    state.getTotalCount()
            ));
        }
        adapter.replaceItems(state.getVisibleParticipants());
        boolean hasPending = state.getRemainingCount() > 0;
        nextPendingButton.setEnabled(hasPending && !busy);
        nextPendingButton.setText(!hasPending
                ? R.string.omr_application_roster_action_complete
                : state.getRecoveryCount() > 0
                ? R.string.omr_application_roster_action_recover_next
                : R.string.omr_application_roster_action_next_pending);
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
        participantListView.setEnabled(!busy);
        nextPendingButton.setEnabled(
                !busy
                        && currentState != null
                        && currentState.getRemainingCount() > 0
        );
        resultsButton.setEnabled(!busy && currentState != null);
        backButton.setEnabled(!busy);
        adapter.notifyDataSetChanged();
        applyVisibility();
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

    public void setOnNextPendingClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        nextPendingButton.setOnClickListener(listener);
    }

    public void setOnResultsClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        resultsButton.setOnClickListener(listener);
    }

    public void setOnParticipantCorrectionListener(
            OnParticipantCorrectionListener listener
    ) {
        ensureNotReleased();
        participantCorrectionListener = listener;
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        searchListener = null;
        participantCorrectionListener = null;
        searchEditText.removeTextChangedListener(searchWatcher);
        nextPendingButton.setOnClickListener(null);
        resultsButton.setOnClickListener(null);
        backButton.setOnClickListener(null);
        adapter.clear();
        participantListView.setAdapter(null);
    }

    private void applyVisibility() {
        if (busy) {
            progressBar.setVisibility(View.VISIBLE);
            participantListView.setVisibility(View.GONE);
            noResultsContainer.setVisibility(View.GONE);
            return;
        }
        progressBar.setVisibility(View.GONE);
        boolean noResults = currentState != null
                && currentState.hasNoMatches();
        noResultsContainer.setVisibility(
                noResults ? View.VISIBLE : View.GONE
        );
        participantListView.setVisibility(
                noResults ? View.GONE : View.VISIBLE
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da chamada já foi liberado."
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

    private final class ParticipantAdapter extends BaseAdapter {
        private List<OmrAssessmentApplicationRosterViewState.ParticipantItem>
                items = Collections.emptyList();

        private void replaceItems(
                List<OmrAssessmentApplicationRosterViewState.ParticipantItem>
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
        public OmrAssessmentApplicationRosterViewState.ParticipantItem getItem(
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
                    R.layout.item_omr_assessment_application_participant,
                    parent,
                    false
            ) : convertView;
            OmrAssessmentApplicationRosterViewState.ParticipantItem item =
                    getItem(position);

            TextView positionText = requireView(itemView,
                    R.id.textOmrApplicationParticipantPosition,
                    TextView.class);
            TextView name = requireView(itemView,
                    R.id.textOmrApplicationParticipantName,
                    TextView.class);
            TextView registration = requireView(itemView,
                    R.id.textOmrApplicationParticipantRegistration,
                    TextView.class);
            TextView status = requireView(itemView,
                    R.id.textOmrApplicationParticipantStatus,
                    TextView.class);
            Button correctButton = requireView(itemView,
                    R.id.buttonOmrApplicationParticipantCorrect,
                    Button.class);

            positionText.setText(context.getString(
                    R.string.omr_application_roster_position,
                    item.getRosterPosition()
            ));
            name.setText(item.getStudentName());
            registration.setText(context.getString(
                    R.string.omr_application_roster_registration,
                    item.getRegistration(),
                    item.getStudentId()
            ));
            if (item.needsHistoryRecovery()) {
                status.setText(context.getString(
                        R.string.omr_application_roster_history_unavailable,
                        item.getLatestRevision()
                ));
                status.setTextColor(0xFFB91C1C);
            } else if (item.isCorrected()) {
                status.setText(context.getString(
                        R.string.omr_application_roster_corrected,
                        item.getLatestRevision(),
                        dateFormat.format(new Date(
                                item.getLatestRecordedAtEpochMillis()
                        ))
                ));
                status.setTextColor(0xFF047857);
            } else {
                status.setText(R.string.omr_application_roster_pending);
                status.setTextColor(0xFFB45309);
            }
            correctButton.setText(item.needsHistoryRecovery()
                    ? R.string.omr_application_roster_action_recover
                    : item.isCorrected()
                    ? R.string.omr_application_roster_action_correct_again
                    : R.string.omr_application_roster_action_correct);
            correctButton.setEnabled(!busy);
            correctButton.setOnClickListener(view -> {
                OnParticipantCorrectionListener listener =
                        participantCorrectionListener;
                if (!released && !busy && listener != null) {
                    listener.onCorrectParticipant(item.getStudentId());
                }
            });
            return itemView;
        }
    }

    private static int pluralSelector(int value) {
        return value == 1 ? 1 : 2;
    }
}
