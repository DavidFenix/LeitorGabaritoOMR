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

/** Renderiza o catalogo de turmas e encaminha as acoes da tela. */
public final class OmrClassListViewBinder {

    public interface OnClassActionListener {
        void onOpenRoster(String classGroupId);
        void onEditClass(String classGroupId);
    }

    public interface OnSearchQueryChangedListener {
        void onSearchQueryChanged(String query);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView countTextView;
    private final TextInputEditText searchEditText;
    private final ListView classListView;
    private final View emptyContainer;
    private final View noResultsContainer;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button createButton;
    private final Button importCsvButton;
    private final ClassAdapter adapter;
    private final TextWatcher searchWatcher;

    private OmrClassListViewState currentState;
    private OnClassActionListener classActionListener;
    private OnSearchQueryChangedListener searchListener;
    private boolean busy;
    private boolean released;

    public OmrClassListViewBinder(View rootView) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da lista de turmas e obrigatoria."
            );
        }

        context = rootView.getContext();
        inflater = LayoutInflater.from(context);
        countTextView = requireView(
                rootView, R.id.textOmrClassListCount, TextView.class
        );
        searchEditText = requireView(
                rootView, R.id.editOmrClassSearch,
                TextInputEditText.class
        );
        classListView = requireView(
                rootView, R.id.listOmrClasses, ListView.class
        );
        emptyContainer = requireView(
                rootView, R.id.containerOmrClassListEmpty, View.class
        );
        noResultsContainer = requireView(
                rootView, R.id.containerOmrClassListNoResults, View.class
        );
        progressBar = requireView(
                rootView, R.id.progressOmrClassList, ProgressBar.class
        );
        backButton = requireView(
                rootView, R.id.buttonOmrClassListBack, Button.class
        );
        createButton = requireView(
                rootView, R.id.buttonOmrClassListCreate, Button.class
        );
        importCsvButton = requireView(
                rootView, R.id.buttonOmrClassListImportCsv, Button.class
        );

        adapter = new ClassAdapter();
        classListView.setAdapter(adapter);

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

    public void render(OmrClassListViewState state) {
        ensureNotReleased();
        if (state == null) {
            throw new IllegalArgumentException(
                    "O estado visual das turmas e obrigatorio."
            );
        }

        currentState = state;
        applyCount(state);
        adapter.replaceItems(state.getVisibleClasses());
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
        classListView.setEnabled(!busy);
        backButton.setEnabled(!busy);
        createButton.setEnabled(!busy);
        importCsvButton.setEnabled(!busy);
        applyVisibility();
    }

    public void setOnClassActionListener(OnClassActionListener listener) {
        ensureNotReleased();
        classActionListener = listener;
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

    public void release() {
        if (released) {
            return;
        }
        released = true;
        classActionListener = null;
        searchListener = null;
        searchEditText.removeTextChangedListener(searchWatcher);
        backButton.setOnClickListener(null);
        createButton.setOnClickListener(null);
        importCsvButton.setOnClickListener(null);
        adapter.clear();
        classListView.setAdapter(null);
    }

    private void notifySearchChanged(CharSequence text) {
        if (released || busy || searchListener == null) {
            return;
        }
        searchListener.onSearchQueryChanged(
                text == null ? "" : text.toString()
        );
    }

    private void notifyOpenRoster(OmrClassListViewState.ClassItem item) {
        if (!busy && classActionListener != null) {
            classActionListener.onOpenRoster(item.getClassGroupId());
        }
    }

    private void notifyEdit(OmrClassListViewState.ClassItem item) {
        if (!busy && classActionListener != null) {
            classActionListener.onEditClass(item.getClassGroupId());
        }
    }

    private void applyCount(OmrClassListViewState state) {
        if (state.hasQuery()) {
            countTextView.setText(context.getString(
                    R.string.omr_class_list_filtered_count,
                    state.getVisibleCount(),
                    state.getTotalCount()
            ));
            return;
        }
        countTextView.setText(context.getResources().getQuantityString(
                R.plurals.omr_class_list_count,
                state.getTotalCount(),
                state.getTotalCount()
        ));
    }

    private void applyVisibility() {
        if (busy) {
            progressBar.setVisibility(View.VISIBLE);
            classListView.setVisibility(View.GONE);
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
        classListView.setVisibility(
                !empty && !noResults ? View.VISIBLE : View.GONE
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da lista de turmas ja foi liberado."
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

    private final class ClassAdapter extends BaseAdapter {
        private List<OmrClassListViewState.ClassItem> items =
                Collections.emptyList();

        private void replaceItems(
                List<OmrClassListViewState.ClassItem> newItems
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
        public OmrClassListViewState.ClassItem getItem(int position) {
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
                    R.layout.item_omr_class_group, parent, false
            ) : convertView;
            OmrClassListViewState.ClassItem item = getItem(position);

            TextView name = requireView(
                    itemView, R.id.textOmrClassItemName, TextView.class
            );
            TextView metadata = requireView(
                    itemView, R.id.textOmrClassItemMetadata, TextView.class
            );
            TextView studentCount = requireView(
                    itemView,
                    R.id.textOmrClassItemStudentCount,
                    TextView.class
            );
            Button roster = requireView(
                    itemView, R.id.buttonOmrClassItemRoster, Button.class
            );
            Button edit = requireView(
                    itemView, R.id.buttonOmrClassItemEdit, Button.class
            );

            name.setText(item.getName());
            metadata.setText(context.getString(
                    R.string.omr_class_list_metadata,
                    item.getGradeLevel(),
                    item.getSchoolYear()
            ));
            studentCount.setText(
                    context.getResources().getQuantityString(
                            R.plurals.omr_class_list_student_count,
                            item.getStudentCount(),
                            item.getStudentCount()
                    )
            );
            roster.setOnClickListener(view -> notifyOpenRoster(item));
            edit.setOnClickListener(view -> notifyEdit(item));
            return itemView;
        }
    }
}
