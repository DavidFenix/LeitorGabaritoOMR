package com.example.leitorgabaritoomr.presentation.student;

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

/**
 * Renderiza o catálogo de alunos e encaminha ações para a Activity.
 */
public final class OmrStudentListViewBinder {

    public interface OnStudentActionListener {
        void onEditStudent(String studentId);
    }

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView countTextView;
    private final TextInputEditText searchEditText;
    private final ListView studentListView;
    private final View emptyContainer;
    private final View noResultsContainer;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button createButton;
    private final Button importCsvButton;
    private final StudentAdapter adapter;
    private final TextWatcher searchWatcher;

    private OmrStudentListViewState currentState;
    private OnStudentActionListener studentActionListener;
    private OnSearchQueryChangedListener searchListener;
    private boolean busy;
    private boolean released;

    public OmrStudentListViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da lista de alunos é obrigatória."
            );
        }

        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        countTextView = requireView(
                rootView,
                R.id.textOmrStudentListCount,
                TextView.class
        );
        searchEditText = requireView(
                rootView,
                R.id.editOmrStudentSearch,
                TextInputEditText.class
        );
        studentListView = requireView(
                rootView,
                R.id.listOmrStudents,
                ListView.class
        );
        emptyContainer = requireView(
                rootView,
                R.id.containerOmrStudentListEmpty,
                View.class
        );
        noResultsContainer = requireView(
                rootView,
                R.id.containerOmrStudentListNoResults,
                View.class
        );
        progressBar = requireView(
                rootView,
                R.id.progressOmrStudentList,
                ProgressBar.class
        );
        backButton = requireView(
                rootView,
                R.id.buttonOmrStudentListBack,
                Button.class
        );
        createButton = requireView(
                rootView,
                R.id.buttonOmrStudentListCreate,
                Button.class
        );
        importCsvButton = requireView(
                rootView,
                R.id.buttonOmrStudentListImportCsv,
                Button.class
        );

        adapter = new StudentAdapter();
        studentListView.setAdapter(adapter);

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

    public void render(OmrStudentListViewState state) {
        ensureNotReleased();

        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual da lista de alunos é obrigatório."
            );
        }

        currentState = state;
        applyCount(state);
        adapter.replaceItems(state.getVisibleStudents());
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
        studentListView.setEnabled(!busy);
        createButton.setEnabled(!busy);
        importCsvButton.setEnabled(!busy);
        applyVisibility();
    }

    public void setOnStudentActionListener(
            OnStudentActionListener listener
    ) {
        ensureNotReleased();
        studentActionListener = listener;
    }

    public void setOnSearchQueryChangedListener(
            OnSearchQueryChangedListener listener
    ) {
        ensureNotReleased();
        searchListener = listener;
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

    public void setOnImportCsvClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        importCsvButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;
        studentActionListener = null;
        searchListener = null;
        searchEditText.removeTextChangedListener(searchWatcher);
        backButton.setOnClickListener(null);
        createButton.setOnClickListener(null);
        importCsvButton.setOnClickListener(null);
        adapter.clear();
        studentListView.setAdapter(null);
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

    private void notifyEdit(
            OmrStudentListViewState.StudentItem item
    ) {
        OnStudentActionListener listener =
                studentActionListener;

        if (listener != null && !busy) {
            listener.onEditStudent(item.getStudentId());
        }
    }

    private void applyCount(OmrStudentListViewState state) {
        if (state.hasQuery()) {
            countTextView.setText(context.getString(
                    R.string.omr_student_list_filtered_count,
                    state.getVisibleCount(),
                    state.getTotalCount()
            ));
            return;
        }

        countTextView.setText(context.getResources().getQuantityString(
                R.plurals.omr_student_list_count,
                state.getTotalCount(),
                state.getTotalCount()
        ));
    }

    private void applyVisibility() {
        if (busy) {
            progressBar.setVisibility(View.VISIBLE);
            studentListView.setVisibility(View.GONE);
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
        studentListView.setVisibility(
                !catalogEmpty && !noResults
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da lista de alunos já foi liberado."
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

    private final class StudentAdapter extends BaseAdapter {
        private List<OmrStudentListViewState.StudentItem> items =
                Collections.emptyList();

        private void replaceItems(
                List<OmrStudentListViewState.StudentItem> newItems
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
        public OmrStudentListViewState.StudentItem getItem(
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
                    R.layout.item_omr_student,
                    parent,
                    false
            )
                    : convertView;

            OmrStudentListViewState.StudentItem item =
                    getItem(position);

            TextView name = requireView(
                    itemView,
                    R.id.textOmrStudentItemName,
                    TextView.class
            );
            TextView registration = requireView(
                    itemView,
                    R.id.textOmrStudentItemRegistration,
                    TextView.class
            );
            Button edit = requireView(
                    itemView,
                    R.id.buttonOmrStudentItemEdit,
                    Button.class
            );

            name.setText(item.getName());
            registration.setText(context.getString(
                    R.string.omr_student_list_registration_format,
                    item.getRegistration()
            ));
            edit.setOnClickListener(view -> notifyEdit(item));

            return itemView;
        }
    }
}
