package com.example.leitorgabaritoomr.presentation.classroom;

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

/** Renderiza a lista de chamada de uma turma. */
public final class OmrClassRosterViewBinder {

    public interface OnEnrollmentActionListener {
        void onEditEnrollment(String studentId);
    }

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView titleTextView;
    private final TextView metadataTextView;
    private final TextView countTextView;
    private final TextInputEditText searchEditText;
    private final ListView rosterListView;
    private final View emptyContainer;
    private final View noResultsContainer;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button enrollButton;
    private final EnrollmentAdapter adapter;
    private final TextWatcher searchWatcher;

    private OmrClassRosterViewState currentState;
    private OnEnrollmentActionListener enrollmentActionListener;
    private OnSearchQueryChangedListener searchListener;
    private boolean busy;
    private boolean released;

    public OmrClassRosterViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da lista de chamada e obrigatoria."
            );
        }

        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        titleTextView = requireView(
                rootView, R.id.textOmrClassRosterTitle, TextView.class
        );
        metadataTextView = requireView(
                rootView, R.id.textOmrClassRosterMetadata, TextView.class
        );
        countTextView = requireView(
                rootView, R.id.textOmrClassRosterCount, TextView.class
        );
        searchEditText = requireView(
                rootView, R.id.editOmrClassRosterSearch,
                TextInputEditText.class
        );
        rosterListView = requireView(
                rootView, R.id.listOmrClassRoster, ListView.class
        );
        emptyContainer = requireView(
                rootView, R.id.containerOmrClassRosterEmpty, View.class
        );
        noResultsContainer = requireView(
                rootView,
                R.id.containerOmrClassRosterNoResults,
                View.class
        );
        progressBar = requireView(
                rootView, R.id.progressOmrClassRoster, ProgressBar.class
        );
        backButton = requireView(
                rootView, R.id.buttonOmrClassRosterBack, Button.class
        );
        enrollButton = requireView(
                rootView, R.id.buttonOmrClassRosterEnroll, Button.class
        );

        adapter = new EnrollmentAdapter();
        rosterListView.setAdapter(adapter);
        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
                // Nenhuma acao necessaria.
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
                // O texto ja foi encaminhado.
            }
        };
        searchEditText.addTextChangedListener(searchWatcher);
    }

    public void render(OmrClassRosterViewState state) {
        ensureNotReleased();
        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual da lista de chamada e obrigatorio."
            );
        }
        currentState = state;
        titleTextView.setText(state.getClassName());
        metadataTextView.setText(context.getString(
                R.string.omr_class_roster_metadata,
                state.getGradeLevel(),
                state.getSchoolYear()
        ));
        applyCount(state);
        adapter.replaceItems(state.getVisibleEnrollments());
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
        rosterListView.setEnabled(!busy);
        backButton.setEnabled(!busy);
        enrollButton.setEnabled(!busy);
        applyVisibility();
    }

    public void setOnEnrollmentActionListener(
            OnEnrollmentActionListener listener
    ) {
        ensureNotReleased();
        enrollmentActionListener = listener;
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

    public void setOnEnrollClickListener(View.OnClickListener listener) {
        ensureNotReleased();
        enrollButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        enrollmentActionListener = null;
        searchListener = null;
        searchEditText.removeTextChangedListener(searchWatcher);
        backButton.setOnClickListener(null);
        enrollButton.setOnClickListener(null);
        adapter.clear();
        rosterListView.setAdapter(null);
    }

    private void notifySearchChanged(CharSequence text) {
        if (released || busy || searchListener == null) {
            return;
        }
        searchListener.onSearchQueryChanged(
                text == null ? "" : text.toString()
        );
    }

    private void notifyEdit(
            OmrClassRosterViewState.EnrollmentItem item
    ) {
        if (!busy && enrollmentActionListener != null) {
            enrollmentActionListener.onEditEnrollment(
                    item.getStudentId()
            );
        }
    }

    private void applyCount(OmrClassRosterViewState state) {
        if (state.hasQuery()) {
            countTextView.setText(context.getString(
                    R.string.omr_class_roster_filtered_count,
                    state.getVisibleCount(),
                    state.getTotalCount()
            ));
            return;
        }
        countTextView.setText(context.getResources().getQuantityString(
                R.plurals.omr_class_roster_count,
                state.getTotalCount(),
                state.getTotalCount()
        ));
    }

    private void applyVisibility() {
        if (busy) {
            progressBar.setVisibility(View.VISIBLE);
            rosterListView.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
            noResultsContainer.setVisibility(View.GONE);
            return;
        }
        progressBar.setVisibility(View.GONE);
        boolean empty = currentState != null
                && currentState.isRosterEmpty();
        boolean noResults = currentState != null
                && currentState.hasNoMatches();
        emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        noResultsContainer.setVisibility(
                noResults ? View.VISIBLE : View.GONE
        );
        rosterListView.setVisibility(
                !empty && !noResults ? View.VISIBLE : View.GONE
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da lista de chamada ja foi liberado."
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
                    "View obrigatoria ausente ou incorreta: " + viewId
            );
        }
        return expectedType.cast(view);
    }

    private final class EnrollmentAdapter extends BaseAdapter {
        private List<OmrClassRosterViewState.EnrollmentItem> items =
                Collections.emptyList();

        private void replaceItems(
                List<OmrClassRosterViewState.EnrollmentItem> newItems
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
        public OmrClassRosterViewState.EnrollmentItem getItem(int position) {
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
                    R.layout.item_omr_class_enrollment, parent, false
            ) : convertView;
            OmrClassRosterViewState.EnrollmentItem item =
                    getItem(position);

            TextView rosterPosition = requireView(
                    itemView,
                    R.id.textOmrClassEnrollmentPosition,
                    TextView.class
            );
            TextView name = requireView(
                    itemView,
                    R.id.textOmrClassEnrollmentName,
                    TextView.class
            );
            TextView registration = requireView(
                    itemView,
                    R.id.textOmrClassEnrollmentRegistration,
                    TextView.class
            );
            Button edit = requireView(
                    itemView,
                    R.id.buttonOmrClassEnrollmentEdit,
                    Button.class
            );

            rosterPosition.setText(context.getString(
                    R.string.omr_class_roster_position_format,
                    item.getRosterPosition()
            ));
            name.setText(item.getName());
            registration.setText(context.getString(
                    R.string.omr_class_roster_registration_format,
                    item.getRegistration()
            ));
            edit.setOnClickListener(view -> notifyEdit(item));
            return itemView;
        }
    }
}
