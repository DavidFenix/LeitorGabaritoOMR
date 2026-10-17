package com.example.leitorgabaritoomr.presentation.grading;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyCsvParser;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyCsvRegistrationService;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyPngZipPlan;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyPngZipPlanner;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAnswerKeyCsvExporter;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAnswerKeyPngZipExporter;
import com.example.leitorgabaritoomr.infrastructure.export.OmrSheetPngRenderer;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordena a listagem, seleção e exclusão dos gabaritos oficiais armazenados
 * no dispositivo.
 *
 * O repositório continua sendo a fonte da verdade. A Activity apenas cria uma
 * fotografia visual, recebe as ações do Binder e renderiza novamente após cada
 * alteração persistida.
 */
public final class OmrAnswerKeyListActivity
        extends AppCompatActivity {

    public static final String EXTRA_REPOSITORY_CHANGED =
            "com.example.leitorgabaritoomr.extra."
                    + "ANSWER_KEY_REPOSITORY_CHANGED";

    public static final String EXTRA_ACTIVE_ANSWER_KEY =
            "com.example.leitorgabaritoomr.extra."
                    + "ACTIVE_ANSWER_KEY";

    private static final String STATE_REPOSITORY_CHANGED =
            "omr.answer_key_list.repository_changed";

    private static final String STATE_PENDING_EXPORT =
            "omr.answer_key_list.pending_export";

    private static final String STATE_PENDING_PNG_ZIP_PLAN_FILE =
            "omr.answer_key_list.pending_png_zip_plan_file";

    private static final String PNG_ZIP_PLAN_FILE_PREFIX =
            "omr-png-zip-plan-";

    private static final String PNG_ZIP_PLAN_FILE_SUFFIX =
            ".bin";

    private static final long PNG_ZIP_PLAN_MAXIMUM_AGE_MILLIS =
            24L * 60L * 60L * 1_000L;

    private static final String EXTRA_STORAGE_NAMESPACE =
            "com.example.leitorgabaritoomr.extra."
                    + "ANSWER_KEY_STORAGE_NAMESPACE";

    private static final String TAG =
            "OmrAnswerKeyList";

    private static final int AUTOMATIC_QUESTIONS_PER_COLUMN = 0;

    private static final int MAX_PREVIEW_ROWS = 10;
    private static final int MAX_PREVIEW_ISSUES = 10;

    private static final String[] CSV_MIME_TYPES = {
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel"
    };

    private static final String CSV_MIME_TYPE = "text/csv";
    private static final String CSV_EXPORT_FILE_NAME =
            "gabaritos-omr.csv";
    private static final String CSV_TEMPLATE_FILE_NAME =
            "modelo-importacao-gabaritos.csv";

    private OmrAnswerKeyRepository repository;
    private OmrAnswerKeyCsvRegistrationService registrationService;
    private OmrAnswerKeyCsvParser csvParser;
    private OmrAnswerKeyCsvExporter csvExporter;
    private OmrAnswerKeyPngZipPlanner pngZipPlanner;
    private OmrAnswerKeyPngZipExporter pngZipExporter;
    private OmrAnswerKeyListViewBinder viewBinder;
    private ExecutorService backgroundExecutor;
    private AlertDialog questionCountDialog;
    private AlertDialog optionCountDialog;
    private AlertDialog questionsPerColumnDialog;
    private AlertDialog csvPreviewDialog;
    private AlertDialog pngZipPreviewDialog;
    private AlertDialog exportSelectionDialog;
    private List<OmrAnswerKeyDefinition> pendingExportAnswerKeys =
            Collections.emptyList();
    private String pendingPngZipPlanFileName;
    private boolean repositoryChanged;
    private volatile boolean released;

    private final ActivityResultLauncher<Intent>
            manualAnswerKeyLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    this::handleManualAnswerKeyResult
            );

    private final ActivityResultLauncher<String[]> csvDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::handleCsvDocument
            );

    private final ActivityResultLauncher<String> csvExportLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            CSV_MIME_TYPE
                    ),
                    this::handleCsvExportDestination
            );

    private final ActivityResultLauncher<String> csvTemplateLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            CSV_MIME_TYPE
                    ),
                    this::handleCsvTemplateDestination
            );

    private final ActivityResultLauncher<String[]> pngZipCsvLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::handlePngZipCsvDocument
            );

    private final ActivityResultLauncher<String> pngZipDestinationLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            OmrAnswerKeyPngZipExporter.MIME_TYPE
                    ),
                    this::handlePngZipDestination
            );

    public static Intent createIntent(
            Context context
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        return new Intent(
                context,
                OmrAnswerKeyListActivity.class
        );
    }

    /**
     * Cria um Intent com armazenamento isolado para testes instrumentados.
     *
     * O método possui visibilidade de pacote para não fazer parte do contrato
     * público usado pelo aplicativo.
     */
    static Intent createIsolatedStorageIntent(
            Context context,
            String storageNamespace
    ) {
        if (storageNamespace == null
                || storageNamespace.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O namespace de armazenamento é obrigatório."
            );
        }

        return createIntent(context).putExtra(
                EXTRA_STORAGE_NAMESPACE,
                storageNamespace.trim()
        );
    }

    public static boolean didRepositoryChange(
            @Nullable Intent resultData
    ) {
        return resultData != null
                && resultData.getBooleanExtra(
                EXTRA_REPOSITORY_CHANGED,
                false
        );
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrAnswerKeyDefinition extractActiveAnswerKey(
            @Nullable Intent resultData
    ) {
        if (resultData == null) {
            return null;
        }

        Serializable value =
                resultData.getSerializableExtra(
                        EXTRA_ACTIVE_ANSWER_KEY
                );

        if (!(value instanceof OmrAnswerKeyDefinition)) {
            return null;
        }

        return (OmrAnswerKeyDefinition) value;
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_omr_answer_key_list
        );

        OmrSharedPreferencesAnswerKeyRepository
                sharedPreferencesRepository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        createRepositoryContext()
                );

        repository = sharedPreferencesRepository;
        registrationService =
                new OmrAnswerKeyCsvRegistrationService(
                        sharedPreferencesRepository
                );
        csvParser = new OmrAnswerKeyCsvParser();
        csvExporter = new OmrAnswerKeyCsvExporter();
        pngZipPlanner = new OmrAnswerKeyPngZipPlanner();
        pngZipExporter = new OmrAnswerKeyPngZipExporter();
        backgroundExecutor = Executors.newSingleThreadExecutor();

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder =
                new OmrAnswerKeyListViewBinder(
                        rootView
                );

        repositoryChanged = savedInstanceState != null
                && savedInstanceState.getBoolean(
                STATE_REPOSITORY_CHANGED,
                false
        );

        pendingExportAnswerKeys = restorePendingExport(
                savedInstanceState
        );

        pendingPngZipPlanFileName = restorePendingPngZipPlanFileName(
                savedInstanceState
        );
        removeExpiredPngZipPlanFiles(
                pendingPngZipPlanFileName
        );

        configureBinder();

        if (repositoryChanged) {
            updateActivityResult();
        } else {
            setResult(Activity.RESULT_CANCELED);
        }

        renderRepository();
    }

    private Context createRepositoryContext() {
        String storageNamespace = getIntent()
                .getStringExtra(
                        EXTRA_STORAGE_NAMESPACE
                );

        if (storageNamespace == null
                || storageNamespace.trim().isEmpty()) {
            return this;
        }

        Context baseContext = this;
        String preferencesSuffix =
                "." + storageNamespace.trim();

        return new ContextWrapper(baseContext) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(
                    String name,
                    int mode
            ) {
                return baseContext.getSharedPreferences(
                        name + preferencesSuffix,
                        mode
                );
            }
        };
    }

    private void configureBinder() {
        viewBinder.setOnAnswerKeyActionListener(
                new OmrAnswerKeyListViewBinder
                        .OnAnswerKeyActionListener() {

                    @Override
                    public void onSelectAnswerKey(
                            String answerKeyId,
                            int answerKeyVersion
                    ) {
                        selectAnswerKey(
                                answerKeyId,
                                answerKeyVersion
                        );
                    }

                    @Override
                    public void onDeleteAnswerKey(
                            String answerKeyId,
                            int answerKeyVersion,
                            String answerKeyName,
                            boolean active
                    ) {
                        confirmDeleteAnswerKey(
                                answerKeyId,
                                answerKeyVersion,
                                answerKeyName,
                                active
                        );
                    }
                }
        );

        viewBinder.setOnCreateClickListener(
                view -> showQuestionCountDialog()
        );

        viewBinder.setOnImportCsvClickListener(
                view -> csvDocumentLauncher.launch(
                        CSV_MIME_TYPES
                )
        );

        viewBinder.setOnExportCsvClickListener(
                view -> showExportSelectionDialog()
        );

        viewBinder.setOnCsvTemplateClickListener(
                view -> csvTemplateLauncher.launch(
                        CSV_TEMPLATE_FILE_NAME
                )
        );

        viewBinder.setOnPngZipClickListener(
                view -> pngZipCsvLauncher.launch(
                        CSV_MIME_TYPES
                )
        );

        viewBinder.setOnBackClickListener(
                view -> finish()
        );
    }

    private void showQuestionCountDialog() {
        if (questionCountDialog != null
                && questionCountDialog.isShowing()) {

            return;
        }

        int minimum = OmrSheetTemplateCatalog
                .MIN_QUESTION_COUNT;

        int maximum = OmrSheetTemplateCatalog
                .MAX_QUESTION_COUNT;

        String[] countLabels =
                new String[maximum - minimum + 1];

        for (int index = 0;
             index < countLabels.length;
             index++) {

            int questionCount = minimum + index;

            countLabels[index] = getResources()
                    .getQuantityString(
                            R.plurals
                                    .omr_answer_key_list_create_question_count,
                            questionCount,
                            questionCount
                    );
        }

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                R.string
                                        .omr_answer_key_list_create_count_title
                        )
                        .setItems(
                                countLabels,
                                (dialogInterface, selectedIndex) ->
                                        showOptionCountDialog(
                                                minimum
                                                        + selectedIndex
                                        )
                        )
                        .setNegativeButton(
                                R.string
                                        .omr_answer_key_list_create_count_cancel,
                                null
                        )
                        .create();

        questionCountDialog = dialog;

        dialog.setOnDismissListener(
                dialogInterface -> {
                    if (questionCountDialog == dialog) {
                        questionCountDialog = null;
                    }
                }
        );

        dialog.show();
    }

    private void showOptionCountDialog(
            int questionCount
    ) {
        if (optionCountDialog != null
                && optionCountDialog.isShowing()) {

            return;
        }

        String[] optionLabels = {
                getString(
                        R.string
                                .omr_answer_key_list_create_four_options
                ),
                getString(
                        R.string
                                .omr_answer_key_list_create_five_options
                )
        };

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                R.string
                                        .omr_answer_key_list_create_option_count_title
                        )
                        .setItems(
                                optionLabels,
                                (dialogInterface, selectedIndex) ->
                                        showQuestionsPerColumnDialog(
                                                questionCount,
                                                selectedIndex == 0
                                                        ? OmrSheetTemplateCatalog
                                                        .STANDARD_V2_FOUR_OPTION_COUNT
                                                        : OmrSheetTemplateCatalog
                                                        .STANDARD_V2_FIVE_OPTION_COUNT
                                        )
                        )
                        .setNegativeButton(
                                R.string
                                        .omr_answer_key_list_create_count_cancel,
                                null
                        )
                        .create();

        optionCountDialog = dialog;

        dialog.setOnDismissListener(
                dialogInterface -> {
                    if (optionCountDialog == dialog) {
                        optionCountDialog = null;
                    }
                }
        );

        dialog.show();
    }

    private void showQuestionsPerColumnDialog(
            int questionCount,
            int optionCount
    ) {
        if (questionsPerColumnDialog != null
                && questionsPerColumnDialog.isShowing()) {

            return;
        }

        int minimum = OmrSheetTemplateCatalog
                .getFlexibleV3MinimumQuestionsPerColumn(
                        questionCount
                );

        int maximum = OmrSheetTemplateCatalog
                .getFlexibleV3MaximumQuestionsPerColumn(
                        questionCount
                );

        String[] distributionLabels =
                new String[maximum - minimum + 2];

        int automaticRows = OmrSheetTemplateCatalog
                .getFlexibleV3AutomaticQuestionsPerColumn(
                        questionCount
                );

        distributionLabels[0] = formatDistributionLabel(
                questionCount,
                automaticRows,
                true
        );

        for (int rows = minimum;
             rows <= maximum;
             rows++) {

            distributionLabels[rows - minimum + 1] =
                    formatDistributionLabel(
                            questionCount,
                            rows,
                            false
                    );
        }

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                R.string
                                        .omr_answer_key_list_create_questions_per_column_title
                        )
                        .setItems(
                                distributionLabels,
                                (dialogInterface, selectedIndex) -> {
                                    int configuredQuestionsPerColumn =
                                            selectedIndex == 0
                                                    ? AUTOMATIC_QUESTIONS_PER_COLUMN
                                                    : minimum
                                                    + selectedIndex
                                                    - 1;

                                    openDynamicAnswerKeyEditor(
                                            questionCount,
                                            optionCount,
                                            configuredQuestionsPerColumn
                                    );
                                }
                        )
                        .setNegativeButton(
                                R.string
                                        .omr_answer_key_list_create_count_cancel,
                                null
                        )
                        .create();

        questionsPerColumnDialog = dialog;

        dialog.setOnDismissListener(
                dialogInterface -> {
                    if (questionsPerColumnDialog == dialog) {
                        questionsPerColumnDialog = null;
                    }
                }
        );

        dialog.show();
    }

    private String formatDistributionLabel(
            int questionCount,
            int questionsPerColumn,
            boolean automatic
    ) {
        String rowText = getResources().getQuantityString(
                R.plurals
                        .omr_answer_key_list_create_items_per_column,
                questionsPerColumn,
                questionsPerColumn
        );

        int columnCount = (questionCount
                + questionsPerColumn
                - 1)
                / questionsPerColumn;

        String columnText = getResources().getQuantityString(
                R.plurals
                        .omr_answer_key_list_create_column_count,
                columnCount,
                columnCount
        );

        return getString(
                automatic
                        ? R.string
                        .omr_answer_key_list_create_distribution_automatic
                        : R.string
                        .omr_answer_key_list_create_distribution_manual,
                rowText,
                columnText
        );
    }

    private void openDynamicAnswerKeyEditor(
            int questionCount,
            int optionCount,
            int configuredQuestionsPerColumn
    ) {
        Intent intent = configuredQuestionsPerColumn
                == AUTOMATIC_QUESTIONS_PER_COLUMN
                ? OmrManualAnswerKeyActivity
                .createFlexibleV4Intent(
                        this,
                        questionCount,
                        optionCount
                )
                : OmrManualAnswerKeyActivity
                .createFlexibleV4Intent(
                        this,
                        questionCount,
                        optionCount,
                        configuredQuestionsPerColumn
                );

        manualAnswerKeyLauncher.launch(
                intent
        );
    }

    private void handleManualAnswerKeyResult(
            ActivityResult activityResult
    ) {
        if (activityResult.getResultCode()
                != Activity.RESULT_OK) {
            return;
        }

        OmrAnswerKeyDefinition createdAnswerKey =
                OmrManualAnswerKeyActivity
                        .extractCreatedAnswerKey(
                                activityResult.getData()
                        );

        if (createdAnswerKey == null) {
            Log.e(
                    TAG,
                    "O cadastro retornou sem gabarito válido."
            );

            showMessage(
                    R.string.omr_answer_key_list_create_error
            );

            return;
        }

        try {
            repository.saveActive(createdAnswerKey);

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível salvar o novo gabarito.",
                    exception
            );

            showMessage(
                    R.string.omr_answer_key_list_save_error
            );

            return;
        }

        markRepositoryChanged();
        renderRepository();

        Toast.makeText(
                this,
                getString(
                        R.string
                        .omr_answer_key_list_created_message,
                        createdAnswerKey.getName()
                ),
                Toast.LENGTH_LONG
        ).show();

        Log.i(
                TAG,
                "Gabarito cadastrado e selecionado"
                        + " | id="
                        + createdAnswerKey.getId()
                        + " | versão="
                        + createdAnswerKey.getVersion()
        );
    }

    private void handleCsvDocument(
            @Nullable Uri documentUri
    ) {
        if (documentUri == null
                || released
                || backgroundExecutor == null) {
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try {
                byte[] bytes = readCsvBytes(documentUri);
                OmrAnswerKeyCsvParser.Preview preview =
                        csvParser.parse(bytes);

                postToUi(() -> showCsvPreview(preview));

            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV de gabaritos acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_answer_key_csv_too_large
                ));

            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao ler CSV de gabaritos.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_answer_key_csv_read_error
                ));
            }
        });
    }

    private byte[] readCsvBytes(
            Uri documentUri
    ) throws IOException, CsvTooLargeException {
        try (InputStream input = getContentResolver()
                .openInputStream(documentUri)) {

            if (input == null) {
                throw new IOException(
                        "O provedor não abriu o CSV selecionado."
                );
            }

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;

                if (total > OmrAnswerKeyCsvParser
                        .MAX_CSV_BYTE_COUNT) {
                    throw new CsvTooLargeException();
                }

                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    void showCsvPreview(
            OmrAnswerKeyCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A prévia do CSV de gabaritos é obrigatória."
            );
        }

        viewBinder.setBusy(false);
        dismissCsvPreviewDialog();

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(
                                preview.isReadyToImport()
                                        ? R.string
                                        .omr_answer_key_csv_preview_title
                                        : R.string
                                        .omr_answer_key_csv_preview_error_title
                        )
                        .setMessage(formatCsvPreview(preview));

        if (preview.isReadyToImport()) {
            builder.setNegativeButton(
                    R.string.omr_answer_key_csv_action_cancel,
                    null
            );
            builder.setPositiveButton(
                    R.string.omr_answer_key_csv_action_import,
                    (dialog, which) -> importCsvPreview(preview)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_answer_key_csv_action_close,
                    null
            );
        }

        AlertDialog dialog = builder.create();
        csvPreviewDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (csvPreviewDialog == dialog) {
                csvPreviewDialog = null;
            }
        });

        dialog.show();
    }

    private String formatCsvPreview(
            OmrAnswerKeyCsvParser.Preview preview
    ) {
        if (preview.isReadyToImport()) {
            int rowCount = preview.getRows().size();
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_answer_key_csv_preview_valid_rows,
                            rowCount,
                            rowCount
                    )
            );

            int visibleCount = Math.min(
                    rowCount,
                    MAX_PREVIEW_ROWS
            );

            for (int index = 0;
                 index < visibleCount;
                 index++) {

                OmrAnswerKeyCsvParser.Row row =
                        preview.getRows().get(index);

                OmrAnswerKeyDefinition definition =
                        row.getAnswerKeyDefinition();

                message.append("\n\n• ")
                        .append(row.getCode())
                        .append(" — ")
                        .append(definition.getName())
                        .append(" • v")
                        .append(definition.getVersion())
                        .append(" • ")
                        .append(definition.getQuestionCount())
                        .append(" questões • A–")
                        .append(optionLetter(row.getOptionCount()));
            }

            int hiddenCount = rowCount - visibleCount;

            if (hiddenCount > 0) {
                message.append("\n\n")
                        .append(getResources().getQuantityString(
                                R.plurals
                                        .omr_answer_key_csv_preview_more_rows,
                                hiddenCount,
                                hiddenCount
                        ));
            }

            message.append("\n\n")
                    .append(getString(
                            R.string
                                    .omr_answer_key_csv_preview_import_note
                    ))
                    .append("\n\n")
                    .append(getString(
                            R.string
                                    .omr_answer_key_csv_preview_technical,
                            preview.getEncoding(),
                            String.valueOf(preview.getDelimiter())
                    ));

            return message.toString();
        }

        List<OmrAnswerKeyCsvParser.Issue> issues =
                preview.getIssues();

        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals
                                .omr_answer_key_csv_preview_error_summary,
                        issues.size(),
                        issues.size()
                )
        );

        int visibleCount = Math.min(
                issues.size(),
                MAX_PREVIEW_ISSUES
        );

        for (int index = 0;
             index < visibleCount;
             index++) {

            OmrAnswerKeyCsvParser.Issue issue =
                    issues.get(index);

            message.append("\n\n• ")
                    .append(getString(
                            R.string.omr_answer_key_csv_issue_line,
                            issue.getLineNumber(),
                            issue.getMessage()
                    ));
        }

        int hiddenCount = issues.size() - visibleCount;

        if (hiddenCount > 0) {
            message.append("\n\n")
                    .append(getResources().getQuantityString(
                            R.plurals
                                    .omr_answer_key_csv_preview_more_errors,
                            hiddenCount,
                            hiddenCount
                    ));
        }

        return message.toString();
    }

    private static char optionLetter(int optionCount) {
        return (char) ('A' + optionCount - 1);
    }

    private void importCsvPreview(
            OmrAnswerKeyCsvParser.Preview preview
    ) {
        if (backgroundExecutor == null) {
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try {
                OmrAnswerKeyCsvRegistrationService.ImportSummary
                        summary = registrationService
                        .importPreview(preview);

                postToUi(() -> {
                    viewBinder.setBusy(false);

                    if (summary.getImportedCount() > 0) {
                        markRepositoryChanged();
                    }

                    renderRepository();

                    Toast.makeText(
                            this,
                            getString(
                                    R.string
                                            .omr_answer_key_csv_import_success,
                                    summary.getImportedCount(),
                                    summary.getUnchangedCount()
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao importar CSV de gabaritos.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_answer_key_csv_import_error
                ));
            }
        });
    }

    private void showExportSelectionDialog() {
        List<OmrAnswerKeyDefinition> answerKeys;

        try {
            answerKeys = repository.loadAll();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar gabaritos para exportação.", exception);
            showMessage(R.string.omr_answer_key_csv_export_error);
            return;
        }

        if (answerKeys.isEmpty()) {
            showMessage(R.string.omr_answer_key_csv_export_empty);
            return;
        }

        dismissExportSelectionDialog();

        String[] labels = new String[answerKeys.size()];
        boolean[] checked = new boolean[answerKeys.size()];

        for (int index = 0;
             index < answerKeys.size();
             index++) {

            OmrAnswerKeyDefinition answerKey =
                    answerKeys.get(index);

            labels[index] = getString(
                    R.string.omr_answer_key_csv_export_item_format,
                    answerKey.getName(),
                    answerKey.getVersion(),
                    answerKey.getQuestionCount()
            );

            checked[index] = true;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        R.string
                                .omr_answer_key_csv_export_selection_title
                )
                .setMultiChoiceItems(
                        labels,
                        checked,
                        (ignored, which, selected) ->
                                checked[which] = selected
                )
                .setNegativeButton(
                        R.string.omr_answer_key_csv_export_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_answer_key_csv_export_save,
                        null
                )
                .create();

        exportSelectionDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (exportSelectionDialog == dialog) {
                exportSelectionDialog = null;
            }
        });

        dialog.setOnShowListener(ignored -> dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    List<OmrAnswerKeyDefinition> selected =
                            selectedAnswerKeys(
                                    answerKeys,
                                    checked
                            );

                    if (selected.isEmpty()) {
                        Toast.makeText(
                                this,
                                R.string
                                        .omr_answer_key_csv_export_selection_required,
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    pendingExportAnswerKeys =
                            Collections.unmodifiableList(
                                    new ArrayList<>(selected)
                            );

                    dialog.dismiss();
                    csvExportLauncher.launch(
                            CSV_EXPORT_FILE_NAME
                    );
                }));

        dialog.show();
    }

    private static List<OmrAnswerKeyDefinition> selectedAnswerKeys(
            List<OmrAnswerKeyDefinition> answerKeys,
            boolean[] checked
    ) {
        List<OmrAnswerKeyDefinition> selected =
                new ArrayList<>();

        for (int index = 0;
             index < answerKeys.size();
             index++) {

            if (checked[index]) {
                selected.add(answerKeys.get(index));
            }
        }

        return selected;
    }

    private void handleCsvExportDestination(
            @Nullable Uri destinationUri
    ) {
        if (destinationUri == null) {
            pendingExportAnswerKeys = Collections.emptyList();
            showMessage(R.string.omr_answer_key_csv_export_cancelled);
            return;
        }

        List<OmrAnswerKeyDefinition> answerKeys =
                new ArrayList<>(pendingExportAnswerKeys);

        pendingExportAnswerKeys = Collections.emptyList();

        if (answerKeys.isEmpty()
                || backgroundExecutor == null) {
            showMessage(R.string.omr_answer_key_csv_export_error);
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try (OutputStream output = getContentResolver()
                    .openOutputStream(destinationUri, "w")) {

                if (output == null) {
                    throw new IOException(
                            "O destino do CSV não pôde ser aberto."
                    );
                }

                csvExporter.write(answerKeys, output);
                postToUi(() -> finishCsvWrite(
                        true,
                        R.string.omr_answer_key_csv_export_success,
                        R.string.omr_answer_key_csv_export_error
                ));

            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao exportar gabaritos.", exception);
                postToUi(() -> finishCsvWrite(
                        false,
                        R.string.omr_answer_key_csv_export_success,
                        R.string.omr_answer_key_csv_export_error
                ));
            }
        });
    }

    private void handleCsvTemplateDestination(
            @Nullable Uri destinationUri
    ) {
        if (destinationUri == null) {
            showMessage(R.string.omr_answer_key_csv_template_cancelled);
            return;
        }

        if (backgroundExecutor == null) {
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try (OutputStream output = getContentResolver()
                    .openOutputStream(destinationUri, "w")) {

                if (output == null) {
                    throw new IOException(
                            "O destino do modelo CSV não pôde ser aberto."
                    );
                }

                csvExporter.writeTemplate(output);
                postToUi(() -> finishCsvWrite(
                        true,
                        R.string.omr_answer_key_csv_template_success,
                        R.string.omr_answer_key_csv_template_error
                ));

            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao salvar modelo CSV.", exception);
                postToUi(() -> finishCsvWrite(
                        false,
                        R.string.omr_answer_key_csv_template_success,
                        R.string.omr_answer_key_csv_template_error
                ));
            }
        });
    }

    private void finishCsvWrite(
            boolean success,
            int successMessage,
            int errorMessage
    ) {
        viewBinder.setBusy(false);
        showMessage(success ? successMessage : errorMessage);
    }

    private void handlePngZipCsvDocument(
            @Nullable Uri documentUri
    ) {
        OmrAnswerKeyCsvParser parser = csvParser;
        OmrAnswerKeyPngZipPlanner planner = pngZipPlanner;

        if (documentUri == null
                || released
                || backgroundExecutor == null
                || parser == null
                || planner == null) {
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try {
                byte[] bytes = readCsvBytes(documentUri);
                OmrAnswerKeyPngZipPlan plan = planner.plan(
                        parser.parse(bytes)
                );

                postToUi(() -> showPngZipPreview(plan));

            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV de cartões acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_answer_key_csv_too_large
                ));

            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao preparar cartões pelo CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_answer_key_png_zip_csv_read_error
                ));
            }
        });
    }

    void showPngZipPreview(
            OmrAnswerKeyPngZipPlan plan
    ) {
        if (plan == null) {
            throw new IllegalArgumentException(
                    "O plano dos cartões PNG é obrigatório."
            );
        }

        viewBinder.setBusy(false);
        dismissPngZipPreviewDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(
                        plan.isReadyToExport()
                                ? R.string
                                .omr_answer_key_png_zip_preview_title
                                : R.string
                                .omr_answer_key_png_zip_preview_error_title
                )
                .setMessage(formatPngZipPreview(plan));

        if (plan.isReadyToExport()) {
            builder.setNegativeButton(
                    R.string.omr_answer_key_png_zip_action_cancel,
                    null
            );
            builder.setPositiveButton(
                    R.string.omr_answer_key_png_zip_action_generate,
                    (dialog, which) ->
                            preparePngZipDestination(plan)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_answer_key_csv_action_close,
                    null
            );
        }

        AlertDialog dialog = builder.create();
        pngZipPreviewDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (pngZipPreviewDialog == dialog) {
                pngZipPreviewDialog = null;
            }
        });
        dialog.show();
    }

    private String formatPngZipPreview(
            OmrAnswerKeyPngZipPlan plan
    ) {
        if (plan.isReadyToExport()) {
            int cardCount = plan.getCardCount();
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_answer_key_png_zip_preview_valid,
                            cardCount,
                            cardCount
                    )
            );

            int visibleCount = Math.min(
                    cardCount,
                    MAX_PREVIEW_ROWS
            );

            for (int index = 0; index < visibleCount; index++) {
                OmrAnswerKeyPngZipPlan.Card card =
                        plan.getCards().get(index);
                OmrAnswerKeyDefinition definition =
                        card.getAnswerKeyDefinition();

                message.append("\n\n• ")
                        .append(card.getCode())
                        .append(" → ")
                        .append(card.getOutputFileName())
                        .append(" • ")
                        .append(definition.getQuestionCount())
                        .append(" questões");
            }

            int hiddenCount = cardCount - visibleCount;
            if (hiddenCount > 0) {
                message.append("\n\n")
                        .append(getResources().getQuantityString(
                                R.plurals
                                        .omr_answer_key_png_zip_preview_more,
                                hiddenCount,
                                hiddenCount
                        ));
            }

            message.append("\n\n")
                    .append(getString(
                            R.string.omr_answer_key_png_zip_preview_note,
                            OmrSheetPngRenderer.DEFAULT_DOTS_PER_INCH
                    ));

            return message.toString();
        }

        List<OmrAnswerKeyPngZipPlan.Issue> issues = plan.getIssues();
        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals.omr_answer_key_png_zip_preview_issues,
                        issues.size(),
                        issues.size()
                )
        );

        int visibleCount = Math.min(
                issues.size(),
                MAX_PREVIEW_ISSUES
        );

        for (int index = 0; index < visibleCount; index++) {
            OmrAnswerKeyPngZipPlan.Issue issue = issues.get(index);
            message.append("\n\n• ")
                    .append(getString(
                            R.string.omr_answer_key_csv_issue_line,
                            issue.getLineNumber(),
                            issue.getMessage()
                    ));
        }

        int hiddenCount = issues.size() - visibleCount;
        if (hiddenCount > 0) {
            message.append("\n\n")
                    .append(getResources().getQuantityString(
                            R.plurals
                                    .omr_answer_key_csv_preview_more_errors,
                            hiddenCount,
                            hiddenCount
                    ));
        }

        return message.toString();
    }

    private void preparePngZipDestination(
            OmrAnswerKeyPngZipPlan plan
    ) {
        if (backgroundExecutor == null || released) {
            showMessage(R.string.omr_answer_key_png_zip_error);
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try {
                String fileName = writePendingPngZipPlan(plan);
                runOnUiThread(() -> finishPngZipPlanPreparation(
                        fileName
                ));

            } catch (IOException | RuntimeException exception) {
                Log.e(
                        TAG,
                        "Falha ao preservar o plano do ZIP.",
                        exception
                );
                postToUi(() -> showBackgroundError(
                        R.string.omr_answer_key_png_zip_error
                ));
            }
        });
    }

    private String writePendingPngZipPlan(
            OmrAnswerKeyPngZipPlan plan
    ) throws IOException {
        File planFile = File.createTempFile(
                PNG_ZIP_PLAN_FILE_PREFIX,
                PNG_ZIP_PLAN_FILE_SUFFIX,
                getCacheDir()
        );
        boolean written = false;

        try (ObjectOutputStream output = new ObjectOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(planFile)
                )
        )) {
            output.writeObject(plan);
            output.flush();
            written = true;
            return planFile.getName();

        } finally {
            if (!written && planFile.exists() && !planFile.delete()) {
                Log.w(TAG, "Não foi possível remover o plano incompleto.");
            }
        }
    }

    private void finishPngZipPlanPreparation(
            String fileName
    ) {
        if (released || viewBinder == null) {
            deletePngZipPlanFileQuietly(fileName);
            return;
        }

        deletePngZipPlanFileQuietly(
                pendingPngZipPlanFileName
        );
        pendingPngZipPlanFileName = fileName;
        viewBinder.setBusy(false);

        try {
            pngZipDestinationLauncher.launch(
                    OmrAnswerKeyPngZipExporter
                            .DEFAULT_ARCHIVE_FILE_NAME
            );
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao abrir o destino do ZIP.", exception);
            deletePngZipPlanFileQuietly(fileName);
            pendingPngZipPlanFileName = null;
            showMessage(R.string.omr_answer_key_png_zip_error);
        }
    }

    private void handlePngZipDestination(
            @Nullable Uri destinationUri
    ) {
        String planFileName = pendingPngZipPlanFileName;
        pendingPngZipPlanFileName = null;

        if (destinationUri == null) {
            deletePngZipPlanFileQuietly(planFileName);
            showMessage(R.string.omr_answer_key_png_zip_cancelled);
            return;
        }

        OmrAnswerKeyPngZipExporter exporter = pngZipExporter;
        if (planFileName == null
                || exporter == null
                || backgroundExecutor == null) {
            deletePngZipPlanFileQuietly(planFileName);
            showMessage(R.string.omr_answer_key_png_zip_error);
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> loadAndWritePngZip(
                planFileName,
                destinationUri,
                exporter
        ));
    }

    private void loadAndWritePngZip(
            String planFileName,
            Uri destinationUri,
            OmrAnswerKeyPngZipExporter exporter
    ) {
        OmrAnswerKeyPngZipPlan plan;

        try {
            plan = readPendingPngZipPlan(planFileName);
        } catch (IOException | ClassNotFoundException
                 | RuntimeException exception) {
            Log.e(TAG, "Falha ao recuperar o plano do ZIP.", exception);
            postToUi(() -> finishPngZipWrite(false, 0));
            return;
        } finally {
            deletePngZipPlanFileQuietly(planFileName);
        }

        if (!plan.isReadyToExport()) {
            postToUi(() -> finishPngZipWrite(false, 0));
            return;
        }

        writePngZip(plan, destinationUri, exporter);
    }

    private OmrAnswerKeyPngZipPlan readPendingPngZipPlan(
            String fileName
    ) throws IOException, ClassNotFoundException {
        File planFile = resolvePngZipPlanFile(fileName);
        if (planFile == null || !planFile.isFile()) {
            throw new IOException(
                    "O plano temporário do ZIP não foi localizado."
            );
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new BufferedInputStream(
                        new FileInputStream(planFile)
                )
        )) {
            Object value = input.readObject();
            if (!(value instanceof OmrAnswerKeyPngZipPlan)) {
                throw new IOException(
                        "O arquivo temporário não contém um plano válido."
                );
            }
            return (OmrAnswerKeyPngZipPlan) value;
        }
    }

    private void writePngZip(
            OmrAnswerKeyPngZipPlan plan,
            Uri destinationUri,
            OmrAnswerKeyPngZipExporter exporter
    ) {
        File temporaryFile = null;
        boolean completed = false;
        boolean destinationOpened = false;

        try {
            temporaryFile = File.createTempFile(
                    "omr-cartoes-",
                    ".zip",
                    getCacheDir()
            );

            OmrAnswerKeyPngZipExporter.ExportSummary summary;
            try (OutputStream temporaryOutput =
                         new FileOutputStream(temporaryFile)) {
                summary = exporter.write(plan, temporaryOutput);
            }

            try (InputStream input = new FileInputStream(temporaryFile)) {
                OutputStream destinationOutput = getContentResolver()
                        .openOutputStream(destinationUri, "w");

                if (destinationOutput == null) {
                    throw new IOException(
                            "O destino do ZIP não pôde ser aberto."
                    );
                }

                destinationOpened = true;
                try (OutputStream output = destinationOutput) {
                    copyCheckingInterruption(input, output);
                    output.flush();
                }
            }

            completed = true;
            int exportedCount = summary.getExportedCount();
            postToUi(() -> finishPngZipWrite(
                    true,
                    exportedCount
            ));

        } catch (IOException | RuntimeException exception) {
            Log.e(TAG, "Falha ao gerar ZIP de cartões PNG.", exception);
            postToUi(() -> finishPngZipWrite(false, 0));

        } finally {
            if (!completed && destinationOpened) {
                deleteDestinationQuietly(destinationUri);
            }
            if (temporaryFile != null
                    && temporaryFile.exists()
                    && !temporaryFile.delete()) {
                Log.w(TAG, "Não foi possível remover o ZIP temporário.");
            }
        }
    }

    private static void copyCheckingInterruption(
            InputStream input,
            OutputStream output
    ) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException(
                        "A cópia do ZIP foi interrompida."
                );
            }
            output.write(buffer, 0, read);
        }
    }

    private void finishPngZipWrite(
            boolean success,
            int exportedCount
    ) {
        viewBinder.setBusy(false);
        if (success) {
            Toast.makeText(
                    this,
                    getResources().getQuantityString(
                            R.plurals.omr_answer_key_png_zip_success,
                            exportedCount,
                            exportedCount
                    ),
                    Toast.LENGTH_LONG
            ).show();
        } else {
            showMessage(R.string.omr_answer_key_png_zip_error);
        }
    }

    private void deleteDestinationQuietly(Uri destinationUri) {
        try {
            getContentResolver().delete(
                    destinationUri,
                    null,
                    null
            );
        } catch (RuntimeException exception) {
            Log.w(TAG, "O destino parcial do ZIP não pôde ser removido.",
                    exception);
        }
    }

    @Nullable
    private File resolvePngZipPlanFile(
            @Nullable String fileName
    ) {
        if (fileName == null
                || !fileName.startsWith(PNG_ZIP_PLAN_FILE_PREFIX)
                || !fileName.endsWith(PNG_ZIP_PLAN_FILE_SUFFIX)
                || fileName.contains("/")
                || fileName.contains("\\")) {
            return null;
        }
        return new File(getCacheDir(), fileName);
    }

    private void deletePngZipPlanFileQuietly(
            @Nullable String fileName
    ) {
        File planFile = resolvePngZipPlanFile(fileName);
        if (planFile != null
                && planFile.exists()
                && !planFile.delete()) {
            Log.w(TAG, "Não foi possível remover o plano temporário.");
        }
    }

    private void removeExpiredPngZipPlanFiles(
            @Nullable String preservedFileName
    ) {
        File[] files = getCacheDir().listFiles(
                (directory, name) -> name.startsWith(
                        PNG_ZIP_PLAN_FILE_PREFIX
                ) && name.endsWith(PNG_ZIP_PLAN_FILE_SUFFIX)
        );
        if (files == null) {
            return;
        }

        long expirationThreshold = System.currentTimeMillis()
                - PNG_ZIP_PLAN_MAXIMUM_AGE_MILLIS;
        for (File file : files) {
            if (file.getName().equals(preservedFileName)
                    || file.lastModified() >= expirationThreshold) {
                continue;
            }
            if (!file.delete()) {
                Log.w(TAG, "Não foi possível remover um plano expirado.");
            }
        }
    }

    private void showBackgroundError(int messageResource) {
        viewBinder.setBusy(false);
        showMessage(messageResource);
    }

    private void postToUi(Runnable action) {
        runOnUiThread(() -> {
            if (!released && viewBinder != null) {
                action.run();
            }
        });
    }

    private void selectAnswerKey(
            String answerKeyId,
            int answerKeyVersion
    ) {
        OmrAnswerKeyDefinition selectedAnswerKey;

        try {
            repository.selectActive(
                    answerKeyId,
                    answerKeyVersion
            );

            selectedAnswerKey = repository.findOrNull(
                    answerKeyId,
                    answerKeyVersion
            );

            if (selectedAnswerKey == null) {
                throw new IllegalStateException(
                        "O gabarito selecionado não foi recuperado."
                );
            }

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível selecionar o gabarito."
                            + " | id="
                            + answerKeyId
                            + " | versão="
                            + answerKeyVersion,
                    exception
            );

            showMessage(
                    R.string.omr_answer_key_list_select_error
            );

            return;
        }

        markRepositoryChanged();
        renderRepository();

        Toast.makeText(
                this,
                getString(
                        R.string
                        .omr_answer_key_list_selected_message,
                        selectedAnswerKey.getName()
                ),
                Toast.LENGTH_SHORT
        ).show();

        Log.i(
                TAG,
                "Gabarito ativo alterado"
                        + " | id="
                        + selectedAnswerKey.getId()
                        + " | versão="
                        + selectedAnswerKey.getVersion()
        );
    }

    private void confirmDeleteAnswerKey(
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName,
            boolean active
    ) {
        int messageResource = active
                ? R.string
                .omr_answer_key_list_delete_active_message
                : R.string
                .omr_answer_key_list_delete_message;

        new AlertDialog.Builder(this)
                .setTitle(
                        R.string.omr_answer_key_list_delete_title
                )
                .setMessage(
                        getString(
                                messageResource,
                                answerKeyName
                        )
                )
                .setNegativeButton(
                        R.string
                        .omr_answer_key_list_delete_cancel,
                        null
                )
                .setPositiveButton(
                        R.string
                        .omr_answer_key_list_delete_confirm,
                        (dialog, which) -> deleteAnswerKey(
                                answerKeyId,
                                answerKeyVersion,
                                answerKeyName
                        )
                )
                .show();
    }

    private void deleteAnswerKey(
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName
    ) {
        boolean deleted;

        try {
            deleted = repository.delete(
                    answerKeyId,
                    answerKeyVersion
            );

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível excluir o gabarito."
                            + " | id="
                            + answerKeyId
                            + " | versão="
                            + answerKeyVersion,
                    exception
            );

            showMessage(
                    R.string.omr_answer_key_list_delete_error
            );

            return;
        }

        if (!deleted) {
            showMessage(
                    R.string.omr_answer_key_list_delete_error
            );

            renderRepository();
            return;
        }

        markRepositoryChanged();
        renderRepository();

        Toast.makeText(
                this,
                getString(
                        R.string
                        .omr_answer_key_list_deleted_message,
                        answerKeyName
                ),
                Toast.LENGTH_SHORT
        ).show();

        Log.i(
                TAG,
                "Gabarito excluído"
                        + " | id="
                        + answerKeyId
                        + " | versão="
                        + answerKeyVersion
        );
    }

    private void renderRepository() {
        try {
            List<OmrAnswerKeyDefinition> answerKeys =
                    repository.loadAll();

            OmrAnswerKeyDefinition activeAnswerKey =
                    repository.loadActiveOrNull();

            viewBinder.render(
                    OmrAnswerKeyListViewState.from(
                            answerKeys,
                            activeAnswerKey
                    )
            );

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível apresentar o repositório.",
                    exception
            );

            viewBinder.render(
                    OmrAnswerKeyListViewState.from(
                            Collections
                                    .<OmrAnswerKeyDefinition>emptyList(),
                            null
                    )
            );

            showMessage(
                    R.string.omr_answer_key_list_load_error
            );
        }
    }

    private void markRepositoryChanged() {
        repositoryChanged = true;
        updateActivityResult();
    }

    private void updateActivityResult() {
        Intent resultData = new Intent().putExtra(
                EXTRA_REPOSITORY_CHANGED,
                true
        );

        try {
            OmrAnswerKeyDefinition activeAnswerKey =
                    repository.loadActiveOrNull();

            if (activeAnswerKey != null) {
                resultData.putExtra(
                        EXTRA_ACTIVE_ANSWER_KEY,
                        activeAnswerKey
                );
            }

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível anexar o gabarito ativo"
                            + " ao resultado da tela.",
                    exception
            );
        }

        setResult(
                Activity.RESULT_OK,
                resultData
        );
    }

    private void showMessage(
            int messageResource
    ) {
        Toast.makeText(
                this,
                messageResource,
                Toast.LENGTH_LONG
        ).show();
    }

    @SuppressWarnings("deprecation")
    private static List<OmrAnswerKeyDefinition> restorePendingExport(
            @Nullable Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return Collections.emptyList();
        }

        Serializable value = savedInstanceState.getSerializable(
                STATE_PENDING_EXPORT
        );

        if (!(value instanceof ArrayList<?>)) {
            return Collections.emptyList();
        }

        List<OmrAnswerKeyDefinition> restored =
                new ArrayList<>();

        for (Object item : (ArrayList<?>) value) {
            if (!(item instanceof OmrAnswerKeyDefinition)) {
                return Collections.emptyList();
            }

            restored.add((OmrAnswerKeyDefinition) item);
        }

        return Collections.unmodifiableList(restored);
    }

    @Nullable
    private String restorePendingPngZipPlanFileName(
            @Nullable Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return null;
        }

        String fileName = savedInstanceState.getString(
                STATE_PENDING_PNG_ZIP_PLAN_FILE
        );
        File planFile = resolvePngZipPlanFile(fileName);
        return planFile != null && planFile.isFile()
                ? fileName
                : null;
    }

    private void dismissCsvPreviewDialog() {
        AlertDialog dialog = csvPreviewDialog;
        csvPreviewDialog = null;

        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
    }

    private void dismissPngZipPreviewDialog() {
        AlertDialog dialog = pngZipPreviewDialog;
        pngZipPreviewDialog = null;

        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
    }

    private void dismissExportSelectionDialog() {
        AlertDialog dialog = exportSelectionDialog;
        exportSelectionDialog = null;

        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putBoolean(
                STATE_REPOSITORY_CHANGED,
                repositoryChanged
        );

        outState.putSerializable(
                STATE_PENDING_EXPORT,
                new ArrayList<>(pendingExportAnswerKeys)
        );

        outState.putString(
                STATE_PENDING_PNG_ZIP_PLAN_FILE,
                pendingPngZipPlanFileName
        );

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        released = true;

        AlertDialog dialog = questionCountDialog;
        questionCountDialog = null;

        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }

        AlertDialog optionDialog = optionCountDialog;
        optionCountDialog = null;

        if (optionDialog != null) {
            optionDialog.setOnDismissListener(null);
            optionDialog.dismiss();
        }

        AlertDialog distributionDialog =
                questionsPerColumnDialog;
        questionsPerColumnDialog = null;

        if (distributionDialog != null) {
            distributionDialog.setOnDismissListener(null);
            distributionDialog.dismiss();
        }

        dismissCsvPreviewDialog();
        dismissPngZipPreviewDialog();
        dismissExportSelectionDialog();

        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
            backgroundExecutor = null;
        }

        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        repository = null;
        registrationService = null;
        csvParser = null;
        csvExporter = null;
        pngZipPlanner = null;
        pngZipExporter = null;
        pendingExportAnswerKeys = Collections.emptyList();

        if (isFinishing()) {
            deletePngZipPlanFileQuietly(
                    pendingPngZipPlanFileName
            );
        }
        pendingPngZipPlanFileName = null;

        super.onDestroy();
    }

    private static final class CsvTooLargeException
            extends Exception {
    }
}
