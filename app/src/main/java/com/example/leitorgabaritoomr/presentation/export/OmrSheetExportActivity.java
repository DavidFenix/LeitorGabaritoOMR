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
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetSvgDocument;
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetSvgGenerator;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Permite escolher de 1 a 90 questões, quatro ou cinco alternativas e
 * salvar o cartão padronizado v2 pelo seletor nativo.
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

    private static final String STATE_PENDING_QUESTION_COUNT =
            "omr.sheet_export.pending_question_count";

    private static final String STATE_PENDING_OPTION_COUNT =
            "omr.sheet_export.pending_option_count";

    private OmrSheetSvgGenerator svgGenerator;
    private OmrSheetExportViewState viewState;
    private OmrSheetExportViewBinder viewBinder;

    private int pendingQuestionCount;
    private int pendingOptionCount;

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
                            viewState.getOptionCount()
                    );

            pendingQuestionCount =
                    document.getQuestionCount();

            pendingOptionCount =
                    viewState.getOptionCount();

            viewBinder.setExportInProgress(true);

            createDocumentLauncher.launch(
                    document.getSuggestedFileName()
            );

        } catch (RuntimeException exception) {
            pendingQuestionCount = 0;
            pendingOptionCount = 0;
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
            pendingQuestionCount = 0;
            pendingOptionCount = 0;

            showMessage(
                    R.string.omr_sheet_export_cancelled
            );

            return;
        }

        int questionCount = pendingQuestionCount;
        int optionCount = pendingOptionCount;

        if (questionCount <= 0 && viewState != null) {
            questionCount = viewState.getQuestionCount();
        }

        if (optionCount <= 0 && viewState != null) {
            optionCount = viewState.getOptionCount();
        }

        try {
            OmrSheetSvgDocument document =
                    generateDocument(
                            questionCount,
                            optionCount
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
            pendingQuestionCount = 0;
            pendingOptionCount = 0;
        }
    }

    private OmrSheetSvgDocument generateDocument(
            int questionCount,
            int optionCount
    ) {
        OmrSheetTemplateSpec spec =
                createExportTemplate(
                        questionCount,
                        optionCount
                );

        return svgGenerator.generate(spec);
    }

    /**
     * Mantém explícita e testável a geometria atualmente entregue ao usuário.
     * Os modelos v1 continuam publicados apenas para leitura de gabaritos já
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
        return OmrSheetTemplateCatalog
                .standardOptionsV2(
                        questionCount,
                        optionCount
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

        try {
            return OmrSheetExportViewState
                    .fromSelection(
                            questionCount,
                            optionCount
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
        }

        outState.putInt(
                STATE_PENDING_QUESTION_COUNT,
                pendingQuestionCount
        );

        outState.putInt(
                STATE_PENDING_OPTION_COUNT,
                pendingOptionCount
        );

        super.onSaveInstanceState(outState);
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
