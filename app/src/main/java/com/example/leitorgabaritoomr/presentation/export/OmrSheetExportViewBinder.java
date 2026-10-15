package com.example.leitorgabaritoomr.presentation.export;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetTextCustomization;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

/**
 * Mantem os componentes Android sincronizados com o estado da exportacao.
 */
public final class OmrSheetExportViewBinder {

    public interface OnSelectionChangedListener {

        void onSelectionChanged(
                OmrSheetExportViewState viewState
        );
    }

    private final Context context;
    private final Spinner questionCountSpinner;
    private final Spinner optionCountSpinner;
    private final Spinner questionsPerColumnSpinner;
    private final TextView selectedCountTextView;
    private final TextView fileNameTextView;
    private final EditText headerEditText;
    private final EditText instructionEditText;
    private final EditText footerEditText;
    private final Button restoreDefaultTextsButton;
    private final ProgressBar progressBar;
    private final Button backButton;
    private final Button saveButton;
    private final TextWatcher headerTextWatcher;
    private final TextWatcher instructionTextWatcher;
    private final TextWatcher footerTextWatcher;

    private OmrSheetExportViewState currentViewState;
    private OnSelectionChangedListener
            selectionChangedListener;

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

        optionCountSpinner = requireView(
                rootView,
                R.id.spinnerOmrSheetOptionCount,
                Spinner.class
        );

