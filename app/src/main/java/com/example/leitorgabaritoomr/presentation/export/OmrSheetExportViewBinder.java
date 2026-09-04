package com.example.leitorgabaritoomr.presentation.export;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

/**
 * Mantem os componentes Android sincronizados com o estado da exportacao.
 */
public final class OmrSheetExportViewBinder {

    public interface OnQuestionCountChangedListener {

        void onQuestionCountChanged(
                OmrSheetExportViewState viewState
        );
    }

    private final Context context;
    private final Spinner questionCountSpinner;
    private final TextView selectedCountTextView;
    private final TextView fileNameTextView;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button saveButton;

    private OmrSheetExportViewState currentViewState;
    private OnQuestionCountChangedListener
            questionCountChangedListener;

    private boolean rendering;
    private boolean released;

    public OmrSheetExportViewBinder(
            View rootView
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da exportacao e obrigatoria."
            );
        }

        context = rootView.getContext();

        questionCountSpinner = requireView(
                rootView,
                R.id.spinnerOmrSheetQuestionCount,
                Spinner.class
        );

        selectedCountTextView = requireView(
                rootView,
                R.id.textOmrSheetExportSelected,
                TextView.class
        );

        fileNameTextView = requireView(
                rootView,
                R.id.textOmrSheetExportFileName,
                TextView.class
        );

        progressBar = requireView(
                rootView,
                R.id.progressOmrSheetExport,
                ProgressBar.class
        );

        backButton = requireView(
                rootView,
                R.id.buttonOmrSheetExportBack,
                Button.class
        );

        saveButton = requireView(
                rootView,
                R.id.buttonOmrSheetExportSave,
                Button.class
        );

        configureQuestionCountSpinner();

        currentViewState =
                OmrSheetExportViewState.defaultState();

        questionCountSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        handleQuestionCountSelected(position);
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                        // O adaptador sempre mantem uma opcao selecionada.
                    }
                }
        );
    }

    public void render(
            OmrSheetExportViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual da exportacao e obrigatorio."
            );
        }

        rendering = true;

        try {
            currentViewState = viewState;

            int selectionIndex =
                    viewState.getSelectionIndex();

            if (questionCountSpinner
                    .getSelectedItemPosition()
                    != selectionIndex) {

                questionCountSpinner.setSelection(
                        selectionIndex,
                        false
                );
            }

            renderSelectionSummary(viewState);

        } finally {
            rendering = false;
        }
    }

    public OmrSheetExportViewState getCurrentViewState() {
        ensureNotReleased();

        int selectionIndex =
                questionCountSpinner
                        .getSelectedItemPosition();

        return OmrSheetExportViewState
                .fromSelectionIndex(selectionIndex);
    }

    public void setExportInProgress(
            boolean exportInProgress
    ) {
        ensureNotReleased();

        questionCountSpinner.setEnabled(!exportInProgress);
        backButton.setEnabled(!exportInProgress);
        saveButton.setEnabled(!exportInProgress);

        progressBar.setVisibility(
                exportInProgress
                        ? View.VISIBLE
                        : View.GONE
        );

        saveButton.setText(
                exportInProgress
                        ? R.string.omr_sheet_export_action_saving
                        : R.string.omr_sheet_export_action_save
        );
    }

    public void setOnQuestionCountChangedListener(
            OnQuestionCountChangedListener listener
    ) {
        ensureNotReleased();
        questionCountChangedListener = listener;
    }

    public void setOnBackClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        backButton.setOnClickListener(listener);
    }

    public void setOnSaveClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        saveButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;
        questionCountChangedListener = null;

        questionCountSpinner.setOnItemSelectedListener(null);
        backButton.setOnClickListener(null);
        saveButton.setOnClickListener(null);
    }

    private void configureQuestionCountSpinner() {
        int minimum = OmrSheetTemplateCatalog
                .COMPACT_MIN_QUESTION_COUNT;

        int maximum = OmrSheetTemplateCatalog
                .COMPACT_MAX_QUESTION_COUNT;

        String[] labels =
                new String[maximum - minimum + 1];

        for (int index = 0;
             index < labels.length;
             index++) {

            int questionCount = minimum + index;

            labels[index] = context.getResources()
                    .getQuantityString(
                            R.plurals
                                    .omr_sheet_export_question_count,
                            questionCount,
                            questionCount
                    );
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_item,
                        labels
                );

        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );

        questionCountSpinner.setAdapter(adapter);
    }

    private void handleQuestionCountSelected(
            int selectionIndex
    ) {
        if (rendering || released) {
            return;
        }

        currentViewState =
                OmrSheetExportViewState
                        .fromSelectionIndex(selectionIndex);

        renderSelectionSummary(currentViewState);

        OnQuestionCountChangedListener listener =
                questionCountChangedListener;

        if (listener != null) {
            listener.onQuestionCountChanged(
                    currentViewState
            );
        }
    }

    private void renderSelectionSummary(
            OmrSheetExportViewState viewState
    ) {
        int questionCount = viewState.getQuestionCount();

        selectedCountTextView.setText(
                context.getResources().getQuantityString(
                        R.plurals
                                .omr_sheet_export_selected_summary,
                        questionCount,
                        questionCount
                )
        );

        fileNameTextView.setText(
                context.getString(
                        R.string
                                .omr_sheet_export_filename_format,
                        questionCount
                )
        );
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da exportacao ja foi liberado."
            );
        }
    }

    private static <T extends View> T requireView(
            View rootView,
            @IdRes int viewId,
            Class<T> viewClass
    ) {
        View view = rootView.findViewById(viewId);

        if (!viewClass.isInstance(view)) {
            throw new IllegalStateException(
                    "A View obrigatoria "
                            + viewId
                            + " nao foi encontrada ou possui tipo invalido."
            );
        }

        return viewClass.cast(view);
    }
}
