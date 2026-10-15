package com.example.leitorgabaritoomr.presentation.export;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetTextCustomization;
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetSvgDocument;
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetSvgGenerator;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Permite escolher de 1 a 90 questões, quatro ou cinco alternativas, a
 * quantidade de itens por coluna e salvar o cartão flexível v4 pelo seletor
 * nativo.
 *
 * O Storage Access Framework concede acesso somente ao destino escolhido pelo
 * usuario. Por isso esta tela nao depende de permissao ampla de armazenamento.
 */
public final class OmrSheetExportActivity
        extends AppCompatActivity {

    private static final String TAG = "OmrSheetExport";

    private static final String STATE_QUESTION_COUNT =
            "omr.sheet_export.question_count";

    private static final String STATE_OPTION_COUNT =
            "omr.sheet_export.option_count";

    private static final String STATE_QUESTIONS_PER_COLUMN =
            "omr.sheet_export.questions_per_column";

    private static final String STATE_PENDING_QUESTION_COUNT =
            "omr.sheet_export.pending_question_count";

    private static final String STATE_PENDING_OPTION_COUNT =
            "omr.sheet_export.pending_option_count";

    private static final String
            STATE_PENDING_QUESTIONS_PER_COLUMN =
            "omr.sheet_export.pending_questions_per_column";

    private static final String STATE_HEADER_OVERRIDDEN =
            "omr.sheet_export.header_overridden";
    private static final String STATE_HEADER_TEXT =
            "omr.sheet_export.header_text";
    private static final String STATE_INSTRUCTION_OVERRIDDEN =
            "omr.sheet_export.instruction_overridden";
    private static final String STATE_INSTRUCTION_TEXT =
            "omr.sheet_export.instruction_text";
    private static final String STATE_FOOTER_OVERRIDDEN =
            "omr.sheet_export.footer_overridden";
    private static final String STATE_FOOTER_TEXT =
            "omr.sheet_export.footer_text";
    private static final String STATE_PENDING_HEADER_OVERRIDDEN =
            "omr.sheet_export.pending_header_overridden";
    private static final String STATE_PENDING_HEADER_TEXT =
            "omr.sheet_export.pending_header_text";
    private static final String STATE_PENDING_INSTRUCTION_OVERRIDDEN =
            "omr.sheet_export.pending_instruction_overridden";
    private static final String STATE_PENDING_INSTRUCTION_TEXT =
            "omr.sheet_export.pending_instruction_text";
    private static final String STATE_PENDING_FOOTER_OVERRIDDEN =
            "omr.sheet_export.pending_footer_overridden";
    private static final String STATE_PENDING_FOOTER_TEXT =
            "omr.sheet_export.pending_footer_text";

    private OmrSheetSvgGenerator svgGenerator;
    private OmrSheetExportViewState viewState;
    private OmrSheetExportViewBinder viewBinder;

    private int pendingQuestionCount;
    private int pendingOptionCount;
    private int pendingQuestionsPerColumn;
    private OmrSheetTextCustomization pendingTextCustomization =
            OmrSheetTextCustomization.defaults();

    private final ActivityResultLauncher<String>
            createDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            OmrSheetSvgDocument.MIME_TYPE
                    ),
                    this::handleDocumentDestination
            );

    public static Intent createIntent(
            Context context
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto e obrigatorio."
            );
        }

        return new Intent(
                context,
                OmrSheetExportActivity.class
        );
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_omr_sheet_export
        );

        svgGenerator = new OmrSheetSvgGenerator();

        viewState = restoreViewState(savedInstanceState);

        pendingQuestionCount =
                savedInstanceState == null
                        ? 0
                        : savedInstanceState.getInt(
                        STATE_PENDING_QUESTION_COUNT,
                        0
                );

        pendingOptionCount =
                savedInstanceState == null
                        ? 0
                        : savedInstanceState.getInt(
                        STATE_PENDING_OPTION_COUNT,
                        0
                );

        pendingQuestionsPerColumn =
                savedInstanceState == null
                        ? OmrSheetExportViewState
                        .AUTOMATIC_QUESTIONS_PER_COLUMN
                        : savedInstanceState.getInt(
                        STATE_PENDING_QUESTIONS_PER_COLUMN,
                        OmrSheetExportViewState
                                .AUTOMATIC_QUESTIONS_PER_COLUMN
                );

        pendingTextCustomization = restoreTextCustomization(
                savedInstanceState,
                STATE_PENDING_HEADER_OVERRIDDEN,
                STATE_PENDING_HEADER_TEXT,
                STATE_PENDING_INSTRUCTION_OVERRIDDEN,
                STATE_PENDING_INSTRUCTION_TEXT,
                STATE_PENDING_FOOTER_OVERRIDDEN,
                STATE_PENDING_FOOTER_TEXT
        );

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder = new OmrSheetExportViewBinder(
                rootView
        );

        configureBinder();

        viewBinder.render(viewState);
        viewBinder.setExportInProgress(
                pendingQuestionCount > 0
        );
    }

    private void configureBinder() {
        viewBinder.setOnSelectionChangedListener(
                changedViewState ->
                        viewState = changedViewState
        );

        viewBinder.setOnBackClickListener(
                view -> finish()
        );

        viewBinder.setOnSaveClickListener(
                view -> requestDocumentDestination()
        );
    }

    private void requestDocumentDestination() {
        viewState = viewBinder.getCurrentViewState();

        try {
            OmrSheetSvgDocument document =
                    generateDocument(
                            viewState.getQuestionCount(),
                            viewState.getOptionCount(),
                            viewState
                                    .getConfiguredQuestionsPerColumn(),
                            viewState.getTextCustomization()
                    );

            pendingQuestionCount =
                    document.getQuestionCount();

            pendingOptionCount =
                    viewState.getOptionCount();

            pendingQuestionsPerColumn =
                    viewState.getConfiguredQuestionsPerColumn();
            pendingTextCustomization =
                    viewState.getTextCustomization();

            viewBinder.setExportInProgress(true);

            createDocumentLauncher.launch(
                    document.getSuggestedFileName()
            );

        } catch (RuntimeException exception) {
            clearPendingExport();
            viewBinder.setExportInProgress(false);

            Log.e(
                    TAG,
                    "Nao foi possivel preparar o cartao-resposta.",
                    exception
            );

            showMessage(
                    R.string.omr_sheet_export_prepare_error
            );
        }
    }

    private void handleDocumentDestination(
            @Nullable Uri destinationUri
    ) {
        if (viewBinder != null) {
            viewBinder.setExportInProgress(false);
        }

        if (destinationUri == null) {
            clearPendingExport();

            showMessage(
                    R.string.omr_sheet_export_cancelled
            );

            return;
        }

        int questionCount = pendingQuestionCount;
        int optionCount = pendingOptionCount;
        int configuredQuestionsPerColumn =
                pendingQuestionsPerColumn;
        OmrSheetTextCustomization textCustomization =
                pendingTextCustomization;

        if (questionCount <= 0 && viewState != null) {
            questionCount = viewState.getQuestionCount();
            optionCount = viewState.getOptionCount();
            configuredQuestionsPerColumn =
                    viewState.getConfiguredQuestionsPerColumn();
            textCustomization = viewState.getTextCustomization();
        }

        try {
            OmrSheetSvgDocument document =
                    generateDocument(
                            questionCount,
                            optionCount,
                            configuredQuestionsPerColumn,
                            textCustomization
                    );

            writeDocument(
                    destinationUri,
                    document
            );

            showMessage(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_sheet_export_saved_success,
                            document.getQuestionCount(),
                            document.getQuestionCount()
                    )
            );

        } catch (IOException | RuntimeException exception) {
            Log.e(
                    TAG,
                    "Nao foi possivel salvar o cartao-resposta."
                            + " uri="
                            + destinationUri,
                    exception
            );

            showMessage(
                    R.string.omr_sheet_export_save_error
            );

        } finally {
            clearPendingExport();
        }
    }

    private OmrSheetSvgDocument generateDocument(
            int questionCount,
            int optionCount,
            int configuredQuestionsPerColumn,
            OmrSheetTextCustomization textCustomization
    ) {
        OmrSheetTemplateSpec spec =
                createExportTemplate(
                        questionCount,
                        optionCount,
                        configuredQuestionsPerColumn
                );

        return svgGenerator.generate(spec, textCustomization);
    }

    /**
     * Mantém explícita e testável a geometria atualmente entregue ao usuário.
     * Os modelos v1 e v2 continuam publicados para leitura de gabaritos já
     * persistidos.
     */
    static OmrSheetTemplateSpec createExportTemplate(
            int questionCount
    ) {
        return createExportTemplate(
                questionCount,
                OmrSheetExportViewState.DEFAULT_OPTION_COUNT
        );
    }

    static OmrSheetTemplateSpec createExportTemplate(
            int questionCount,
            int optionCount
    ) {
        return createExportTemplate(
                questionCount,
                optionCount,
                OmrSheetExportViewState
                        .AUTOMATIC_QUESTIONS_PER_COLUMN
        );
    }

    static OmrSheetTemplateSpec createExportTemplate(
            int questionCount,
            int optionCount,
            int configuredQuestionsPerColumn
    ) {
        if (configuredQuestionsPerColumn
                == OmrSheetExportViewState
                .AUTOMATIC_QUESTIONS_PER_COLUMN) {

            return OmrSheetTemplateCatalog
                    .flexibleOptionsV4(
                            questionCount,
                            optionCount
                    );
        }

        return OmrSheetTemplateCatalog
                .flexibleOptionsV4(
                        questionCount,
                        optionCount,
                        configuredQuestionsPerColumn
                );
    }

    private void writeDocument(
            Uri destinationUri,
            OmrSheetSvgDocument document
    ) throws IOException {
        try (OutputStream outputStream =
                     getContentResolver().openOutputStream(
                             destinationUri,
                             "wt"
                     )) {

            if (outputStream == null) {
                throw new IOException(
                        "O Android nao abriu o destino escolhido."
                );
            }

            outputStream.write(document.getUtf8Bytes());
            outputStream.flush();
        }
    }

    private OmrSheetExportViewState restoreViewState(
            @Nullable Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return OmrSheetExportViewState.defaultState();
        }

        int questionCount = savedInstanceState.getInt(
                STATE_QUESTION_COUNT,
                OmrSheetExportViewState
                        .DEFAULT_QUESTION_COUNT
        );

        int optionCount = savedInstanceState.getInt(
                STATE_OPTION_COUNT,
                OmrSheetExportViewState
                        .DEFAULT_OPTION_COUNT
        );

        int configuredQuestionsPerColumn =
                savedInstanceState.getInt(
                        STATE_QUESTIONS_PER_COLUMN,
                        OmrSheetExportViewState
                                .AUTOMATIC_QUESTIONS_PER_COLUMN
                );

        OmrSheetTextCustomization textCustomization =
                restoreTextCustomization(
                        savedInstanceState,
                        STATE_HEADER_OVERRIDDEN,
                        STATE_HEADER_TEXT,
                        STATE_INSTRUCTION_OVERRIDDEN,
                        STATE_INSTRUCTION_TEXT,
                        STATE_FOOTER_OVERRIDDEN,
                        STATE_FOOTER_TEXT
                );

        try {
            return OmrSheetExportViewState
                    .fromSelection(
                            questionCount,
                            optionCount,
                            configuredQuestionsPerColumn,
                            textCustomization
                    );

        } catch (IllegalArgumentException exception) {
            Log.w(
                    TAG,
                    "Estado salvo da exportacao era invalido.",
                    exception
            );

            return OmrSheetExportViewState.defaultState();
        }
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        if (viewBinder != null) {
            viewState = viewBinder.getCurrentViewState();
        }

        if (viewState != null) {
            outState.putInt(
                    STATE_QUESTION_COUNT,
                    viewState.getQuestionCount()
            );

            outState.putInt(
                    STATE_OPTION_COUNT,
                    viewState.getOptionCount()
            );

            outState.putInt(
                    STATE_QUESTIONS_PER_COLUMN,
                    viewState.getConfiguredQuestionsPerColumn()
            );

            saveTextCustomization(
                    outState,
                    viewState.getTextCustomization(),
                    STATE_HEADER_OVERRIDDEN,
                    STATE_HEADER_TEXT,
                    STATE_INSTRUCTION_OVERRIDDEN,
                    STATE_INSTRUCTION_TEXT,
                    STATE_FOOTER_OVERRIDDEN,
                    STATE_FOOTER_TEXT
            );
        }

        outState.putInt(
                STATE_PENDING_QUESTION_COUNT,
                pendingQuestionCount
        );

        outState.putInt(
                STATE_PENDING_OPTION_COUNT,
                pendingOptionCount
        );

        outState.putInt(
                STATE_PENDING_QUESTIONS_PER_COLUMN,
                pendingQuestionsPerColumn
        );

        saveTextCustomization(
                outState,
                pendingTextCustomization,
                STATE_PENDING_HEADER_OVERRIDDEN,
                STATE_PENDING_HEADER_TEXT,
                STATE_PENDING_INSTRUCTION_OVERRIDDEN,
                STATE_PENDING_INSTRUCTION_TEXT,
                STATE_PENDING_FOOTER_OVERRIDDEN,
                STATE_PENDING_FOOTER_TEXT
        );

        super.onSaveInstanceState(outState);
    }

    private OmrSheetTextCustomization restoreTextCustomization(
            @Nullable Bundle state,
            String headerOverriddenKey,
            String headerTextKey,
            String instructionOverriddenKey,
            String instructionTextKey,
            String footerOverriddenKey,
            String footerTextKey
    ) {
        if (state == null) {
            return OmrSheetTextCustomization.defaults();
        }

        String headerOverride = state.getBoolean(
                headerOverriddenKey,
                false
        ) ? readSavedText(state, headerTextKey) : null;
        String instructionOverride = state.getBoolean(
                instructionOverriddenKey,
                false
        ) ? readSavedText(state, instructionTextKey) : null;
        String footerOverride = state.getBoolean(
                footerOverriddenKey,
                false
        ) ? readSavedText(state, footerTextKey) : null;

        try {
            return OmrSheetTextCustomization.fromOverrides(
                    headerOverride,
                    instructionOverride,
                    footerOverride
            );
        } catch (IllegalArgumentException exception) {
            Log.w(
                    TAG,
                    "Textos salvos da exportacao eram invalidos.",
                    exception
            );
            return OmrSheetTextCustomization.defaults();
        }
    }

    private String readSavedText(
            Bundle state,
            String key
    ) {
        String value = state.getString(key);
        return value == null ? "" : value;
    }

    private void saveTextCustomization(
            Bundle state,
            OmrSheetTextCustomization customization,
            String headerOverriddenKey,
            String headerTextKey,
            String instructionOverriddenKey,
            String instructionTextKey,
            String footerOverriddenKey,
            String footerTextKey
    ) {
        state.putBoolean(
                headerOverriddenKey,
                customization.hasHeaderOverride()
        );
        state.putBoolean(
                instructionOverriddenKey,
                customization.hasInstructionOverride()
        );
        state.putBoolean(
                footerOverriddenKey,
                customization.hasFooterOverride()
        );

        saveOptionalText(
                state,
                headerTextKey,
                customization.hasHeaderOverride(),
                customization.getHeaderOverride()
        );
        saveOptionalText(
                state,
                instructionTextKey,
                customization.hasInstructionOverride(),
                customization.getInstructionOverride()
        );
        saveOptionalText(
                state,
                footerTextKey,
                customization.hasFooterOverride(),
                customization.getFooterOverride()
        );
    }

    private void saveOptionalText(
            Bundle state,
            String key,
            boolean overridden,
            String value
    ) {
        if (overridden) {
            state.putString(key, value);
        } else {
            state.remove(key);
        }
    }

    private void clearPendingExport() {
        pendingQuestionCount = 0;
        pendingOptionCount = 0;
        pendingQuestionsPerColumn =
                OmrSheetExportViewState
                        .AUTOMATIC_QUESTIONS_PER_COLUMN;
        pendingTextCustomization =
                OmrSheetTextCustomization.defaults();
    }

    @Override
    protected void onDestroy() {
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        svgGenerator = null;
        viewState = null;

        super.onDestroy();
    }

    private void showMessage(
            int stringResource
    ) {
        showMessage(getString(stringResource));
    }

    private void showMessage(
            String message
    ) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