        questionsPerColumnSpinner = requireView(
                rootView,
                R.id.spinnerOmrSheetQuestionsPerColumn,
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

        headerEditText = requireView(
                rootView,
                R.id.editOmrSheetHeaderText,
                EditText.class
        );
        instructionEditText = requireView(
                rootView,
                R.id.editOmrSheetInstructionText,
                EditText.class
        );
        footerEditText = requireView(
                rootView,
                R.id.editOmrSheetFooterText,
                EditText.class
        );
        restoreDefaultTextsButton = requireView(
                rootView,
                R.id.buttonOmrSheetRestoreDefaultTexts,
                Button.class
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

        currentViewState =
                OmrSheetExportViewState.defaultState();

        headerTextWatcher = createTextWatcher(0);
        instructionTextWatcher = createTextWatcher(1);
        footerTextWatcher = createTextWatcher(2);

        headerEditText.addTextChangedListener(headerTextWatcher);
        instructionEditText.addTextChangedListener(
                instructionTextWatcher
        );
        footerEditText.addTextChangedListener(footerTextWatcher);
        restoreDefaultTextsButton.setOnClickListener(
                view -> restoreDefaultTexts()
        );

        configureQuestionCountSpinner();
        configureOptionCountSpinner();
        configureQuestionsPerColumnSpinner(
                currentViewState
        );

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

        optionCountSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        handleOptionCountSelected(position);
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                        // O adaptador sempre mantem uma opcao selecionada.
                    }
                }
        );

        questionsPerColumnSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        handleQuestionsPerColumnSelected(
                                position
                        );
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

            int optionSelectionIndex =
                    viewState.getOptionSelectionIndex();

            if (optionCountSpinner
                    .getSelectedItemPosition()
                    != optionSelectionIndex) {

                optionCountSpinner.setSelection(
                        optionSelectionIndex,
                        false
                );
            }

            configureQuestionsPerColumnSpinner(
                    viewState
            );

            int questionsPerColumnSelectionIndex =
                    viewState
                            .getQuestionsPerColumnSelectionIndex();

            if (questionsPerColumnSpinner
                    .getSelectedItemPosition()
                    != questionsPerColumnSelectionIndex) {

                questionsPerColumnSpinner.setSelection(
                        questionsPerColumnSelectionIndex,
                        false
                );
            }

            renderSelectionSummary(viewState);
            renderTextCustomization(viewState);

        } finally {
            rendering = false;
        }
    }

    public OmrSheetExportViewState getCurrentViewState() {
        ensureNotReleased();

        int selectionIndex =
                questionCountSpinner
                        .getSelectedItemPosition();

        int optionSelectionIndex =
                optionCountSpinner
                        .getSelectedItemPosition();

        int questionsPerColumnSelectionIndex =
                questionsPerColumnSpinner
                        .getSelectedItemPosition();

        return OmrSheetExportViewState
                .fromSelectionIndexes(
                        selectionIndex,
                        optionSelectionIndex,
                        questionsPerColumnSelectionIndex
                )
                .withTextCustomization(
                        currentViewState.getTextCustomization()
                );
    }

    public void setExportInProgress(
            boolean exportInProgress
    ) {
        ensureNotReleased();

        questionCountSpinner.setEnabled(!exportInProgress);
        optionCountSpinner.setEnabled(!exportInProgress);
        questionsPerColumnSpinner.setEnabled(!exportInProgress);
        headerEditText.setEnabled(!exportInProgress);
        instructionEditText.setEnabled(!exportInProgress);
        footerEditText.setEnabled(!exportInProgress);
        restoreDefaultTextsButton.setEnabled(!exportInProgress);
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

    public void setOnSelectionChangedListener(
            OnSelectionChangedListener listener
    ) {
        ensureNotReleased();
        selectionChangedListener = listener;
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
        selectionChangedListener = null;

        questionCountSpinner.setOnItemSelectedListener(null);
        optionCountSpinner.setOnItemSelectedListener(null);
        questionsPerColumnSpinner.setOnItemSelectedListener(null);
        headerEditText.removeTextChangedListener(headerTextWatcher);
        instructionEditText.removeTextChangedListener(
                instructionTextWatcher
        );
        footerEditText.removeTextChangedListener(footerTextWatcher);
        restoreDefaultTextsButton.setOnClickListener(null);
        backButton.setOnClickListener(null);
        saveButton.setOnClickListener(null);
    }

    private void configureQuestionCountSpinner() {
        int minimum = OmrSheetTemplateCatalog
                .MIN_QUESTION_COUNT;

        int maximum = OmrSheetTemplateCatalog
                .MAX_QUESTION_COUNT;

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

    private void configureOptionCountSpinner() {
        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        context,
                        R.array
                                .omr_sheet_export_option_count_entries,
                        android.R.layout.simple_spinner_item
                );

        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );

        optionCountSpinner.setAdapter(adapter);
    }

    private void configureQuestionsPerColumnSpinner(
            OmrSheetExportViewState viewState
    ) {
        int questionCount = viewState.getQuestionCount();

        int minimum = OmrSheetTemplateCatalog
                .getFlexibleV3MinimumQuestionsPerColumn(
                        questionCount
                );

        int maximum = OmrSheetTemplateCatalog
                .getFlexibleV3MaximumQuestionsPerColumn(
                        questionCount
                );

        String[] labels =
                new String[maximum - minimum + 2];

        int automaticRows = OmrSheetTemplateCatalog
                .getFlexibleV3AutomaticQuestionsPerColumn(
                        questionCount
                );

        labels[0] = context.getString(
                R.string
                        .omr_sheet_export_questions_per_column_automatic,
                formatItemsPerColumn(automaticRows),
                formatColumnCount(
                        calculateColumnCount(
                                questionCount,
                                automaticRows
                        )
                )
        );

        for (int rows = minimum;
             rows <= maximum;
             rows++) {

            labels[rows - minimum + 1] =
                    context.getString(
                            R.string
                                    .omr_sheet_export_questions_per_column_manual,
                            formatItemsPerColumn(rows),
                            formatColumnCount(
                                    calculateColumnCount(
                                            questionCount,
                                            rows
                                    )
                            )
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

        questionsPerColumnSpinner.setAdapter(adapter);
    }

    private void handleQuestionCountSelected(
            int selectionIndex
    ) {
        if (rendering || released) {
            return;
        }

        currentViewState =
                currentViewState.withQuestionCount(
                        OmrSheetTemplateCatalog
                                .MIN_QUESTION_COUNT
                                + selectionIndex
                );

        rendering = true;

        try {
            configureQuestionsPerColumnSpinner(
                    currentViewState
            );

            questionsPerColumnSpinner.setSelection(
                    currentViewState
                            .getQuestionsPerColumnSelectionIndex(),
                    false
            );

        } finally {
            rendering = false;
        }

        notifySelectionChanged();
    }

    private void handleOptionCountSelected(
            int selectionIndex
    ) {
        if (rendering || released) {
            return;
        }

        currentViewState =
                currentViewState.withOptionCount(
                        OmrSheetTemplateCatalog
                                .STANDARD_V2_FOUR_OPTION_COUNT
                                + selectionIndex
                );

        notifySelectionChanged();
    }

    private void handleQuestionsPerColumnSelected(
            int selectionIndex
    ) {
        if (rendering || released) {
            return;
        }

        currentViewState = currentViewState
                .withQuestionsPerColumnSelectionIndex(
                        selectionIndex
                );

        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        renderTextCustomization(currentViewState);
        renderSelectionSummary(currentViewState);

        OnSelectionChangedListener listener =
                selectionChangedListener;

        if (listener != null) {
            listener.onSelectionChanged(
                    currentViewState
            );
        }
    }

    private TextWatcher createTextWatcher(
            int field
    ) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence sequence,
                    int start,
                    int count,
                    int after
            ) {
                // Nenhuma preparacao necessaria.
            }

            @Override
            public void onTextChanged(
                    CharSequence sequence,
                    int start,
                    int before,
                    int count
            ) {
                // A atualizacao ocorre quando o texto estiver completo.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (rendering || released) {
                    return;
                }

                OmrSheetTextCustomization customization =
                        currentViewState.getTextCustomization();
                String value = editable == null
                        ? ""
                        : editable.toString();

                if (field == 0) {
                    customization =
                            customization.withHeaderOverride(value);
                } else if (field == 1) {
                    customization = customization
                            .withInstructionOverride(value);
                } else {
                    customization =
                            customization.withFooterOverride(value);
                }

                currentViewState = currentViewState
                        .withTextCustomization(customization);
                notifySelectionChangedWithoutRenderingTexts();
            }
        };
    }

    private void restoreDefaultTexts() {
        if (released) {
            return;
        }

        currentViewState = currentViewState
                .withTextCustomization(
                        OmrSheetTextCustomization.defaults()
                );
        renderTextCustomization(currentViewState);
        notifySelectionChangedWithoutRenderingTexts();
    }

    private void renderTextCustomization(
            OmrSheetExportViewState viewState
    ) {
        boolean previousRendering = rendering;
        rendering = true;

        try {
            setTextIfDifferent(
                    headerEditText,
                    viewState.getHeaderText()
            );
            setTextIfDifferent(
                    instructionEditText,
                    viewState.getInstructionText()
            );
            setTextIfDifferent(
                    footerEditText,
                    viewState.getFooterText()
            );
        } finally {
            rendering = previousRendering;
        }
    }

    private void setTextIfDifferent(
            EditText editText,
            String text
    ) {
        if (!editText.getText().toString().equals(text)) {
            editText.setText(text);
            editText.setSelection(editText.length());
        }
    }

    private void notifySelectionChangedWithoutRenderingTexts() {
        renderSelectionSummary(currentViewState);

        OnSelectionChangedListener listener =
                selectionChangedListener;
        if (listener != null) {
            listener.onSelectionChanged(currentViewState);
        }
    }

    private void renderSelectionSummary(
            OmrSheetExportViewState viewState
    ) {
        int questionCount = viewState.getQuestionCount();
        int optionCount = viewState.getOptionCount();
        int questionsPerColumn =
                viewState.getEffectiveQuestionsPerColumn();
        int columnCount = viewState.getColumnCount();

        String questionsPerColumnText =
                formatItemsPerColumn(questionsPerColumn);

        String distributionText =
                viewState.isAutomaticQuestionsPerColumn()
                        ? context.getString(
                        R.string
                                .omr_sheet_export_distribution_automatic,
                        questionsPerColumnText
                )
                        : questionsPerColumnText;

        selectedCountTextView.setText(
                context.getResources().getQuantityString(
                        R.plurals
                                .omr_sheet_export_selected_summary,
                        questionCount,
                        questionCount,
                        optionCount,
                        distributionText,
                        formatColumnCount(columnCount)
                )
        );

        fileNameTextView.setText(
                context.getString(
                        R.string
                                .omr_sheet_export_filename_format,
                        questionCount,
                        optionCount,
                        questionsPerColumn
                )
        );
    }

    private String formatItemsPerColumn(
            int questionsPerColumn
    ) {
        return context.getResources().getQuantityString(
                R.plurals
                        .omr_sheet_export_items_per_column,
                questionsPerColumn,
                questionsPerColumn
        );
    }

    private String formatColumnCount(
            int columnCount
    ) {
        return context.getResources().getQuantityString(
                R.plurals.omr_sheet_export_column_count,
                columnCount,
                columnCount
        );
    }

    private int calculateColumnCount(
            int questionCount,
            int questionsPerColumn
    ) {
        return (questionCount
                + questionsPerColumn
                - 1)
                / questionsPerColumn;
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
